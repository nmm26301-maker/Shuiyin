package com.zizai.watermark;
import org.json.JSONObject;
import java.io.IOException;
import java.util.Locale;
final class GalleryMetadata {
    static final String PREFIX="__WM_GALLERY__";
    static void add(LinkExtractor.Result result,String line)throws Exception {
        if(!line.startsWith(PREFIX))return;
        if(line.length()>24000)throw new IOException("图集信息过大");
        JSONObject o=new JSONObject(line.substring(PREFIX.length()));String url=o.optString("url"),ext=o.optString("extension").toLowerCase(Locale.ROOT);
        if(!EngineMetadata.https(url))return;
        LinkExtractor.Kind kind;
        if(java.util.Arrays.asList("jpg","jpeg","png","webp","gif","avif","heic","heif").contains(ext))kind=LinkExtractor.Kind.IMAGE;
        else if(java.util.Arrays.asList("mp4","webm","m4v","mov").contains(ext))kind=LinkExtractor.Kind.VIDEO;
        else if(java.util.Arrays.asList("mp3","m4a","ogg").contains(ext))kind=LinkExtractor.Kind.AUDIO;
        else return;
        String title=o.optString("title");if(!title.isEmpty())result.title=title.substring(0,Math.min(160,title.length()));
        String description=o.optString("description");if(!description.isEmpty())result.description=description.substring(0,Math.min(6000,description.length()));
        String ref=o.optString("referer");if(!EngineMetadata.https(ref))ref=result.source;
        int index=Math.max(1,Math.min(24,o.optInt("index",result.items.size()+1)));
        result.addItem(new LinkExtractor.Item(kind,url,"图集"+(kind==LinkExtractor.Kind.IMAGE?"图片 ":kind==LinkExtractor.Kind.VIDEO?"视频 ":"音频 ")+index,ref,false,index,o.optString("userAgent")));
    }
}
