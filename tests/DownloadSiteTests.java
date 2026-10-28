package com.zizai.watermark;

import java.nio.file.Files;
import java.nio.file.Path;

/** Checks routing, media handoff and the actual form script used by the WebView. */
public final class DownloadSiteTests {
    private static int checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception {
        check(DownloadSites.ALL.size()==12,"twelve distinct routes; Cobalt not duplicated");
        check(DownloadSites.SEARCH.size()==6,"six actual website providers; no GitHub repository pretends to parse");
        java.util.Set<String> urls=new java.util.HashSet<>();
        for(DownloadSites.Site site:DownloadSites.ALL){
            check(urls.add(site.url),"unique route "+site.name);
            check(DownloadSites.host(site.url).length()>0,"valid HTTPS route "+site.name);
            check(site.searchable==DownloadSites.SEARCH.contains(site),"search eligibility "+site.name);
        }
        check(DownloadSites.verificationNames().length==7&&DownloadSites.verificationNames()[6].equals(DownloadSites.SEARCH.get(5).name),"verification chooser covers all providers");
        check(DownloadSites.ALL.stream().anyMatch(site->site.matches("视频号")&&!site.searchable),"WeChat desktop tools discoverable");
        check(DownloadSites.ALL.stream().anyMatch(site->site.matches("Telegram")&&!site.searchable),"Telegram script discoverable");
        check(DownloadSites.ALL.stream().anyMatch(site->site.matches("Youwee")),"Youwee full repository resolved");
        String[][] routes={{"https://x.com/a/status/12","Cobalt"},{"https://twitter.com/a/status/12","Cobalt"},{"https://v.douyin.com/123/","IdeaFlow 解析"},{"https://vm.tiktok.com/abc/","TikTokio"},{"https://www.xiaohongshu.com/explore/abc","GreenVideo"},{"https://xhslink.com/a/abc","GreenVideo"},{"https://b23.tv/abc","GreenVideo"},{"https://www.bilibili.com/video/BV1x","GreenVideo"}};
        for(String[] row:routes)check(DownloadSites.recommended(row[0]).name.equals(row[1]),"route "+row[0]);
        for(String url:new String[]{"https://x.com.evil.org/","https://evilx.com/","https://x.com@evil.org/","http://x.com/",""})check(DownloadSites.recommended(url)==null,"no lookalike match");
        check(DownloadSites.ALL.get(0).matches(" 推特 ")&&DownloadSites.ALL.get(0).matches("COBALT.TOOLS"),"name and localized search");
        check(DownloadSites.ALL.get(1).matches("抖音")&&DownloadSites.ALL.get(1).matches("海外版"),"Douyin and TikTok searchable");
        check(DownloadSites.ALL.get(2).matches("B站")&&DownloadSites.ALL.get(2).matches("小红书"),"XHS and Bilibili searchable");
        check(!DownloadSites.ALL.get(0).owns("https://cobalt.tools.evil.org/"),"form fills original website only");
        check(DownloadSites.mediaKind("https://cdn.example.org/get?id=1&sig=a%2Fb","video/mp4","").equals("VIDEO"),"opaque signed media MIME");
        check(DownloadSites.mediaKind("https://cdn.example.org/get","application/octet-stream","attachment; filename=\"clip.mp4\"").equals("VIDEO"),"attachment filename media");
        check(DownloadSites.mediaKind("https://cdn.example.org/a.M4A?token=x","","").equals("AUDIO"),"signed extension audio");
        check(DownloadSites.mediaKind("https://cdn.example.org/img","image/png","").equals("IMAGE"),"image download");
        for(String mime:new String[]{"text/html","application/javascript","application/vnd.android.package-archive"})check(DownloadSites.mediaKind("https://cdn.example.org/clip.mp4",mime,"").isEmpty(),"nonmedia response blocked");
        for(String url:new String[]{"blob:https://cobalt.tools/abc","http://cdn.example.org/a.mp4","https://user:pass@cdn.example.org/a.mp4"})check(DownloadSites.mediaKind(url,"video/mp4","").isEmpty(),"unsafe URL excluded");
        org.json.JSONArray configs=new org.json.JSONArray();for(DownloadSites.Site site:DownloadSites.SEARCH)configs.put(new org.json.JSONObject().put("url",site.url).put("input",site.input).put("button",site.button).put("buttonText",site.buttonText));
        if(args.length>1)Files.writeString(Path.of(args[1]),configs.toString());
        Files.writeString(Path.of(args[0]),DownloadSites.fillScript("https://x.com/a/status/1?text='\\\"\n中文"));
        System.out.println("PASS website download routes: "+checks+" checks");
    }
}
