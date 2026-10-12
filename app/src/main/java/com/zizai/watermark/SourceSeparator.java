package com.zizai.watermark;
import com.sun.jna.*;
import java.io.*;
import java.nio.*;
import java.util.*;

/** Thin, bounded-memory binding to the upstream sherpa-onnx source-separation C API. */
final class SourceSeparator implements AutoCloseable {
    interface Api extends Library {
        Pointer SherpaOnnxCreateOfflineSourceSeparation(Config config);
        void SherpaOnnxDestroyOfflineSourceSeparation(Pointer engine);
        Pointer SherpaOnnxOfflineSourceSeparationProcess(Pointer engine,Pointer channels,int channelsCount,int count,int rate);
        void SherpaOnnxDestroySourceSeparationOutput(Pointer output);
        int SherpaOnnxOfflineSourceSeparationGetOutputSampleRate(Pointer engine);
    }
    @Structure.FieldOrder({"vocals","accompaniment"}) public static class Spleeter extends Structure {public String vocals="",accompaniment="";}
    @Structure.FieldOrder({"model"}) public static class Uvr extends Structure {public String model="";}
    @Structure.FieldOrder({"spleeter","uvr","num_threads","debug","provider"}) public static class Model extends Structure {public Spleeter spleeter=new Spleeter();public Uvr uvr=new Uvr();public int num_threads=2,debug=0;public String provider="cpu";}
    @Structure.FieldOrder({"model"}) public static class Config extends Structure {public Model model=new Model();}
    @Structure.FieldOrder({"samples","num_channels","n"}) public static class Stem extends Structure {public Pointer samples;public int num_channels,n;public Stem(Pointer p){super(p);read();}}
    @Structure.FieldOrder({"stems","num_stems","sample_rate"}) public static class Output extends Structure {public Pointer stems;public int num_stems,sample_rate;public Output(Pointer p){super(p);read();}}
    interface Progress {void update(long completed,long total)throws Exception;}
    static final int RATE=44100,CHANNELS=2,CORE=6*RATE,CONTEXT=RATE;
    private final Api api;private Pointer engine;
    SourceSeparator(String library,String model)throws IOException {
        api=Native.load(library,Api.class);Config config=new Config();config.model.uvr.model=model;config.write();
        engine=api.SherpaOnnxCreateOfflineSourceSeparation(config);
        if(engine==null)throw new IOException("人声分离模型无法加载");
        if(api.SherpaOnnxOfflineSourceSeparationGetOutputSampleRate(engine)!=RATE){close();throw new IOException("人声模型采样率不匹配");}
    }
    /** PCM stereo float32 -> PCM stereo float32. Include context and trim it, preserving exact length. */
    void separate(File source,File target,Progress progress)throws Exception {
        if(engine==null)throw new IOException("人声分离组件已关闭");
        long bytes=source.length();if(bytes==0||bytes%8!=0)throw new IOException("音频数据不完整");long frames=bytes/8;
        if(frames>RATE*60L*30)throw new IOException("去 BGM 当前支持 30 分钟以内的视频");
        try(RandomAccessFile in=new RandomAccessFile(source,"r");OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){
            for(long start=0;start<frames;start+=CORE){
                JobControl.check();long from=Math.max(0,start-CONTEXT),to=Math.min(frames,start+CORE+CONTEXT);int n=(int)(to-from),take=(int)Math.min(CORE,frames-start),offset=(int)(start-from);
                byte[] raw=new byte[n*8];in.seek(from*8);in.readFully(raw);ByteBuffer buf=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);float[][] samples={new float[n],new float[n]};
                for(int i=0;i<n;i++)for(int c=0;c<2;c++){float v=buf.getFloat();if(!Float.isFinite(v))throw new IOException("音频含无效采样");samples[c][i]=Math.max(-1,Math.min(1,v));}
                try(Memory left=new Memory(n*4L);Memory right=new Memory(n*4L);Memory ptrs=new Memory(Native.POINTER_SIZE*2L)){
                    left.write(0,samples[0],0,n);right.write(0,samples[1],0,n);ptrs.setPointer(0,left);ptrs.setPointer(Native.POINTER_SIZE,right);
                    Pointer result=api.SherpaOnnxOfflineSourceSeparationProcess(engine,ptrs,2,n,RATE);
                    if(result==null)throw new IOException("人声分离未返回音频");
                    try{
                        JobControl.check();
                        Output output=new Output(result);if(output.num_stems<1||output.num_stems>8||output.sample_rate!=RATE||output.stems==null)throw new IOException("人声分离输出无效");
                        // Upstream UVR returns vocals first, accompaniment second.
                        Stem vocals=new Stem(output.stems);if(vocals.num_channels!=2||vocals.n<offset+take||vocals.samples==null)throw new IOException("分离音频长度或声道错误");
                        float[] a=vocals.samples.getPointer(0).getFloatArray(offset*4L,take),b=vocals.samples.getPointer(Native.POINTER_SIZE).getFloatArray(offset*4L,take);
                        byte[] encoded=new byte[take*8];ByteBuffer dest=ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
                        for(int i=0;i<take;i++){if(!Float.isFinite(a[i])||!Float.isFinite(b[i]))throw new IOException("分离音频采样无效");dest.putFloat(Math.max(-1,Math.min(1,a[i])));dest.putFloat(Math.max(-1,Math.min(1,b[i])));}out.write(encoded);
                    }finally{api.SherpaOnnxDestroySourceSeparationOutput(result);}
                }
                JobControl.check();if(progress!=null)progress.update(Math.min(frames,start+take),frames);
            }
        }catch(Exception|Error e){target.delete();throw e;}
        if(target.length()!=bytes){target.delete();throw new IOException("去 BGM 输出长度不完整");}
    }
    public void close(){if(engine!=null){api.SherpaOnnxDestroyOfflineSourceSeparation(engine);engine=null;}}
}
