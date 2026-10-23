package com.zizai.watermark;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Public-page metadata + direct HTTPS media downloader.
 * NOT an authenticated platform scraper, DRM bypasser or "thousands of sites" promise.
 * Network requests happen only after the user enters/shares a link and taps Parse/Save.
 */
public final class LinkExtractor {
    private LinkExtractor() {}
    private static final int HTML_LIMIT = 1_500_000;
    private static final long FILE_LIMIT = MediaLimits.DOWNLOAD;
    private static final Pattern LINK = Pattern.compile("https?://[^\\s<>\\\"'，。；）】]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern META = Pattern.compile("<meta\\b[^>]*>",Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile("([\\w:-]+)\\s*=\\s*(?:\\\"([^\\\"]*)\\\"|'([^']*)'|([^\\s>]+))",Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
    private static final Pattern SOURCES = Pattern.compile("<(?:video|audio|source)\\b[^>]*>",Pattern.CASE_INSENSITIVE);

    public enum Kind { VIDEO, IMAGE, AUDIO }
    public static class Item {
        public final Kind kind;
        public final String url;
        public final String label;
        public final String referer;
        public final String source;
        public final String fallbackUrl;
        public final String userAgent;
        public final boolean engine;
        public final int engineIndex;
        public Item(Kind kind,String url,String label) {this(kind,url,label,"");}
        public Item(Kind kind,String url,String label,String referer) { this(kind,url,label,referer,false,0); }
        Item(Kind kind,String url,String label,String referer,boolean engine,int index){this(kind,url,label,referer,engine,index,"");}
        Item(Kind kind,String url,String label,String referer,boolean engine,int index,String agent){this(kind,url,label,referer,engine,index,agent,referer);}
        Item(Kind kind,String url,String label,String referer,boolean engine,int index,String agent,String source){this(kind,url,label,referer,engine,index,agent,source,"");}
        Item(Kind kind,String url,String label,String referer,boolean engine,int index,String agent,String source,String fallback){this.fallbackUrl=fallback==null?"":fallback;this.source=source==null?"":source;this.kind=kind;this.url=url;this.label=label;this.referer=referer;this.engine=engine;this.engineIndex=index;this.userAgent=agent!=null&&agent.length()<768&&agent.indexOf('\r')<0&&agent.indexOf('\n')<0?agent:"";}
    }
    public static class Result {
        public String title="网页素材";
        public String description="";
        public String source="";
        public String notice="";
        public final List<Item> items = new ArrayList<>();
        private final Set<String> unique = new HashSet<>();
        public void add(Kind kind,String url,String label) {
            if(url==null||url.isEmpty()||items.size()>=24)return;
            try {URI u=new URI(url);if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getRawUserInfo()!=null)return;
                if(unique.add(kind+"|"+url))items.add(new Item(kind,url,label,source));}
            catch(Exception ignored) {}
        }
        public void addEngine(Kind kind,String url,String label,int index){
            if(items.size()<24&&EngineMetadata.https(url)&&unique.add(kind+"|engine|"+url+"|"+index))items.add(new Item(kind,url,label,source,true,index));
        }
        public void addItem(Item item){
            if(items.size()>=24||!EngineMetadata.https(item.url))return;
            String key=item.kind+"|"+(item.engine?"engine|":"")+item.url+(item.engine?"|"+item.engineIndex:"");
            if(unique.add(key))items.add(item);
        }
        public int count(Kind kind) {int n=0;for(Item item:items)if(item.kind==kind)n++;return n;}
    }
    public static String extractLink(String raw) {
        if(raw==null)return "";
        Matcher m=LINK.matcher(raw);
        if(!m.find())return "";
        String s=m.group();
        while(!s.isEmpty()&&".,;!?)]}，。；！？）】".indexOf(s.charAt(s.length()-1))>=0)s=s.substring(0,s.length()-1);
        if(s.regionMatches(true,0,"http://",0,7))s="https://"+s.substring(7);
        return s;
    }
    static List<String> extractLinks(String raw)throws IOException {
        java.util.LinkedHashSet<String> urls=new java.util.LinkedHashSet<>();Matcher m=LINK.matcher(raw==null?"":raw);
        while(m.find()){String url=extractLink(m.group());if(url.isEmpty())continue;int hash=url.indexOf('#');if(hash>=0)url=url.substring(0,hash);urls.add(url);
            if(urls.size()>LinkSaveQueue.MAX_LINKS)throw new IOException("一次最多添加 20 条分享链接，请分批添加");}
        return new ArrayList<>(urls);
    }
    public static Result parse(String suppliedText) throws Exception {
        String input=extractLink(suppliedText);
        if(input.isEmpty())throw new IOException("请输入以 https:// 开头的公开网页或媒体链接");
        HttpURLConnection conn=open(input,"");
        Result res=new Result();
        try {
            String finalUrl=conn.getURL().toExternalForm();
            res.source=finalUrl;
            String mime=cleanMime(conn.getContentType());
            Kind direct=fromMime(mime,finalUrl);
            if(direct!=null && !mime.startsWith("text/")) {
                res.title="已识别媒体直链";
                res.add(direct,finalUrl,"原始媒体文件");
                return res;
            }
            if(!mime.isEmpty() && !mime.contains("html") && !mime.contains("text/plain"))
                throw new IOException("该链接不是可提取的公开网页或媒体文件（"+mime+"）");
            byte[] bytes=limitedRead(conn.getInputStream(), HTML_LIMIT);
            java.nio.charset.Charset charset=StandardCharsets.UTF_8;
            String contentType=conn.getContentType();
            if(contentType!=null){Matcher cm=Pattern.compile("charset=([A-Za-z0-9_-]+)",Pattern.CASE_INSENSITIVE).matcher(contentType);if(cm.find())try{charset=java.nio.charset.Charset.forName(cm.group(1));}catch(Exception ignored){}}
            String html=new String(bytes,charset);
            return parsePublicHtml(html,finalUrl);
        } finally {conn.disconnect();}
    }
    static Result parseXhs(Context context,String input)throws Exception {
        return parseXhs(new java.io.File(context.getFilesDir(),"link-cookies.txt"),input);
    }
    static String resolveShare(Context context,String url)throws Exception{
        HttpURLConnection c=open(url,"",new java.io.File(context.getFilesDir(),"link-cookies.txt"));
        try{return c.getURL().toExternalForm();}finally{c.disconnect();}
    }
    static Result parseXhs(java.io.File cookies,String input)throws Exception {
        HttpURLConnection c=open(input,"",cookies);
        try{
            String source=c.getURL().toExternalForm();
            if(!XhsParser.matches(source))throw new IOException("小红书短链接跳到了其他网站，请检查分享链接");
            String html=new String(limitedRead(c.getInputStream(),HTML_LIMIT),StandardCharsets.UTF_8);
            Result candidates=XhsParser.parseHtml(html,source),result=new Result();result.source=source;result.title=candidates.title;result.description=candidates.description;
            Exception last=null;boolean video=false;int attempts=0;
            for(Item item:candidates.items)if(item.kind==Kind.VIDEO){
                video=true;if(attempts++>=5)break;JobControl.check();HttpURLConnection probe=null;
                try{
                    probe=open(item.url,source,cookies,"bytes=0-31");
                    long size=probe.getContentLengthLong();String range=probe.getHeaderField("Content-Range");if(range!=null){Matcher total=Pattern.compile("/(\\d+)$").matcher(range);if(total.find())size=Long.parseLong(total.group(1));}if(size>FILE_LIMIT)throw new IOException("视频超过 2GB 下载上限");
                    byte[] header=new byte[32];int n=0;try(InputStream in=probe.getInputStream()){while(n<header.length){int count=in.read(header,n,header.length-n);if(count<0)break;n+=count;}}
                    if(!MediaSignature.accepts("video/mp4",header,n))throw new IOException("视频候选地址未返回媒体文件");
                    result.add(Kind.VIDEO,probe.getURL().toExternalForm(),item.label.replace("候选",""));
                    break;
                }catch(Exception error){JobControl.check();last=error;}finally{if(probe!=null)probe.disconnect();}
            }
            if(video&&result.count(Kind.VIDEO)==0)throw new IOException("已读到笔记，但视频地址暂时不可下载。请重新解析更新地址，或尝试网页辅助。",last);
            for(Item item:candidates.items)if(item.kind==Kind.IMAGE)result.addItem(item);
            return result;
        }finally{c.disconnect();}
    }
    static Result parseDouyin(Context context,String input)throws Exception {
        java.io.File cookies=new java.io.File(context.getFilesDir(),"link-cookies.txt");
        String source=input;if(DouyinParser.id(source).isEmpty())source=resolveShare(context,input);
        String mobile=DouyinParser.sharePage(source);if(mobile.isEmpty())throw new IOException("抖音短链接未展开到作品页面");
        HttpURLConnection connection=open(mobile,"",cookies);
        Result candidates;
        try{
            candidates=DouyinParser.parseHtml(new String(limitedRead(connection.getInputStream(),HTML_LIMIT),StandardCharsets.UTF_8),source);
        }finally{connection.disconnect();}
        Result prepared=new Result();prepared.source=source;prepared.title=candidates.title;prepared.description=candidates.description;
        for(Item item:candidates.items)prepared.addItem(new Item(item.kind,item.url,item.label,mobile,false,0,WebSessionCookies.userAgent(""),source));
        return verifyVideoCandidates(context,prepared);
    }
    static Result verifyVideoCandidates(Context context,Result candidates)throws Exception {
        return verifyVideoCandidates(context,candidates,8000);
    }
    static Result verifyVideoCandidates(Context context,Result candidates,long budgetMs)throws Exception {
        java.io.File cookies=new java.io.File(context.getFilesDir(),"link-cookies.txt");
        return VideoCandidates.select(candidates,(item,deadline)->open(item.url,item.referer,cookies,"bytes=0-63","",item.userAgent,deadline),budgetMs);
    }
    /** Optional self-hosted platform engine. Links are sent only to the configured endpoint. */
    public static Result parsePlatform(String share,String endpoint)throws Exception {
        ensurePublic(endpoint);JobControl.check();
        HttpURLConnection connection=(HttpURLConnection)new URL(endpoint).openConnection();
        connection.setInstanceFollowRedirects(false);connection.setRequestMethod("POST");connection.setConnectTimeout(10000);connection.setReadTimeout(60000);
        connection.setRequestProperty("Content-Type","application/json; charset=utf-8");connection.setDoOutput(true);
        try {
            byte[] request=new org.json.JSONObject().put("url",extractLink(share)).toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(request.length);
            try(OutputStream out=connection.getOutputStream()){out.write(request);}
            int code=connection.getResponseCode();if(code<200||code>=300)throw new IOException("平台解析服务返回 HTTP "+code);
            String json=new String(limitedRead(connection.getInputStream(),HTML_LIMIT),StandardCharsets.UTF_8);JobControl.check();return platformResult(json);
        }finally{connection.disconnect();}
    }
    static Result platformResult(String json)throws Exception {
        org.json.JSONObject body=new org.json.JSONObject(json);if(body.has("error"))throw new IOException(body.optString("error","平台解析失败"));
        Result result=new Result();result.source=body.optString("source","");result.title=cleanText(body.optString("title","平台素材"),160);result.description=cleanText(body.optString("description",""),6000);
        org.json.JSONArray items=body.optJSONArray("items");
        if(items!=null)for(int i=0;i<Math.min(24,items.length());i++){
            org.json.JSONObject item=items.optJSONObject(i);if(item==null)continue;String type=item.optString("kind","").toUpperCase(Locale.ROOT);
            try{result.add(Kind.valueOf(type),item.optString("url",""),item.optString("label","平台公开素材"));}catch(IllegalArgumentException ignored){}
        }
        if(result.items.isEmpty())throw new IOException("该平台暂未返回可下载的 HTTPS 媒体地址");return result;
    }
    /** Package-private for deterministic HTML metadata fixture tests. */
    static Result parsePublicHtml(String html, String finalUrl) throws IOException {
        Result res=new Result();res.source=finalUrl;
        Matcher title=TITLE.matcher(html);
        if(title.find())res.title=cleanText(title.group(1),160);
        String ogTitle="",description="";
        Matcher metas=META.matcher(html);
        while(metas.find()) {
            String tag=metas.group(),key=attribute(tag,"property");if(key.isEmpty())key=attribute(tag,"name");
            key=key.toLowerCase(Locale.ROOT);String val=attribute(tag,"content");
            switch(key) {
                case "og:title": case "twitter:title": if(ogTitle.isEmpty())ogTitle=val;break;
                case "og:description": case "twitter:description": case "description":if(description.isEmpty())description=val;break;
                case "og:image": case "og:image:url": case "og:image:secure_url": case "twitter:image": case "twitter:image:src":
                    res.add(Kind.IMAGE,resolve(finalUrl,decode(val)),"封面 / 页面图片");break;
                case "og:video": case "og:video:url": case "og:video:secure_url": case "twitter:player:stream":
                    res.add(Kind.VIDEO,resolve(finalUrl,decode(val)),"页面视频候选地址");break;
                case "og:audio": case "og:audio:url": case "og:audio:secure_url":res.add(Kind.AUDIO,resolve(finalUrl,decode(val)),"页面音频");break;
            }
        }
        if(!ogTitle.isEmpty())res.title=cleanText(ogTitle,160);
        res.description=cleanText(description,6000);
        Matcher tags=SOURCES.matcher(html);
        while(tags.find() && res.items.size()<16) {
            String tag=tags.group(), raw=attribute(tag,"src");
            String candidate=resolve(finalUrl,decode(raw));
            if(candidate.isEmpty())continue;
            String t=tag.substring(1,Math.min(tag.length(),10)).toLowerCase(Locale.ROOT);
            String kindHint=attribute(tag,"type").toLowerCase(Locale.ROOT);
            Kind sniff=fromMime(kindHint,candidate);
            String prefix=html.substring(0,tags.start()).toLowerCase(Locale.ROOT);
            boolean audioParent=prefix.lastIndexOf("<audio")>prefix.lastIndexOf("<video");
            Kind k=sniff!=null?sniff:t.startsWith("audio")||kindHint.startsWith("audio/")||audioParent?Kind.AUDIO:Kind.VIDEO;
            res.add(k,candidate,k==Kind.AUDIO?"页面音频":"页面视频");
        }
        Matcher blocks=Pattern.compile("<script\\b[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(html);
        while(blocks.find())try{parseStructured(new org.json.JSONTokener(blocks.group(1)).nextValue(),res,0);}catch(Exception ignored){}
        if(res.items.isEmpty() && res.description.isEmpty())
            throw new IOException("未找到可直接保存的公开素材。部分平台需要登录、动态接口或禁止网页提取；请使用平台授权下载方式。");
        return res;
    }
    private static String attribute(String tag,String name) {
        Matcher a=ATTR.matcher(tag);
        while(a.find())if(name.equalsIgnoreCase(a.group(1))) {
            for(int i=2;i<=4;i++)if(a.group(i)!=null)return a.group(i);
        }
        return "";
    }
    private static String decode(String s) {
        Matcher m=Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);").matcher(s);StringBuffer out=new StringBuffer();
        while(m.find()){
            String replacement=m.group();
            try{String v=m.group(1);int code=v.startsWith("x")?Integer.parseInt(v.substring(1),16):Integer.parseInt(v);if(Character.isValidCodePoint(code))replacement=new String(Character.toChars(code));}catch(Exception ignored){}
            m.appendReplacement(out,Matcher.quoteReplacement(replacement));
        }m.appendTail(out);
        return out.toString().replace("&quot;","\"").replace("&apos;","'").replace("&lt;","<").replace("&gt;",">").replace("&nbsp;"," ").replace("&amp;","&");
    }
    private static String cleanText(String html,int limit) {
        // Remove actual HTML tags before decoding entities so escaped literal angle
        // brackets in captions do not disappear as if they were markup.
        String txt=decode(html.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>"," ").replaceAll("(?s)<[^>]+>"," ")).replaceAll("\\s+"," ").trim();
        return txt.length()>limit?txt.substring(0,limit)+"…":txt;
    }
    private static void parseStructured(Object value,Result res,int depth)throws Exception {
        if(depth>8)return;
        if(value instanceof org.json.JSONArray){org.json.JSONArray array=(org.json.JSONArray)value;for(int i=0;i<Math.min(array.length(),48);i++)parseStructured(array.get(i),res,depth+1);return;}
        if(!(value instanceof org.json.JSONObject))return;
        org.json.JSONObject o=(org.json.JSONObject)value;
        String type=o.optString("@type","").toLowerCase(Locale.ROOT);
        if(type.contains("videoobject")||type.contains("audioobject")||type.contains("imageobject")){
            Kind kind=type.contains("audioobject")?Kind.AUDIO:type.contains("imageobject")?Kind.IMAGE:Kind.VIDEO;
            res.add(kind,resolve(res.source,o.optString("contentUrl","")),"结构化页面素材");
            Object thumbnail=o.opt("thumbnailUrl");
            if(thumbnail instanceof String)res.add(Kind.IMAGE,resolve(res.source,(String)thumbnail),"素材封面");
            if(thumbnail instanceof org.json.JSONArray){org.json.JSONArray a=(org.json.JSONArray)thumbnail;for(int i=0;i<Math.min(8,a.length());i++)res.add(Kind.IMAGE,resolve(res.source,a.optString(i,"")),"素材封面");}
            if(res.title.equals("网页素材")&&o.has("name"))res.title=cleanText(o.optString("name"),160);
            if(res.description.isEmpty())res.description=cleanText(o.optString("description"),6000);
        }
        java.util.Iterator<String> keys=o.keys();int count=0;
        while(keys.hasNext()&&count++<80){Object child=o.opt(keys.next());if(child instanceof org.json.JSONObject||child instanceof org.json.JSONArray)parseStructured(child,res,depth+1);}
    }
    private static String resolve(String base,String path) {
        try {
            if(path.isEmpty()||path.startsWith("data:")||path.startsWith("blob:"))return "";
            URI u=new URI(base).resolve(path.replace(" ","%20"));
            return "https".equalsIgnoreCase(u.getScheme())?u.toString():"";
        } catch(Exception ex){return "";}
    }
    private static String cleanMime(String type) {
        if(type==null)return "";
        return type.split(";",2)[0].trim().toLowerCase(Locale.ROOT);
    }
    private static Kind fromMime(String mime,String url) {
        if(mime.startsWith("image/"))return Kind.IMAGE;
        if(mime.startsWith("video/"))return Kind.VIDEO;
        if(mime.startsWith("audio/"))return Kind.AUDIO;
        String path;
        try{path=new URI(url).getPath().toLowerCase(Locale.ROOT);}catch(Exception e){return null;}
        if(path.matches(".*\\.(png|jpe?g|webp|gif|heic|heif|avif|bmp)$"))return Kind.IMAGE;
        if(path.matches(".*\\.(mp4|m4v|mov|webm)$"))return Kind.VIDEO;
        if(path.matches(".*\\.(mp3|m4a|aac|ogg|wav|flac)$"))return Kind.AUDIO;
        return null;
    }
    static void ensurePublic(String raw) throws Exception {
        URI u=new URI(raw);
        if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getRawUserInfo()!=null)
            throw new IOException("只允许公开 HTTPS 链接");
        String host=u.getHost().toLowerCase(Locale.ROOT);
        if(host.equals("localhost")||host.endsWith(".local")||host.endsWith(".internal"))
            throw new IOException("不允许访问本地网络地址");
        for(InetAddress a:InetAddress.getAllByName(host)) {
            if((a.getAddress().length==16&&(a.getAddress()[0]&0xFE)==0xFC)||a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())
                throw new IOException("不允许访问内网或本机地址");
        }
    }
    private static HttpURLConnection open(String raw,String referer) throws Exception {
        return open(raw,referer,null,"");
    }
    private static HttpURLConnection open(String raw,String referer,java.io.File cookies)throws Exception {return open(raw,referer,cookies,"");}
    private static HttpURLConnection open(String raw,String referer,java.io.File cookies,String range) throws Exception {return open(raw,referer,cookies,range,"");}
    private static HttpURLConnection open(String raw,String referer,java.io.File cookies,String range,String validator) throws Exception {return open(raw,referer,cookies,range,validator,"");}
    private static HttpURLConnection open(String raw,String referer,java.io.File cookies,String range,String validator,String agent) throws Exception {
        return open(raw,referer,cookies,range,validator,agent,0);
    }
    private static HttpURLConnection open(String raw,String referer,java.io.File cookies,String range,String validator,String agent,long deadline) throws Exception {
        String next=raw;
        for(int i=0;i<5;i++) {
            JobControl.check();ensurePublic(next);
            long remaining=deadline==0?0:java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime());
            if(deadline!=0&&remaining<=0)throw new IOException("视频预检超时");
            HttpURLConnection c=(HttpURLConnection)new URL(next).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(deadline==0?10000:(int)Math.max(1,Math.min(3000,remaining)));
            c.setReadTimeout(deadline==0?12000:(int)Math.max(1,Math.min(3000,remaining)));
            c.setRequestProperty("User-Agent",agent.isEmpty()?WebSessionCookies.userAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Mobile Safari/537.36"):agent);
            if(!referer.isEmpty()&&referer.startsWith("https://"))c.setRequestProperty("Referer",referer);
            c.setRequestProperty("Accept","text/html,video/*,image/*,audio/*,*/*;q=0.5");
            String cookie=CookieFile.header(cookies,next);if(cookie.isEmpty())cookie=WebSessionCookies.header(next);if(!cookie.isEmpty())c.setRequestProperty("Cookie",cookie);
            c.setRequestProperty("Accept-Encoding","identity");
            if(!range.isEmpty()){c.setRequestProperty("Range",range);if(!validator.isEmpty())c.setRequestProperty("If-Range",validator);}
            try {
                int status=c.getResponseCode();
                if(status>=300&&status<400) {
                    String target=c.getHeaderField("Location");
                    if(target==null)throw new IOException("网站跳转缺少目标地址");
                    next=resolve(next,target);
                    c.disconnect();
                    if(next.isEmpty())throw new IOException("网站跳转不是 HTTPS 链接");
                    continue;
                }
                if(status<200||status>=300)throw new IOException("网站无法访问（HTTP "+status+"），可能需要登录或授权");
                return c;
            }catch(Exception e){c.disconnect();throw e;}
        }
        throw new IOException("网站重定向次数过多");
    }
    private static byte[] limitedRead(InputStream in,int limit) throws IOException {
        try(InputStream source=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n;int total=0;
            while((n=source.read(buf))!=-1) {
                JobControl.check();total+=n;if(total>limit)throw new IOException("网页内容过大，暂不支持解析");
                out.write(buf,0,n);
            }
            return out.toByteArray();
        }
    }
    /** Save a publicly retrievable source to system gallery/media library, pending until fully written. */
    public interface Progress {
        void update(long received,long total);
        default void phase(String label){}
        default void nativeSample(long bytes,long total,double speed,double eta,boolean estimated){update(bytes,total);}
        default void complete(){}
    }
    public static Uri download(Context ctx,Item item) throws Exception {return download(ctx,item,null);}
    public static Uri download(Context ctx,Item item,Progress progress) throws Exception {return download(ctx,item,progress,true);}
    static Uri download(Context ctx,Item item,Progress progress,boolean publish) throws Exception {
        try{return downloadOnce(ctx,item,progress,publish);}catch(Exception error){
            JobControl.check();if(item.kind!=Kind.IMAGE||!XhsParser.matches(item.source)||item.fallbackUrl.isEmpty()||item.fallbackUrl.equals(item.url))throw error;
            if(progress!=null)progress.phase("原图地址不可用，回退笔记图片并继续去水印");
            return downloadOnce(ctx,new Item(item.kind,item.fallbackUrl,item.label,item.referer,false,0,item.userAgent,item.source),progress,publish);
        }
    }
    static boolean segmented(String url){try{String path=new URI(url).getPath().toLowerCase(Locale.ROOT);return path.endsWith(".m3u8")||path.endsWith(".mpd");}catch(Exception error){return false;}}
    private static Uri downloadOnce(Context ctx,Item item,Progress progress,boolean publish) throws Exception {
        if(item.engine||item.kind!=Kind.IMAGE&&segmented(item.url))return PlatformEngine.download(ctx,new Item(item.kind,item.url,item.label,item.referer,true,item.engineIndex,item.userAgent),progress,publish);
        if(progress!=null)progress.phase("连接素材服务器");
        java.io.File cookies=new java.io.File(ctx.getFilesDir(),"link-cookies.txt");
        HttpURLConnection initial=open(item.url,item.referer,cookies,"","",item.userAgent);java.io.File temporary=null;Uri saved=null;
        try {
            String initialMime=cleanMime(initial.getContentType());
            if(initialMime.contains("mpegurl")||initialMime.contains("dash+xml")){initial.disconnect();return PlatformEngine.download(ctx,new Item(item.kind,item.url,item.label,item.referer,true,0,item.userAgent),progress,publish);}
            temporary=java.io.File.createTempFile("download-",".partial",ctx.getCacheDir());
            java.util.concurrent.atomic.AtomicBoolean first=new java.util.concurrent.atomic.AtomicBoolean(true);
            RangeDownload.Result transfer=RangeDownload.fetch(temporary,FILE_LIMIT,(offset,validator)->{
                HttpURLConnection connection=first.getAndSet(false)?initial:open(item.url,item.referer,cookies,offset>0?"bytes="+offset+"-":"",offset>0?validator:"",item.userAgent);
                try{String type=cleanMime(connection.getContentType());if(type.isEmpty()||type.equals("application/octet-stream"))type=inferMime(item.url,item.kind);
                    if(!matches(item.kind,type))throw new IOException("链接返回了网页或不同类型的内容，请重新解析或登录后再保存");return connection;
                }catch(Exception e){connection.disconnect();throw e;}
            },progress==null?null:new RangeDownload.Progress(){public void phase(String label){progress.phase(label);}public void bytes(long bytes,long total){progress.update(bytes,total);}});
            JobControl.check();String mime=cleanMime(transfer.mime);if(mime.isEmpty()||mime.equals("application/octet-stream"))mime=inferMime(item.url,item.kind);
            byte[] header=new byte[32];int count;try(InputStream source=new java.io.FileInputStream(temporary)){count=source.read(header);}
            if(!MediaSignature.accepts(mime,header,Math.max(0,count)))throw new IOException("链接没有返回可识别的媒体文件，请重新解析或使用网页辅助");
            String prefix=item.kind==Kind.IMAGE?"image":item.kind==Kind.VIDEO?"video":"audio";
            String dir=item.kind==Kind.IMAGE?"Pictures/水印工坊":item.kind==Kind.VIDEO?"Movies/水印工坊":"Music/水印工坊";
            Uri collection=item.kind==Kind.IMAGE?MediaStore.Images.Media.EXTERNAL_CONTENT_URI:item.kind==Kind.VIDEO?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            saved=MediaOps.create(ctx,mime,prefix+"_"+System.currentTimeMillis()+"."+extension(mime,item.kind),dir,collection);
            if(progress!=null){progress.phase("正在写入待处理素材");progress.update(0,transfer.bytes);}
            try(InputStream source=new java.io.FileInputStream(temporary);OutputStream destination=ctx.getContentResolver().openOutputStream(saved,"w")){
                if(destination==null)throw new IOException("无法写入媒体文件");byte[] buffer=new byte[65536];int n;long written=0,last=0;
                while((n=source.read(buffer))!=-1){JobControl.check();destination.write(buffer,0,n);written+=n;if(progress!=null&&written-last>=256*1024){progress.update(written,transfer.bytes);last=written;}}destination.flush();if(progress!=null)progress.update(written,transfer.bytes);
            }
            JobControl.check();if(publish){MediaOps.finish(ctx,saved);if(progress!=null)progress.complete();}else if(progress!=null)progress.phase("下载完成，等待去水印处理");return saved;
        }catch(Exception ex){if(saved!=null)try{ctx.getContentResolver().delete(saved,null,null);}catch(Exception ignored){}throw ex;}
        finally{initial.disconnect();if(temporary!=null)temporary.delete();}
    }
    private static boolean matches(Kind kind,String mime) {
        return mime.startsWith(kind==Kind.IMAGE?"image/":kind==Kind.VIDEO?"video/":"audio/");
    }
    private static String inferMime(String url,Kind kind) {
        String path="";try{path=new URI(url).getPath().toLowerCase(Locale.ROOT);}catch(Exception ignored){}
        if(path.endsWith(".png"))return "image/png";
        if(path.endsWith(".webp"))return "image/webp";
        if(path.endsWith(".gif"))return "image/gif";
        if(path.endsWith(".mp3"))return "audio/mpeg";
        if(path.endsWith(".m4a"))return "audio/mp4";
        if(path.endsWith(".wav"))return "audio/wav";
        if(path.endsWith(".flac"))return "audio/flac";if(path.endsWith(".ogg"))return "audio/ogg";if(path.endsWith(".aac"))return "audio/aac";
        if(path.endsWith(".heic"))return "image/heic";if(path.endsWith(".avif"))return "image/avif";if(path.endsWith(".mov"))return "video/quicktime";
        if(path.endsWith(".webm"))return kind==Kind.AUDIO?"audio/webm":"video/webm";
        return kind==Kind.IMAGE?"image/jpeg":kind==Kind.VIDEO?"video/mp4":"audio/mp4";
    }
    private static String extension(String mime,Kind kind) {
        if(mime.contains("png"))return "png";if(mime.contains("webp"))return "webp";
        if(mime.contains("gif"))return "gif";if(mime.contains("jpeg"))return "jpg";
        if(mime.contains("webm"))return "webm";if(mime.contains("quicktime"))return "mov";
        if(mime.contains("mpeg")&&kind==Kind.AUDIO)return "mp3";
        if(mime.contains("wav"))return "wav";if(mime.contains("ogg"))return "ogg";
        if(mime.contains("aac"))return "aac";
        if(mime.contains("flac"))return "flac";if(mime.contains("heic")||mime.contains("heif"))return "heic";
        if(mime.contains("avif"))return "avif";if(mime.contains("bmp"))return "bmp";if(mime.contains("matroska"))return "mkv";if(mime.contains("msvideo"))return "avi";
        if(kind==Kind.AUDIO)return "m4a";
        return kind==Kind.IMAGE?"jpg":"mp4";
    }
    public static Bitmap loadPreview(String url) {
        try {
            HttpURLConnection c=open(url,"");
            try {
                String mime=cleanMime(c.getContentType());
                if(!mime.startsWith("image/")||c.getContentLengthLong()>2_000_000)return null;
                byte[] bytes=limitedRead(c.getInputStream(),2_000_000);
                BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
                if(opts.outWidth<=0||opts.outHeight<=0||opts.outWidth>30000||opts.outHeight>30000)return null;
                opts.inSampleSize=Math.max(1,(int)Math.ceil(Math.max(opts.outWidth,opts.outHeight)/800d));
                opts.inJustDecodeBounds=false;
                return BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
            }finally{c.disconnect();}
        }catch(OutOfMemoryError e){return null;}catch(Exception ignored){return null;}
    }
}
