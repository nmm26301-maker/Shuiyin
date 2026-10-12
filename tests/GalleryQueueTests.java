package com.zizai.watermark;
import java.util.*;
import java.nio.file.*;
public final class GalleryQueueTests {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        List<String> links=LinkExtractor.extractLinks("一 https://example.org/a。 二 https://example.org/b#x\n重复 https://example.org/a");
        check(links.size()==2&&links.get(1).equals("https://example.org/b"),"share text: punctuation, fragments and duplicates");
        LinkSaveQueue queue=new LinkSaveQueue();check(queue.add(links,LinkSaveQueue.Mode.MEDIA)==2,"add batch");
        check(queue.add(links,LinkSaveQueue.Mode.MEDIA)==0,"duplicate share skipped");
        check(queue.add(links.subList(0,1),LinkSaveQueue.Mode.AUDIO)==1,"different mode retained");
        ArrayList<String> more=new ArrayList<>();for(int i=0;i<18;i++)more.add("https://example.org/"+i);
        try{queue.add(more,LinkSaveQueue.Mode.MEDIA);throw new AssertionError("limit accepted");}catch(java.io.IOException expected){check(queue.entries.size()==3,"oversize batch is atomic");}
        try{queue.add(Arrays.asList("https://example.org/new","file:///bad"),LinkSaveQueue.Mode.MEDIA);throw new AssertionError("invalid accepted");}catch(java.io.IOException expected){check(queue.entries.size()==3,"invalid batch is atomic");}
        queue.entries.get(0).state=LinkSaveQueue.State.SAVING;queue.entries.get(0).saved.put("image1","content://media/images/1");
        queue.entries.get(1).state=LinkSaveQueue.State.FAILED;queue.entries.get(1).error="expired";queue.entries.get(2).state=LinkSaveQueue.State.DONE;
        LinkSaveQueue restored=LinkSaveQueue.decode(queue.encode());check(restored.entries.size()==3,"queue round trip");
        check(restored.entries.get(0).saved.get("image1").equals("content://media/images/1"),"partial outputs survive reopen");
        restored.recover();check(restored.entries.get(0).state==LinkSaveQueue.State.PAUSED,"in-flight recovery");
        restored.retryFailed();check(restored.entries.get(0).state==LinkSaveQueue.State.WAITING&&restored.entries.get(1).state==LinkSaveQueue.State.WAITING,"paused and failed retry");
        check(restored.entries.get(0).saved.size()==1&&restored.entries.get(2).state==LinkSaveQueue.State.DONE,"retry preserves finished outputs and done states");
        restored.clearDone();check(restored.entries.size()==2,"clear completed only");
        check(LinkSaveQueue.decode("broken").entries.isEmpty(),"corrupt queue handled");
        check(LinkSaveQueue.decode("{\"schema\":2,\"entries\":[]}").entries.isEmpty(),"unknown schema handled");
        LinkExtractor.Result r=new LinkExtractor.Result();r.source="https://imgur.com/a/abcde";
        for(String line:Files.readAllLines(Paths.get(args[0])))GalleryMetadata.add(r,line);
        check(r.count(LinkExtractor.Kind.IMAGE)==2&&r.count(LinkExtractor.Kind.VIDEO)==1,"actual gallery extractor messages reach Java parser");
        check(r.items.get(0).referer.equals(r.source)&&!r.items.get(0).userAgent.isEmpty(),"gallery request headers retained");
        r.add(LinkExtractor.Kind.IMAGE,"https://cdn.example.org/cover.jpg","视频封面");r.add(LinkExtractor.Kind.AUDIO,"https://cdn.example.org/audio.m4a","音频");
        check(LinkSaveQueue.choose(r,LinkSaveQueue.Mode.IMAGES).size()==2,"image mode excludes cover and video");
        check(LinkSaveQueue.choose(r,LinkSaveQueue.Mode.MEDIA).size()==3,"media mode excludes cover and redundant audio");
        check(LinkSaveQueue.choose(r,LinkSaveQueue.Mode.AUDIO).size()==1,"explicit audio chosen");
        LinkExtractor.Result v=new LinkExtractor.Result();v.addEngine(LinkExtractor.Kind.VIDEO,"https://example.org/post","视频",1);
        check(LinkSaveQueue.choose(v,LinkSaveQueue.Mode.AUDIO).get(0).kind==LinkExtractor.Kind.VIDEO,"audio falls back to extraction from video");
        LinkExtractor.Item before=new LinkExtractor.Item(LinkExtractor.Kind.IMAGE,"https://cdn.example.org/a.jpg?token=old","图集图片 1");
        LinkExtractor.Item after=new LinkExtractor.Item(LinkExtractor.Kind.IMAGE,"https://cdn.example.org/a.jpg?token=new","图集图片 1");
        check(LinkSaveQueue.key(before).equals(LinkSaveQueue.key(after)),"refreshed signed URL identifies saved asset");
        check(!LinkSaveQueue.key(before).equals(LinkSaveQueue.key(new LinkExtractor.Item(LinkExtractor.Kind.IMAGE,"https://cdn.example.org/b.jpg","图集图片 1"))),"different asset remains distinct");
        GalleryMetadata.add(r,GalleryMetadata.PREFIX+"{\"url\":\"file:///secret\",\"extension\":\"jpg\"}");check(r.items.size()==5,"unsafe media URL excluded");
        GalleryMetadata.add(r,GalleryMetadata.PREFIX+"{\"url\":\"https://cdn.example.org/run.py\",\"extension\":\"py\"}");check(r.items.size()==5,"unsupported extension excluded");
        GalleryMetadata.add(r,GalleryMetadata.PREFIX+"{\"url\":\"https://cdn.example.org/next.png\",\"extension\":\"png\",\"referer\":\"https://u:p@example.org/\",\"userAgent\":\"bad\\r\\nInjected: x\"}");
        check(r.items.get(5).referer.equals(r.source)&&r.items.get(5).userAgent.isEmpty(),"unsafe request headers sanitized");
        for(int i=0;i<50;i++)GalleryMetadata.add(r,GalleryMetadata.PREFIX+"{\"url\":\"https://cdn.example.org/"+i+".png\",\"extension\":\"png\"}");check(r.items.size()==24,"gallery asset limit enforced");
        check(GalleryRoutes.prefer("https://www.instagram.com/p/abc/"),"Instagram post image route");
        check(!GalleryRoutes.prefer("https://www.instagram.com/reel/abc/"),"Instagram reel video route");
        check(GalleryRoutes.prefer("https://www.pinterest.com/pin/123/"),"Pinterest gallery route");
        check(!GalleryRoutes.prefer("https://pinterest.com.attacker.org/pin/123/"),"gallery domain boundary");
        System.out.println("PASS gallery and queue: "+checks+" checks");
    }
}
