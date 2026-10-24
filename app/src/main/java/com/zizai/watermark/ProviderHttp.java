package com.zizai.watermark;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Explicit public-form/authorized-instance protocols; never derives protected API tokens. */
final class ProviderHttp {
    interface Opener {HttpURLConnection open(String url)throws Exception;}
    private static final Opener PUBLIC=url->{LinkExtractor.ensurePublic(url);return (HttpURLConnection)new URL(url).openConnection();};
    static final String AGENT="Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Mobile Safari/537.36";
    static final class Request {
        final String url,contentType,referer,authorization;final byte[] body;
        Request(String url,String type,String referer,String authorization,byte[] body){this.url=url;contentType=type;this.referer=referer;this.authorization=authorization;this.body=body;}
    }
    static LinkExtractor.Result tiktokio(String share)throws Exception{return execute(tiktokioRequest(share),share,false,PUBLIC);}
    static LinkExtractor.Result cobalt(String share,String endpoint,String key)throws Exception{return execute(cobaltRequest(share,endpoint,key),share,true,PUBLIC);}
    static Request tiktokioRequest(String share){
        String boundary="----WatermarkForm"+UUID.randomUUID().toString().replace("-","");
        String body="--"+boundary+"\r\nContent-Disposition: form-data; name=\"page\"\r\n\r\n"+share+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"ajax\"\r\n\r\n1\r\n--"+boundary+"--\r\n";
        return new Request("https://tiktokio.bio/","multipart/form-data; boundary="+boundary,"https://tiktokio.bio/","",body.getBytes(StandardCharsets.UTF_8));
    }
    static Request cobaltRequest(String share,String endpoint,String key)throws Exception {
        if(!EngineMetadata.https(endpoint))throw new IOException("请先配置自建或已授权的 Cobalt HTTPS 接口");
        if(key==null)key="";if(key.length()>512||key.chars().anyMatch(c->c<32||c>126))throw new IOException("Cobalt API Key 格式无效");
        byte[] body=new JSONObject().put("url",share).put("localProcessing","disabled").toString().getBytes(StandardCharsets.UTF_8);
        return new Request(endpoint,"application/json","",key.isEmpty()?"":"Api-Key "+key,body);
    }
    static LinkExtractor.Result execute(Request request,String share,boolean cobalt,Opener opener)throws Exception {
        return execute(request,share,cobalt,opener,12000);
    }
    static LinkExtractor.Result execute(Request request,String share,boolean cobalt,Opener opener,long budgetMs)throws Exception {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(budgetMs);checkDeadline(deadline);
        JobControl.check();HttpURLConnection connection=opener.open(request.url);
        try{
            checkDeadline(deadline);int timeout=(int)Math.max(1,Math.min(4000,java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime())));
            connection.setInstanceFollowRedirects(false);connection.setRequestMethod("POST");connection.setConnectTimeout(timeout);connection.setReadTimeout(timeout);connection.setDoOutput(true);
            connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("Content-Type",request.contentType);connection.setRequestProperty("User-Agent",WebSessionCookies.userAgent(AGENT));
            if(!request.referer.isEmpty())connection.setRequestProperty("Referer",request.referer);
            if(!request.authorization.isEmpty())connection.setRequestProperty("Authorization",request.authorization);
            String cookies=WebSessionCookies.header(request.url);if(!cookies.isEmpty())connection.setRequestProperty("Cookie",cookies);
            connection.setFixedLengthStreamingMode(request.body.length);try(OutputStream out=connection.getOutputStream()){out.write(request.body);}
            checkDeadline(deadline);int status=connection.getResponseCode();WebSessionCookies.receive(request.url,connection.getHeaderFields());checkDeadline(deadline);
            if(status==401||status==403)throw new IOException("接口拒绝访问（HTTP "+status+"），需要有效授权或正常网页验证");
            if(status>=300&&status<400)throw new IOException("接口返回跳转，未转发授权信息；请填写最终接口地址");
            if(status<200||status>=300)throw new IOException("接口返回 HTTP "+status);
            String text;try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){checkDeadline(deadline);if(out.size()+n>1500000)throw new IOException("接口响应过大");out.write(bytes,0,n);}text=out.toString("UTF-8");
            }
            checkDeadline(deadline);
            if(!text.trim().startsWith("{"))throw new IOException("网站返回网页而非接口结果，可能要求验证");
            return cobalt?cobaltResult(text,share,request.url):tiktokioResult(text,share);
        }finally{connection.disconnect();}
    }
    private static void checkDeadline(long deadline)throws IOException {
        JobControl.check();if(System.nanoTime()>=deadline)throw new java.net.SocketTimeoutException("解析接口请求超时，继续网页回退");
    }
    static LinkExtractor.Result tiktokioResult(String json,String share)throws Exception {
        JSONObject object=new JSONObject(json);if(!object.optBoolean("success")){
            String message=object.optString("error","未返回下载链接").replaceAll("<[^>]*>","").replaceAll("\\s+"," ").trim();
            throw new IOException("TikTokio 接口拒绝："+message.substring(0,Math.min(160,message.length())));
        }
        LinkExtractor.Result result=result(share,"TikTokio 素材");result.description=object.optString("desc","");JSONObject links=object.optJSONObject("links");
        if(links!=null){Iterator<String> keys=links.keys();while(keys.hasNext()){
            String label=keys.next(),lower=label.toLowerCase(Locale.ROOT);if(lower.contains("watermark")&&!lower.contains("no watermark")&&!lower.contains("without watermark"))continue;
            String url=links.optString(label,"");String kind=DownloadSites.mediaKind(url,"","");if(kind.isEmpty())kind=lower.matches(".*(mp3|audio).*?")?"AUDIO":lower.contains("video")?"VIDEO":lower.matches(".*(photo|image).*?")?"IMAGE":"";
            add(result,kind,url,label,"https://tiktokio.bio/");
        }}return requireItems(result);
    }
    static LinkExtractor.Result cobaltResult(String json,String share,String endpoint)throws Exception {
        JSONObject object=new JSONObject(json);String status=object.optString("status");LinkExtractor.Result result=result(share,"Cobalt 素材");
        if(status.equals("redirect")||status.equals("tunnel")){
            String url=object.optString("url"),filename=object.optString("filename"),kind=DownloadSites.mediaKind(url,"","attachment; filename=\""+filename+"\"");
            add(result,kind.isEmpty()?"VIDEO":kind,url,filename,endpoint);
        }else if(status.equals("picker")){
            add(result,"AUDIO",object.optString("audio"),object.optString("audioFilename","图集背景音频"),endpoint);
            JSONArray picker=object.optJSONArray("picker");if(picker!=null)for(int i=0;i<Math.min(24,picker.length());i++){
                JSONObject row=picker.optJSONObject(i);if(row==null)continue;String type=row.optString("type"),kind=type.equals("photo")?"IMAGE":type.equals("video")?"VIDEO":type.equals("audio")?"AUDIO":"";add(result,kind,row.optString("url"),"Cobalt "+type,endpoint);
            }
        }else if(status.equals("local-processing"))throw new IOException("Cobalt 返回分离轨道，未把它误当成完整视频");
        else if(status.equals("error")){String code=object.optJSONObject("error")==null?"":object.optJSONObject("error").optString("code");throw new IOException(code.startsWith("api.auth.")?"Cobalt 需要有效的实例授权":"Cobalt 未能解析这条链接");}
        return requireItems(result);
    }
    private static LinkExtractor.Result result(String share,String title){LinkExtractor.Result r=new LinkExtractor.Result();r.source=share;r.title=title;return r;}
    private static void add(LinkExtractor.Result result,String kind,String url,String label,String referer){try{result.addItem(new LinkExtractor.Item(LinkExtractor.Kind.valueOf(kind),url,label,referer,false,0,WebSessionCookies.userAgent(AGENT),result.source));}catch(IllegalArgumentException ignored){}}
    private static LinkExtractor.Result requireItems(LinkExtractor.Result result)throws IOException{if(result.items.isEmpty())throw new IOException("接口未返回可下载的 HTTPS 媒体");return result;}
}
