package com.zizai.watermark;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real HTTP replies and a generated MP4, not a URL-extension-only success test. */
public final class VideoCandidateTests {
    static int checks;static byte[] mp4;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static final class Tracked extends HttpURLConnection {
        final HttpURLConnection actual;boolean closed;int bytes;
        Tracked(HttpURLConnection actual){super(actual.getURL());this.actual=actual;}
        public void connect()throws IOException{actual.connect();}
        public void disconnect(){closed=true;actual.disconnect();}
        public boolean usingProxy(){return actual.usingProxy();}
        public int getResponseCode()throws IOException{return actual.getResponseCode();}
        public String getHeaderField(String name){return actual.getHeaderField(name);}
        public InputStream getInputStream()throws IOException{return new FilterInputStream(actual.getInputStream()){
            public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);if(n>0)bytes+=n;return n;}
        };}
    }
    static final class Wire implements AutoCloseable {
        final HttpServer server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        final ExecutorService workers=Executors.newCachedThreadPool(r->{Thread t=new Thread(r);t.setDaemon(true);return t;});
        final List<Tracked> opened=new ArrayList<>();final List<String> queries=new CopyOnWriteArrayList<>();
        Wire()throws IOException{
            server.createContext("/",exchange->{try{
                String path=exchange.getRequestURI().getPath();queries.add(String.valueOf(exchange.getRequestURI().getRawQuery()));
                byte[] body=Arrays.copyOf(mp4,64);int status=206;String range="bytes 0-63/"+mp4.length;
                if(path.equals("/html")){body="<html>verification required</html>".getBytes();status=200;}
                if(path.equals("/json")){body="{\"error\":\"invalid video\"}".getBytes();status=200;}
                if(path.equals("/403")){status=403;body="denied".getBytes();}
                if(path.equals("/404")){status=404;body="missing".getBytes();}
                if(path.equals("/huge"))range="bytes 0-63/"+(MediaLimits.DOWNLOAD+1);
                if(path.equals("/offset"))range="bytes 4-67/"+mp4.length;
                if(path.equals("/badlength"))range="bytes 0-62/"+mp4.length;
                if(path.equals("/unknown"))range="bytes 0-63/*";
                if(path.equals("/compressed"))exchange.getResponseHeaders().set("Content-Encoding","gzip");
                if(path.equals("/short")){body=Arrays.copyOf(mp4,8);status=200;}
                if(path.equals("/full")){body=mp4;status=200;}
                if(path.equals("/manifest")){body="#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:1,\npart.ts\n#EXT-X-ENDLIST\n".getBytes();status=200;}
                exchange.getResponseHeaders().set("Content-Type",path.equals("/manifest")?"application/vnd.apple.mpegurl":"video/mp4");
                if(status==206)exchange.getResponseHeaders().set("Content-Range",range);
                exchange.sendResponseHeaders(status,body.length);
                try(OutputStream out=exchange.getResponseBody()){
                    if(path.equals("/delayed"))try{Thread.sleep(150);}catch(InterruptedException ignored){}
                    if(path.equals("/slow")){for(byte b:body){out.write(b);out.flush();try{Thread.sleep(25);}catch(InterruptedException e){break;}}}
                    else out.write(body);
                }
            }catch(IOException disconnected){/* Expected when a bounded prefix probe closes. */}finally{exchange.close();}});
            server.setExecutor(workers);server.start();
        }
        HttpURLConnection open(LinkExtractor.Item item,long deadline)throws Exception{
            URI original=new URI(item.url);String query=original.getRawQuery();
            HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+server.getAddress().getPort()+original.getRawPath()+(query==null?"":"?"+query)).openConnection();
            c.setConnectTimeout(500);c.setReadTimeout(500);c.setRequestProperty("Range","bytes=0-63");c.setInstanceFollowRedirects(false);
            Tracked tracked=new Tracked(c);opened.add(tracked);return tracked;
        }
        public void close(){server.stop(0);workers.shutdownNow();}
    }
    static LinkExtractor.Result result(String...paths){
        LinkExtractor.Result result=new LinkExtractor.Result();result.source="https://www.douyin.com/video/7312345678901234567";result.title="selected work";result.description="caption";
        for(String path:paths)result.addItem(new LinkExtractor.Item(LinkExtractor.Kind.VIDEO,"https://cdn.example.org/"+path,"视频",result.source,false,0,"Session-UA",result.source));return result;
    }
    static void rejects(Wire wire,String path)throws Exception{
        try{VideoCandidates.select(result(path),wire::open,1500);throw new AssertionError("bad candidate accepted: "+path);}
        catch(IOException expected){check(expected.getMessage().contains("预检未通过"),path+" rejected");}
        check(wire.opened.get(wire.opened.size()-1).closed,path+" HTTP connection closed");
    }
    public static void main(String[] args)throws Exception{
        mp4=Files.readAllBytes(Path.of(args[0]));check(mp4.length>64&&MediaSignature.accepts("video/mp4",mp4,64),"real generated MP4 fixture");
        try(Wire wire=new Wire()){
            LinkExtractor.Result candidates=result("403","html","json","ok?sig=a%2Fb");
            LinkExtractor.Result found=VideoCandidates.select(candidates,wire::open,3000);
            check(wire.opened.size()==4,"fourth valid candidate survives three rejected addresses");
            check(found.count(LinkExtractor.Kind.VIDEO)==1&&found.items.get(0).url.endsWith("a%2Fb"),"one usable candidate, original signature unchanged");
            check(wire.queries.contains("sig=a%2Fb"),"signed query arrives intact on real wire");
            check(found.items.get(0)==candidates.items.get(3),"referer, UA and source are the original download item");
            check(found.title.equals(candidates.title)&&found.description.equals(candidates.description),"work metadata retained");
            check(wire.opened.stream().allMatch(c->c.closed),"every probe connection closed");
            check(wire.opened.get(3).bytes==64,"application reads only media prefix");
            for(String path:new String[]{"404","huge","offset","badlength","compressed","short"})rejects(wire,path);
            found=VideoCandidates.select(result("full"),wire::open,1000);check(found.count(LinkExtractor.Kind.VIDEO)==1,"valid server ignoring Range still usable");
            found=VideoCandidates.select(result("unknown"),wire::open,1000);check(found.count(LinkExtractor.Kind.VIDEO)==1,"valid partial response with unknown total retained; final downloader enforces byte cap");
            found=VideoCandidates.select(result("manifest"),wire::open,1000);check(found.count(LinkExtractor.Kind.VIDEO)==1&&wire.opened.get(wire.opened.size()-1).closed,"opaque URL with declared segmented MIME retains original downloader engine handoff");
            int exhausted=wire.opened.size();try{VideoCandidates.select(result("ok"),wire::open,0);throw new AssertionError("spent budget ignored");}
            catch(IOException expected){check(wire.opened.size()==exhausted,"no extra network work after browser search budget is spent");}
            try{VideoCandidates.select(result("delayed"),wire::open,80);throw new AssertionError("late complete prefix accepted");}
            catch(IOException expected){check(expected.getMessage().contains("超时"),"a full valid prefix arriving after the deadline cannot win");}
            int before=wire.opened.size();AtomicBoolean flag=new AtomicBoolean(true);JobControl.attach(flag);
            try{VideoCandidates.select(result("ok"),wire::open,1000);throw new AssertionError("cancel ignored");}catch(InterruptedIOException expected){check(wire.opened.size()==before,"cancel starts no network request");}finally{JobControl.detach();}
            long start=System.nanoTime();try{VideoCandidates.select(result("slow"),wire::open,100);throw new AssertionError("deadline ignored");}
            catch(IOException expected){check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1500,"slow response cannot consume the whole search budget");}
            check(wire.opened.get(wire.opened.size()-1).closed,"timed out response closed");
            flag.set(false);JobControl.attach(flag);Thread cancel=new Thread(()->{try{Thread.sleep(75);}catch(InterruptedException ignored){}flag.set(true);});cancel.start();
            try{VideoCandidates.select(result("slow"),wire::open,1000);throw new AssertionError("in-flight cancel ignored");}
            catch(InterruptedIOException expected){check(wire.opened.get(wire.opened.size()-1).closed,"in-flight cancellation closes active probe");}finally{JobControl.detach();cancel.join(1000);}
            CountDownLatch rejected=new CountDownLatch(1);
            LinkExtractor.Result winner=MediaSearchRace.run(Arrays.asList(
                new ParallelSearch.Source("bad browser",()->{try{return VideoCandidates.select(result("html"),wire::open,1000);}finally{rejected.countDown();}}),
                new ParallelSearch.Source("valid fallback",()->{if(!rejected.await(2,TimeUnit.SECONDS))throw new IOException("missing probe");return result("ok");})),2000);
            check(winner.items.get(0).url.endsWith("/ok"),"HTML response cannot win the original parse race and cancel a working fallback");
            LinkExtractor.Result images=new LinkExtractor.Result();images.add(LinkExtractor.Kind.IMAGE,"https://cdn.example.org/photo.jpg","照片");before=wire.opened.size();
            check(VideoCandidates.select(images,wire::open,1000)==images&&wire.opened.size()==before,"photo work never becomes a failed video probe");
            LinkExtractor.Result nativeResult=new LinkExtractor.Result();nativeResult.addEngine(LinkExtractor.Kind.VIDEO,candidates.source,"native",0);
            check(VideoCandidates.select(nativeResult,wire::open,1000).items.get(0).engine&&wire.opened.size()==before,"native selector keeps existing engine download path");
            check(VideoCandidates.select(result("master.m3u8"),wire::open,1000).count(LinkExtractor.Kind.VIDEO)==1&&wire.opened.size()==before,"known segmented stream keeps existing engine path");
            candidates=result("ok");candidates.add(LinkExtractor.Kind.IMAGE,"https://cdn.example.org/cover.jpg","封面");
            found=VideoCandidates.select(candidates,wire::open,1000);check(found.count(LinkExtractor.Kind.IMAGE)==1,"cover preview retained after successful probe");
        }
        System.out.println("PASS video candidates: "+checks+" checks; real HTTP and MP4 prefix");
    }
}
