package com.zizai.watermark;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.provider.MediaStore;
import com.yausername.ffmpeg.FFmpeg;
import java.io.*;
import java.util.*;

/** Offline vocals separation, then remux with the original video packets. */
final class BgmRemover {
    interface Progress {void update(String message);}
    static boolean hasAudio(Context context,Uri input)throws IOException {
        MediaExtractor e=new MediaExtractor();
        try{e.setDataSource(context,input,null);for(int i=0;i<e.getTrackCount();i++){String mime=e.getTrackFormat(i).getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("audio/"))return true;}return false;}
        finally{e.release();}
    }
    static Uri export(Context context,Uri input,boolean publish,Progress progress)throws Exception {
        MediaExtractor probe=new MediaExtractor();long duration=0;boolean audio=false,video=false;
        try {
            probe.setDataSource(context,input,null);
            for(int i=0;i<probe.getTrackCount();i++) {
                MediaFormat f=probe.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);
                if(mime==null)continue;audio|=mime.startsWith("audio/");video|=mime.startsWith("video/");
                if(f.containsKey(MediaFormat.KEY_DURATION))duration=Math.max(duration,f.getLong(MediaFormat.KEY_DURATION));
            }
        }finally{probe.release();}
        if(!video||!audio)throw new IOException(!audio?"视频没有音轨，无需去 BGM":"素材没有视频轨道");
        if(duration>BgmCommands.MAX_SECONDS*1_000_000)throw new IOException("去 BGM 当前支持 30 分钟以内的视频");
        File directory=new File(context.getCacheDir(),"bgm-"+UUID.randomUUID());
        if(!directory.mkdirs())throw new IOException("无法创建音频处理缓存");
        File source=new File(directory,"input.media"),pcm=new File(directory,"audio.f32"),vocals=new File(directory,"vocals.f32"),output=new File(directory,"output.mp4");
        Uri saved=null;boolean success=false;
        try {
            long reserve=Math.max(96L*1024*1024,duration*44100*16/1_000_000+64L*1024*1024);
            if(directory.getUsableSpace()<reserve)throw new IOException("去 BGM 缓存空间不足，请释放手机空间");
            if(progress!=null)progress.update("去 BGM · 读取视频…");
            try(InputStream in=context.getContentResolver().openInputStream(input);OutputStream out=new FileOutputStream(source)){
                if(in==null)throw new IOException("视频无法读取");copy(in,out,directory,1024L*1024*1024);
            }
            JobControl.check();if(directory.getUsableSpace()<reserve)throw new IOException("去 BGM 缓存空间不足，请释放手机空间");
            File model=AutoModelStore.model(context,"UVR_MDXNET_9482.onnx",AutoModelStore.UVR_SHA);
            File executable=new File(context.getApplicationInfo().nativeLibraryDir,"libffmpeg.so");
            if(!executable.isFile())throw new IOException("缺少本机音频处理组件，请安装完整安装包");
            FFmpeg.getInstance().init(context.getApplicationContext());
            Map<String,String> env=new HashMap<>();env.put("LD_LIBRARY_PATH",new File(context.getNoBackupFilesDir(),"youtubedl-android/packages/ffmpeg/usr/lib").getAbsolutePath());env.put("TMPDIR",directory.getAbsolutePath());
            if(progress!=null)progress.update("去 BGM · 解码音轨…");
            run(executable,BgmCommands.decode(source.getAbsolutePath(),pcm.getAbsolutePath()),env,directory,pcm,BgmCommands.MAX_PCM_BYTES);
            JobControl.check();if(progress!=null)progress.update("去 BGM · 加载本机人声模型…");
            File lib=new File(context.getApplicationInfo().nativeLibraryDir,"libsherpa-onnx-c-api.so");
            try(SourceSeparator separator=new SourceSeparator(lib.getAbsolutePath(),model.getAbsolutePath())){
                separator.separate(pcm,vocals,(done,total)->{space(directory);if(progress!=null)progress.update("去 BGM · 分离人声 "+(done*100/total)+"%");});
            }
            JobControl.check();if(progress!=null)progress.update("去 BGM · 将人声合回视频…");
            run(executable,BgmCommands.mux(source.getAbsolutePath(),vocals.getAbsolutePath(),output.getAbsolutePath()),env,directory,output,1024L*1024*1024);
            MediaExtractor check=new MediaExtractor();boolean hasVideo=false,hasSound=false;
            try{check.setDataSource(output.getAbsolutePath());for(int i=0;i<check.getTrackCount();i++){String mime=check.getTrackFormat(i).getString(MediaFormat.KEY_MIME);hasVideo|=mime!=null&&mime.startsWith("video/");hasSound|=mime!=null&&mime.startsWith("audio/");}}
            finally{check.release();}
            if(!hasVideo||!hasSound)throw new IOException("去 BGM 输出缺少画面或声音");
            JobControl.check();if(progress!=null)progress.update("去 BGM · 保存成品…");
            saved=MediaOps.create(context,"video/mp4","vocals_"+System.currentTimeMillis()+".mp4","Movies/水印工坊",MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
            try(InputStream in=new FileInputStream(output);OutputStream out=context.getContentResolver().openOutputStream(saved,"w")){
                if(out==null)throw new IOException("无法保存成品");copy(in,out,directory,1024L*1024*1024);
            }
            JobControl.check();if(publish)MediaOps.finish(context,saved);success=true;return saved;
        } finally {
            if(!success&&saved!=null)try{context.getContentResolver().delete(saved,null,null);}catch(Exception ignored){}
            delete(directory);
        }
    }
    private static void copy(InputStream in,OutputStream out,File directory,long limit)throws Exception {
        byte[] b=new byte[65536];int n;long count=0;
        while((n=in.read(b))!=-1){JobControl.check();count+=n;if(count>limit)throw new IOException("去 BGM 支持 1GB 以内的视频");space(directory);out.write(b,0,n);}out.flush();
    }
    private static void space(File directory)throws IOException{if(directory.getUsableSpace()<32L*1024*1024)throw new IOException("手机缓存空间不足");}
    private static void run(File executable,List<String> arguments,Map<String,String> env,File directory,File output,long limit)throws Exception {
        List<String> command=new ArrayList<>();command.add(executable.getAbsolutePath());command.addAll(arguments);
        NativeProcess.run(command,env,JobControl.flag(),30*60_000,null,null,()->{space(directory);if(output.length()>limit)throw new IOException("去 BGM 素材超过时长或大小上限");});
        JobControl.check();if(!output.isFile()||output.length()==0||output.length()>limit)throw new IOException("音频处理未生成有效输出，或输出超过上限");
    }
    private static void delete(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)delete(child);file.delete();}
}
