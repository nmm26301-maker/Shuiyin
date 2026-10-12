package com.zizai.watermark;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/** Android system codec wrappers: storage access framework + scoped MediaStore. */
public final class MediaOps {
    private MediaOps() {}
    public static Uri saveBitmap(Context ctx, Bitmap result,String filename) throws Exception {
        return saveBitmap(ctx,result,filename,true);
    }
    static Uri saveBitmap(Context ctx, Bitmap result,String filename,boolean publish) throws Exception {
        Uri uri=create(ctx,"image/png",filename,"Pictures/水印工坊",MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        try {
            try(OutputStream out=ctx.getContentResolver().openOutputStream(uri,"w")) {
                if(out==null || !result.compress(Bitmap.CompressFormat.PNG,100,out)) throw new IOException("无法输出图片");
                out.flush();
            }
            checkCancelled();if(publish)finish(ctx,uri);
            return uri;
        } catch(Exception e){ctx.getContentResolver().delete(uri,null,null);throw e;}
    }
    static Uri create(Context ctx,String mime,String name,String path,Uri store) throws IOException {
        ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
        values.put(MediaStore.MediaColumns.MIME_TYPE,mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,path);
        values.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri uri=ctx.getContentResolver().insert(store,values);
        if(uri==null)throw new IOException("无法创建输出文件，检查手机存储空间");
        return uri;
    }
    static void finish(Context ctx,Uri uri)throws IOException {
        ContentValues ready=new ContentValues();ready.put(MediaStore.MediaColumns.IS_PENDING,0);
        if(ctx.getContentResolver().update(uri,ready,null,null)!=1)throw new IOException("无法完成媒体库登记");
    }
    public static Uri saveGif(Context ctx, Uri video) throws Exception {
        Uri result=create(ctx,"image/gif","video_"+System.currentTimeMillis()+".gif",
                "Pictures/水印工坊",MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();
        try(ParcelFileDescriptor fd=ctx.getContentResolver().openFileDescriptor(video,"r")) {
            if(fd==null)throw new IOException("视频无法读取");
            retriever.setDataSource(fd.getFileDescriptor());
            long duration=durationUs(retriever);
            int count=(int)Math.min(32,Math.ceil(Math.min(8_000_000L,duration)/250_000.0));
            if(count<1)throw new IOException("视频太短或无法读取");
            try(OutputStream output=ctx.getContentResolver().openOutputStream(result,"w")) {
                if(output==null)throw new IOException("无法创建 GIF 输出流");
                // Determine the source aspect before writing the GIF header.
                Bitmap first=retriever.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if(first==null)throw new IOException("无法解码视频画面");
                int width=320;
                int height=Math.max(2, Math.min(640, (int)Math.round(first.getHeight()*320.0/first.getWidth())));
                GifWriter gif=new GifWriter(output,width,height,25);
                gif.begin();
                for(int i=0;i<count;i++) {
                    checkCancelled();
                    Bitmap frame = i==0 ? first : retriever.getFrameAtTime(i*250_000L,MediaMetadataRetriever.OPTION_CLOSEST);
                    if(frame==null)continue;
                    Bitmap resized=Bitmap.createScaledBitmap(frame,width,height,true);
                    gif.addFrame(resized);
                    if(resized!=frame)resized.recycle();
                    frame.recycle();
                }
                gif.finish();
                output.flush();
            }
            checkCancelled();finish(ctx,result);
            return result;
        } catch(Exception e){ctx.getContentResolver().delete(result,null,null);throw e;}
        finally {retriever.release();}
    }
    public static Bitmap frame(Context ctx,Uri video,long timestampUs) throws Exception {
        MediaMetadataRetriever r=new MediaMetadataRetriever();
        try(ParcelFileDescriptor fd=ctx.getContentResolver().openFileDescriptor(video,"r")) {
            if(fd==null)throw new IOException("视频打开失败");
            r.setDataSource(fd.getFileDescriptor());
            long dur=durationUs(r);
            if(timestampUs<0||timestampUs>=dur)throw new IOException("取帧时间须小于视频时长");
            Bitmap f=r.getFrameAtTime(timestampUs,MediaMetadataRetriever.OPTION_CLOSEST);
            if(f==null)throw new IOException("这个时间点没有可提取的画面");
            return f;
        } finally {r.release();}
    }
    private static long durationUs(MediaMetadataRetriever retriever) {
        try {return Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))*1000L;}
        catch(Exception e){return 8_000_000L;}
    }
    public static Uri saveRemux(Context ctx,Uri video,boolean audioOnly,boolean mute,long startUs,long endUs) throws Exception {
        if(audioOnly&&mute)throw new IllegalArgumentException("不能同时提取和静音");
        long now=System.currentTimeMillis();
        Uri out=create(ctx, audioOnly?"audio/mp4":"video/mp4",
                (audioOnly?"audio_":"edited_")+now+(audioOnly?".m4a":".mp4"),
                audioOnly?"Music/水印工坊":"Movies/水印工坊",
                audioOnly?MediaStore.Audio.Media.EXTERNAL_CONTENT_URI:MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        MediaExtractor extractor=new MediaExtractor();
        MediaMuxer muxer=null;
        boolean started=false;
        try(ParcelFileDescriptor sourceFd=ctx.getContentResolver().openFileDescriptor(video,"r");
            ParcelFileDescriptor outputFd=ctx.getContentResolver().openFileDescriptor(out,"w")) {
            if(sourceFd==null || outputFd==null)throw new IOException("文件读写失败");
            extractor.setDataSource(sourceFd.getFileDescriptor());
            muxer=new MediaMuxer(outputFd.getFileDescriptor(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int[] tracks=new int[extractor.getTrackCount()];
            for(int i=0;i<tracks.length;i++)tracks[i]=-1;
            int used=0;int selectedVideo=-1;
            int maxSize=4*1024*1024;
            for(int t=0;t<extractor.getTrackCount();t++) {
                MediaFormat format=extractor.getTrackFormat(t);
                String mime=format.getString(MediaFormat.KEY_MIME);
                if(mime==null)continue;
                boolean audio=mime.startsWith("audio/");
                boolean videoTrack=mime.startsWith("video/");
                if(audioOnly) {
                    if(!mime.equals("audio/mp4a-latm"))continue;
                } else if(mute ? !videoTrack : !(audio||videoTrack))continue;
                if(videoTrack&&selectedVideo<0)selectedVideo=t;
                tracks[t]=muxer.addTrack(format);
                extractor.selectTrack(t);
                used++;
                if(format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE))
                    maxSize=Math.max(maxSize,Math.min(16*1024*1024,format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)));
            }
            if(used==0)throw new IOException(audioOnly?"当前视频没有可无损提取的 AAC 音轨":"视频中没有可处理的媒体轨道");
            if(!audioOnly) {
                MediaMetadataRetriever r=new MediaMetadataRetriever();
                try {r.setDataSource(sourceFd.getFileDescriptor());
                    String rotation=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                    if(rotation!=null) {
                        int degrees=Integer.parseInt(rotation);
                        if(degrees==0||degrees==90||degrees==180||degrees==270)muxer.setOrientationHint(degrees);
                    }
                }catch(Exception ignored) {} finally {r.release();}
            }
            ByteBuffer buffer=ByteBuffer.allocateDirect(maxSize);
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            long base;
            if(startUs>0&&selectedVideo>=0){
                MediaExtractor sync=new MediaExtractor();
                try{sync.setDataSource(sourceFd.getFileDescriptor());sync.selectTrack(selectedVideo);sync.seekTo(startUs,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);base=sync.getSampleTime();}
                finally{sync.release();}
                if(base<0)throw new IOException("裁剪起点之后没有视频");
                extractor.seekTo(base,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            }else{if(startUs>0)extractor.seekTo(startUs,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);base=extractor.getSampleTime();}
            if(base<0)throw new IOException("选定时间之后没有媒体数据");
            int written=0;boolean videoWritten=false;
            muxer.start();started=true;
            while(true) {
                checkCancelled();
                int inTrack=extractor.getSampleTrackIndex();
                if(inTrack<0)break;
                long time=extractor.getSampleTime();
                if(endUs>0 && time>endUs)break;
                if(tracks[inTrack]<0){extractor.advance();continue;}
                buffer.clear();
                int size=extractor.readSampleData(buffer,0);
                if(size<0)break;
                if(size>=buffer.capacity())throw new IOException("视频单帧太大，暂不支持该文件");
                if(time<base){extractor.advance();continue;}
                if(inTrack==selectedVideo&&!videoWritten){
                    if((extractor.getSampleFlags()&MediaExtractor.SAMPLE_FLAG_SYNC)==0)throw new IOException("裁剪起点不是可解码的关键帧");
                    videoWritten=true;
                }
                info.offset=0;info.size=size;
                info.presentationTimeUs=Math.max(0,time-base);
                info.flags=extractor.getSampleFlags();
                muxer.writeSampleData(tracks[inTrack],buffer,info);written++;
                extractor.advance();
            }
            if(written==0||(selectedVideo>=0&&!videoWritten))throw new IOException("选定区间没有可导出的媒体数据");
            muxer.stop();started=false;
            muxer.release();muxer=null;
            checkCancelled();finish(ctx,out);
            return out;
        } catch(Exception e){ctx.getContentResolver().delete(out,null,null);throw e;}
        finally {
            try{extractor.release();}catch(Exception ignored){}
            if(muxer!=null) {
                if(started)try{muxer.stop();}catch(Exception ignored){}
                try{muxer.release();}catch(Exception ignored){}
            }
        }
    }
    static void checkCancelled() throws IOException {
        JobControl.check();
    }
    public static Uri changeFingerprint(Context ctx,Uri input)throws Exception {
        Uri out=create(ctx,"video/mp4","fingerprint_"+System.currentTimeMillis()+".mp4","Movies/水印工坊",MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        try {
            try(java.io.InputStream in=ctx.getContentResolver().openInputStream(input);OutputStream stream=ctx.getContentResolver().openOutputStream(out,"w")){
                if(in==null||stream==null)throw new IOException("文件无法读写");
                Mp4Fingerprint.copyWithFreeBox(in,stream);
            }
            checkCancelled();finish(ctx,out);return out;
        }catch(Exception e){ctx.getContentResolver().delete(out,null,null);throw e;}
    }

}
