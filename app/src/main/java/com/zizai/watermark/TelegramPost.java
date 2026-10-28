package com.zizai.watermark;

import java.io.IOException;
import java.net.URI;
import java.util.regex.*;

/** Selected public Telegram embed post. No private-session or restricted-channel extraction. */
final class TelegramPost {
    static boolean matches(String url){return DownloadSites.host(url).equals("t.me");}
    static String page(String source)throws Exception {
        if(!matches(source))throw new IOException("请输入 t.me 公开帖子链接");
        Matcher m=Pattern.compile("/(?:s/)?([A-Za-z][A-Za-z0-9_]{3,63})/([1-9][0-9]{0,18})/?").matcher(new URI(source).getPath());
        if(!m.matches()||m.group(1).equals("joinchat"))throw new IOException("仅支持公开频道的具体帖子链接，私有频道和邀请链接尚未支持");
        return "https://t.me/"+m.group(1)+"/"+m.group(2);
    }
    static String embed(String source)throws Exception{return page(source)+"?embed=1";}
    static LinkExtractor.Result parse(String html,String source)throws Exception {
        String page=page(source),post=page.substring("https://t.me/".length());
        if(html.length()>1500000)throw new IOException("Telegram 页面过大");
        if(html.contains("tgme_widget_message_protected")||html.contains("data-protected=\"true\""))throw new IOException("该 Telegram 内容限制保存");
        Matcher marker=Pattern.compile("\\bdata-post=[\"']"+Pattern.quote(post)+"[\"']").matcher(html);
        if(!marker.find())throw new IOException("Telegram 未返回这条公开帖子，可能需要登录或当前网络不可访问");
        String selected=html.substring(marker.end());Matcher next=Pattern.compile("\\bdata-post=[\"']").matcher(selected);if(next.find())selected=selected.substring(0,next.start());
        LinkExtractor.Result result=new LinkExtractor.Result();result.source=page;result.title="Telegram 公开帖子";
        Matcher videos=Pattern.compile("(?is)<video\\b[^>]*>").matcher(selected);
        while(videos.find())add(result,LinkExtractor.Kind.VIDEO,attribute(videos.group(),"src"),page,"公开帖子视频");
        Matcher photos=Pattern.compile("(?is)<a\\b[^>]*class=[\"'][^\"']*tgme_widget_message_photo_wrap[^\"']*[\"'][^>]*>").matcher(selected);
        while(photos.find()){
            Matcher image=Pattern.compile("background-image\\s*:\\s*url\\(['\"]?([^'\")]+)").matcher(attribute(photos.group(),"style"));
            if(image.find())add(result,LinkExtractor.Kind.IMAGE,image.group(1),page,"公开帖子图片");
        }
        if(result.items.isEmpty())throw new IOException("这条公开帖子没有可保存的视频或图片");return result;
    }
    private static String attribute(String tag,String name){Matcher m=Pattern.compile("(?is)\\b"+name+"\\s*=\\s*([\"'])(.*?)\\1").matcher(tag);return m.find()?m.group(2):"";}
    private static void add(LinkExtractor.Result r,LinkExtractor.Kind kind,String url,String page,String label){
        r.addItem(new LinkExtractor.Item(kind,url.replace("&amp;","&"),label,page,false,0,WebSessionCookies.userAgent(""),page));
    }
}
