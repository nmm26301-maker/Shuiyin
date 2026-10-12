package com.zizai.watermark;

import java.util.concurrent.atomic.AtomicBoolean;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class LinkTests {
    private static int checks;
    private static void check(boolean condition,String description){checks++;if(!condition)throw new AssertionError(description);}
    private static LinkExtractor.Result parse(String html)throws Exception{return LinkExtractor.parsePublicHtml(html,"https://example.org/post/page");}
    public static void main(String[] args)throws Exception {
        check(LinkExtractor.extractLink("分享这个视频 https://example.org/v/abc?x=1&y=2，复制打开").equals("https://example.org/v/abc?x=1&y=2"),"Chinese share normalization");
        check(LinkExtractor.extractLink("(https://example.org/v/abc). next").equals("https://example.org/v/abc"),"trailing punctuation");
        check(LinkExtractor.extractLink("file:///etc/passwd").isEmpty(),"only HTTPS");
        check(LinkExtractor.extractLink("复制 http://xhslink.com/a/abc，打开").equals("https://xhslink.com/a/abc"),"HTTP share links upgraded to HTTPS");
        check(LinkExtractor.segmented("https://example.org/live.m3u8?token=x")&&!LinkExtractor.segmented("https://example.org/clip.mp4"),"segmented manifests recognized");
        LinkExtractor.Result copied=new LinkExtractor.Result();copied.source="https://example.org/post";copied.addEngine(LinkExtractor.Kind.VIDEO,copied.source,"video",2);LinkExtractor.Result target=new LinkExtractor.Result();target.addItem(copied.items.get(0));check(target.items.get(0).engine&&target.items.get(0).engineIndex==2,"web result copy retains native selectors");
        LinkExtractor.Result r=parse("<TITLE>Fallback</TITLE><META content='标题 &#x732B;' property='og:title'><meta property='og:description' content='你好 &amp; 再见'><meta content='../video.mp4?a=1&amp;b=2' property='og:video'><meta name='twitter:image' content='//cdn.example.org/a.png'>");
        check(r.title.equals("标题 猫"),"numeric entity");check(r.description.equals("你好 & 再见"),"HTML entity");
        check(r.count(LinkExtractor.Kind.VIDEO)==1&&r.count(LinkExtractor.Kind.IMAGE)==1,"OG assets");
        check(r.items.get(0).url.equals("https://example.org/video.mp4?a=1&b=2"),"relative URL plus query");
        check(r.items.get(0).referer.equals(r.source),"referer preserved");
        r=parse("<meta property=og:image content='/a.jpg'><meta property='og:image:secure_url' content='/a.jpg'><meta property=og:image content='/b.jpg'>");
        check(r.count(LinkExtractor.Kind.IMAGE)==2,"deduplicate repeated OG but retain gallery covers");
        r=parse("<audio><source src='/song' /></audio><video><source src='/clip' /></video>");
        check(r.count(LinkExtractor.Kind.AUDIO)==1&&r.count(LinkExtractor.Kind.VIDEO)==1,"source inherits media parent");
        r=parse("<script type='application/ld+json'>{\"@graph\":[{\"@type\":\"VideoObject\",\"name\":\"测试素材\",\"description\":\"描述\",\"contentUrl\":\"/clip.mp4\",\"thumbnailUrl\":[\"/one.jpg\",\"/two.jpg\"]}]}</script>");
        check(r.title.equals("测试素材")&&r.description.equals("描述"),"JSON-LD caption");
        check(r.count(LinkExtractor.Kind.VIDEO)==1&&r.count(LinkExtractor.Kind.IMAGE)==2,"JSON-LD nested media");
        r=parse("<title>title</title><meta name=description content=caption><script type='application/ld+json'>{ broken </script>");check(r.title.equals("title"),"bad JSON-LD does not discard valid caption metadata");
        try{parse("<title>小红书</title>");throw new AssertionError("title-only shell accepted");}catch(IOException expected){checks++;}
        r=parse("<meta name=description content='literal &lt;cat&gt; &quot;你好&quot;'>");check(r.description.equals("literal <cat> \"你好\""),"escaped literal brackets retained");
        r=parse("<meta property=og:image content='data:image/png,abc'><meta property=og:video content='http://example.org/v.mp4'><meta name=description content=words>");check(r.items.isEmpty(),"unsafe schemes excluded");
        r=new LinkExtractor.Result();r.add(LinkExtractor.Kind.VIDEO,"https://user:secret@example.org/v.mp4","x");check(r.items.isEmpty(),"URI credentials rejected");
        for(int i=0;i<40;i++)r.add(LinkExtractor.Kind.IMAGE,"https://example.org/"+i+".jpg","x");check(r.items.size()==24,"bounded result list");
        byte[] png={(byte)137,'P','N','G',13,10,26,10};check(MediaSignature.accepts("image/png",png,png.length),"PNG signature");
        byte[] fake="<html>HTTP error, not a video</html>".getBytes();check(!MediaSignature.accepts("video/mp4",fake,fake.length),"error HTML rejected");
        byte[] mp4=Files.readAllBytes(Paths.get(args[0]));check(MediaSignature.accepts("video/mp4",mp4,Math.min(mp4.length,32)),"real MP4 signature");
        for(String url:new String[]{"https://127.0.0.1/a","https://[::1]/a","https://[fd00::1]/a","http://example.org/a","https://user:password@example.org/a"}){
            try{LinkExtractor.ensurePublic(url);throw new AssertionError("private/non HTTPS target accepted");}catch(IOException expected){checks++;}
        }
        AtomicBoolean flag=new AtomicBoolean(true);JobControl.attach(flag);try{JobControl.check();throw new AssertionError("cancel ignored");}catch(InterruptedIOException expected){checks++;}finally{JobControl.detach();}
        JobControl.check();checks++; // next independent job is unaffected
        System.out.println("PASS link extraction: "+checks+" checks (production metadata parser, structured media, signatures, cancellation)");
    }
}
