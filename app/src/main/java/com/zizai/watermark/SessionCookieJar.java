package com.zizai.watermark;
import java.net.URI;
/** Convert cookies from the user's in-app session into an exact-host native engine jar. */
final class SessionCookieJar {
    static String netscape(String url,String header){
        if(!EngineMetadata.https(url)||header==null||header.isEmpty()||header.contains("\r")||header.contains("\n"))return "";
        try{URI u=new URI(url);String path=u.getPath();if(path==null||path.isEmpty())path="/";StringBuilder out=new StringBuilder("# Netscape HTTP Cookie File\n");int count=0;
            for(String part:header.split(";")){int eq=part.indexOf('=');if(eq<1)continue;String name=part.substring(0,eq).trim(),value=part.substring(eq+1).trim();if(!name.matches("[A-Za-z0-9_!#$%&'*+.^`|~-]+")||value.contains("\t"))continue;
                out.append(u.getHost()).append("\tFALSE\t").append(path).append("\tTRUE\t0\t").append(name).append('\t').append(value).append('\n');count++;
            }return count==0?"":out.toString();
        }catch(Exception e){return "";}
    }
}
