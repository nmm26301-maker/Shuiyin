package com.zizai.watermark;
import java.net.URI;
import java.util.Locale;
final class GalleryRoutes {
    static boolean prefer(String url){
        try{URI u=new URI(url);String host=u.getHost().toLowerCase(Locale.ROOT),path=u.getPath();
            if(host.equals("instagram.com")||host.endsWith(".instagram.com"))return path.startsWith("/p/");
            for(String domain:new String[]{"pinterest.com","pin.it","pixiv.net","deviantart.com","flickr.com","500px.com","imgur.com","artstation.com","behance.net","tumblr.com"})
                if(host.equals(domain)||host.endsWith("."+domain))return true;
        }catch(Exception ignored){}return false;
    }
}
