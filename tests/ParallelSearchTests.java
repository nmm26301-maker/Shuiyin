package com.zizai.watermark;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ParallelSearchTests {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static LinkExtractor.Result result(String url){LinkExtractor.Result r=new LinkExtractor.Result();r.title="测试作品";r.add(LinkExtractor.Kind.VIDEO,url,"视频");return r;}
    public static void main(String[] args)throws Exception {
        String share="https://www.xiaohongshu.com/explore/abc?xsec_token=a%2Bb";CountDownLatch started=new CountDownLatch(4);
        List<ParallelSearch.Source> sources=new ArrayList<>();
        for(int i=0;i<4;i++){final int index=i;sources.add(new ParallelSearch.Source("源"+i,()->{started.countDown();if(!started.await(2,TimeUnit.SECONDS))throw new IOException("not concurrent");if(index==3)throw new IOException("网站要求人工验证");return result(index==2?"https://cdn.example.org/a.mp4?sig=a%2Fb":"https://cdn.example.org/a.mp4?sig=a%2Fb");}));}
        LinkExtractor.Result merged=ParallelSearch.run(share,sources,new AtomicBoolean(),3000,null);
        check(started.getCount()==0,"all four sources start before any finishes");check(merged.items.size()==1,"exact media dedup across providers");check(merged.items.get(0).url.endsWith("a%2Fb"),"signed query retained");
        check(merged.source.equals(share)&&merged.items.get(0).source.equals(share),"original work origin retained");check(merged.notice.contains("源3：网站要求人工验证"),"challenge failure reported per source");check(merged.notice.contains("源0：找到"),"success source reported");
        CountDownLatch exited=new CountDownLatch(3);sources.clear();sources.add(new ParallelSearch.Source("原解析",()->result("https://cdn.example.org/fast.mp4")));
        for(int i=0;i<3;i++)sources.add(new ParallelSearch.Source("慢源"+i,()->{try{while(true){JobControl.check();new CountDownLatch(1).await(30,TimeUnit.MILLISECONDS);}}finally{exited.countDown();}}));
        long start=System.nanoTime();merged=ParallelSearch.run(share,sources,new AtomicBoolean(),150,null);
        check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1500,"one slow source cannot stall indefinitely");check(merged.items.size()==1&&merged.notice.contains("搜索超时"),"partial successes survive timeout");check(exited.await(2,TimeUnit.SECONDS),"timed out providers terminated");
        AtomicBoolean cancelled=new AtomicBoolean(true);try{ParallelSearch.run(share,Collections.singletonList(new ParallelSearch.Source("source",()->result("https://cdn.example.org/a.mp4"))),cancelled,3000,null);throw new AssertionError("cancel ignored");}catch(InterruptedIOException expected){checks++;}
        try{ParallelSearch.run(share,Collections.singletonList(new ParallelSearch.Source("失败",()->{throw new IOException("不支持");})),new AtomicBoolean(),1000,null);throw new AssertionError("false success");}catch(IOException expected){check(expected.getMessage().contains("不支持"),"all failures remain failures");}
        merged=ParallelSearch.run(share,Arrays.asList(new ParallelSearch.Source("缺组件",()->{throw new UnsatisfiedLinkError("native missing");}),new ParallelSearch.Source("正常",()->result("https://cdn.example.org/good.mp4"))),new AtomicBoolean(),1000,null);
        check(merged.items.size()==1&&merged.notice.contains("组件不可用"),"native component failure cannot discard other providers");
        LinkExtractor.Result provider=new LinkExtractor.Result();provider.addItem(new LinkExtractor.Item(LinkExtractor.Kind.IMAGE,"https://cdn.example.org/original","图片","https://greenvideo.cc/",false,0,"UA",share,"https://cdn.example.org/fallback"));
        merged=ParallelSearch.run(share,Collections.singletonList(new ParallelSearch.Source("GreenVideo",()->provider)),new AtomicBoolean(),1000,null);LinkExtractor.Item image=merged.items.get(0);
        check(image.referer.equals("https://greenvideo.cc/")&&image.source.equals(share)&&image.userAgent.equals("UA"),"HTTP referer separate from repair origin");check(image.fallbackUrl.endsWith("/fallback"),"photo fallback survives merge");check(RepairTemplate.key(image,1080,1440).startsWith("小红书|IMAGE"),"third-party photo uses original platform repair template");
        System.out.println("PASS parallel search: "+checks+" checks");
    }
}
