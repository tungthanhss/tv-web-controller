package vn.lan.tvbrowser;

import android.app.Activity;
import android.os.*;
import android.graphics.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BrowserActivity extends Activity {
    private WebView web;
    private FrameLayout frame;
    private TextView bar;
    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private Pointer pointer;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ServerSocket server;
    private final ExecutorService clients = Executors.newFixedThreadPool(4);
    private volatile String currentUrl = "", title = "LAN Browser", error = "";
    private volatile boolean loading;
    private volatile boolean pageFullscreen, videoFullscreen;
    private volatile String mediaState = "{}";
    private String mediaScript;
    private String adScript;
    private AdBlock adBlock;
    private final AtomicInteger blockedAds = new AtomicInteger();
    private volatile String cosmeticAds="{}";
    private final Runnable mediaPoll = new Runnable() {
        @Override public void run() { cleanAds();mediaAction("state", 0); ui.postDelayed(this, 1000); }
    };
    private String address;
    private float cursorX = 400, cursorY = 240;
    private static final int PORT = 8765;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        address = "http://" + localIp() + ":" + PORT;
        LinearLayout root = new LinearLayout(this); root.setOrientation(1); root.setBackgroundColor(0xff101521);
        bar = new TextView(this); bar.setTextColor(0xffdce6fa); bar.setTextSize(14); bar.setPadding(20, 8, 20, 8);
        bar.setText("LAN Browser  •  Điện thoại: " + address);
        root.addView(bar);
        frame = new FrameLayout(this); root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
        web = new WebView(this); frame.addView(web, new FrameLayout.LayoutParams(-1, -1));
        pointer = new Pointer(); frame.addView(pointer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        try (InputStream script = getAssets().open("media-control.js"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];for(int n;(n=script.read(buffer))!=-1;)bytes.write(buffer,0,n);
            mediaScript=bytes.toString("UTF-8");
        } catch(IOException e) { throw new IllegalStateException(e); }
        try (InputStream rules=getAssets().open("ad-hosts.txt");InputStream script=getAssets().open("adblock.js");ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            adBlock=new AdBlock(new InputStreamReader(rules,StandardCharsets.UTF_8));
            byte[] buffer=new byte[4096];for(int n;(n=script.read(buffer))!=-1;)bytes.write(buffer,0,n);adScript=bytes.toString("UTF-8");
        } catch(IOException e) { throw new IllegalStateException(e); }
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(true);settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setLoadWithOverviewMode(true); settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true); settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r) {
                if(adBlock.blocks(r.getUrl().getHost(),r.getUrl().getPath(),r.isForMainFrame())) {
                    blockedAds.incrementAndGet();
                    return new WebResourceResponse("text/plain","UTF-8",204,"No Content",Collections.singletonMap("Access-Control-Allow-Origin","*"),new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if(adBlock.blocks(r.getUrl().getHost(),r.getUrl().getPath(),r.isForMainFrame())) {blockedAds.incrementAndGet();return true;}
                if (r.isForMainFrame() && isVideoUrl(r.getUrl().toString())) { openUrl(r.getUrl().toString());return true; }
                return !isWeb(r.getUrl().toString());
            }
            @Override public void onPageStarted(WebView v, String url, Bitmap icon) {
                loading = true; currentUrl = url; error = ""; pageFullscreen=false;mediaState="{}";cosmeticAds="{}";updateFullscreen();
                Uri uri=Uri.parse(url);if(uri.getPath()!=null && uri.getPath().equals("/player.html") && "127.0.0.1".equals(uri.getHost())) currentUrl=uri.getQueryParameter("src");
            }
            @Override public void onPageCommitVisible(WebView v,String url) {cleanAds();}
            @Override public void onPageFinished(WebView v, String url) { loading = false; title = v.getTitle();cleanAds();mediaAction("state",0); }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) { loading = false; error = e.getDescription().toString(); }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onCreateWindow(WebView v,boolean dialog,boolean gesture,Message message) {blockedAds.incrementAndGet();return false;}
            @Override public void onReceivedTitle(WebView v, String t) { title = t; }
            @Override public void onShowCustomView(View v, CustomViewCallback cb) {
                if (fullscreen != null) { cb.onCustomViewHidden(); return; }
                fullscreen = v; fullscreenCallback = cb; videoFullscreen=true;
                v.setBackgroundColor(Color.BLACK); frame.addView(v, new FrameLayout.LayoutParams(-1, -1)); updateFullscreen();
            }
            @Override public void onHideCustomView() { hideFullscreen(); }
        });
        home();
        ui.post(mediaPoll);
        new Thread(() -> {
            try {
                server = new ServerSocket(); server.setReuseAddress(true); server.bind(new InetSocketAddress(PORT));
                while (!server.isClosed()) {
                    Socket socket = server.accept(); socket.setSoTimeout(4000);
                    clients.execute(() -> serve(socket));
                }
            } catch (IOException e) { if (server == null || !server.isClosed()) ui.post(() -> bar.setText("Không mở được điều khiển: " + e.getMessage())); }
        }, "phone-remote").start();
    }

    private static boolean isWeb(String url) { String s = Uri.parse(url).getScheme(); return "http".equalsIgnoreCase(s) || "https".equalsIgnoreCase(s); }
    private void cleanAds() {
        web.evaluateJavascript(adScript,result->{try{cosmeticAds=new JSONObject(result).toString();}catch(Exception ignored){}});
    }
    private static boolean isVideoUrl(String url) {
        String path=Uri.parse(url).getPath();return isWeb(url) && path!=null && path.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|m4v|webm|ogv|m3u8|mpd|mov|3gp)");
    }
    private void openUrl(String url) {
        exitFullscreen();
        web.loadUrl(isVideoUrl(url) ? "http://127.0.0.1:"+PORT+"/player.html?src="+Uri.encode(url) : url);
    }
    private void updateFullscreen() {
        bar.setVisibility(pageFullscreen||videoFullscreen ? View.GONE : View.VISIBLE);
        pointer.setVisibility(pageFullscreen||videoFullscreen ? View.GONE : View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(5894);
    }
    private void mediaAction(String action, double value) {
        web.evaluateJavascript("("+mediaScript+")("+JSONObject.quote(action)+","+value+")", result -> {
            try {
                JSONObject state=new JSONObject(result);mediaState=state.toString();
                if (!action.equals("state")) { error=state.optString("error","");pageFullscreen=state.optBoolean("fullscreen");updateFullscreen(); }
                if (action.equals("toggle") && !state.optBoolean("found")) {
                    dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,85));dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,85));
                }
            } catch(Exception ignored) {}
        });
    }
    private void exitFullscreen() {
        hideFullscreen();pageFullscreen=false;mediaAction("exit",0);updateFullscreen();
    }
    private String localIp() {
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces()))
                for (InetAddress a : Collections.list(n.getInetAddresses()))
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
        } catch (Exception ignored) {}
        return "192.168.2.34";
    }
    private void home() {
        exitFullscreen();
        web.loadDataWithBaseURL("https://lan-browser.invalid/", "<!doctype html><html lang='vi'><meta charset='utf-8'><title>LAN Browser</title><meta name='viewport' content='width=device-width'><style>body{background:#101521;color:#f1f5ff;font:20px sans-serif;padding:4vh 7vw}small{color:#a8b7d1}h1{font-size:48px;letter-spacing:-2px;margin:24px 0}section{border:1px solid #334156;border-radius:24px;padding:24px;margin-top:24px;max-width:760px}strong{color:#a5b4fc;letter-spacing:12px;font-size:40px}p{line-height:1.6}</style><small>LAN BROWSER / SẴN SÀNG KẾT NỐI</small><h1>Màn hình lớn.<br>Điều khiển trong tay.</h1><p>Mở địa chỉ dưới đây trên điện thoại cùng Wi-Fi.</p><section>" + address + "<p>Mở địa chỉ này là điều khiển được ngay.</p><small>Nhập website, dùng bàn di chuột và gõ từ điện thoại.</small></section></html>", "text/html", "UTF-8", null);
    }
    private void hideFullscreen() {
        if (fullscreen != null) { View old=fullscreen;WebChromeClient.CustomViewCallback cb=fullscreenCallback;fullscreen=null;fullscreenCallback=null;videoFullscreen=false;frame.removeView(old);cb.onCustomViewHidden();updateFullscreen(); }
    }
    @Override public void onBackPressed() { if (fullscreen != null || pageFullscreen) exitFullscreen(); else if (web.canGoBack()) web.goBack(); else home(); }
    @Override public void onDestroy() {
        try { if (server != null) server.close(); } catch (IOException ignored) {}
        ui.removeCallbacksAndMessages(null);clients.shutdownNow(); web.destroy(); super.onDestroy();
    }
    private class Pointer extends View {
        private final Paint paint = new Paint(3);
        Pointer() { super(BrowserActivity.this); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            paint.setColor(0xcc101521); canvas.drawCircle(cursorX, cursorY, 10, paint);
            paint.setColor(0xffa5b4fc); canvas.drawCircle(cursorX, cursorY, 6, paint);
        }
    }
    private void command(JSONObject cmd) throws Exception {
        String action = cmd.getString("action");
        // Validate before posting; all WebView operations stay on the main thread.
        if (!Arrays.asList("open","back","forward","reload","home","move","click","scroll","text","key","media","fullscreen","seek","mute").contains(action)) throw new IllegalArgumentException("Lệnh không hợp lệ");
        if (action.equals("open") && !isWeb(cmd.getString("url"))) throw new IllegalArgumentException("Chỉ mở địa chỉ HTTP/HTTPS");
        if (action.equals("text") && cmd.getString("text").length() > 8000) throw new IllegalArgumentException("Nội dung quá dài");
        if (action.equals("key") && !Arrays.asList("up","down","left","right","enter","delete","play","volup","voldown").contains(cmd.getString("key"))) throw new IllegalArgumentException("Phím không hợp lệ");
        ui.post(() -> {
            try {
                switch (action) {
                    case "open": openUrl(cmd.getString("url")); break;
                    case "back": onBackPressed(); break;
                    case "forward": if (web.canGoForward()) web.goForward(); break;
                    case "reload": web.reload(); break;
                    case "home": home(); break;
                    case "move":
                        cursorX = Math.max(0, Math.min(web.getWidth()-1, cursorX + bounded(cmd.optDouble("dx"), -2000, 2000)));
                        cursorY = Math.max(0, Math.min(web.getHeight()-1, cursorY + bounded(cmd.optDouble("dy"), -2000, 2000)));
                        pointer.invalidate(); break;
                    case "click":
                        long now = SystemClock.uptimeMillis();
                        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, cursorX, cursorY, 0);
                        MotionEvent up = MotionEvent.obtain(now, now+60, MotionEvent.ACTION_UP, cursorX, cursorY, 0);
                        View touchTarget=fullscreen!=null?fullscreen:web;
                        touchTarget.dispatchTouchEvent(down); touchTarget.dispatchTouchEvent(up); down.recycle(); up.recycle(); break;
                    case "scroll": web.evaluateJavascript("window.scrollBy(0," + bounded(cmd.optDouble("dy"), -3000, 3000) + ")", null); break;
                    case "text":
                        String value = JSONObject.quote(cmd.getString("text"));
                        web.evaluateJavascript("(()=>{const e=document.activeElement;if(!e)return;if(e.isContentEditable){e.textContent="+value+"}else if(e.tagName==='INPUT'||e.tagName==='TEXTAREA'){const p=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;Object.getOwnPropertyDescriptor(p,'value').set.call(e,"+value+")}else{return}e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));})()", null); break;
                    case "media": mediaAction("toggle",0);break;
                    case "seek": mediaAction("seek",bounded(cmd.optDouble("seconds"),-3600,3600));break;
                    case "mute": mediaAction("mute",0);break;
                    case "fullscreen": if (fullscreen!=null || pageFullscreen) exitFullscreen();else mediaAction("fullscreen",0);break;
                    case "key":
                        int key;
                        switch (cmd.getString("key")) {
                            case "up":key=19;break;case "down":key=20;break;case "left":key=21;break;case "right":key=22;break;
                            case "delete":key=67;break;case "play":key=85;break;case "volup":key=24;break;case "voldown":key=25;break;default:key=66;
                        }
                        dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key)); dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key)); break;
                }
            } catch (Exception e) { error = e.getMessage(); }
        });
    }
    private static float bounded(double value, int min, int max) { return Double.isFinite(value) ? (float)Math.max(min, Math.min(max,value)) : 0; }
    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int b; (b=in.read())!=-1;) { if (b==10) return bytes.toString("UTF-8").replace("\r", ""); bytes.write(b); if(bytes.size()>8192) throw new IOException("Header too long"); }
        throw new EOFException();
    }
    private void serve(Socket socket) {
        try (Socket connection = socket) {
            InputStream in = connection.getInputStream();
            String[] request = line(in).split(" ");
            if (request.length != 3) return;
            Map<String,String> headers = new HashMap<>();
            for (int i=0;;i++) {
                String h = line(in); if(h.isEmpty()) break; if(i>40) throw new IOException("Too many headers");
                int colon=h.indexOf(':'); if(colon>0) headers.put(h.substring(0,colon).toLowerCase(Locale.ROOT),h.substring(colon+1).trim());
            }
            String path=request[1].split("\\?",2)[0], method=request[0];
            int length=Integer.parseInt(headers.getOrDefault("content-length","0"));
            if(length<0||length>65536) { reply(connection,413,"application/json","{\"error\":\"Request too large\"}".getBytes(StandardCharsets.UTF_8));return; }
            // Drain bounded request bodies before replying, including rejected authentication.
            // Otherwise closing a socket with unread POST data can reset the response.
            byte[] data = new byte[length]; int read=0;
            while(read<length) { int n=in.read(data,read,length-read);if(n<0)throw new EOFException();read+=n; }
            String origin=headers.get("origin");
            if (origin!=null && !origin.equals("http://"+headers.get("host"))) { reply(connection,403,"application/json","{\"error\":\"Origin rejected\"}".getBytes(StandardCharsets.UTF_8)); return; }
            boolean api=path.equals("/command")||path.equals("/state");
            if (api) {
                try {
                    JSONObject result=new JSONObject();
                    if (path.equals("/state") && method.equals("GET")) {
                        result.put("url",currentUrl).put("title",title).put("loading",loading).put("error",error)
                            .put("fullscreen",pageFullscreen||videoFullscreen).put("nativeFullscreen",videoFullscreen).put("media",new JSONObject(mediaState))
                            .put("adblock",new JSONObject(cosmeticAds).put("active",true).put("blockedRequests",blockedAds.get()).put("hostRules",adBlock.size()));
                    } else if (method.equals("POST") && headers.getOrDefault("content-type", "").startsWith("application/json")) {
                        if(length<2) throw new IllegalArgumentException("Yêu cầu không có nội dung");
                        JSONObject body=new JSONObject(new String(data,StandardCharsets.UTF_8));
                        command(body);result.put("ok",true);
                    } else throw new IllegalArgumentException("Yêu cầu không hợp lệ");
                    reply(connection,200,"application/json",result.toString().getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) { reply(connection,400,"application/json",new JSONObject().put("error",e.getMessage()).toString().getBytes(StandardCharsets.UTF_8)); }
            } else if (method.equals("GET") && Arrays.asList("/","/index.html","/remote.js","/style.css","/player.html","/player.css","/player.js","/vendor/hls.min.js","/vendor/dash.all.min.js").contains(path)) {
                String file=path.equals("/")?"index.html":path.substring(1);
                try(InputStream asset=getAssets().open(file);ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                    byte[] buffer=new byte[4096];for(int n;(n=asset.read(buffer))!=-1;)bytes.write(buffer,0,n);
                    reply(connection,200,file.endsWith(".js")?"text/javascript":file.endsWith(".css")?"text/css":"text/html",bytes.toByteArray());
                }
            } else reply(connection,404,"text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
        } catch(Exception ignored) { /* Disconnected clients cannot receive an error response. */ }
    }
    private static void reply(Socket socket,int status,String type,byte[] body) throws IOException {
        OutputStream out=socket.getOutputStream();
        String header="HTTP/1.1 "+status+" "+(status==200?"OK":"Error")+"\r\nContent-Type: "+type+"; charset=utf-8\r\nContent-Length: "+body.length+"\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'self'; style-src 'self'; script-src 'self'; media-src http: https: blob:; connect-src http: https:; worker-src 'self' blob:; frame-ancestors 'none'; base-uri 'none'; form-action 'self'\r\nConnection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));out.write(body);out.flush();
    }
}
