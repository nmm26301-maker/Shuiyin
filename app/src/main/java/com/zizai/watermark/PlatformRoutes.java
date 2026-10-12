package com.zizai.watermark;
import java.net.URI;
import java.util.Locale;
final class PlatformRoutes {
    private static final String[] SHORT={"v.douyin.com","b23.tv","vm.tiktok.com","vt.tiktok.com","fb.watch","pin.it","t.co","v.kuaishou.com"};
    static boolean shortLink(String url){String host=host(url);for(String s:SHORT)if(s.equals(host))return true;return false;}
    static String host(String url){try{String h=new URI(url).getHost();return h==null?"":h.toLowerCase(Locale.ROOT);}catch(Exception e){return "";}}
    static String name(String url){String h=host(url);
        String[][] names={{"acfun.cn","AcFun"},{"youku.com","优酷"},{"iqiyi.com","爱奇艺"},{"v.qq.com","腾讯视频"},{"mgtv.com","芒果TV"},{"ximalaya.com","喜马拉雅"},{"zhihu.com","知乎视频"},{"y.qq.com","QQ音乐"},{"linkedin.com","LinkedIn"},{"tumblr.com","Tumblr"},{"ted.com","TED"},{"bbc.com","BBC"},{"espn.com","ESPN"},{"coub.com","Coub"},{"streamable.com","Streamable"},{"imgur.com","Imgur"},{"flickr.com","Flickr"},{"music.yandex.ru","Yandex Music"},{"bitchute.com","BitChute"},{"bigo.tv","Bigo Live"},{"mixcloud.com","Mixcloud"},{"youtube.com","YouTube"},{"youtu.be","YouTube"},{"tiktok.com","TikTok"},{"douyin.com","抖音"},{"bilibili.com","哔哩哔哩"},{"b23.tv","哔哩哔哩"},{"xiaohongshu.com","小红书"},{"xhslink.cn","小红书"},{"xhslink.com","小红书"},{"kuaishou.com","快手"},{"weibo.com","微博"},{"weibo.cn","微博"},{"instagram.com","Instagram"},{"facebook.com","Facebook"},{"fb.watch","Facebook"},{"x.com","X"},{"twitter.com","X"},{"reddit.com","Reddit"},{"pinterest.com","Pinterest"},{"pin.it","Pinterest"},{"vimeo.com","Vimeo"},{"dailymotion.com","Dailymotion"},{"twitch.tv","Twitch"},{"nicovideo.jp","Niconico"},{"soundcloud.com","SoundCloud"},{"bandcamp.com","Bandcamp"},{"bsky.app","Bluesky"},{"vk.com","VK"},{"ok.ru","OK"},{"rutube.ru","Rutube"},{"rumble.com","Rumble"},{"naver.com","Naver"},{"t.me","Telegram"},{"snapchat.com","Snapchat"},{"threads.net","Threads"},{"threads.com","Threads"},{"sooplive.co.kr","SOOP"}};
        if(h.equals("t.co"))return "X";if(h.equals("sooplive.com")||h.endsWith(".sooplive.com"))return "SOOP";
        for(String[] n:names)if(h.equals(n[0])||h.endsWith("."+n[0]))return n[1];return "网页";
    }
}
