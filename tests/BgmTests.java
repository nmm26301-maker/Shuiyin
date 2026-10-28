package com.zizai.watermark;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Runs production DSP and both real ONNX models; fixture audio is synthesized locally. */
public final class BgmTests {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static byte[] tone(int samples){byte[] bytes=new byte[samples*4];for(int i=0;i<samples;i++)for(int c=0;c<2;c++){
        int sample=(int)(8000*Math.sin(2*Math.PI*(c==0?440:660)*i/44100));int p=i*4+c*2;bytes[p]=(byte)sample;bytes[p+1]=(byte)(sample>>8);
    }return bytes;}
    static int sample(byte[] bytes,int i){return (short)((bytes[i]&255)|(bytes[i+1]<<8));}
    public static void main(String[] args)throws Exception {
        Path directory=Path.of(args[0]);Files.createDirectories(directory);File input=directory.resolve("identity.pcm").toFile(),output=directory.resolve("identity-output.pcm").toFile();
        float[] r=new float[4096],im=new float[4096];Random random=new Random(42);for(int i=0;i<r.length;i++)r[i]=random.nextFloat()*2-1;float[] original=r.clone();
        VocalDsp.fft(r,im,false);VocalDsp.fft(r,im,true);double worst=0;for(int i=0;i<r.length;i++)worst=Math.max(worst,Math.abs(r[i]-original[i]));check(worst<1e-5,"FFT inverse reconstruction");
        byte[] signal=tone(44100*13);Files.write(input.toPath(),signal);List<Integer> progress=new ArrayList<>();
        VocalDsp.separate(input,output,magnitude->{float[] mask=new float[magnitude.length];Arrays.fill(mask,1);return mask;},(done,total)->progress.add(done));
        byte[] reconstructed=Files.readAllBytes(output.toPath());check(reconstructed.length==signal.length,"overlap-add preserves sample count across blocks");
        double error=0,energy=0;for(int i=8192;i<signal.length-8192;i+=2){double a=sample(signal,i),b=sample(reconstructed,i);error+=(a-b)*(a-b);energy+=a*a;}
        check(error/energy<1e-6,"unity mask preserves both channels including block join");check(progress.size()==2&&progress.get(1)==2,"bounded blocks report progress");
        VocalDsp.separate(input,output,magnitude->new float[magnitude.length],null);byte[] silent=Files.readAllBytes(output.toPath());boolean zero=true;for(byte b:silent)zero&=b==0;check(zero,"zero masks remove all energy");
        try{VocalDsp.separate(input,output,magnitude->{float[] mask=new float[magnitude.length];Arrays.fill(mask,Float.NaN);return mask;},null);throw new AssertionError("NaN mask");}catch(IOException expected){checks++;}
        AtomicBoolean cancelled=new AtomicBoolean(true);JobControl.attach(cancelled);try{VocalDsp.separate(input,output,m->{throw new AssertionError("inference after cancellation");},null);throw new AssertionError("cancel ignored");}catch(InterruptedIOException expected){checks++;}finally{JobControl.detach();}
        Files.write(input.toPath(),new byte[]{1});try{VocalDsp.separate(input,output,m->m,null);throw new AssertionError("truncated PCM");}catch(IOException expected){checks++;}
        File mixed=directory.resolve("decoded.pcm").toFile(),voice=directory.resolve("actual-vocals.pcm").toFile();
        long started=System.nanoTime();try(VocalModel model=new VocalModel(new File(args[1]),new File(args[2]))){VocalDsp.separate(mixed,voice,model,null);}
        AtomicBoolean stop=new AtomicBoolean(false);JobControl.attach(stop);
        try(VocalModel model=new VocalModel(new File(args[1]),new File(args[2]))){
            Thread trigger=new Thread(()->{try{Thread.sleep(150);stop.set(true);}catch(InterruptedException ignored){}});trigger.start();long begin=System.nanoTime();
            try{model.apply(new float[2*512*1024]);throw new AssertionError("native cancellation ignored");}
            catch(Exception expected){check(stop.get(),"actual inference interrupted by cancellation flag");}
            finally{trigger.join();}
            check((System.nanoTime()-begin)/1_000_000<5000,"cancellation does not wait for complete audio processing");
        }finally{JobControl.detach();}
        byte[] before=Files.readAllBytes(mixed.toPath()),after=Files.readAllBytes(voice.toPath());check(before.length==after.length&&after.length>0,"actual neural pipeline preserves duration");
        long sum=0,difference=0;for(int i=0;i<after.length;i+=2){int a=sample(before,i),b=sample(after,i);sum+=(long)b*b;difference+=(long)(a-b)*(a-b);}
        check(sum>1_000_000,"real model output is not silent");check(difference>1_000_000,"real model changes mixed audio");
        JSONObject commands=new JSONObject().put("decode",AudioBgm.decodeArgs(directory.resolve("input.mp4").toString(),mixed.toString())).put("mux",AudioBgm.muxArgs(directory.resolve("input.mp4").toString(),voice.toString(),directory.resolve("output.mp4").toString()));
        Files.writeString(directory.resolve("commands.json"),commands.toString());
        check(AudioBgm.muxArgs("original","voice","out").contains("copy")&&!AudioBgm.muxArgs("original","voice","out").contains("-an"),"video stream preserved and vocal audio mapped");
        System.out.println("PASS local AI BGM: "+checks+" checks; real Spleeter/ONNX inference in "+((System.nanoTime()-started)/1_000_000)+" ms");
    }
}
