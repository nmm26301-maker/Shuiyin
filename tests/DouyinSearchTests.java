package com.zizai.watermark;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

public final class DouyinSearchTests {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static LinkExtractor.Result media(LinkExtractor.Kind kind,String label){LinkExtractor.Result r=new LinkExtractor.Result();r.add(kind,"https://cdn.example.org/media?sig=a%2Fb",label);return r;}
    public static void main(String[] args)throws Exception {
        CountDownLatch started=new CountDownLatch(3),stopped=new CountDownLatch(1);
        List<ParallelSearch.Source> sources=Arrays.asList(
            new ParallelSearch.Source("slow",()->{started.countDown();try{new CountDownLatch(1).await();return null;}finally{stopped.countDown();}}),
            new ParallelSearch.Source("cover",()->{started.countDown();return media(LinkExtractor.Kind.IMAGE,"作品封面");}),
            new ParallelSearch.Source("playback",()->{started.countDown();if(!started.await(2,TimeUnit.SECONDS))throw new IOException("not parallel");return media(LinkExtractor.Kind.VIDEO,"video");}));
        long start=System.nanoTime();LinkExtractor.Result result=MediaSearchRace.run(sources,3000);
        check(started.getCount()==0,"all attempts run concurrently");check(result.count(LinkExtractor.Kind.VIDEO)==1,"cover cannot win over video");
        check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<2000,"working media does not wait for slow path timeout");check(stopped.await(2,TimeUnit.SECONDS),"losing request interrupted after success");
        check(!MediaSearchRace.usable(media(LinkExtractor.Kind.AUDIO,"背景音乐")),"audio alone cannot count as Douyin video");
        check(!MediaSearchRace.usable(media(LinkExtractor.Kind.IMAGE,"缩略图")),"thumbnail does not count as photo work");
        check(MediaSearchRace.usable(media(LinkExtractor.Kind.IMAGE,"抖音图片 1")),"real photo works still supported");
        result=MediaSearchRace.run(Arrays.asList(new ParallelSearch.Source("missing",()->{throw new UnsatisfiedLinkError();}),new ParallelSearch.Source("working",()->media(LinkExtractor.Kind.VIDEO,"video"))),1000);
        check(result.count(LinkExtractor.Kind.VIDEO)==1,"unavailable native component cannot discard browser result");
        try{MediaSearchRace.run(Arrays.asList(new ParallelSearch.Source("HTTP",()->{throw new IOException("未返回作品");}),new ParallelSearch.Source("engine",()->media(LinkExtractor.Kind.IMAGE,"封面"))),1000);throw new AssertionError("false success");}
        catch(IOException expected){check(expected.getMessage().contains("HTTP：未返回作品")&&expected.getMessage().contains("engine：未返回视频或图集"),"specific failure reasons survive");}
        CountDownLatch timedOut=new CountDownLatch(1);start=System.nanoTime();
        try{MediaSearchRace.run(Collections.singletonList(new ParallelSearch.Source("timeout",()->{try{new CountDownLatch(1).await();return null;}finally{timedOut.countDown();}})),150);throw new AssertionError("timeout");}
        catch(IOException expected){check(expected.getMessage().contains("超时"),"bounded timeout explained");}
        check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1500&&timedOut.await(2,TimeUnit.SECONDS),"timeout terminates pending path");
        AtomicBoolean cancel=new AtomicBoolean(true),called=new AtomicBoolean();JobControl.attach(cancel);
        try{MediaSearchRace.run(Collections.singletonList(new ParallelSearch.Source("cancelled",()->{called.set(true);return null;})),1000);throw new AssertionError("cancel");}
        catch(InterruptedIOException expected){check(!called.get(),"pre-cancel starts no work");}finally{JobControl.detach();}
        cancel.set(false);JobControl.attach(cancel);CountDownLatch running=new CountDownLatch(1),exited=new CountDownLatch(1);
        Thread trigger=new Thread(()->{try{running.await();cancel.set(true);}catch(InterruptedException e){throw new RuntimeException(e);}});trigger.start();
        try{MediaSearchRace.run(Collections.singletonList(new ParallelSearch.Source("cancel",()->{running.countDown();try{new CountDownLatch(1).await();return null;}finally{exited.countDown();}})),3000);throw new AssertionError("cancel");}
        catch(InterruptedIOException expected){check(exited.await(2,TimeUnit.SECONDS),"in-flight cancellation stops child");}finally{JobControl.detach();trigger.join(1000);}
        String page="https://www.douyin.com/video/7312345678901234567",url="https://v3-web.douyinvod.com/video/tos/opaque?sig=a%2Fb";
        JSONObject item=new JSONObject().put("aweme_id",DouyinParser.id(page)).put("desc","work").put("video",new JSONObject().put("play_addr_h264",new JSONObject().put("url_list",new JSONArray().put(url))));
        String state=new JSONObject().put("aweme_detail",item).toString();
        result=DouyinParser.parseHtml("window._ROUTER_DATA={};<script id='RENDER_DATA'>"+URLEncoder.encode(state,StandardCharsets.UTF_8.name())+"</script>",page);
        check(result.count(LinkExtractor.Kind.VIDEO)==1&&result.items.get(0).url.equals(url),"empty router falls through to render data and H264 address");
        JSONObject snapshot=new JSONObject().put("douyin",state).put("media",new JSONArray().put(new JSONObject().put("kind","VIDEO").put("url",url)));
        result=DouyinBrowserMedia.parse(snapshot,page,page,"Session-UA");check(result.items.size()==1,"structured and playing URL deduplicated");
        LinkExtractor.Item video=result.items.get(0);check(video.referer.equals(page)&&video.userAgent.equals("Session-UA")&&video.source.equals(page),"ordinary browser media retains original downloader headers");
        check(video.url.endsWith("a%2Fb"),"signed playback query not rewritten");
        JSONObject active=new JSONObject().put("media",new JSONArray().put(new JSONObject().put("kind","VIDEO").put("url",url).put("active",true)).put(new JSONObject().put("kind","VIDEO").put("url","https://cdn.example.org/prefetched.mp4")));
        result=DouyinBrowserMedia.parse(active,page,page,"UA");check(result.items.size()==1&&result.items.get(0).url.equals(url),"current video accepted; unrelated prefetch cannot win automatic race");
        check(DouyinBrowserMedia.parse(snapshot,page,page.replace("4567","4568"),"UA").items.isEmpty(),"other work snapshot rejected");
        item.getJSONObject("video").put("is_drm",1);snapshot.put("douyin",new JSONObject().put("aweme_detail",item).toString());
        try{DouyinBrowserMedia.parse(snapshot,page,page,"UA");throw new AssertionError("DRM");}catch(IOException expected){checks++;}
        snapshot=new JSONObject().put("media",new JSONArray().put(new JSONObject().put("kind","VIDEO").put("url","blob:"+page)).put(new JSONObject().put("kind","IMAGE").put("url","https://cdn.example.org/ad.jpg")));
        check(DouyinBrowserMedia.parse(snapshot,page,page,"UA").items.isEmpty(),"blob and unrelated images not downloadable playback");
        System.out.println("PASS Douyin search: "+checks+" checks");
    }
}
