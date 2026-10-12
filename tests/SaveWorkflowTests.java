package com.zizai.watermark;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SaveWorkflowTests {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static void fails(RunnableError work,String text)throws Exception {try{work.run();throw new AssertionError("expected failure: "+text);}catch(IOException e){check(e.getMessage().contains(text),text);}}
    interface RunnableError {void run()throws Exception;}
    static final class Fixture implements AutoCloseable {
        final ServerSocket socket=new ServerSocket(0,10,InetAddress.getLoopbackAddress());final List<String> requests=Collections.synchronizedList(new ArrayList<>());final Thread thread;volatile Throwable failure;
        final byte[] original=new byte[327789];
        Fixture(String mode)throws Exception {
            for(int i=0;i<original.length;i++)original[i]=(byte)(i*31);
            thread=new Thread(()->{try{for(int attempt=0;attempt<(mode.equals("exhaust")?3:mode.equals("unknown")||mode.equals("limit")||mode.equals("compressed")?1:2);attempt++){
                try(Socket connection=socket.accept()){
                    connection.setSoTimeout(5000);BufferedReader reader=new BufferedReader(new InputStreamReader(connection.getInputStream(),StandardCharsets.US_ASCII));String line;StringBuilder request=new StringBuilder();while((line=reader.readLine())!=null&&!line.isEmpty())request.append(line).append('\n');requests.add(request.toString());
                    int start=0;java.util.regex.Matcher range=java.util.regex.Pattern.compile("(?i)Range: bytes=(\\d+)-").matcher(request);if(range.find()&&!mode.equals("ignored")&&!mode.equals("weak"))start=Integer.parseInt(range.group(1));
                    boolean partial=attempt==0&&!mode.equals("unknown")&&!mode.equals("limit")&&!mode.equals("compressed")||mode.equals("exhaust");
                    String etag=mode.equals("weak")?"W/\"v1\"":mode.equals("changed")&&attempt>0?"\"v2\"":"\"v1\"";
                    String header="HTTP/1.1 "+(start>0?"206 Partial Content":"200 OK")+"\r\nContent-Type: video/mp4\r\nConnection: close\r\nETag: "+etag+"\r\n";
                    if(!mode.equals("unknown"))header+="Content-Length: "+(original.length-start)+"\r\n";
                    if(start>0)header+="Content-Range: bytes "+(mode.equals("bad-range")?start+1:start)+"-"+(original.length-1)+"/"+original.length+"\r\n";
                    if(mode.equals("compressed"))header+="Content-Encoding: gzip\r\n";
                    OutputStream out=connection.getOutputStream();out.write((header+"\r\n").getBytes(StandardCharsets.US_ASCII));out.write(original,start,partial?Math.min(100000,original.length-start):original.length-start);out.flush();
                }
            }}catch(Throwable e){if(!socket.isClosed())failure=e;}},"HTTP-resume-fixture");thread.setDaemon(true);thread.start();
        }
        HttpURLConnection open(long offset,String validator)throws Exception {
            HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+socket.getLocalPort()+"/video").openConnection();c.setConnectTimeout(3000);c.setReadTimeout(3000);c.setRequestProperty("Accept-Encoding","identity");if(offset>0){c.setRequestProperty("Range","bytes="+offset+"-");c.setRequestProperty("If-Range",validator);}return c;
        }
        public void close()throws Exception {socket.close();thread.join(1000);if(failure!=null)throw new AssertionError(failure);}
    }
    static final class Work implements SaveTransaction.Work<Object> {
        final Object pending=new Object(),repaired=new Object();final List<String> events=new ArrayList<>();boolean skip,failRepair,failPublish,cancel;
        public Object download(){events.add("download pending");if(cancel)JobControl.flag().set(true);return pending;}
        public Object repair(Object p)throws Exception{events.add("repair");if(failRepair)throw new IOException("repair failed");return skip?p:repaired;}
        public void publish(Object p)throws Exception{events.add("publish");if(failPublish)throw new IOException("publish failed");}
        public void discard(Object p){events.add("discard pending");}
        public void complete(){events.add("complete");}
    }
    public static void main(String[] args)throws Exception {
        for(String mode:new String[]{"resume","ignored","weak","unknown"})try(Fixture server=new Fixture(mode)){
            Path p=Files.createTempFile("range-",".partial");try{
                List<Long> bytes=new ArrayList<>();RangeDownload.Result result=RangeDownload.fetch(p.toFile(),1000000,server::open,new RangeDownload.Progress(){public void phase(String s){}public void bytes(long b,long t){bytes.add(b);}});
                check(result.bytes==server.original.length&&Arrays.equals(Files.readAllBytes(p),server.original),mode+": output exactly matches source");
                if(!mode.equals("unknown")){check(server.requests.size()==2,mode+": one recovery request");String request=server.requests.get(1).toLowerCase(Locale.ROOT);check(mode.equals("weak")?!request.contains("range:"):request.contains("range: bytes=100000-")&&request.contains("if-range: \"v1\""),mode+": safe range and validator headers");}
                check(bytes.get(bytes.size()-1)==server.original.length,mode+": final progress exact byte count");
            }finally{Files.deleteIfExists(p);}
        }
        for(String mode:new String[]{"changed","bad-range","limit","compressed","exhaust"})try(Fixture server=new Fixture(mode)){
            Path p=Files.createTempFile("range-",".partial");try{
                fails(()->RangeDownload.fetch(p.toFile(),mode.equals("limit")?10:1000000,server::open,null),mode.equals("changed")?"素材已改变":mode.equals("bad-range")?"范围无效":mode.equals("limit")?"上限":mode.equals("compressed")?"压缩传输":"素材下载中断");
                if(mode.equals("exhaust"))check(server.requests.size()==3,"bounded recovery attempts");
                if(mode.equals("changed")||mode.equals("bad-range"))check(Files.size(p)==100000,"unsafe response never appended");
            }finally{Files.deleteIfExists(p);}
        }
        Work w=new Work();check(SaveTransaction.run(w)==w.repaired,"returns repaired output");check(w.events.equals(Arrays.asList("download pending","repair","complete","discard pending")),"repair completes before clearing pending source; source never published");
        w=new Work();w.skip=true;check(SaveTransaction.run(w)==w.pending,"clean original returned");check(w.events.equals(Arrays.asList("download pending","repair","publish","complete")),"clean original published before completion");
        w=new Work();w.failRepair=true;Work failed=w;fails(()->SaveTransaction.run(failed),"repair failed");check(!w.events.contains("complete")&&w.events.contains("discard pending"),"failed repair discards raw pending without success");
        w=new Work();w.skip=true;w.failPublish=true;Work registration=w;fails(()->SaveTransaction.run(registration),"publish failed");check(w.events.contains("discard pending")&&!w.events.contains("complete"),"failed media commit never marked complete");
        AtomicBoolean cancelled=new AtomicBoolean();JobControl.attach(cancelled);try{w=new Work();w.cancel=true;Work stopped=w;fails(()->SaveTransaction.run(stopped),"取消");check(!w.events.contains("repair")&&w.events.contains("discard pending"),"cancelled pending cleared");}finally{JobControl.detach();}
        float[] box={.1f,.1f,.2f,.2f};check(Arrays.equals(box,RepairTemplate.decode(RepairTemplate.encode(box))),"remembered normalized region round trip");check(RepairTemplate.decode("NaN,0,1,1")==null,"nonfinite remembered region rejected");check(RepairTemplate.decode("0,0,1,1")==null,"whole image repair rejected");
        LinkExtractor.Item video=new LinkExtractor.Item(LinkExtractor.Kind.VIDEO,"https://cdn.example.org/x.mp4","","https://www.tiktok.com/@a/video/1");String key=RepairTemplate.key(video,720,1280);check(key.equals(RepairTemplate.key(video,1080,1920)),"same aspect shares chosen region");check(!key.equals(RepairTemplate.key(video,1280,720)),"portrait and landscape never share repair template");check(!key.equals(RepairTemplate.key(new LinkExtractor.Item(LinkExtractor.Kind.IMAGE,video.url,"",video.referer),720,1280)),"photo and video templates isolated");
        if(args.length>1){
            Path folder=Paths.get(args[1]);Files.createDirectories(folder);Path source=folder.resolve("marked.mp4"),output=folder.resolve("repaired.mp4");
            NativeProcess.run(Arrays.asList("ffmpeg","-hide_banner","-y","-loglevel","error","-f","lavfi","-i","color=blue:size=320x180:rate=30:duration=2","-f","lavfi","-i","sine=frequency=440:sample_rate=48000:duration=2","-vf","drawbox=x=50:y=30:w=60:h=20:color=white:t=fill","-c:v","libx264","-threads","2","-c:a","aac",source.toString()),Collections.emptyMap(),new AtomicBoolean(),15000,null);
            float[] r=RepairTemplate.validate(new float[]{48/320f,28/180f,112/320f,52/180f});List<String> command=new ArrayList<>();command.add("ffmpeg");command.addAll(VideoFilters.exportArguments(source.toString(),output.toString(),1,VideoFilters.region(r,320,180),true));List<Integer> progress=new ArrayList<>();
            NativeProcess.run(command,Collections.emptyMap(),new AtomicBoolean(),15000,line->{if(line.startsWith("out_time_us="))progress.add((int)Math.min(98,Long.parseLong(line.substring(12))*98/2000000));});check(Files.size(output)>1000,"real watermark repair produces media");check(!progress.isEmpty()&&progress.get(progress.size()-1)>90,"actual FFmpeg processing progress available");
        }
        Files.writeString(Paths.get(args[0]),WebMediaScanner.script());System.out.println("PASS save workflow: "+checks+" checks; real HTTP truncation/resume and pending repair lifecycle");
    }
}
