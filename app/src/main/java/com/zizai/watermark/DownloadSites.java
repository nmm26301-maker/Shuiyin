package com.zizai.watermark;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/** Website-assisted download routes, separate from the custom JSON parsing API. */
final class DownloadSites {
    static final class Site {
        final String name,url,keywords,detail,input,button,buttonText;
        final boolean searchable;
        Site(String name,String url,String keywords,String detail){this(name,url,keywords,detail,"","","");}
        Site(String name,String url,String keywords,String detail,String input,String button,String buttonText){
            this.name=name;this.url=url;this.keywords=keywords;this.detail=detail;this.input=input;this.button=button;this.buttonText=buttonText;searchable=!input.isEmpty();
        }
        boolean matches(String query){return (name+" "+url+" "+keywords).toLowerCase(Locale.ROOT).contains(query==null?"":query.trim().toLowerCase(Locale.ROOT));}
        boolean owns(String page){return !host(url).isEmpty()&&host(url).equals(host(page));}
    }
    static final List<Site> ALL=java.util.Collections.unmodifiableList(java.util.Arrays.asList(
        new Site("Cobalt","https://cobalt.tools/","推特 Twitter X x.com 视频 下载 cobalt.tools","推特 / X 视频 · 可配置授权 API，或使用网页辅助","#link-area","#download-button",""),
        new Site("TikTokio","https://tiktokio.bio/","抖音 Douyin TikTok 海外版 短视频 下载 tiktokio.bio","TikTok 视频 · 实际表单请求；不保证支持抖音","#main_page_text","#submit",""),
        new Site("GreenVideo","https://greenvideo.cc/","小红书 XHS B站 B 站 哔哩哔哩 bilibili b23.tv 视频 下载 greenvideo.cc","小红书 / B 站 · 需要验证时请手动完成","input.n-input__input-el","button.button-1",""),
        new Site("IdeaFlow 解析","https://parse.ideaflow.top/","抖音 小红书 快手 微博 图集 实况 ideaflow","公开分享链接解析 · 视频 / 图片 / 实况统一保存","#app input[type=text]","","解析"),
        new Site("酷酷工具","https://dy.kukutool.com/","小红书 知乎 抖音 微博 快手 去水印 kukutool","网页解析 · 可能要求验证码或登录，请正常完成后重试","input[placeholder=\"粘贴带链接的文本\"],input[placeholder=\"Paste text with link\"]","button.bg-blue-500",""),
        new Site("Twitter Video Downloader","https://twittervideodownloader.com/en/","推特 Twitter X x.com GIF twittervideodownloader","推特 / X 视频与 GIF · 网站表单解析","#tweetURL","#submitBtn",""),
        new Site("全能视频下载器 · res-downloader","https://github.com/putyy/res-downloader","小红书 抖音 快手 视频号 微信 电脑 Windows macOS Linux","电脑软件 · 需在电脑运行并配置本机代理；打开项目查看安装说明"),
        new Site("视频号下载器 · wx_channels_download","https://github.com/ltaoo/wx_channels_download","视频号 微信 Windows macOS 电脑 ltaoo","电脑软件 · 依赖电脑微信及本机服务；手机分享链接不能直接调用"),
        new Site("微信视频下载工具","https://github.com/qiye45/wechatVideoDownload","微信 视频号 直播 回放 电脑 Windows qiye45","电脑工具 · 按项目说明在电脑运行；当前入口提供安装与使用文档"),
        new Site("Youwee","https://github.com/vanloctech/youwee","YouTube TikTok yt-dlp Windows macOS Linux 电脑","电脑软件 · 基于 yt-dlp；本应用已有同类平台引擎，项目程序需另行安装"),
        new Site("VidBee","https://github.com/nexmoe/VidBee","多平台 YouTube TikTok Twitter B站 yt-dlp 电脑","电脑软件 / 自建服务 · 需自行运行；打开项目查看官方安装说明"),
        new Site("Telegram 媒体下载工具","https://github.com/Neet-Nestor/Telegram-Media-Downloader","Telegram 电报 图片 视频 浏览器 脚本 油猴","浏览器用户脚本 · 需支持扩展的浏览器及本人 Telegram 登录；不能直接在应用内运行")
    ));
    static final List<Site> SEARCH=java.util.Collections.unmodifiableList(ALL.stream().filter(site->site.searchable).collect(java.util.stream.Collectors.toList()));
    static String[] verificationNames(){String[] names=new String[SEARCH.size()+1];names[0]="原平台作品 · 播放 / 登录验证";for(int i=0;i<SEARCH.size();i++)names[i+1]=SEARCH.get(i).name;return names;}
    static Site recommended(String source){
        String h=host(source);
        if(domain(h,"x.com")||domain(h,"twitter.com")||h.equals("t.co"))return ALL.get(0);
        if(domain(h,"douyin.com")||domain(h,"iesdouyin.com"))return SEARCH.get(3);
        if(domain(h,"tiktok.com"))return ALL.get(1);
        if(domain(h,"xiaohongshu.com")||domain(h,"xhslink.com")||domain(h,"bilibili.com")||h.equals("b23.tv"))return ALL.get(2);
        if(domain(h,"kuaishou.com")||domain(h,"gifshow.com")||domain(h,"weibo.com")||domain(h,"weibo.cn"))return SEARCH.get(3);
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
