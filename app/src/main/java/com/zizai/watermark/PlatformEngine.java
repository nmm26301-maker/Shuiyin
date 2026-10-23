package com.zizai.watermark;

import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import com.yausername.ffmpeg.FFmpeg;
import java.io.*;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;


/** Embedded upstream yt-dlp + Python + FFmpeg. No hosted/demo parsing service. */
final class PlatformEngine {
    interface Status {void update(String message);}
    private static final long LIMIT=MediaLimits.DOWNLOAD;
    private static final String COOKIES="link-cookies.txt";
    static void init(Context context,Status status)throws Exception {
        JobControl.check();if(status!=null)status.update("正在准备解析引擎，首次使用需解压组件…");
        String[] binaries={"libpython.so","libpython.zip.so","libqjs.so","libffmpeg.so","libffmpeg.zip.so"};
        for(String binary:binaries)if(!new File(context.getApplicationInfo().nativeLibraryDir,binary).isFile())throw new IOException("解析组件缺失；请安装适合本机的完整安装包");
        YoutubeDL.getInstance().init(context.getApplicationContext());
        BundledEngine.ensure(context);
        FFmpeg.getInstance().init(context.getApplicationContext());JobControl.check();
    }
    private static YoutubeDLRequest request(Context context,String url,int index)throws Exception {
        LinkExtractor.ensurePublic(url);
        YoutubeDLRequest request=new YoutubeDLRequest(url);
        request.addOption("--ignore-config");request.addOption("--no-playlist");
        request.addOption("--socket-timeout",15);request.addOption("--retries",2);request.addOption("--fragment-retries",2);
        request.addOption("--playlist-end",24);request.addOption("--no-warnings");
        if(index>0)request.addOption("--playlist-items",index);
        File cookies=new File(context.getFilesDir(),COOKIES);
        if(cookies.isFile())request.addOption("--cookies",cookies.getAbsolutePath());
        else if(!WebSessionCookies.header(url).isEmpty()){String agent=WebSessionCookies.userAgent("");if(!agent.isEmpty())request.addOption("--user-agent",agent);}
        return request;
    }
    static LinkExtractor.Result parse(Context context,String share,Status status)throws Exception {
        init(context,status);if(status!=null)status.update("正在识别平台素材…");
        YoutubeDLRequest request=request(context,share,0);
        request.addOption("--dump-single-json");request.addOption("--skip-download");
        String json=execute(context,request,150_000,null,null,true,share);
        JobControl.check();return EngineMetadata.parse(json,share);
    }
    private static String execute(Context context,YoutubeDLRequest request,long timeout,File cache,LinkExtractor.Progress progress,boolean metadata,String sessionUrl)throws Exception {
        File base=new File(context.getNoBackupFilesDir(),"youtubedl-android"),bin=new File(context.getApplicationInfo().nativeLibraryDir);
        File python=new File(bin,"libpython.so"),script=new File(base,"yt-dlp/yt-dlp");
        request.addOption("--no-cache-dir");request.addOption("--ffmpeg-location",new File(bin,"libffmpeg.so").getAbsolutePath());request.addOption("--js-runtimes","quickjs:"+new File(bin,"libqjs.so").getAbsolutePath());
        Map<String,String> environment=new HashMap<>();environment.put("LD_LIBRARY_PATH",new File(base,"packages/python/usr/lib").getAbsolutePath()+":"+new File(base,"packages/ffmpeg/usr/lib").getAbsolutePath());
        environment.put("PYTHONHOME",new File(base,"packages/python/usr").getAbsolutePath());environment.put("SSL_CERT_FILE",new File(base,"packages/python/usr/etc/tls/cert.pem").getAbsolutePath());environment.put("TMPDIR",context.getCacheDir().getAbsolutePath());
        AtomicInteger group=new AtomicInteger();AtomicBoolean first=new AtomicBoolean(true);StringBuilder json=new StringBuilder();
        boolean listRules=request.buildCommand().contains("--list-extractors");
        File sessionJar=null;
        if(progress!=null){request.addOption("--progress");request.addOption("--progress-delta",0.3);request.addOption("--progress-template",EngineProgress.TEMPLATE);request.addOption("--progress-template",EngineProgress.POST_TEMPLATE);progress.phase("正在连接平台并准备素材");}
        try{
            if(!request.hasOption("--cookies")){
                String cookie=SessionCookieJar.netscape(sessionUrl,WebSessionCookies.header(sessionUrl));
                if(!cookie.isEmpty()){sessionJar=File.createTempFile("session-",".txt",context.getCacheDir());try(OutputStream out=new FileOutputStream(sessionJar)){out.write(cookie.getBytes(java.nio.charset.StandardCharsets.UTF_8));}request.addOption("--cookies",sessionJar.getAbsolutePath());}
            }
            NativeProcess.run(EngineCommand.build(python.getAbsolutePath(),script.getAbsolutePath(),request.buildCommand()),environment,JobControl.flag(),timeout,line->{
                if(first.getAndSet(false)){if(line.startsWith(EngineCommand.GROUP_PREFIX)){try{int value=Integer.parseInt(line.substring(EngineCommand.GROUP_PREFIX.length()));if(value>1)group.set(value);}catch(NumberFormatException ignored){}return;}}
                if(metadata&&line.startsWith("{")){if(line.length()>4_000_000)throw new IOException("平台素材信息过大");json.setLength(0);json.append(line);}
                if(listRules&&ExtractorCatalog.line(line)){if(json.length()>500_000)throw new IOException("引擎目录过大");json.append(line).append('\n');}
                if(progress!=null){EngineProgress.Sample sample=EngineProgress.parse(line);if(sample!=null)progress.nativeSample(sample.bytes,sample.total,sample.speed,sample.eta,sample.estimated);else if(line.startsWith("__WM_POST__"))progress.phase("正在合并 / 转换音视频");}
            },()->{int pid=group.get();if(pid>1)try{android.system.Os.kill(-pid,9);}catch(Exception ignored){}},()->{
                if(cache!=null&&size(cache)>2*LIMIT+8*1024*1024)throw new IOException("素材超过 2GB 下载上限");
            });
            JobControl.check();if(metadata&&json.length()==0)throw new IOException("平台引擎没有返回素材信息");return json.toString();
        }catch(Exception error){JobControl.check();throw new IOException(friendlyError(error),error);}finally{if(sessionJar!=null)sessionJar.delete();}
    }
    private static String friendlyError(Exception e){
        return ExtractionErrors.message(e);
    }
    static Uri download(Context context,LinkExtractor.Item item,LinkExtractor.Progress progress)throws Exception {return download(context,item,progress,true);}
    static Uri download(Context context,LinkExtractor.Item item,LinkExtractor.Progress progress,boolean publish)throws Exception {
        init(context,null);File cache=new File(context.getCacheDir(),"link-"+UUID.randomUUID());
        if(!cache.mkdirs())throw new IOException("无法创建下载缓存，请检查空间");
        Uri saved=null;
        try {
            YoutubeDLRequest request=request(context,item.url,item.engineIndex);
            if(!item.referer.isEmpty()&&!item.referer.equals(item.url)&&EngineMetadata.https(item.referer))request.addOption("--referer",item.referer);
            if(!item.userAgent.isEmpty())request.addOption("--user-agent",item.userAgent);
            request.addOption("--max-filesize","2048M");request.addOption("--no-mtime");request.addOption("--newline");
            request.addOption("-o",new File(cache,"media.%(ext)s").getAbsolutePath());
            if(item.kind==LinkExtractor.Kind.AUDIO){
                request.addOption("-f","bestaudio/best");request.addOption("-x");request.addOption("--audio-format","m4a");
            }else{
                // Prefer AVC/AAC MP4 for Android. Other supported streams remain usable via FFmpeg remux.
                request.addOption("-f","bestvideo[ext=mp4][vcodec^=avc1]+bestaudio[ext=m4a]/best[ext=mp4]/bestvideo+bestaudio/best");
                request.addOption("--merge-output-format","mp4");request.addOption("--remux-video","mp4");
            }
            execute(context,request,15*60_000,cache,progress,false,item.url);JobControl.check();
            File output=new File(cache,item.kind==LinkExtractor.Kind.AUDIO?"media.m4a":"media.mp4");
            if(!output.isFile())throw new IOException("引擎未生成完整媒体文件；源格式可能不支持当前封装");
            if(output.length()==0||output.length()>LIMIT)throw new IOException("素材为空或超过 2GB 下载上限");
            String mime=item.kind==LinkExtractor.Kind.AUDIO?"audio/mp4":"video/mp4";
            byte[] header=new byte[32];int count;try(InputStream in=new FileInputStream(output)){count=in.read(header);}
            if(!MediaSignature.accepts(mime,header,Math.max(0,count)))throw new IOException("下载内容不是有效的媒体文件");
            saved=MediaOps.create(context,mime,(item.kind==LinkExtractor.Kind.AUDIO?"audio_":"video_")+System.currentTimeMillis()+(item.kind==LinkExtractor.Kind.AUDIO?".m4a":".mp4"),item.kind==LinkExtractor.Kind.AUDIO?"Music/水印工坊":"Movies/水印工坊",item.kind==LinkExtractor.Kind.AUDIO?MediaStore.Audio.Media.EXTERNAL_CONTENT_URI:MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
            if(progress!=null){progress.phase("正在写入系统相册 / 音乐媒体库");progress.update(0,output.length());}
            try(InputStream in=new FileInputStream(output);OutputStream out=context.getContentResolver().openOutputStream(saved,"w")){
                if(out==null)throw new IOException("无法保存到媒体库");byte[] buffer=new byte[65536];int n;
                long written=0,last=0;while((n=in.read(buffer))!=-1){JobControl.check();out.write(buffer,0,n);written+=n;if(progress!=null&&written-last>=256*1024){progress.update(written,output.length());last=written;}}out.flush();if(progress!=null)progress.update(written,output.length());
            }
            JobControl.check();if(publish){MediaOps.finish(context,saved);if(progress!=null)progress.complete();}else if(progress!=null)progress.phase("下载完成，等待去水印处理");return saved;
        }catch(Exception e){if(saved!=null)context.getContentResolver().delete(saved,null,null);throw e;}
        finally{delete(cache);}
    }
    static String update(Context context)throws Exception {
        init(context,null);YoutubeDL.UpdateStatus status=YoutubeDL.getInstance().updateYoutubeDL(context,YoutubeDL.UpdateChannel._STABLE);
        JobControl.check();String version=BundledEngine.version(new File(context.getNoBackupFilesDir(),"youtubedl-android/yt-dlp/yt-dlp"));
        try{syncRules(context);}catch(Exception e){JobControl.check();}
        return (status==YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE?"引擎已是最新稳定版":"引擎已更新")+(version==null?"":" · "+version);
    }
    static String syncRules(Context context)throws Exception {
        init(context,null);String rules=execute(context,new YoutubeDLRequest("--list-extractors"),60_000,null,null,false,"");
        if(rules.split("\n").length<100)throw new IOException("引擎未返回完整目录，请重试");
        String version=BundledEngine.version(new File(context.getNoBackupFilesDir(),"youtubedl-android/yt-dlp/yt-dlp"));
        context.getSharedPreferences("platform_catalog",0).edit().putString("rules",rules).putString("version",version==null?"当前引擎":version).apply();return rules;
    }
    static void importCookies(Context context,Uri uri)throws Exception {
        File target=new File(context.getFilesDir(),COOKIES),temporary=new File(context.getFilesDir(),COOKIES+".tmp");
        try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=context.getContentResolver().openInputStream(uri)){
                if(in==null)throw new IOException("无法打开 Cookie 文件");byte[] buf=new byte[8192];int n;
                while((n=in.read(buf))!=-1){JobControl.check();if(bytes.size()+n>1024*1024)throw new IOException("Cookie 文件超过 1MB");bytes.write(buf,0,n);}
            }
            String text=bytes.toString("UTF-8");if(!text.startsWith("# Netscape HTTP Cookie File")&&!text.startsWith("# HTTP Cookie File"))throw new IOException("请导入 Netscape 格式的 cookies.txt 文件");
            try(FileOutputStream out=new FileOutputStream(temporary)){out.write(bytes.toByteArray());out.getFD().sync();}
            JobControl.check();if(!temporary.renameTo(target))throw new IOException("无法保存 Cookie 文件");
        }finally{temporary.delete();}
    }
    static void clearCookies(Context context){new File(context.getFilesDir(),COOKIES).delete();}
    private static long size(File file){if(file.isFile())return file.length();long total=0;File[] children=file.listFiles();if(children!=null)for(File child:children)total+=size(child);return total;}
    private static void delete(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)delete(child);file.delete();}
}
