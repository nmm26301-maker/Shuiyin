package com.zizai.watermark;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs the exact production JNA binding and chunk planner with the real UVR model. */
public final class SourceSeparatorSmoke {
    public static void main(String[] args)throws Exception {
        File input=new File(args[2]),output=new File(args[3]);int[] chunks={0};AtomicBoolean cancelled=new AtomicBoolean();
        try(SourceSeparator separator=new SourceSeparator(args[0],args[1])) {
            separator.separate(input,output,(done,total)->{chunks[0]++;System.out.println("UVR "+done+" / "+total);});
            if(output.length()!=input.length()||chunks[0]<2)throw new AssertionError("duration/chunk mismatch");
            byte[] raw=Files.readAllBytes(output.toPath());ByteBuffer b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);double energy=0;
            while(b.hasRemaining()){float x=b.getFloat();if(!Float.isFinite(x)||Math.abs(x)>1)throw new AssertionError("invalid sample");energy+=x*x;}
            if(energy<1e-4||java.util.Arrays.equals(raw,Files.readAllBytes(input.toPath())))throw new AssertionError("output silent or unchanged");
            JobControl.attach(cancelled);cancelled.set(true);File target=new File(output.getParentFile(),"cancelled.f32");
            try{separator.separate(input,target,null);throw new AssertionError("cancel ignored");}catch(InterruptedIOException expected){if(target.exists())throw new AssertionError("cancel left partial output");}finally{JobControl.detach();}
            System.out.println("PASS real UVR model: "+chunks[0]+" chunks, exact duration, finite non-silent vocals, cancellation cleanup");
        }
    }
}
