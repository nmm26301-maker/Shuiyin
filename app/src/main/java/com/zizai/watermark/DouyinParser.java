package com.zizai.watermark;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Public mobile-share SSR reader; selects the requested work, never recommendations. */
final class DouyinParser {
    static boolean matches(String url){String h=PlatformRoutes.host(url);return h.equals("douyin.com")||h.endsWith(".douyin.com")||h.equals("iesdouyin.com")||h.endsWith(".iesdouyin.com");}
    static String id(String source){Matcher m=Pattern.compile("/(?:share/(?:video|slides)|video|note)/(\\d{8,24})(?:/|\\?|$)|[?&](?:modal_id|aweme_id)=(\\d{8,24})(?:&|$)").matcher(source);return m.find()?(m.group(1)==null?m.group(2):m.group(1)):"";}
    static String sharePage(String source){String id=id(source);return id.isEmpty()?"":"https://www.iesdouyin.com/share/video/"+id+"/";}
    static LinkExtractor.Result parseHtml(String html,String source)throws Exception {
        String requested=id(source);if(requested.isEmpty())throw new IOException("未识别抖音作品编号，请重新复制分享链接");
        JSONObject state=null;Matcher assignment=Pattern.compile("(?:window\\.)?_ROUTER_DATA\\s*=").matcher(html);
        if(assignment.find())state=new JSONObject(balanced(html,html.indexOf('{',assignment.end())));
        if(state==null){Matcher render=Pattern.compile("<script[^>]*id=[\"']RENDER_DATA[\"'][^>]*>(.*?)</script>",Pattern.DOTALL|Pattern.CASE_INSENSITIVE).matcher(html);if(render.find()){String data=render.group(1).trim();state=new JSONObject(data.startsWith("{")?data:URLDecoder.decode(data,StandardCharsets.UTF_8.name()));}}
        if(state==null)throw new IOException("抖音分享页未返回作品数据，可能要求登录或验证");
        JSONObject item=find(state,requested,0);if(item==null)throw new IOException("抖音页面没有返回这条作品，未提取其他推荐内容");
        if(item.optInt("is_prohibited",0)!=0||item.optBoolean("is_private",false))throw new IOException("这条抖音作品不可公开访问");
        LinkExtractor.Result result=new LinkExtractor.Result();result.source=source;result.title=item.optString("desc","抖音素材");result.description=item.optString("desc","");
        JSONArray images=item.optJSONArray("images");if(images==null){JSONObject post=item.optJSONObject("image_post_info");if(post!=null)images=post.optJSONArray("images");}
        if(images!=null)for(int i=0;i<Math.min(24,images.length());i++){JSONObject image=images.optJSONObject(i);if(image==null)continue;JSONObject display=image.optJSONObject("display_image");urls(result,LinkExtractor.Kind.IMAGE,display==null?image:display,"抖音图片 "+(i+1));}
        JSONObject video=item.optJSONObject("video");if(video!=null&&result.items.isEmpty()){
            if(video.optInt("is_drm",0)!=0||video.optInt("drm_type",0)!=0)throw new IOException("该抖音视频受加密保护");
            JSONArray rates=video.optJSONArray("bit_rate");if(rates!=null)for(int i=0;i<Math.min(12,rates.length());i++){JSONObject rate=rates.optJSONObject(i);if(rate!=null)urls(result,LinkExtractor.Kind.VIDEO,rate.optJSONObject("play_addr"),"抖音视频 · 清晰度 "+rate.optInt("bit_rate"));}
            urls(result,LinkExtractor.Kind.VIDEO,video.optJSONObject("play_addr"),"抖音视频");
        }
        if(result.items.isEmpty())throw new IOException("抖音作品未返回可下载素材");return result;
    }
    private static void urls(LinkExtractor.Result result,LinkExtractor.Kind kind,JSONObject object,String label){
        if(object==null)return;JSONArray list=object.optJSONArray("url_list");if(list==null)return;
        for(int i=0;i<Math.min(4,list.length());i++){String url=list.optString(i,"");
            try{URI u=new URI(url);String h=u.getHost();if(h==null)continue;
                if("http".equalsIgnoreCase(u.getScheme())&&trustedCdn(h))url="https://"+url.substring(7);
                if(kind==LinkExtractor.Kind.VIDEO&&trustedCdn(h)&&"/aweme/v1/playwm/".equals(u.getRawPath()))url=url.replaceFirst("/aweme/v1/playwm/","/aweme/v1/play/");
                int before=result.items.size();result.add(kind,url,label);if(kind==LinkExtractor.Kind.IMAGE&&result.items.size()>before)break;
            }catch(Exception ignored){}
        }
    }
    private static boolean trustedCdn(String host){String h=host.toLowerCase(Locale.ROOT);for(String suffix:new String[]{"douyin.com","iesdouyin.com","snssdk.com","amemv.com","douyinvod.com","byteimg.com","ibyteimg.com","douyinpic.com","douyincdn.com","pstatp.com"})if(h.equals(suffix)||h.endsWith("."+suffix))return true;return false;}
    private static JSONObject find(Object value,String id,int depth){
        if(depth>12)return null;if(value instanceof JSONObject){JSONObject o=(JSONObject)value;if(id.equals(o.optString("aweme_id")))return o;
            Iterator<String> keys=o.keys();while(keys.hasNext()){String key=keys.next();if(key.toLowerCase(Locale.ROOT).contains("recommend"))continue;JSONObject found=find(o.opt(key),id,depth+1);if(found!=null)return found;}
        }else if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<Math.min(a.length(),100);i++){JSONObject found=find(a.opt(i),id,depth+1);if(found!=null)return found;}}return null;
    }
    private static String balanced(String html,int start)throws IOException {
        if(start<0)throw new IOException("抖音作品数据为空");boolean quote=false,escape=false;int depth=0;
        for(int i=start;i<html.length();i++){char c=html.charAt(i);if(quote){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')quote=false;}else if(c=='"')quote=true;else if(c=='{')depth++;else if(c=='}'&&--depth==0)return XhsParser.normalize(html.substring(start,i+1));}
        throw new IOException("抖音作品数据不完整");
    }
}
