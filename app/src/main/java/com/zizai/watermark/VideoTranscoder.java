package com.zizai.watermark;

import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.opengl.*;
import android.os.*;
import android.provider.MediaStore;
import android.view.Surface;
import java.io.IOException;
import java.nio.*;

/** Decoder -> OES texture -> OpenGL -> AVC surface encoder -> MP4. No network. */
public final class VideoTranscoder {
    public static class Options {
        public int maxSide=1920,bitrate=6_000_000;
        public boolean mirror,mute;
        public float cropAspect=0,speed=1;
        public ImageEditorView.Mark mark;
    }
    public interface Progress {void update(int percent);}
    public static Uri export(Context context,Uri input,Options options,Progress progress)throws Exception {
        MediaExtractor extractor=new MediaExtractor();MediaCodec decoder=null,encoder=null;
        MediaMuxer muxer=null;GlFrames gl=null;Uri result=null;
        boolean muxStarted=false;int videoOut=-1,audioOut=-1;
        try {
            if(options.speed<.5f||options.speed>4)throw new IOException("倍速支持 0.5× 到 4×");
            if(options.speed!=1&&!options.mute)throw new IOException("当前倍速输出需要关闭声音");
            extractor.setDataSource(context,input,null);
            MediaFormat video=null,audio=null;int videoIndex=-1;
            for(int i=0;i<extractor.getTrackCount();i++){
                MediaFormat f=extractor.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("video/")&&video==null){video=f;videoIndex=i;}
                else if(mime!=null&&mime.startsWith("audio/")&&audio==null)audio=f;
            }
            if(video==null)throw new IOException("文件没有视频轨道");
            if(video.containsKey(MediaFormat.KEY_COLOR_TRANSFER)){
                int transfer=video.getInteger(MediaFormat.KEY_COLOR_TRANSFER);
                if(transfer==MediaFormat.COLOR_TRANSFER_ST2084||transfer==MediaFormat.COLOR_TRANSFER_HLG)
                    throw new IOException("当前渲染暂不支持 HDR 视频，请使用 SDR 素材");
            }
            if(audio!=null&&!options.mute&&!"audio/mp4a-latm".equals(audio.getString(MediaFormat.KEY_MIME)))
                throw new IOException("保留声音目前支持 AAC 音轨；可关闭声音后导出");
            int width=video.getInteger(MediaFormat.KEY_WIDTH),height=video.getInteger(MediaFormat.KEY_HEIGHT);
            int rotation=video.containsKey(MediaFormat.KEY_ROTATION)?video.getInteger(MediaFormat.KEY_ROTATION):0;
            rotation=((rotation%360)+360)%360;
            if(rotation%90!=0)throw new IOException("不支持此视频旋转角度");
            int dw=(rotation==90||rotation==270)?height:width,dh=(rotation==90||rotation==270)?width:height;
            if(options.cropAspect>0){if((float)dw/dh>options.cropAspect)dw=Math.round(dh*options.cropAspect);else dh=Math.round(dw/options.cropAspect);}
            float factor=Math.min(1f,(float)options.maxSide/Math.max(dw,dh));
            dw=Math.max(16,Math.round(dw*factor)/2*2);dh=Math.max(16,Math.round(dh*factor)/2*2);
            encoder=MediaCodec.createEncoderByType("video/avc");
            MediaCodecInfo.VideoCapabilities caps=encoder.getCodecInfo().getCapabilitiesForType("video/avc").getVideoCapabilities();
            int alignW=caps.getWidthAlignment(),alignH=caps.getHeightAlignment();
            dw=Math.max(alignW,dw/alignW*alignW);dh=Math.max(alignH,dh/alignH*alignH);
            if(!caps.isSizeSupported(dw,dh))throw new IOException("设备编码器不支持此尺寸，试试 720p 压缩");
            MediaFormat output=MediaFormat.createVideoFormat("video/avc",dw,dh);
            output.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            output.setInteger(MediaFormat.KEY_BIT_RATE,caps.getBitrateRange().clamp(options.bitrate));
            output.setInteger(MediaFormat.KEY_FRAME_RATE,30);output.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
            encoder.configure(output,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface encoderSurface=encoder.createInputSurface();
            gl=new GlFrames(encoderSurface,dw,dh,VideoGeometry.coordinates(width,height,rotation,options.cropAspect,options.mirror),options.mark);
            encoder.start();
            video.setInteger(MediaFormat.KEY_ROTATION,0); // rotation is baked into GL coordinates
            decoder=MediaCodec.createDecoderByType(video.getString(MediaFormat.KEY_MIME));
            decoder.configure(video,gl.decoderSurface,null,0);decoder.start();extractor.selectTrack(videoIndex);
            result=MediaOps.create(context,"video/mp4","video_"+System.currentTimeMillis()+".mp4","Movies/水印工坊",MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
            try(ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(result,"w")){
                if(fd==null)throw new IOException("无法创建视频输出");
                muxer=new MediaMuxer(fd.getFileDescriptor(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                if(audio!=null&&!options.mute)audioOut=muxer.addTrack(audio);
                MediaCodec.BufferInfo decInfo=new MediaCodec.BufferInfo(),encInfo=new MediaCodec.BufferInfo();
                boolean inputDone=false,decoderDone=false,encoderDone=false;
                long firstPts=-1,lastPts=-1,duration=video.containsKey(MediaFormat.KEY_DURATION)?video.getLong(MediaFormat.KEY_DURATION):0;
                long watchdog=SystemClock.elapsedRealtime();int frames=0,lastProgress=-1;
                while(!encoderDone){
                    MediaOps.checkCancelled();boolean advanced=false;
                    // Drain the encoder before adding frames, avoiding surface backpressure.
                    while(true){
                        int index=encoder.dequeueOutputBuffer(encInfo,0);
                        if(index==MediaCodec.INFO_TRY_AGAIN_LATER)break;
                        if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                            if(muxStarted)throw new IOException("编码格式异常变化");
                            videoOut=muxer.addTrack(encoder.getOutputFormat());muxer.start();muxStarted=true;advanced=true;continue;
                        }
                        if(index>=0){
                            if((encInfo.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)encInfo.size=0;
                            if(encInfo.size>0){
                                if(!muxStarted)throw new IOException("编码器未输出有效格式");
                                ByteBuffer b=encoder.getOutputBuffer(index);if(b==null)throw new IOException("编码缓冲为空");
                                b.position(encInfo.offset);b.limit(encInfo.offset+encInfo.size);muxer.writeSampleData(videoOut,b,encInfo);
                            }
                            encoderDone=(encInfo.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                            encoder.releaseOutputBuffer(index,false);advanced=true;
                        }
                    }
                    if(!inputDone){
                        int index=decoder.dequeueInputBuffer(10_000);
                        if(index>=0){
                            ByteBuffer b=decoder.getInputBuffer(index);if(b==null)throw new IOException("解码缓冲为空");b.clear();
                            int size=extractor.readSampleData(b,0);
                            if(size<0){decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}
                            else {decoder.queueInputBuffer(index,0,size,extractor.getSampleTime(),0);extractor.advance();}
                            advanced=true;
                        }
                    }
                    if(!decoderDone){
                        int index=decoder.dequeueOutputBuffer(decInfo,10_000);
                        if(index>=0){
                            boolean eos=(decInfo.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                            boolean render=decInfo.size>0;
                            if(render){
                                if(firstPts<0)firstPts=decInfo.presentationTimeUs;
                                long pts=Math.max(0,(long)((decInfo.presentationTimeUs-firstPts)/options.speed));
                                // Bound to 30 fps; dropping excess fast frames retains correct duration.
                                render=lastPts<0||pts-lastPts>=30_000;
                                decoder.releaseOutputBuffer(index,render);
                                if(render){gl.awaitAndDraw(pts*1000L);lastPts=pts;frames++;}
                                if(progress!=null&&duration>0){int percent=(int)Math.min(94,Math.max(0,decInfo.presentationTimeUs*94/duration));if(percent!=lastProgress){progress.update(percent);lastProgress=percent;}}
                            }else decoder.releaseOutputBuffer(index,false);
                            if(eos){decoderDone=true;encoder.signalEndOfInputStream();}advanced=true;
                        }else if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)advanced=true;
                    }
                    if(advanced)watchdog=SystemClock.elapsedRealtime();
                    else if(SystemClock.elapsedRealtime()-watchdog>25_000)throw new IOException("设备编解码器超时，请换用较低分辨率或其他视频");
                }
                if(frames==0||!muxStarted)throw new IOException("未解码出可导出的画面");
                if(audioOut>=0)copyAudio(context,input,muxer,audioOut,firstPts,progress);
                muxer.stop();muxStarted=false;muxer.release();muxer=null;
            }
            MediaOps.checkCancelled();MediaOps.finish(context,result);if(progress!=null)progress.update(100);return result;
        } catch(Exception e){
            if(result!=null)try{context.getContentResolver().delete(result,null,null);}catch(Exception ignored){}
            throw e;
        } finally {
            try{extractor.release();}catch(Exception ignored){}
            releaseCodec(decoder);releaseCodec(encoder);
            if(gl!=null)gl.close();
            if(muxer!=null){if(muxStarted)try{muxer.stop();}catch(Exception ignored){}try{muxer.release();}catch(Exception ignored){}}
        }
    }
    private static void releaseCodec(MediaCodec codec){if(codec!=null){try{codec.stop();}catch(Exception ignored){}try{codec.release();}catch(Exception ignored){}}}
    private static void copyAudio(Context c,Uri uri,MediaMuxer mux,int track,long base,Progress progress)throws Exception {
        MediaExtractor ex=new MediaExtractor();
        try {
            ex.setDataSource(c,uri,null);int index=-1;
            for(int i=0;i<ex.getTrackCount();i++)if("audio/mp4a-latm".equals(ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME))){index=i;break;}
            if(index<0)throw new IOException("原始音轨不可读取");ex.selectTrack(index);
            ByteBuffer b=ByteBuffer.allocateDirect(2*1024*1024);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            while(true){MediaOps.checkCancelled();b.clear();int n=ex.readSampleData(b,0);if(n<0)break;long pts=ex.getSampleTime()-base;
                if(pts>=0){info.set(0,n,pts,ex.getSampleFlags());mux.writeSampleData(track,b,info);}ex.advance();}
            if(progress!=null)progress.update(99);
        }finally{ex.release();}
    }

    private static final class GlFrames implements AutoCloseable {
        private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
        private EGLContext context=EGL14.EGL_NO_CONTEXT;
        private EGLSurface window=EGL14.EGL_NO_SURFACE;
        private SurfaceTexture texture;
        private Surface decoderSurface;
        private Surface encoderInput;
        private HandlerThread callbacks;
        private final Object monitor=new Object();private boolean available;
        private final float[] transform=new float[16];
        private FloatBuffer vertices,videoCoords,overlayCoords;
        private int program,overlayProgram,external,overlay=-1;
        private final int width,height;
        GlFrames(Surface encoderSurface,int w,int h,float[] coords,ImageEditorView.Mark mark)throws Exception {
            width=w;height=h;encoderInput=encoderSurface;
            try {
                display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
                if(display==EGL14.EGL_NO_DISPLAY||!EGL14.eglInitialize(display,new int[2],0,new int[2],0))throw new IOException("OpenGL 初始化失败");
                EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
                int[] attrs={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,0x3142,1,EGL14.EGL_NONE};
                if(!EGL14.eglChooseConfig(display,attrs,0,configs,0,1,count,0)||count[0]==0)throw new IOException("设备不支持视频渲染表面");
                context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
                window=EGL14.eglCreateWindowSurface(display,configs[0],encoderSurface,new int[]{EGL14.EGL_NONE},0);
                if(context==EGL14.EGL_NO_CONTEXT||window==EGL14.EGL_NO_SURFACE||!EGL14.eglMakeCurrent(display,window,window,context))throw new IOException("无法创建 OpenGL 编码上下文");
                program=link("#extension GL_OES_EGL_image_external : require\nprecision mediump float; varying vec2 uv; uniform samplerExternalOES image; void main(){gl_FragColor=texture2D(image,uv);}");
                overlayProgram=link("precision mediump float; varying vec2 uv; uniform sampler2D image; void main(){gl_FragColor=texture2D(image,uv);}");
                vertices=buffer(new float[]{-1,-1,1,-1,-1,1,1,1});videoCoords=buffer(coords);overlayCoords=buffer(new float[]{0,1,1,1,0,0,1,0});
                external=makeTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
                texture=new SurfaceTexture(external);texture.setDefaultBufferSize(w,h);
                callbacks=new HandlerThread("video-frame-callback");callbacks.start();
                texture.setOnFrameAvailableListener(t->{synchronized(monitor){available=true;monitor.notifyAll();}},new Handler(callbacks.getLooper()));
                decoderSurface=new Surface(texture);
                if(mark!=null){
                    Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                    try{WatermarkPainter.draw(new Canvas(b),w,h,mark);overlay=makeTexture(GLES20.GL_TEXTURE_2D);GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,b,0);}finally{b.recycle();}
                }
                checkGl();
            }catch(Exception e){close();throw e;}
        }
        private static FloatBuffer buffer(float[] f){FloatBuffer b=ByteBuffer.allocateDirect(f.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(f).position(0);return b;}
        private static int makeTexture(int target){int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);GLES20.glBindTexture(target,ids[0]);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);return ids[0];}
        private static int shader(int type,String source)throws IOException {
            int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);int[] status=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,status,0);
            if(status[0]==0){String log=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw new IOException("视频着色器错误: "+log);}return s;
        }
        private static int link(String fragment)throws IOException {
            String vertex="attribute vec2 pos; attribute vec2 tex; uniform mat4 matrix; varying vec2 uv; void main(){gl_Position=vec4(pos,0.0,1.0);uv=(matrix*vec4(tex,0.0,1.0)).xy;}";
            int vs=shader(GLES20.GL_VERTEX_SHADER,vertex),fs=shader(GLES20.GL_FRAGMENT_SHADER,fragment),p=GLES20.glCreateProgram();
            GLES20.glAttachShader(p,vs);GLES20.glAttachShader(p,fs);GLES20.glLinkProgram(p);GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);
            int[] status=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,status,0);if(status[0]==0)throw new IOException("视频着色器链接失败");return p;
        }
        void awaitAndDraw(long ptsNanos)throws Exception {
            synchronized(monitor){long deadline=SystemClock.elapsedRealtime()+8_000;while(!available){long left=deadline-SystemClock.elapsedRealtime();if(left<=0)throw new IOException("等待视频画面超时");monitor.wait(left);MediaOps.checkCancelled();}available=false;}
            texture.updateTexImage();texture.getTransformMatrix(transform);
            GLES20.glViewport(0,0,width,height);GLES20.glClearColor(0,0,0,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            draw(program,external,GLES11Ext.GL_TEXTURE_EXTERNAL_OES,videoCoords,transform);
            if(overlay>=0){
                GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA);
                float[] identity=new float[16];android.opengl.Matrix.setIdentityM(identity,0);draw(overlayProgram,overlay,GLES20.GL_TEXTURE_2D,overlayCoords,identity);GLES20.glDisable(GLES20.GL_BLEND);
            }
            checkGl();if(!EGLExt.eglPresentationTimeANDROID(display,window,ptsNanos)||!EGL14.eglSwapBuffers(display,window))throw new IOException("提交编码画面失败");
        }
        private void draw(int p,int image,int target,FloatBuffer coords,float[] matrix){
            GLES20.glUseProgram(p);int pos=GLES20.glGetAttribLocation(p,"pos"),tex=GLES20.glGetAttribLocation(p,"tex");
            vertices.position(0);coords.position(0);GLES20.glEnableVertexAttribArray(pos);GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,vertices);
            GLES20.glEnableVertexAttribArray(tex);GLES20.glVertexAttribPointer(tex,2,GLES20.GL_FLOAT,false,0,coords);
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(p,"matrix"),1,false,matrix,0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(target,image);GLES20.glUniform1i(GLES20.glGetUniformLocation(p,"image"),0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(pos);GLES20.glDisableVertexAttribArray(tex);
        }
        private static void checkGl()throws IOException {int error=GLES20.glGetError();if(error!=GLES20.GL_NO_ERROR)throw new IOException("视频绘制失败: "+error);}
        @Override public void close(){
            if(decoderSurface!=null)decoderSurface.release();if(texture!=null)texture.release();if(callbacks!=null)callbacks.quitSafely();
            if(display!=EGL14.EGL_NO_DISPLAY){
                EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,window);
                if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
                EGL14.eglReleaseThread();EGL14.eglTerminate(display);
                display=EGL14.EGL_NO_DISPLAY;
            }
            if(encoderInput!=null){encoderInput.release();encoderInput=null;}
        }
    }
}
