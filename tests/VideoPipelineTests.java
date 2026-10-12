package com.zizai.watermark;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Uses production filter planner and process runner, with host FFmpeg fixtures. */
public final class VideoPipelineTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void run(List<String> args)throws Exception {NativeProcess.run(args,Collections.emptyMap(),new AtomicBoolean(),20_000,null);}
    private static void export(Path input,Path output,double speed,int[] rectangle,boolean audio)throws Exception {
        List<String> command=new ArrayList<>();command.add("ffmpeg");command.addAll(VideoFilters.exportArguments(input.toString(),output.toString(),speed,rectangle,audio));run(command);check(Files.size(output)>500,"nonempty output");
    }
    public static void main(String[] args)throws Exception {
        Path folder=Paths.get(args[0]).toAbsolutePath();Files.createDirectories(folder);
        for(double speed:new double[]{.5,.75,1.25,1.5,2,3,4}){
            String chain=VideoFilters.tempo(speed);double product=1;for(String part:chain.split(",")){double factor=Double.parseDouble(part.split("=")[1]);check(factor>=.5&&factor<=2,"tempo quality bounds");product*=factor;}check(Math.abs(product-speed)<.000001,"chained tempo product");
        }
        for(double bad:new double[]{0,-1,5,Double.NaN,Double.POSITIVE_INFINITY}){try{VideoFilters.tempo(bad);throw new AssertionError("invalid speed accepted");}catch(IOException expected){checks++;}}
        int[] edge=VideoFilters.region(new float[]{0,0,1,1},320,180);check(edge[0]>=2&&edge[1]>=2&&edge[0]+edge[2]<=318&&edge[1]+edge[3]<=178,"repair has surrounding border");
        int[] reverse=VideoFilters.region(new float[]{.4f,.6f,.1f,.2f},320,180);check(reverse[0]==32&&reverse[1]==36&&reverse[2]==96&&reverse[3]==72,"normalized region across frame dimensions");
        try{VideoFilters.region(new float[]{.2f,.2f,.2f,.2f},320,180);throw new AssertionError("empty region accepted");}catch(IOException expected){checks++;}
        Path source=folder.resolve("source.mp4"),silent=folder.resolve("silent.mp4"),delay=folder.resolve("delay.mp4"),logo=folder.resolve("logo.mp4");
        run(Arrays.asList("ffmpeg","-hide_banner","-y","-loglevel","error","-f","lavfi","-i","testsrc2=size=320x180:rate=30:duration=8","-f","lavfi","-i","sine=frequency=440:sample_rate=48000:duration=8","-c:v","libx264","-threads","2","-c:a","aac","-pix_fmt","yuv420p",source.toString()));
        run(Arrays.asList("ffmpeg","-hide_banner","-y","-loglevel","error","-f","lavfi","-i","testsrc2=size=320x180:rate=30:duration=8","-an","-c:v","libx264","-threads","2",silent.toString()));
        run(Arrays.asList("ffmpeg","-hide_banner","-y","-loglevel","error","-f","lavfi","-i","testsrc2=size=320x180:rate=30:duration=8","-itsoffset","0.5","-f","lavfi","-i","sine=frequency=440:sample_rate=48000:duration=8","-c:v","libx264","-threads","2","-c:a","aac",delay.toString()));
        run(Arrays.asList("ffmpeg","-hide_banner","-y","-loglevel","error","-f","lavfi","-i","color=blue:size=320x180:rate=30:duration=2","-vf","drawbox=x=50:y=30:w=60:h=20:color=white:t=fill","-c:v","libx264","-threads","2","-crf","18",logo.toString()));
        for(double speed:new double[]{.5,1.5,2,3,4})export(source,folder.resolve("speed-"+speed+".mp4"),speed,null,true);
        export(silent,folder.resolve("speed-silent.mp4"),2,null,false);
        export(delay,folder.resolve("speed-delay.mp4"),2,null,true);
        export(logo,folder.resolve("repaired.mp4"),1,VideoFilters.region(new float[]{48/320f,28/180f,112/320f,52/180f},320,180),false);
        List<String> audioCommand=new ArrayList<>();audioCommand.add("ffmpeg");audioCommand.addAll(VideoFilters.audioArguments(source.toString(),folder.resolve("extracted.m4a").toString()));run(audioCommand);check(Files.size(folder.resolve("extracted.m4a"))>1000,"standalone native audio conversion");
        AtomicBoolean cancel=new AtomicBoolean();long started=System.currentTimeMillis();
        try{NativeProcess.run(Arrays.asList("python3","-u","-c","import time; print('READY'); time.sleep(20)"),Collections.emptyMap(),cancel,30_000,line->cancel.set(true));throw new AssertionError("native cancel ignored");}catch(InterruptedIOException expected){checks++;}
        check(System.currentTimeMillis()-started<3000,"cancel actually terminates process");
        String next=NativeProcess.run(Arrays.asList("python3","-c","print('NEXT')"),Collections.emptyMap(),new AtomicBoolean(),3000,null);check(next.contains("NEXT"),"following job independent of previous cancellation");
        try{NativeProcess.run(Arrays.asList("python3","-c","import time; time.sleep(20)"),Collections.emptyMap(),new AtomicBoolean(),200,null);throw new AssertionError("timeout ignored");}catch(IOException expected){checks++;}
        try{NativeProcess.run(Arrays.asList("python3","-c","import sys; print('ERROR'); sys.exit(7)"),Collections.emptyMap(),new AtomicBoolean(),3000,null);throw new AssertionError("failed exit accepted");}catch(IOException expected){check(expected.getMessage().contains("ERROR"),"failure log retained");}
        System.out.println("PASS video pipeline: "+checks+" checks; actual FFmpeg outputs, cancel, timeout, next-job isolation");
    }
}
