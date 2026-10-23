package com.zizai.watermark;

import java.nio.file.*;

public final class PlaybackRequestTests {
    static int checks;
    static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        String share="https://www.douyin.com/video/7312345678901234567";
        check(PlaybackRequests.kind("https://v3-web.douyinvod.com/opaque?mime_type=video_mp4&sig=a%2Fb",share)==LinkExtractor.Kind.VIDEO,"extensionless playback query recognized");
        check(PlaybackRequests.kind("https://v3-web.douyinvod.com/video/tos/opaque?sig=a%2Fb",share)==LinkExtractor.Kind.VIDEO,"public CDN video path recognized");
        check(PlaybackRequests.kind("https://aweme.snssdk.com/aweme/v1/play/?video_id=abc",share)==LinkExtractor.Kind.VIDEO,"public play endpoint");
        check(PlaybackRequests.kind("https://v3-web.douyinvod.com.evil.org/video/tos/opaque",share)==null,"CDN host boundary");
        check(PlaybackRequests.kind("https://v3-web.douyinvod.com/account/data",share)==null,"unrelated CDN request excluded");
        check(PlaybackRequests.kind("https://v3-web.douyinvod.com/video/tos/opaque","https://other.example.org/")==null,"extensionless classifier scoped to original platform");
        check(PlaybackRequests.kind("blob:https://www.douyin.com/opaque",share)==null,"blob not treated as reusable URL");
        check(PlaybackRequests.kind("http://v3-web.douyinvod.com/video/tos/opaque",share)==null,"HTTP excluded");
        check(PlaybackRequests.kind("https://user:pass@v3-web.douyinvod.com/video/tos/opaque",share)==null,"credentials excluded");
        check(PlaybackRequests.onWorkPage(share,share),"original work page allowed");
        check(!PlaybackRequests.onWorkPage(share,"https://www.douyin.com/video/7312345678901234568"),"another work excluded");
        check(!PlaybackRequests.onWorkPage(share,"https://www.douyin.com/login"),"login or recommendation feed not original media");
        check(PlaybackRequests.header(java.util.Collections.singletonMap("referer","https://www.douyin.com/"),"Referer","").equals("https://www.douyin.com/"),"Chromium lowercase request header retained");
        check(PlaybackRequests.header(java.util.Collections.singletonMap("user-agent","x\r\ny"),"User-Agent","fallback").equals("fallback"),"unsafe header discarded");
        WebSessionCookies.configureAgent("Normal-WebView-Agent");check(WebSessionCookies.userAgent("fallback").equals("Normal-WebView-Agent"),"same in-app session user agent used for retry");
        WebSessionCookies.configureAgent("bad\r\nInjected: 1");check(WebSessionCookies.userAgent("fallback").equals("Normal-WebView-Agent"),"header injection rejected");
        WebSessionCookies.configureAgent("");check(WebSessionCookies.userAgent("fallback").equals("fallback"),"default agent fallback");
        Files.writeString(Path.of(args[0]),WebMediaScanner.script());System.out.println("PASS playback requests: "+checks+" checks");
    }
}
