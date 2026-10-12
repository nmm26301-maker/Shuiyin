package com.zizai.watermark;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Converts yt-dlp's public metadata to the same result model as webpage extraction. */
final class EngineMetadata {
    static LinkExtractor.Result parse(String json,String share)throws Exception {
        JSONObject root=new JSONObject(json);LinkExtractor.Result result=new LinkExtractor.Result();
        result.source=share;result.title=bounded(root.optString("title","平台素材"),160);
        result.description=bounded(root.optString("description",""),6000);
        JSONArray entries=root.optJSONArray("entries");
        if(entries!=null){
            for(int i=0;i<Math.min(entries.length(),8);i++){
                JSONObject entry=entries.optJSONObject(i);if(entry==null)continue;
                addEntry(result,entry,share,i+1);
                if(result.description.isEmpty())result.description=bounded(entry.optString("description",""),6000);
            }
        }else addEntry(result,root,share,0);
        if(result.items.isEmpty())throw new IOException("解析引擎未返回可下载的视频、音频或图片");
        return result;
    }
    private static void addEntry(LinkExtractor.Result r,JSONObject info,String share,int index){
        String title=bounded(info.optString("title","素材"),90);
        boolean video=false,audio=false,unknownVideo=false,unknownAudio=false;
        JSONArray formats=info.optJSONArray("formats");
        if(formats!=null)for(int i=0;i<formats.length();i++){
            JSONObject f=formats.optJSONObject(i);if(f==null)continue;
            if(f.optBoolean("has_drm",false))continue;
            video|=codec(f,"vcodec");audio|=codec(f,"acodec");
            String formatExt=f.optString("ext","").toLowerCase(Locale.ROOT);
            unknownVideo|=formatExt.matches("mp4|webm|mov|m4v|mkv|flv|ts")&&!"none".equals(f.optString("vcodec"));
            unknownAudio|=formatExt.matches("m4a|mp3|aac|flac|ogg|opus|wav");
        }
        video|=codec(info,"vcodec");audio|=codec(info,"acodec");
        String ext=info.optString("ext","").toLowerCase(Locale.ROOT);
        boolean image=ext.matches("jpe?g|png|webp|gif|avif|heic|heif");
        if(image)r.add(LinkExtractor.Kind.IMAGE,info.optString("url",""),title+" · 原图");
        else {
            if(!video&&!audio){video=unknownVideo;audio=unknownAudio;}
            if(video)r.addEngine(LinkExtractor.Kind.VIDEO,share,title+" · 视频",index);
            if(audio)r.addEngine(LinkExtractor.Kind.AUDIO,share,title+" · 音频",index);
        }
        String thumbnail=info.optString("thumbnail","");
        if(!https(thumbnail)){
            JSONArray list=info.optJSONArray("thumbnails");
            if(list!=null)for(int i=list.length()-1;i>=0;i--){JSONObject t=list.optJSONObject(i);if(t!=null&&https(t.optString("url"))){thumbnail=t.optString("url");break;}}
        }
        r.add(LinkExtractor.Kind.IMAGE,thumbnail,title+" · 封面");
    }
    private static boolean codec(JSONObject o,String key){String value=o.optString(key,"");return !value.isEmpty()&&!value.equals("none")&&!value.equals("null")&&!value.equals("images")&&!value.equals("unknown");}
    static boolean https(String s){try{URI u=new URI(s);return "https".equalsIgnoreCase(u.getScheme())&&u.getHost()!=null&&u.getRawUserInfo()==null;}catch(Exception e){return false;}}
    private static String bounded(String s,int limit){return s.length()>limit?s.substring(0,limit)+"…":s;}
}
