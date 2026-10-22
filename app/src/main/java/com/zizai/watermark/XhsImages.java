package com.zizai.watermark;

import java.net.URI;
import java.util.Locale;
import org.json.*;

/** Prefer published originals; unsigned XHS CDN transforms get one reversible fallback. */
final class XhsImages {
    static String primary(JSONObject image){
        for(String key:new String[]{"urlOriginal","urlOrigin","originalUrl"}){String url=XhsParser.mediaUrl(image.optString(key,""));if(!url.isEmpty())return url;}
        JSONArray info=image.optJSONArray("infoList");if(info!=null)for(int i=0;i<info.length();i++){
            JSONObject row=info.optJSONObject(i);if(row==null)continue;String scene=row.optString("imageScene","");
            if(scene.equalsIgnoreCase("ORIGINAL")||scene.equalsIgnoreCase("WB_DFT")){String url=XhsParser.mediaUrl(row.optString("url",""));if(!url.isEmpty())return originalCandidate(url);}
        }
        return originalCandidate(fallback(image));
    }
    static String fallback(JSONObject image){
        String url=XhsParser.mediaUrl(image.optString("urlDefault",image.optString("url","")));if(!url.isEmpty())return url;
        JSONArray info=image.optJSONArray("infoList");if(info!=null)for(int i=0;i<info.length();i++){JSONObject row=info.optJSONObject(i);if(row!=null){url=XhsParser.mediaUrl(row.optString("url",""));if(!url.isEmpty())return url;}}return "";
    }
    static String originalCandidate(String url){
        try{URI u=new URI(url);String h=u.getHost(),path=u.getRawPath();if(h==null||path==null||u.getRawQuery()!=null||u.getRawFragment()!=null)return url;
            h=h.toLowerCase(Locale.ROOT);if(!h.equals("xhscdn.com")&&!h.endsWith(".xhscdn.com"))return url;
            int at=path.indexOf('!');if(at<1||!path.substring(at).matches("!nd_[A-Za-z0-9_]+"))return url;
            return "https://"+u.getRawAuthority()+path.substring(0,at);
        }catch(Exception e){return url;}
    }
}
