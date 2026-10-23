package com.zizai.watermark;
/** Only this application's WebView session; never reads other browsers or exports cookies. */
final class WebSessionCookies {
    private static volatile boolean enabled;
    private static volatile String agent="";
    static void configureAgent(String value){if(value!=null&&value.length()<768&&!value.contains("\r")&&!value.contains("\n"))agent=value;}
    static String userAgent(String fallback){return agent.isEmpty()?fallback:agent;}
    static void enable(){enabled=true;}
    static void receive(String url,java.util.Map<String,java.util.List<String>> headers){
        if(!enabled||!EngineMetadata.https(url)||headers==null)return;
        try{android.webkit.CookieManager jar=android.webkit.CookieManager.getInstance();
            for(java.util.Map.Entry<String,java.util.List<String>> entry:headers.entrySet())if(entry.getKey()!=null&&entry.getKey().equalsIgnoreCase("Set-Cookie")&&entry.getValue()!=null)
                for(String cookie:entry.getValue())if(cookie!=null&&!cookie.contains("\r")&&!cookie.contains("\n"))jar.setCookie(url,cookie);
            jar.flush();
        }catch(RuntimeException ignored){}
    }
    static String header(String url){
        if(!enabled||!EngineMetadata.https(url))return "";
        try{String value=android.webkit.CookieManager.getInstance().getCookie(url);return value==null||value.contains("\r")||value.contains("\n")?"":value;}catch(Exception ignored){return "";}
    }
}
