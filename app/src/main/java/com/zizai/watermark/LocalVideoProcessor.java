package com.zizai.watermark;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.provider.MediaStore;
import com.yausername.ffmpeg.FFmpeg;
import java.io.*;
import java.util.*;

/** Local FFmpeg for pitch-preserving speed and static rectangular logo repair. */
final class LocalVideoProcessor {
    interface Progress {void update(String message);}
    static Uri export(Context context,Uri input,double speed,boolean keepAudio,float[] region,Progress progress)throws Exception {
        return exportRegions(context,input,speed,keepAudio,region==null?null:new float[][]{region},progress);
    }
    static Uri exportRegions(Context context,Uri input,double speed,boolean keepAudio,float[][] regions,Progress progress)throws Exception {
        return process(context,input,speed,keepAudio,regions,false,progress);
    }
    static Uri exportAudio(Context context,Uri input,Progress progress)throws Exception{return process(context,input,1,true,null,true,progress);}
    private static Uri process(Context context,Uri input,double speed,boolean keepAudio,float[][] regions,boolean audioOnly,Progress progress)throws Exception {
        MediaExtractor extractor=new MediaExtractor();int width=0,height=0;long duration=0;boolean hasAudio=false;
        try{
            extractor.setDataSource(context,input,null);
            for(int i=0;i<extractor.getTrackCount();i++){
                MediaFormat format=extractor.getTrackFormat(i);String mime=format.getString(MediaFormat.KEY_MIME);if(mime==null)continue;
                if(mime.startsWith("audio/"))hasAudio=true;
                if(mime.startsWith("video/")&&width==0){
                    width=format.getInteger(MediaFormat.KEY_WIDTH);height=format.getInteger(MediaFormat.KEY_HEIGHT);
                    int rotation=format.containsKey(MediaFormat.KEY_ROTATION)?format.getInteger(MediaFormat.KEY_ROTATION):0;
                    rotation=((rotation%360)+360)%360;if(rotation%90!=0)throw new IOException("不支持此视频旋转角度");
                    if(rotation==90||rotation==270){int swap=width;width=height;height=swap;}
                    if(format.containsKey(MediaFormat.KEY_DURATION))duration=format.getLong(MediaFormat.KEY_DURATION);
                    if(!audioOnly&&format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)){
                        int transfer=format.getInteger(MediaFormat.KEY_COLOR_TRANSFER);
                        if(transfer==MediaFormat.COLOR_TRANSFER_ST2084||transfer==MediaFormat.COLOR_TRANSFER_HLG)throw new IOException("当前处理暂不支持 HDR，请使用 SDR 视频");
                    }
                }
            }
        }finally{extractor.release();}
        if(!audioOnly&&(width==0||height==0))throw new IOException("素材没有有效的视频轨道");
        if(audioOnly&&!hasAudio)throw new IOException("素材没有音频轨道");
        int[][] rectangles=regions==null?null:VideoFilters.regions(regions,width,height);
        VideoFilters.tempo(speed);JobControl.check();if(progress!=null)progress.update("准备本地视频处理组件…");
        File executable=new File(context.getApplicationInfo().nativeLibraryDir,"libffmpeg.so");
        if(!executable.isFile())throw new IOException("缺少本机视频处理组件，请安装完整安装包");
        FFmpeg.getInstance().init(context.getApplicationContext());JobControl.check();
        File directory=new File(context.getCacheDir(),"video-"+UUID.randomUUID());if(!directory.mkdirs())throw new IOException("无法创建处理缓存");
        File source=new File(directory,"input.media"),output=new File(directory,audioOnly?"output.m4a":"output.mp4");Uri saved=null;
        try{
            if(progress!=null)progress.update("读取视频到本机缓存…");
            try(InputStream in=context.getContentResolver().openInputStream(input);OutputStream out=new FileOutputStream(source)){
                if(in==null)throw new IOException("无法读取原视频");byte[] bytes=new byte[65536];int n;long count=0;
                while((n=in.read(bytes))!=-1){JobControl.check();out.write(bytes,0,n);count+=n;
                    if(count>1024L*1024*1024)throw new IOException("此处理支持 1GB 以内的视频");
                    if(count%(4*1024*1024)<65536&&directory.getUsableSpace()<32L*1024*1024)throw new IOException("缓存空间不足，请释放手机空间");
                }
            }
            JobControl.check();List<String> command=new ArrayList<>();command.add(executable.getAbsolutePath());
            command.addAll(audioOnly?VideoFilters.audioArguments(source.getAbsolutePath(),output.getAbsolutePath()):VideoFilters.exportRegionArguments(source.getAbsolutePath(),output.getAbsolutePath(),speed,rectangles,keepAudio&&hasAudio));
            Map<String,String> environment=new HashMap<>();environment.put("LD_LIBRARY_PATH",new File(context.getNoBackupFilesDir(),"youtubedl-android/packages/ffmpeg/usr/lib").getAbsolutePath());environment.put("TMPDIR",directory.getAbsolutePath());
            final long total=duration>0?(long)(duration/speed):0;
            if(progress!=null)progress.update("正在处理视频 · 0%");
            NativeProcess.run(command,environment,JobControl.flag(),30*60_000,line->{
                if(line.startsWith("out_time_us=")){try{long time=Long.parseLong(line.substring(12));if(progress!=null&&total>0)progress.update("正在处理视频 · "+Math.max(0,Math.min(98,time*98/total))+"%");}catch(NumberFormatException ignored){}}
                if(output.length()>1024L*1024*1024)throw new IOException("导出超过 1GB 上限");
            });
            JobControl.check();if(!output.isFile()||output.length()==0)throw new IOException("视频处理未产生有效输出");
            MediaExtractor probe=new MediaExtractor();try{probe.setDataSource(output.getAbsolutePath());boolean track=false;for(int i=0;i<probe.getTrackCount();i++){String mime=probe.getTrackFormat(i).getString(MediaFormat.KEY_MIME);track|=mime!=null&&mime.startsWith(audioOnly?"audio/":"video/");}if(!track)throw new IOException("输出媒体轨道无法读取");}finally{probe.release();}
            if(progress!=null)progress.update("保存到系统相册…");
            saved=MediaOps.create(context,audioOnly?"audio/mp4":"video/mp4",(audioOnly?"audio_":regions==null?"speed_":"repair_")+System.currentTimeMillis()+(audioOnly?".m4a":".mp4"),audioOnly?"Music/水印工坊":"Movies/水印工坊",audioOnly?MediaStore.Audio.Media.EXTERNAL_CONTENT_URI:MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
            try(InputStream in=new FileInputStream(output);OutputStream out=context.getContentResolver().openOutputStream(saved,"w")){
                if(out==null)throw new IOException("无法保存视频");byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1){JobControl.check();out.write(bytes,0,n);}out.flush();
            }
            JobControl.check();MediaOps.finish(context,saved);return saved;
        }catch(Exception e){if(saved!=null)context.getContentResolver().delete(saved,null,null);throw e;}
        finally{delete(directory);}
    }
    private static void delete(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)delete(child);file.delete();}
}
