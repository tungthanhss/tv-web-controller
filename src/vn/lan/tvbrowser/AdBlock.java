package vn.lan.tvbrowser;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/** Host rules stay independent of Android so their boundaries can be checked with javac/java. */
public final class AdBlock {
    private final Set<String> hosts = new HashSet<>();
    private static final Pattern AD_PATH = Pattern.compile("(?:^|/)(?:ads|adserver|adservice|advertisements|pagead)(?:/|$)", Pattern.CASE_INSENSITIVE);
    public AdBlock(Reader source) throws IOException {
        BufferedReader reader = new BufferedReader(source);
        for(String line;(line=reader.readLine())!=null;) {
            String[] fields=line.trim().split("\\s+");
            if(fields.length>=2 && (fields[0].equals("0.0.0.0")||fields[0].equals("127.0.0.1"))) {
                String host=fields[1].toLowerCase(Locale.ROOT);
                if(host.indexOf('.')>0 && !host.equals("localhost.localdomain"))hosts.add(host);
            }
        }
        // Observed sponsored destinations on chophim.cam, kept specific to avoid blocking shared shorteners.
        Collections.addAll(hosts,"dbvn888.com","789b668.me","hpasoj.cc","khuyenmaitic88.space","shortlink.linkroyal.workers.dev","analytics.chophim.app");
    }
    public int size() { return hosts.size(); }
    public boolean blocks(String host,String path) {
        return blocks(host,path,false);
    }
    public boolean blocks(String host,String path,boolean mainFrame) {
        if(host==null) return false;
        host=host.toLowerCase(Locale.ROOT);
        // Only subresources use generic path rules: a document named /ads/ may be legitimate content.
        if(!mainFrame && path!=null && AD_PATH.matcher(path).find()) return true;
        for(String name=host;name.indexOf('.')>=0;name=name.substring(name.indexOf('.')+1)) if(hosts.contains(name)) return true;
        return false;
    }
    public static void main(String[] args) throws Exception {
        AdBlock rules = new AdBlock(new FileReader(args[0]));
        if(rules.size()<3000) throw new AssertionError("Missing upstream host rules");
        String[][] blocked={{"doubleclick.net","/"},{"ads.doubleclick.net","/video"},{"DBVN888.COM","/"},{"any-site.example","/ads/banner.gif"},{"other-site.example","/pagead/script.js"},{"third-site.example","/x/adserver/request"},{"chophim.cam","/ads/webp/cat.webp"}};
        String[][] allowed={{"notdoubleclick.net","/"},{"doubleclick.net.example.com","/"},{"any-site.example","/downloads/movie.mp4"},{"another-site.example","/shadow/player.js"},{"chophim.cam","/_next/static/chunks/player.js"},{"chophim.cam","/xem/movie"},{"localhost","/player.html"},{"127.0.0.1","/player.html"},{"test-streams.mux.dev","/x36xhzz.m3u8"},{"dash.akamaized.net","/manifest.mpd"}};
        for(String[] item:blocked) if(!rules.blocks(item[0],item[1])) throw new AssertionError(Arrays.toString(item));
        for(String[] item:allowed) if(rules.blocks(item[0],item[1])) throw new AssertionError(Arrays.toString(item));
        if(rules.blocks("example.com","/ads/guide",true))throw new AssertionError("Document path false positive");
        System.out.println("PASS: "+rules.size()+" ad hosts; subdomain boundaries and video/site resources");
    }
}
