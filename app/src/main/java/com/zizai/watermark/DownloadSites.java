package com.zizai.watermark;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/** Website-assisted download routes, separate from the custom JSON parsing API. */
final class DownloadSites {
    static final class Site {
        final String name,url,keywords,detail;
        Site(String name,String url,String keywords,String detail){this.name=name;this.url=url;this.keywords=keywords;this.detail=detail;}
        boolean matches(String query){return (name+" "+url+" "+keywords).toLowerCase(Locale.ROOT).contains(query==null?"":query.trim().toLowerCase(Locale.ROOT));}
        boolean owns(String page){return host(url).equals(host(page));}
    }
    static final List<Site> ALL=java.util.Collections.unmodifiableList(java.util.Arrays.asList(
        new Site("Cobalt","https://cobalt.tools/","推特 Twitter X x.com 视频 下载 cobalt.tools","推特 / X 视频 · 网站解析，生成下载链接后保存"),
        new Site("TikTokio","https://tiktokio.bio/","抖音 Douyin TikTok 海外版 短视频 下载 tiktokio.bio","TikTok 视频 · 抖音链接入口已收录，实际支持待验证"),
        new Site("GreenVideo","https://greenvideo.cc/","小红书 XHS B站 B 站 哔哩哔哩 bilibili b23.tv 视频 下载 greenvideo.cc","小红书视频 · B 站链接入口已收录，实际支持待验证")
    ));
    static Site recommended(String source){
        String h=host(source);
        if(domain(h,"x.com")||domain(h,"twitter.com")||h.equals("t.co"))return ALL.get(0);
        if(domain(h,"douyin.com")||domain(h,"iesdouyin.com")||domain(h,"tiktok.com"))return ALL.get(1);
        if(domain(h,"xiaohongshu.com")||domain(h,"xhslink.com")||domain(h,"bilibili.com")||h.equals("b23.tv"))return ALL.get(2);
        return null;
    }
    static String host(String raw){try{URI u=new URI(raw);if(!"https".equalsIgnoreCase(u.getScheme())||u.getRawUserInfo()!=null)return "";String h=u.getHost();return h==null?"":h.toLowerCase(Locale.ROOT);}catch(Exception e){return "";}}
    private static boolean domain(String h,String suffix){return h.equals(suffix)||h.endsWith("."+suffix);}
    static String mediaKind(String url,String mime,String disposition){
        if(host(url).isEmpty())return "";
        String type=mime==null?"":mime.toLowerCase(Locale.ROOT).split(";",2)[0].trim();
        if(type.startsWith("video/")||type.contains("mpegurl")||type.contains("dash+xml"))return "VIDEO";
        if(type.startsWith("audio/"))return "AUDIO";
        if(type.matches("image/(jpeg|png|webp|gif|avif|heic|heif)"))return "IMAGE";
        if(!type.isEmpty()&&!type.equals("application/octet-stream")&&!type.equals("binary/octet-stream"))return "";
        String path="";try{path=new URI(url).getPath();}catch(Exception ignored){}
        String name=(path+" "+(disposition==null?"":disposition)).toLowerCase(Locale.ROOT);
        if(name.matches("(?s).*\\.(mp4|m4v|mov|webm|m3u8|mpd)(?:[\\s\"';].*)?$"))return "VIDEO";
        if(name.matches("(?s).*\\.(mp3|m4a|aac|ogg|opus|wav|flac)(?:[\\s\"';].*)?$"))return "AUDIO";
        if(name.matches("(?s).*\\.(jpe?g|png|webp|gif|avif)(?:[\\s\"';].*)?$"))return "IMAGE";
        return "";
    }
    static String fillScript(String source){
        StringBuilder escaped=new StringBuilder();for(int i=0;i<source.length();i++)escaped.append(String.format(Locale.ROOT,"\\u%04x",(int)source.charAt(i)));
        return "(function(){var x=Array.from(document.querySelectorAll('input[type=url],input[name=url],input[name=link],input[type=text],input:not([type])')).find(x=>!x.disabled&&!x.readOnly&&x.getClientRects().length);if(!x)return false;var s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(x,'"+escaped+"');x.dispatchEvent(new Event('input',{bubbles:true}));x.dispatchEvent(new Event('change',{bubbles:true}));return true;})()";
    }
}
