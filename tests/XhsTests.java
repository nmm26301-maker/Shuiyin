package com.zizai.watermark;
import java.io.*;
import java.nio.file.*;
import org.json.*;
public final class XhsTests {
    static int checks;
    static void check(boolean valid,String label){checks++;if(!valid)throw new AssertionError(label);}
    static void rejects(String json,String source)throws Exception{try{XhsParser.parseState(json,source);throw new AssertionError("empty/wrong state accepted");}catch(IOException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        String source="https://www.xiaohongshu.com/discovery/item/69d77b3400000000230058e7?xsec_token=a%2Bb%3D";
        LinkExtractor.Result actual=XhsParser.parseHtml(Files.readString(Paths.get(args[0])),source);
        check(actual.count(LinkExtractor.Kind.VIDEO)>=3,"real mobile SSR streams");check(actual.count(LinkExtractor.Kind.IMAGE)==1,"real mobile cover");check(actual.title.equals("TED演讲：人生尽头有何求"),"real title");check(actual.description.contains("人生尽头有何求"),"real caption");check(actual.source.equals(source),"signed source query retained");
        check(actual.items.get(1).label.contains("h264"),"H264 preferred ahead of HEVC");
        check(actual.items.stream().filter(i->i.kind==LinkExtractor.Kind.VIDEO).allMatch(i->i.url.startsWith("https://")),"CDN HTTP upgraded");
        check(actual.items.stream().anyMatch(i->i.url.contains("?sign=")&&i.url.contains("&t=")),"media signature not stripped");
        String n="{\"noteId\":\"abc\",\"title\":\"literal undefined } \\\"\",\"desc\":\"行1\\n行2\",\"imageList\":[{\"urlDefault\":\"http://sns-webpic-qc.xhscdn.com/one.jpg\"},{\"infoList\":[{\"url\":\"https://cdn.example.org/two.jpg\"}]}]}";
        LinkExtractor.Result desktop=XhsParser.parseHtml("<script>window.__INITIAL_STATE__={\"unused\":undefined,\"note\":{\"noteDetailMap\":{\"abc\":{\"note\":"+n+"}}}};</script>","https://www.xiaohongshu.com/explore/abc");
        check(desktop.count(LinkExtractor.Kind.IMAGE)==2,"desktop gallery");check(desktop.title.contains("undefined }"),"undefined and braces inside string preserved");check(desktop.description.contains("\n"),"caption line break retained");
        rejects("{}",source);rejects("{\"note\":{\"noteDetailMap\":{\"other\":{\"note\":"+n+"}}}}",source);rejects("{\"noteData\":{\"data\":{\"noteData\":"+n+"}}}",source);
        check(XhsParser.matches("https://xhslink.cn/o/a")&&XhsParser.matches(source)&&!XhsParser.matches("https://xiaohongshu.com.evil.org/x"),"platform host exact boundary");
        check(XhsParser.mediaUrl("http://evil.org/a.mp4").isEmpty(),"unrelated HTTP excluded");check(XhsParser.mediaUrl("https://u:p@cdn.example.org/a").isEmpty(),"URL credential excluded");
        JSONObject v2=new JSONObject().put("video",new JSONObject().put("drm_type",0)).put("stream",new JSONObject().put("h264",new JSONArray().put(new JSONObject().put("master_url","http://sns-video-zl.xhscdn.com/v2.mp4?sign=a%2Bb").put("video_codec","h264").put("height",720))));
        JSONObject vnote=new JSONObject().put("title","V2").put("video",new JSONObject().put("mediaV2",v2.toString()));JSONObject vstate=new JSONObject().put("noteData",new JSONObject().put("data",new JSONObject().put("noteData",vnote)));
        LinkExtractor.Result vr=XhsParser.parseState(vstate.toString(),source);check(vr.count(LinkExtractor.Kind.VIDEO)==1&&vr.items.get(0).url.endsWith("sign=a%2Bb"),"mediaV2 snake_case stream and escaped token");
        v2.getJSONObject("video").put("drm_type",1);vnote.getJSONObject("video").put("mediaV2",v2.toString());rejects(vstate.toString(),source);
        vnote.put("video",new JSONObject().put("media",new JSONObject().put("video",new JSONObject().put("drmType",1))));rejects(vstate.toString(),source);
        vnote.put("video",new JSONObject().put("consumer",new JSONObject().put("originVideoKey","../../private")));rejects(vstate.toString(),source);
        String cookie=".xiaohongshu.com\tTRUE\t/\tTRUE\t0\tsession\tsecret\n#HttpOnly_www.xiaohongshu.com\tFALSE\t/explore\tTRUE\t200\ta\t1\n.xiaohongshu.com\tTRUE\t/\tTRUE\t5\told\tx\n";
        check(CookieFile.header(cookie,"https://www.xiaohongshu.com/explore/abc",100).equals("session=secret; a=1"),"cookie secure expiry HttpOnly");check(CookieFile.header(cookie,"https://xiaohongshu.com.evil.org/a",100).isEmpty(),"no cookie leak to other domains");check(CookieFile.header(cookie,"http://www.xiaohongshu.com/a",100).isEmpty(),"secure cookies not sent over HTTP");check(!CookieFile.header(cookie,"https://www.xiaohongshu.com/explorer",100).contains("a=1"),"cookie path boundary");
        check(WebSessionCookies.header(source).isEmpty(),"WebView session untouched before user opens web assistant");
        for(String error:new String[]{"__WM_GROUP__17949\nERROR: [generic] timed out\nnull","Traceback\n/data/secret\nnull", "ERROR: Unsupported URL"}){String friendly=ExtractionErrors.message(new IOException(error));check(!friendly.contains("__WM_GROUP__")&&!friendly.contains("ERROR:")&&!friendly.contains("null")&&!friendly.contains("/data/"),"internal errors hidden");}
        System.out.println("PASS XHS: "+checks+" checks");
        if(args.length>1){LinkExtractor.Result live=LinkExtractor.parseXhs((File)null,args[1]);check(live.count(LinkExtractor.Kind.VIDEO)==1,"live verified video");check(live.count(LinkExtractor.Kind.IMAGE)==1,"live verified cover");System.out.println("LIVE PASS: "+live.title+" video="+live.count(LinkExtractor.Kind.VIDEO)+" image="+live.count(LinkExtractor.Kind.IMAGE)+" selected="+live.items.get(0).label);Files.writeString(Paths.get(args[2]),live.items.get(0).url);}
    }
}
