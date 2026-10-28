package com.zizai.watermark;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.*;

/** Native capability adapters. Desktop executables are never labelled as installed engines. */
final class NativeSourceTools {
    enum Mode {WEB_CAPTURE,ENGINE,TELEGRAM,UNAVAILABLE}
    static Mode mode(String project){
        if(project.equals("https://github.com/putyy/res-downloader"))return Mode.WEB_CAPTURE;
        if(project.equals("https://github.com/vanloctech/youwee")||project.equals("https://github.com/nexmoe/VidBee"))return Mode.ENGINE;
        if(project.equals("https://github.com/Neet-Nestor/Telegram-Media-Downloader"))return Mode.TELEGRAM;
        return Mode.UNAVAILABLE;
    }
    static final int[] HEIGHTS={0,1080,720,480,360};
    static int height(int value){for(int height:HEIGHTS)if(value==height)return value;return 0;}
    static String videoFormat(int value){
        int height=height(value);String cap=height==0?"":"[height<="+height+"]";
        return "bestvideo"+cap+"[ext=mp4][vcodec^=avc1]+bestaudio[ext=m4a]/best"+cap+"[ext=mp4]/bestvideo"+cap+"+bestaudio/best"+cap;
    }
    static String normalize(String url)throws Exception {
        if(!EngineMetadata.https(url))throw new IOException("请输入有效 HTTPS 作品链接");
        URI uri=new URI(url);String host=uri.getHost().toLowerCase(Locale.ROOT);
        if(host.equals("t.me"))return TelegramPost.page(url);
        if((host.equals("youtube.com")||host.endsWith(".youtube.com"))&&"/watch".equals(uri.getPath())){
            Map<String,String> query=query(uri.getRawQuery());String id=query.get("v");
            if(id!=null&&id.matches("[A-Za-z0-9_-]{6,64}"))return "https://www.youtube.com/watch?v="+id;
        }
        return url;
    }
    static final class Request {
        final String url;final int height;final boolean audio;final String action;
        Request(String url,int height,boolean audio,String action){this.url=url;this.height=height;this.audio=audio;this.action=action;}
    }
    static Request youwee(String value)throws Exception {
        if(value==null||value.length()>8192)throw new IOException("Youwee 请求为空或过长");
        URI uri=new URI(value);if(!"youwee".equals(uri.getScheme())||!"download".equals(uri.getHost())||uri.getRawUserInfo()!=null)throw new IOException("不是受支持的 Youwee 请求");
        Map<String,String> q=query(uri.getRawQuery());if(!"1".equals(q.get("v")))throw new IOException("Youwee 链接版本不受支持");
        String media=q.getOrDefault("media","video"),action=q.getOrDefault("action","queue_only"),quality=q.getOrDefault("quality","best");
        if(!media.equals("video")&&!media.equals("audio")||!action.equals("queue_only")&&!action.equals("download_now"))throw new IOException("Youwee 下载参数无效");
        String numeric=quality.replaceFirst("p$","");int height=numeric.equals("1080")?1080:numeric.equals("720")?720:numeric.equals("480")?480:numeric.equals("360")?360:0;
        return new Request(normalize(q.getOrDefault("url","")),height,media.equals("audio"),action);
    }
    private static Map<String,String> query(String value)throws Exception {
        Map<String,String> result=new LinkedHashMap<>();if(value==null)return result;
        for(String pair:value.split("&")){String[] parts=pair.split("=",2);String key=URLDecoder.decode(parts[0],"UTF-8"),text=URLDecoder.decode(parts.length>1?parts[1]:"","UTF-8");
            if(result.put(key,text)!=null)throw new IOException("链接含重复参数");}
        return result;
    }
}
