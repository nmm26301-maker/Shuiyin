package com.zizai.watermark;

import ai.onnxruntime.*;
import java.io.*;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real two-model Wiener masking, using pinned ONNX weights on the local CPU. */
final class VocalModel implements VocalDsp.Mask,AutoCloseable {
    private final OrtEnvironment environment=OrtEnvironment.getEnvironment();
    private final OrtSession vocals,accompaniment;private final OrtSession.RunOptions run=new OrtSession.RunOptions();
    private final AtomicBoolean active=new AtomicBoolean(true);private final Thread cancel;
    VocalModel(File vocalFile,File backingFile)throws Exception {
        OrtSession v=null,a=null;
        try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()){
            options.setIntraOpNumThreads(2);options.setInterOpNumThreads(1);options.setMemoryPatternOptimization(false);options.setCPUArenaAllocator(false);
            v=environment.createSession(vocalFile.getAbsolutePath(),options);a=environment.createSession(backingFile.getAbsolutePath(),options);
        }catch(Exception|LinkageError|OutOfMemoryError e){if(v!=null)v.close();if(a!=null)a.close();run.close();throw e;}
        vocals=v;accompaniment=a;AtomicBoolean flag=JobControl.flag();
        cancel=new Thread(()->{try{while(active.get()){if(flag!=null&&flag.get()){run.setTerminate(true);return;}Thread.sleep(100);}}catch(InterruptedException ignored){}catch(OrtException ignored){}},"vocal-cancellation");cancel.setDaemon(true);cancel.start();
    }
    public float[] apply(float[] magnitude)throws Exception {
        JobControl.check();float[] voice,backing;
        try(OnnxTensor tensor=OnnxTensor.createTensor(environment,FloatBuffer.wrap(magnitude),new long[]{2,1,512,1024})){
            voice=infer(vocals,tensor);JobControl.check();backing=infer(accompaniment,tensor);
        }
        if(voice.length!=magnitude.length||backing.length!=voice.length)throw new IOException("人声模型输出尺寸无效");
        for(int i=0;i<voice.length;i++){
            if((i&65535)==0)JobControl.check();double v=voice[i],a=backing[i];voice[i]=(float)((v*v+5e-11)/(v*v+a*a+1e-10));
        }
        return voice;
    }
    private float[] infer(OrtSession session,OnnxTensor tensor)throws Exception {
        try(OrtSession.Result result=session.run(Collections.singletonMap("x",tensor),run)){
            FloatBuffer value=((OnnxTensor)result.get(0)).getFloatBuffer();float[] out=new float[value.remaining()];value.get(out);return out;
        }
    }
    public void close()throws Exception {active.set(false);cancel.interrupt();cancel.join();run.close();vocals.close();accompaniment.close();}
}
