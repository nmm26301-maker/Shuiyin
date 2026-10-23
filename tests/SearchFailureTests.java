package com.zizai.watermark;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exercise the same nested failures that reach the Activity and queue. */
public final class SearchFailureTests {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        String share="https://www.douyin.com/video/7312345678901234567";
        List<ParallelSearch.Source> providers=Arrays.asList(
            new ParallelSearch.Source("原解析",()->MediaSearchRace.run(Arrays.asList(
                new ParallelSearch.Source("分享页",()->{throw new IOException("页面作品为空");}),
                new ParallelSearch.Source("平台引擎",()->{throw new IOException("Fresh cookies are needed");}),
                new ParallelSearch.Source("原作品网页",()->{throw new IOException("没有当前媒体");})),1000)),
            new ParallelSearch.Source("Cobalt",()->{throw new IOException("尚未配置授权接口");}),
            new ParallelSearch.Source("TikTokio",()->{throw new IOException("接口拒绝当前链接");}),
            new ParallelSearch.Source("GreenVideo",()->{throw new IOException("访问平台超时");}));
        try{ParallelSearch.run(share,providers,new AtomicBoolean(),2000,null);throw new AssertionError("empty result");}
        catch(SearchFailure expected){
            String shown=ExtractionErrors.message(expected);check(shown.startsWith("四路搜索未取得素材"),"aggregate heading preserved");
            for(String name:new String[]{"原解析","分享页","平台引擎","原作品网页","Cobalt","TikTokio","GreenVideo"})check(shown.contains(name+"："),name+" reason survives nested normalization");
            check(shown.contains("有效的访问会话")&&shown.contains("访问平台超时"),"one login/timeout does not erase other sources");
            check(shown.contains("页面作品为空")&&shown.contains("接口拒绝当前链接"),"specific parser failures visible");
        }
        LinkedHashMap<String,String> reasons=new LinkedHashMap<>();reasons.put("site","链接被拒绝 https://cdn.example.org/video?token=secret-url&sig=private-sign\nCookie: session=private-cookie\nAuthorization: Bearer private-header\napi_key=private-key token=private-token\n重试失败");
        String shown=ExtractionErrors.message(new SearchFailure("搜索失败",reasons));
        for(String hidden:new String[]{"secret-url","private-sign","private-cookie","private-header","private-key","private-token"})check(!shown.contains(hidden),hidden+" excluded from copyable report");
        check(shown.contains("重试失败")&&shown.contains("链接已省略"),"useful reason retained after sanitization");
        check(!SearchFailure.clean("接口错误 {\"api_key\":\"quoted-secret\"}",400).contains("quoted-secret"),"quoted JSON credentials hidden");
        check(!SearchFailure.clean("接口错误 {\"Authorization\":\"Bearer quoted-header\"}",400).contains("quoted-header"),"quoted session header hidden");
        reasons.clear();for(int i=0;i<30;i++)reasons.put("provider"+i,"未返回作品".repeat(500));
        shown=new SearchFailure("搜索失败",reasons).getMessage();check(shown.length()<4500,"diagnostics bounded for large provider errors");
        try{MediaSearchRace.run(Collections.singletonList(new ParallelSearch.Source("网页播放",()->{new java.util.concurrent.CountDownLatch(1).await();return null;})),100);throw new AssertionError("timeout");}
        catch(SearchFailure expected){check(ExtractionErrors.message(expected).contains("网页播放：搜索超时"),"timed out inner path identified by name");}
        check(ExtractionErrors.message(new IOException("Fresh cookies are needed")).startsWith("平台需要"),"ordinary errors retain friendly mapping");
        System.out.println("PASS search diagnostics: "+checks+" checks");
    }
}
