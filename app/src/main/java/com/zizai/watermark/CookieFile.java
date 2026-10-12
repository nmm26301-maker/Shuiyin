package com.zizai.watermark;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.URI;
import java.util.Locale;
/** Netscape cookies restricted to their domain, path, security and expiry. */
final class CookieFile {
    static String header(File file,String url){
        if(file==null||!file.isFile()||file.length()>1_048_576)return "";
        try{return header(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8),url,System.currentTimeMillis()/1000);}catch(Exception e){return "";}
    }
    static String header(String text,String url,long now){
        try{URI u=new URI(url);String host=u.getHost().toLowerCase(Locale.ROOT),path=u.getPath();if(path==null||path.isEmpty())path="/";StringBuilder value=new StringBuilder();
            for(String line:text.split("\\r?\\n")){
                if(line.startsWith("#HttpOnly_"))line=line.substring(10);else if(line.startsWith("#"))continue;
                String[] p=line.split("\\t",-1);if(p.length!=7)continue;String domain=p[0].toLowerCase(Locale.ROOT);if(domain.startsWith("."))domain=domain.substring(1);
                boolean match=host.equals(domain)||("TRUE".equalsIgnoreCase(p[1])&&host.endsWith("."+domain));if(!match||domain.isEmpty())continue;
                String cp=p[2].isEmpty()?"/":p[2];if(!path.equals(cp)&&!(path.startsWith(cp)&&(cp.endsWith("/")||path.charAt(cp.length())=='/')))continue;
                if("TRUE".equalsIgnoreCase(p[3])&&!"https".equalsIgnoreCase(u.getScheme()))continue;
                long expiry;try{expiry=Long.parseLong(p[4]);}catch(Exception e){continue;}if(expiry!=0&&expiry<=now)continue;
                if(!p[5].matches("[A-Za-z0-9_!#$%&'*+.^`|~-]+")||p[6].contains(";")||p[6].contains("\r")||p[6].contains("\n"))continue;
                if(value.length()>0)value.append("; ");value.append(p[5]).append('=').append(p[6]);
            }return value.toString();
        }catch(Exception e){return "";}
    }
}
