package com.zizai.watermark;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.*;

public final class NativeSourceTests {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    interface Work {void run()throws Exception;}
    static void rejects(Work work)throws Exception{try{work.run();throw new AssertionError("invalid input accepted");}catch(IOException|java.net.URISyntaxException expected){checks++;}}
    public static void main(String[] args)throws Exception {
        int available=0;for(DownloadSites.Site site:DownloadSites.ALL)if(!site.searchable){
            NativeSourceTools.Mode mode=NativeSourceTools.mode(site.url);if(mode!=NativeSourceTools.Mode.UNAVAILABLE)available++;
            if(site.url.contains("qiye45")||site.url.contains("ltaoo"))check(mode==NativeSourceTools.Mode.UNAVAILABLE,"desktop WeChat dependency is never labelled as ported");
        }check(available==4,"four capability adapters; two desktop ports unfinished");
        check(NativeSourceTools.mode("https://github.com/putyy/res-downloader")==NativeSourceTools.Mode.WEB_CAPTURE,"local web capture action");
        check(NativeSourceTools.mode("https://github.com/nexmoe/VidBee")==NativeSourceTools.Mode.ENGINE,"native shared engine action");
        check(NativeSourceTools.videoFormat(0).equals("bestvideo[ext=mp4][vcodec^=avc1]+bestaudio[ext=m4a]/best[ext=mp4]/bestvideo+bestaudio/best"),"default native format unchanged");
        for(int h:new int[]{1080,720,480,360})for(String alternative:NativeSourceTools.videoFormat(h).split("/"))check(alternative.contains("[height<="+h+"]"),"every fallback obeys requested height");
        check(NativeSourceTools.height(9999)==0,"unsupported preference cannot inject a native format");
        String source="https://www.youtube.com/watch?v=AbCdEf12345&list=PL1&index=3";
        check(NativeSourceTools.normalize(source).equals("https://www.youtube.com/watch?v=AbCdEf12345"),"Youwee single-watch URL does not enqueue a playlist");
        String signed="https://cdn.example.org/movie.mp4?sig=a%2Fb&x=1";
        check(NativeSourceTools.normalize(signed).equals(signed),"other signed URLs untouched");
        NativeSourceTools.Request r=NativeSourceTools.youwee("youwee://download?v=1&url="+URLEncoder.encode(source,"UTF-8")+"&action=queue_only&media=audio&quality=128");
        check(r.audio&&r.height==0&&r.action.equals("queue_only")&&r.url.endsWith("AbCdEf12345"),"official Youwee v1 fields decoded once");
        String base="youwee://download?v=1&url="+URLEncoder.encode(signed,"UTF-8");
        check(NativeSourceTools.youwee(base+"&action=download_now&media=video&quality=720").height==720,"actual Youwee numeric quality and action supported");
        check(NativeSourceTools.youwee(base).url.equals(signed),"protocol retains encoded CDN signature");
        rejects(()->NativeSourceTools.youwee(base+"&url=https://evil.example/"));
        rejects(()->NativeSourceTools.youwee(base.replace("v=1","v=2")));
        rejects(()->NativeSourceTools.youwee(base+"&media=shell"));
        rejects(()->NativeSourceTools.youwee(base+"&action=delete"));
        rejects(()->NativeSourceTools.youwee("youwee://download?v=1&url=javascript:alert(1)"));
        rejects(()->NativeSourceTools.youwee("youwee://evil?v=1&url=https://example.org/"));
        rejects(()->NativeSourceTools.youwee("x".repeat(8193)));
        check(TelegramPost.page("https://t.me/s/sample_channel/359?embed=1").equals("https://t.me/sample_channel/359"),"public Telegram preview normalized to selected post");
        for(String url:new String[]{"https://t.me/c/123/456","https://t.me/+invite","https://t.me/joinchat/123","https://t.me/sample_channel","https://t.me.evil.org/sample_channel/359","http://t.me/sample_channel/359"})rejects(()->TelegramPost.page(url));
        String post="https://t.me/sample_channel/359";
        String html="<div data-post=\"sample_channel/359\"><video src=\"https://cdn.example.org/v.mp4?x=1&amp;sig=a%2Fb\"></video>"+
          "<a class=\"tgme_widget_message_photo_wrap\" style=\"background-image:url('https://cdn.example.org/p.jpg')\"></a>"+
          "<i class=\"tgme_widget_message_video_thumb\" style=\"background-image:url('https://cdn.example.org/cover.jpg')\"></i>"+
          "<div data-post=\"other_channel/100\"><video src=\"https://cdn.example.org/unrelated.mp4\"></video>";
        LinkExtractor.Result result=TelegramPost.parse(html,post);
        check(result.count(LinkExtractor.Kind.VIDEO)==1&&result.count(LinkExtractor.Kind.IMAGE)==1,"public post video and photo, no video cover or next post");
        check(result.items.get(0).url.endsWith("x=1&sig=a%2Fb")&&result.items.get(0).referer.equals(post)&&result.items.get(0).source.equals(post),"public media uses existing downloader with original source and exact signature");
        rejects(()->TelegramPost.parse(html.replace("sample_channel/359","sample_channel/358"),post));
        rejects(()->TelegramPost.parse("<div data-post=\"sample_channel/359\"><p>No media</p>",post));
        rejects(()->TelegramPost.parse("data-protected=\"true\""+html,post));
        rejects(()->TelegramPost.parse(html.replace("https://cdn.example.org/v.mp4?x=1&amp;sig=a%2Fb","blob:media").replace("https://cdn.example.org/p.jpg","http://cdn.example.org/p.jpg"),post));
        String manifest=Files.readString(Path.of("app/src/main/AndroidManifest.xml"));
        check(manifest.contains("android:scheme=\"youwee\"")&&manifest.contains("android:host=\"download\""),"Youwee intent is registered for only the documented host");
        if(args.length>0){org.json.JSONObject formats=new org.json.JSONObject();for(int height:NativeSourceTools.HEIGHTS)formats.put(String.valueOf(height),NativeSourceTools.videoFormat(height));Files.writeString(Path.of(args[0]),formats.toString());}
        System.out.println("PASS native capability adapters: "+checks+" checks");
    }
}
