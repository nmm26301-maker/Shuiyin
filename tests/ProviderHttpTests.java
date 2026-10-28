package com.zizai.watermark;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public final class ProviderHttpTests {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    interface Work {void run()throws Exception;}
    static void rejects(Work work,String expected)throws Exception{try{work.run();throw new AssertionError("expected failure");}catch(IOException error){check(error.getMessage().contains(expected),"explicit "+expected);}}
    static final class Fixture implements AutoCloseable {
        final ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());final Thread thread;volatile String headers="",body="";volatile Throwable failure;final java.util.concurrent.CountDownLatch responding=new java.util.concurrent.CountDownLatch(1);
        Fixture(int status,String json,String extra)throws Exception{
            this(status,json,extra,0,0);
        }
        Fixture(int status,String json,String extra,int byteDelay,int bodyDelay)throws Exception{
            thread=new Thread(()->{try(Socket client=server.accept()){
                client.setSoTimeout(3000);BufferedInputStream in=new BufferedInputStream(client.getInputStream());ByteArrayOutputStream head=new ByteArrayOutputStream();int b;
                while((b=in.read())!=-1){head.write(b);if(head.size()>65536)throw new IOException("large headers");byte[] a=head.toByteArray();int n=a.length;if(n>=4&&a[n-4]==13&&a[n-3]==10&&a[n-2]==13&&a[n-1]==10)break;}
                headers=head.toString("UTF-8");int length=0;for(String line:headers.split("\r\n"))if(line.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());
                body=new String(in.readNBytes(length),StandardCharsets.UTF_8);byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
                client.getOutputStream().write(("HTTP/1.1 "+status+" Test\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n"+extra+"\r\n").getBytes(StandardCharsets.US_ASCII));client.getOutputStream().flush();responding.countDown();
                if(bodyDelay>0)Thread.sleep(bodyDelay);
                if(byteDelay>0){for(byte value:bytes){client.getOutputStream().write(value);client.getOutputStream().flush();Thread.sleep(byteDelay);}}
                else client.getOutputStream().write(bytes);client.getOutputStream().flush();
            }catch(Throwable error){if(!((byteDelay>0||bodyDelay>0)&&error instanceof IOException))failure=error;}},"provider-wire-fixture");thread.setDaemon(true);thread.start();
        }
        HttpURLConnection open(String ignored)throws Exception{return (HttpURLConnection)new URL("http://127.0.0.1:"+server.getLocalPort()+"/").openConnection();}
        public void close()throws Exception{server.close();thread.join(2000);if(failure!=null)throw new AssertionError(failure);}
    }
    public static void main(String[] args)throws Exception {
        String share="https://www.tiktok.com/@test/video/7312345678901234567?sig=a%2Fb";
        String success="{\"success\":true,\"desc\":\"caption\",\"links\":{\"Video (No Watermark)\":\"https://cdn.example.org/play?sig=a%2Fb\",\"Video (Watermark)\":\"https://cdn.example.org/marked.mp4\",\"MP3\":\"https://cdn.example.org/audio\"}}";
        ProviderHttp.Request request=ProviderHttp.tiktokioRequest(share);
        try(Fixture wire=new Fixture(200,success,"")){
            LinkExtractor.Result result=ProviderHttp.execute(request,share,false,wire::open);
            check(wire.headers.startsWith("POST / HTTP/1.1"),"actual HTTP POST");check(wire.headers.contains("multipart/form-data; boundary="),"actual multipart content type");
            check(wire.headers.contains("Referer: https://tiktokio.bio/"),"same-site form referer");check(wire.body.contains("name=\"page\"\r\n\r\n"+share),"exact signed source form field");check(wire.body.contains("name=\"ajax\"\r\n\r\n1"),"actual ajax form field");
            check(result.count(LinkExtractor.Kind.VIDEO)==1&&result.count(LinkExtractor.Kind.AUDIO)==1,"complete video and audio, no marked variant");check(result.items.get(0).source.equals(share)&&result.items.get(0).referer.equals("https://tiktokio.bio/"),"origin separate from HTTP referer");check(result.items.stream().anyMatch(i->i.url.endsWith("a%2Fb")),"output signature retained");
        }
        ProviderHttp.Request cobalt=ProviderHttp.cobaltRequest(share,"https://instance.example.org/","fixture-key");
        check(cobalt.authorization.equals("Api-Key fixture-key"),"official API key auth scheme");check(new JSONObject(new String(cobalt.body,StandardCharsets.UTF_8)).getString("localProcessing").equals("disabled"),"request complete media");
        try(Fixture wire=new Fixture(200,"{\"status\":\"tunnel\",\"url\":\"https://instance.example.org/tunnel?id=1\",\"filename\":\"track.mp3\"}","")){
            LinkExtractor.Result result=ProviderHttp.execute(cobalt,share,true,wire::open);check(wire.headers.contains("Authorization: Api-Key fixture-key"),"wire auth header");check(result.count(LinkExtractor.Kind.AUDIO)==1,"Cobalt audio filename classification");
        }
        try(Fixture wire=new Fixture(302,"","Location: https://other.example.org/\r\n")){rejects(()->ProviderHttp.execute(cobalt,share,true,wire::open),"未转发授权信息");}
        try(Fixture wire=new Fixture(403,"fixture-key", "")){rejects(()->ProviderHttp.execute(cobalt,share,true,wire::open),"需要有效授权");}
        rejects(()->ProviderHttp.cobaltRequest(share,"http://instance.example.org/","key"),"HTTPS");rejects(()->ProviderHttp.cobaltRequest(share,"https://instance.example.org/","x\r\nInjected: y"),"格式无效");
        rejects(()->ProviderHttp.tiktokioResult("{\"error\":\"Wrong link format\"}",share),"Wrong link format");
        rejects(()->ProviderHttp.cobaltResult("{\"status\":\"local-processing\"}",share,cobalt.url),"分离轨道");
        rejects(()->ProviderHttp.cobaltResult("{\"status\":\"error\",\"error\":{\"code\":\"api.auth.jwt.missing\"}}",share,cobalt.url),"实例授权");
        LinkExtractor.Result picker=ProviderHttp.cobaltResult("{\"status\":\"picker\",\"picker\":[{\"type\":\"photo\",\"url\":\"https://cdn.example.org/a.jpg\"},{\"type\":\"video\",\"url\":\"http://cdn.example.org/b.mp4\"}]}",share,cobalt.url);check(picker.items.size()==1&&picker.count(LinkExtractor.Kind.IMAGE)==1,"picker HTTPS media only");
        rejects(()->ProviderHttp.tiktokioResult("{\"success\":true,\"links\":{\"Video\":\"https://user:pass@cdn.example.org/a.mp4\"}}",share),"未返回");
        try{ProviderHttp.tiktokioResult("{\"error\":\"Wrong link format\"}",share);throw new AssertionError("missing service error");}
        catch(IOException error){check(ExtractionErrors.message(error).contains("Wrong link format"),"actual service rejection survives friendly error formatting");}
        try(Fixture wire=new Fixture(200,success,"",20,0)){
            long started=System.nanoTime();rejects(()->ProviderHttp.execute(request,share,false,wire::open,100),"超时");
            check(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)<1500,"slow trickle response leaves time for webpage fallback");
        }
        try(Fixture wire=new Fixture(200,success,"",0,150)){
            try{ProviderHttp.execute(request,share,false,wire::open,100);throw new AssertionError("delayed response accepted");}
            catch(SocketTimeoutException expected){check(true,"delayed response observes the request budget");}
        }
        java.util.concurrent.atomic.AtomicBoolean called=new java.util.concurrent.atomic.AtomicBoolean();
        rejects(()->ProviderHttp.execute(request,share,false,url->{called.set(true);return null;},0),"超时");check(!called.get(),"spent budget starts no request");
        java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean(true);JobControl.attach(cancelled);
        try{ProviderHttp.execute(request,share,false,url->{called.set(true);return null;},1000);throw new AssertionError("cancel ignored");}
        catch(InterruptedIOException expected){check(!called.get(),"pre-cancel starts no request");}finally{JobControl.detach();}
        cancelled.set(false);JobControl.attach(cancelled);
        try(Fixture wire=new Fixture(200,success,"",20,0)){
            Thread trigger=new Thread(()->{try{wire.responding.await();Thread.sleep(50);cancelled.set(true);}catch(InterruptedException ignored){}});trigger.setDaemon(true);trigger.start();
            try{ProviderHttp.execute(request,share,false,wire::open,1000);throw new AssertionError("in-flight cancel ignored");}
            catch(InterruptedIOException expected){check(true,"in-flight cancellation preserved instead of converted to ordinary service failure");}finally{trigger.join(1000);}
        }finally{JobControl.detach();}
        String album="{\"status\":\"picker\",\"audio\":\"https://cdn.example.org/music?sig=a%2Fb\",\"audioFilename\":\"background.mp3\",\"picker\":[{\"type\":\"photo\",\"url\":\"https://cdn.example.org/p.jpg\"}]}";
        LinkExtractor.Result collection=ProviderHttp.cobaltResult(album,share,cobalt.url);
        check(collection.count(LinkExtractor.Kind.IMAGE)==1&&collection.count(LinkExtractor.Kind.AUDIO)==1,"documented picker background audio retained with photos");
        LinkExtractor.Item sound=collection.items.stream().filter(i->i.kind==LinkExtractor.Kind.AUDIO).findFirst().orElseThrow();
        check(sound.url.endsWith("a%2Fb")&&sound.label.equals("background.mp3"),"opaque audio URL, signed query and audio filename retained");
        check(sound.source.equals(share)&&sound.referer.equals(cobalt.url),"album audio uses existing downloader headers and work origin");
        collection=ProviderHttp.cobaltResult(album.replace("https://cdn.example.org/music?sig=a%2Fb","http://cdn.example.org/music"),share,cobalt.url);
        check(collection.count(LinkExtractor.Kind.AUDIO)==0&&collection.count(LinkExtractor.Kind.IMAGE)==1,"unsafe optional audio does not discard valid photos");
        org.json.JSONObject large=new org.json.JSONObject(album);org.json.JSONArray photos=new org.json.JSONArray();
        for(int i=0;i<24;i++)photos.put(new org.json.JSONObject().put("type","photo").put("url","https://cdn.example.org/"+i+".jpg"));large.put("picker",photos);
        collection=ProviderHttp.cobaltResult(large.toString(),share,cobalt.url);
        check(collection.items.size()==24&&collection.count(LinkExtractor.Kind.AUDIO)==1,"audio retained within shared 24-item limit");
        ProviderHttp.Request idea=ProviderHttp.ideaFlowRequest(share);
        check(java.net.URLDecoder.decode(idea.url.substring(idea.url.indexOf("?url=")+5),"UTF-8").equals(share),"IdeaFlow encodes complete signed share exactly once");
        String ideaJson=new JSONObject().put("code",200).put("data",new JSONObject().put("title","caption").put("video_url","https://cdn.example.org/movie?sig=a%2Fb")
            .put("cover_url","https://cdn.example.org/cover.jpg").put("images",new JSONArray().put(new JSONObject().put("url","https://cdn.example.org/photo.jpg").put("live_photo_url","https://cdn.example.org/live.mp4")))).toString();
        try(Fixture wire=new Fixture(200,ideaJson,"")){
            LinkExtractor.Result r=ProviderHttp.execute(idea,share,ProviderHttp::ideaFlowResult,wire::open,12000);
            check(wire.headers.startsWith("GET / HTTP/1.1")&&wire.body.isEmpty(),"documented IdeaFlow GET has no POST body");
            check(r.count(LinkExtractor.Kind.VIDEO)==2&&r.count(LinkExtractor.Kind.IMAGE)==1,"public response video, photo and live photo video");
            check(r.items.stream().noneMatch(i->i.url.contains("cover.jpg")),"cover not mistaken for photo material");
            check(r.items.get(0).referer.equals("https://parse.ideaflow.top/")&&r.items.get(0).source.equals(share),"new provider handed to existing downloader with original work and site referer");
            check(r.description.equals("caption")&&r.items.get(0).url.endsWith("a%2Fb"),"caption and exact signed output retained");
        }
        rejects(()->ProviderHttp.ideaFlowResult("{\"code\":500,\"msg\":\"站点维护\"}",share,"https://parse.ideaflow.top/"),"站点维护");
        rejects(()->ProviderHttp.ideaFlowResult("{\"code\":200,\"data\":{\"video_url\":\"http://cdn.example.org/a.mp4\"}}",share,"https://parse.ideaflow.top/"),"未返回");
        System.out.println("PASS native provider HTTP: "+checks+" checks (real local wire, auth, redirects, public form, response adapters)");
    }
}
