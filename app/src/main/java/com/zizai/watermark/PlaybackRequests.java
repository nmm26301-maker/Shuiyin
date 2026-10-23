package com.zizai.watermark;

import java.net.URI;
import java.util.Locale;

/** Public playback URLs, including extensionless Douyin CDN requests. No decryption. */
final class PlaybackRequests {
    static String header(java.util.Map<String,String> headers,String name,String fallback){
        if(headers!=null)for(java.util.Map.Entry<String,String> entry:headers.entrySet())if(entry.getKey()!=null&&entry.getKey().equalsIgnoreCase(name)){
            String value=entry.getValue();if(value!=null&&!value.contains("\r")&&!value.contains("\n"))return value;
        }return fallback;
    }
    static boolean onWorkPage(String source,String page){
        if(!DouyinParser.matches(source))return true;
        if(!DouyinParser.matches(page)||DouyinParser.id(page).isEmpty())return false;
        String requested=DouyinParser.id(source);return requested.isEmpty()||requested.equals(DouyinParser.id(page));
    }
    static LinkExtractor.Kind kind(String url,String source){
        if(!EngineMetadata.https(url))return null;
        try{URI u=new URI(url);String path=u.getPath()==null?"":u.getPath().toLowerCase(Locale.ROOT);
            if(path.matches(".*\\.(mp4|m4v|mov|webm|m3u8|mpd)$"))return LinkExtractor.Kind.VIDEO;
            if(path.matches(".*\\.(mp3|m4a|aac|ogg|wav|opus)$"))return LinkExtractor.Kind.AUDIO;
            if(!DouyinParser.matches(source))return null;
            String host=u.getHost().toLowerCase(Locale.ROOT),query=u.getRawQuery()==null?"":u.getRawQuery().toLowerCase(Locale.ROOT);
            if((host.equals("douyinvod.com")||host.endsWith(".douyinvod.com"))&&(path.contains("/video/")||query.matches("(?:.*&)?mime_type=video(?:_|%2f|/)mp4(?:&.*)?")))return LinkExtractor.Kind.VIDEO;
            if((host.equals("douyin.com")||host.endsWith(".douyin.com")||host.equals("aweme.snssdk.com"))&&path.equals("/aweme/v1/play/"))return LinkExtractor.Kind.VIDEO;
        }catch(Exception ignored){}return null;
    }
}
