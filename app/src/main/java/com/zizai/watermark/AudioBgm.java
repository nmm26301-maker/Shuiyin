package com.zizai.watermark;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.provider.MediaStore;
import com.yausername.ffmpeg.FFmpeg;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Decode locally, separate real vocal stems and remux without re-encoding video. */
final class AudioBgm {
    interface Progress {void update(String message);}
    static List<String> decodeArgs(String source,String pcm){return Arrays.asList("-y","-nostdin","-i",source,"-map","0:a:0","-vn","-t","181","-ar","44100","-ac","2","-c:a","pcm_s16le","-f","s16le",pcm);}
    static List<String> muxArgs(String source,String pcm,String output){return Arrays.asList("-y","-nostdin","-i",source,"-f","s16le","-ar","44100","-ac","2","-i",pcm,"-map","0:v:0","-map","1:a:0","-c:v","copy","-c:a","aac","-b:a","192k","-af","apad","-shortest","-map_metadata","0","-movflags","+faststart",output);}
    static Uri export(Context context,Uri input,Progress progress)throws Exception {
        checkTracks(context,input);JobControl.check();progress.update("准备本地人声分离模型…");
        File vocals=model(context,"vocals.fp16.onnx","24cef84aedcd1fe87c0b743ef3370ad34dc1fabf6c9014d6128a75a538c7b668");
        File backing=model(context,"accompaniment.fp16.onnx","d14cea55793cc531a5875f5f4da08207d1c5ab9292e8e0099a104eecb014fcc0");
        FFmpeg.getInstance().init(context.getApplicationContext());File directory=new File(context.getCacheDir(),"bgm-"+UUID.randomUUID());if(!directory.mkdirs())throw new IOException("无法创建人声处理缓存");
        File source=new File(directory,"input.media"),decoded=new File(directory,"decoded.pcm"),voice=new File(directory,"vocals.pcm"),output=new File(directory,"vocals.mp4");Uri saved=null;boolean published=false;
        try{
            progress.update("读取原视频…");try(InputStream in=context.getContentResolver().openInputStream(input);OutputStream out=new FileOutputStream(source)){
                if(in==null)throw new IOException("无法读取原视频");byte[] bytes=new byte[65536];int n;long count=0;
                while((n=in.read(bytes))!=-1){JobControl.check();count+=n;if(count>MediaLimits.DOWNLOAD/2)throw new IOException("去 BGM 支持 1GB 以内的视频");out.write(bytes,0,n);}
            }
            progress.update("解码音轨…");execute(context,directory,decodeArgs(source.getAbsolutePath(),decoded.getAbsolutePath()));
            JobControl.check();try(VocalModel separator=new VocalModel(vocals,backing)){
                VocalDsp.separate(decoded,voice,separator,(done,total)->progress.update("AI 保留人声 · "+done+" / "+total+" 段"));
            }
            JobControl.check();progress.update("保留原画面，合成人声版视频…");execute(context,directory,muxArgs(source.getAbsolutePath(),voice.getAbsolutePath(),output.getAbsolutePath()));
            verifyOutput(output);progress.update("保存人声版到相册…");
            saved=MediaOps.create(context,"video/mp4","vocals_"+System.currentTimeMillis()+".mp4","Movies/水印工坊",MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
            try(InputStream in=new FileInputStream(output);OutputStream out=context.getContentResolver().openOutputStream(saved,"w")){
                if(out==null)throw new IOException("无法保存人声版视频");byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1){JobControl.check();out.write(bytes,0,n);}out.flush();
            }
            JobControl.check();MediaOps.finish(context,saved);published=true;return saved;
        }catch(Exception error){JobControl.check();throw error;}
        finally{if(saved!=null&&!published)try{context.getContentResolver().delete(saved,null,null);}catch(Exception ignored){}delete(directory);}
    }
    private static void checkTracks(Context context,Uri input)throws Exception {
        MediaExtractor e=new MediaExtractor();boolean video=false,audio=false;
        try{e.setDataSource(context,input,null);for(int i=0;i<e.getTrackCount();i++){
            MediaFormat f=e.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);video|=mime!=null&&mime.startsWith("video/");audio|=mime!=null&&mime.startsWith("audio/");
            if(f.containsKey(MediaFormat.KEY_DURATION)&&f.getLong(MediaFormat.KEY_DURATION)>180_000_000L)throw new IOException("去 BGM 暂支持 3 分钟以内的视频，请先裁剪");
        }}finally{e.release();}if(!video||!audio)throw new IOException("请选择带声音的视频，静音视频无法分离人声");
    }
    private static void verifyOutput(File file)throws Exception {
        if(!file.isFile()||file.length()<32||file.length()>MediaLimits.DOWNLOAD/2)throw new IOException("人声视频输出为空或过大");
        MediaExtractor e=new MediaExtractor();boolean audio=false,video=false;
        try{e.setDataSource(file.getAbsolutePath());for(int i=0;i<e.getTrackCount();i++){String mime=e.getTrackFormat(i).getString(MediaFormat.KEY_MIME);audio|=mime!=null&&mime.startsWith("audio/");video|=mime!=null&&mime.startsWith("video/");}}finally{e.release();}
        if(!audio||!video)throw new IOException("人声视频缺少有效画面或音轨");
    }
    private static void execute(Context context,File directory,List<String> arguments)throws Exception {
        JobControl.check();File exe=new File(context.getApplicationInfo().nativeLibraryDir,"libffmpeg.so");if(!exe.isFile())throw new IOException("缺少 FFmpeg 本地处理组件");
        List<String> command=new ArrayList<>();command.add(exe.getAbsolutePath());command.addAll(arguments);
        Map<String,String> env=new HashMap<>();env.put("LD_LIBRARY_PATH",new File(context.getNoBackupFilesDir(),"youtubedl-android/packages/ffmpeg/usr/lib").getAbsolutePath());env.put("TMPDIR",directory.getAbsolutePath());
        NativeProcess.run(command,env,JobControl.flag(),10*60_000,line->{});
    }
    private static File model(Context context,String name,String sha)throws Exception {
        File directory=new File(context.getNoBackupFilesDir(),"bgm-models");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法准备模型目录");
        File file=new File(directory,name);if(file.isFile()&&digest(file).equals(sha))return file;
        File temporary=new File(directory,name+".tmp");try{
            try(InputStream in=context.getAssets().open("bgm/"+name);OutputStream out=new FileOutputStream(temporary)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1){JobControl.check();out.write(bytes,0,n);}}
            if(!digest(temporary).equals(sha))throw new IOException("人声模型校验失败，请安装完整安装包");
            if(file.exists()&&!file.delete()||!temporary.renameTo(file))throw new IOException("模型安装失败");return file;
        }finally{temporary.delete();}
    }
    private static String digest(File file)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1){JobControl.check();digest.update(bytes,0,n);}}
        StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    private static void delete(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)delete(child);file.delete();}
}
