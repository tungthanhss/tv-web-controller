package vn.lan.tvbrowser;

import java.io.*;
import java.util.*;

/** Host rules stay independent of Android so their boundaries can be checked with javac/java. */
public final class AdBlock {
    private final Set<String> hosts = new HashSet<>();
    public AdBlock(Reader source) throws IOException {
        BufferedReader reader = new BufferedReader(source);
        for(String line;(line=reader.readLine())!=null;) {
            String[] fields=line.trim().split("\\s+");
            if(fields.length>=2 && (fields[0].equals("0.0.0.0")||fields[0].equals("127.0.0.1"))) hosts.add(fields[1].toLowerCase(Locale.ROOT));
        }
        // Observed sponsored destinations on chophim.cam, kept specific to avoid blocking shared shorteners.
        Collections.addAll(hosts,"dbvn888.com","789b668.me","hpasoj.cc","khuyenmaitic88.space","shortlink.linkroyal.workers.dev","analytics.chophim.app");
    }
    public int size() { return hosts.size(); }
    public boolean blocks(String host,String path) {
        if(host==null) return false;
        host=host.toLowerCase(Locale.ROOT);
        if(isChoPhim(host) && path!=null && path.toLowerCase(Locale.ROOT).startsWith("/ads/")) return true;
        for(String name=host;name.indexOf('.')>=0;name=name.substring(name.indexOf('.')+1)) if(hosts.contains(name)) return true;
        return false;
    }
    private static boolean isChoPhim(String host) { return host.equals("chophim.cam")||host.endsWith(".chophim.cam")||host.equals("chophim.lat")||host.endsWith(".chophim.lat"); }
    public static void main(String[] args) throws Exception {
        AdBlock rules = new AdBlock(new FileReader(args[0]));
        if(rules.size()<3000) throw new AssertionError("Missing upstream host rules");
        String[][] blocked={{"doubleclick.net","/"},{"ads.doubleclick.net","/video"},{"DBVN888.COM","/"},{"chophim.cam","/ads/webp/cat.webp"},{"chophim.lat","/ads/banner.gif"}};
        String[][] allowed={{"notdoubleclick.net","/"},{"doubleclick.net.example.com","/"},{"chophim.cam","/_next/static/chunks/player.js"},{"chophim.cam","/xem/movie"},{"127.0.0.1","/player.html"},{"test-streams.mux.dev","/x36xhzz.m3u8"},{"dash.akamaized.net","/manifest.mpd"}};
        for(String[] item:blocked) if(!rules.blocks(item[0],item[1])) throw new AssertionError(Arrays.toString(item));
        for(String[] item:allowed) if(rules.blocks(item[0],item[1])) throw new AssertionError(Arrays.toString(item));
        System.out.println("PASS: "+rules.size()+" ad hosts; subdomain boundaries and video/site resources");
    }
}
