package com.zizai.watermark;
/** Only this application's WebView session; never reads other browsers or exports cookies. */
final class WebSessionCookies {
    private static volatile boolean enabled;
    static void enable(){enabled=true;}
    static String header(String url){
        if(!enabled||!EngineMetadata.https(url))return "";
        try{String value=android.webkit.CookieManager.getInstance().getCookie(url);return value==null||value.contains("\r")||value.contains("\n")?"":value;}catch(Exception ignored){return "";}
    }
}
