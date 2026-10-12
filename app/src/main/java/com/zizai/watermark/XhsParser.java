package com.zizai.watermark;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.json.JSONObject;
import org.json.JSONArray;

/** Reads note metadata, never executes page scripts. Mobile and desktop SSR layouts. */
final class XhsParser {
    static boolean matches(String url){
        try{String h=new URI(url).getHost();return h!=null&&(h.equalsIgnoreCase("xhslink.cn")||h.equalsIgnoreCase("xhslink.com")||h.equalsIgnoreCase("xiaohongshu.com")||h.toLowerCase(java.util.Locale.ROOT).endsWith(".xiaohongshu.com"));}catch(Exception e){return false;}
    }
    static String mediaUrl(String url){
        if(url==null)return "";
        try{if(url.startsWith("//"))url="https:"+url;URI u=new URI(url);String host=u.getHost();
            if(host==null||u.getRawUserInfo()!=null)return "";
            // XHS currently serializes its own CDN URLs as HTTP. Upgrade only its CDN.
            if("http".equalsIgnoreCase(u.getScheme())&&(host.equalsIgnoreCase("xhscdn.com")||host.toLowerCase(java.util.Locale.ROOT).endsWith(".xhscdn.com")))url="https://"+url.substring(7);
            return EngineMetadata.https(url)?url:"";
        }catch(Exception e){return "";}
    }
    static String stateJson(String html)throws IOException{
        Matcher m=Pattern.compile("(?:window\\.)?__INITIAL_STATE__\\s*=").matcher(html);
        if(!m.find())throw new IOException("小红书页面未返回笔记数据。可用网页辅助打开笔记后再提取，或导入本人 cookies.txt 重试。");
        int start=html.indexOf('{',m.end());if(start<0)throw new IOException("小红书笔记数据为空");
        boolean quoted=false,escape=false;int depth=0;
        for(int i=start;i<html.length();i++){
            char c=html.charAt(i);
            if(quoted){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')quoted=false;continue;}
            if(c=='"')quoted=true;else if(c=='{')depth++;else if(c=='}'&&--depth==0)return normalize(html.substring(start,i+1));
        }
        throw new IOException("小红书笔记数据不完整，请重新解析");
    }
    static String normalize(String value){
        StringBuilder out=new StringBuilder();boolean quoted=false,escape=false;
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(!quoted&&value.startsWith("undefined",i)&&(i==0||!Character.isJavaIdentifierPart(value.charAt(i-1)))&&(i+9==value.length()||!Character.isJavaIdentifierPart(value.charAt(i+9)))){out.append("null");i+=8;continue;}
            out.append(c);if(quoted){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')quoted=false;}else if(c=='"')quoted=true;
        }return out.toString();
    }
    private static JSONObject object(JSONObject parent,String key){JSONObject child=parent==null?null:parent.optJSONObject(key);return child==null?new JSONObject():child;}
    static LinkExtractor.Result parseHtml(String html,String source)throws Exception{return parseState(stateJson(html),source);}
    static LinkExtractor.Result parseState(String json,String source)throws Exception{
        JSONObject state=new JSONObject(json),note=object(object(object(state,"noteData"),"data"),"noteData");
        if(note.length()==0){JSONObject map=object(object(state,"note"),"noteDetailMap");String id="";
            Matcher m=Pattern.compile("/(?:explore|discovery/item)/([a-fA-F0-9]+)").matcher(source);if(m.find())id=m.group(1);
            // Select the requested note only: recommendations are not extraction results.
            note=object(object(map,id),"note");
        }
        if(note.length()==0)throw new IOException("小红书未返回这条笔记的素材。请用网页辅助打开笔记确认可访问，再提取；需要登录时可导入本人 cookies.txt。");
        Matcher requested=Pattern.compile("/(?:explore|discovery/item)/([a-fA-F0-9]+)").matcher(source);
        if(requested.find()&&note.has("noteId")&&!requested.group(1).equalsIgnoreCase(note.optString("noteId")))throw new IOException("页面返回的笔记与分享链接不一致，请重新复制分享链接");
        LinkExtractor.Result r=new LinkExtractor.Result();r.source=source;r.title=note.optString("title","");if(r.title.isEmpty())r.title="小红书笔记";r.description=note.optString("desc","");
        JSONObject video=object(note,"video"),media=object(video,"media");
        if(object(media,"video").optInt("drmType",0)!=0)throw new IOException("这条视频受加密保护，无法导出媒体文件");
        JSONObject streams=object(media,"stream");
        if(streams.length()==0){String v2=video.optString("mediaV2","");if(!v2.isEmpty())try{JSONObject decoded=new JSONObject(v2);if(object(decoded,"video").optInt("drm_type",0)!=0)throw new IOException("这条视频受加密保护，无法导出媒体文件");streams=object(decoded,"stream");}catch(org.json.JSONException ignored){}}
        List<JSONObject> choices=new ArrayList<>();Iterator<String> codecs=streams.keys();while(codecs.hasNext()){
            JSONArray array=streams.optJSONArray(codecs.next());if(array==null)continue;for(int i=0;i<Math.min(24,array.length());i++){JSONObject entry=array.optJSONObject(i);if(entry!=null)choices.add(entry);}
        }
        choices.sort((a,b)->{boolean ah=codec(a).contains("264"),bh=codec(b).contains("264");if(ah!=bh)return ah?-1:1;return Integer.compare(b.optInt("height",0),a.optInt("height",0));});
        String origin=object(video,"consumer").optString("originVideoKey","");
        if(!origin.isEmpty()&&!origin.contains("..")&&origin.matches("[A-Za-z0-9/_-]+"))r.add(LinkExtractor.Kind.VIDEO,"https://sns-video-bd.xhscdn.com/"+origin,"原始视频候选");
        for(JSONObject entry:choices){if(entry.optInt("hdrType",entry.optInt("hdr_type",0))!=0)continue;
            String label=entry.optInt("height",0)+"p · "+codec(entry);r.add(LinkExtractor.Kind.VIDEO,mediaUrl(entry.optString("masterUrl",entry.optString("master_url",""))),label);
            JSONArray backup=entry.optJSONArray("backupUrls");if(backup==null)backup=entry.optJSONArray("backup_urls");if(backup!=null)for(int i=0;i<Math.min(2,backup.length());i++)r.add(LinkExtractor.Kind.VIDEO,mediaUrl(backup.optString(i,"")),label+" · 备用线路");
        }
        JSONArray images=note.optJSONArray("imageList");if(images!=null)for(int i=0;i<Math.min(24,images.length());i++){
            JSONObject image=images.optJSONObject(i);if(image==null)continue;String url=image.optString("urlDefault",image.optString("url",""));
            if(url.isEmpty()){JSONArray info=image.optJSONArray("infoList");if(info!=null)for(int j=0;j<info.length();j++){JSONObject item=info.optJSONObject(j);if(item!=null&&url.isEmpty())url=item.optString("url","");}}
            r.add(LinkExtractor.Kind.IMAGE,mediaUrl(url),"video".equals(note.optString("type"))?"视频封面":"笔记图片 "+(i+1));
        }
        if(r.items.isEmpty())throw new IOException("笔记页面没有返回可下载的素材，请用网页辅助打开后重试");
        return r;
    }
    private static String codec(JSONObject entry){return entry.optString("videoCodec",entry.optString("video_codec",""));}
}
