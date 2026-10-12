package com.zizai.watermark;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exercises the production multi-region filter with two marks and a protected subtitle. */
public final class MultiRegionTests {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static void rejects(float[][] boxes)throws Exception {try{RepairRegions.validate(boxes);throw new AssertionError("invalid boxes accepted");}catch(IOException expected){checks++;}}
    static void run(List<String> command)throws Exception {NativeProcess.run(command,Collections.emptyMap(),new AtomicBoolean(),20_000,null);}
    public static void main(String[] args)throws Exception {
        float[][] boxes={{.02f,.85f,.25f,.98f},{.82f,.85f,.98f,.98f}};
        check(Arrays.deepEquals(boxes,RepairRegions.decode(RepairRegions.encode(boxes))),"two independent templates survive round trip");
        float[][] validated=RepairRegions.validate(boxes);validated[0][0]=.8f;check(boxes[0][0]==.02f,"template snapshots are isolated");
        check(RepairRegions.decode("0.1,0.1,0.2,0.2")==null,"old one-box template requires new confirmation");
        check(RepairRegions.decode("v2;NaN,0,1,1")==null,"bad stored box rejected");
        rejects(null);rejects(new float[0][]);rejects(new float[][]{null});rejects(new float[][]{{0,0,1,1}});
        rejects(new float[][]{{.01f,.01f,.41f,.41f},{.5f,.5f,.9f,.9f}});
        float[][] nine=new float[9][];for(int i=0;i<nine.length;i++)nine[i]=new float[]{.01f,.01f,.04f,.04f};rejects(nine);
        check(RepairRegions.validate(Arrays.copyOf(nine,8)).length==8,"eight selected regions supported");
        check(RepairRegions.suggestions(false)==null,"other platforms receive no guessed boxes");
        float[][] suggested=RepairRegions.validate(RepairRegions.suggestions(true));check(suggested.length==2&&suggested[0][1]>.9&&suggested[1][1]>.9,"both suggested marks are at bottom");
        int[][] regions=VideoFilters.regions(boxes,640,360);String filters=VideoFilters.videoRegions(1,regions);
        check(filters.split("delogo=",-1).length-1==2,"both regions become actual delogo filters");
        check(!filters.contains("crop="),"video is not cropped to remove marks");
        check(!filters.contains("scale=")&&!filters.contains("fps="),"watermark repair keeps source resolution and frame rate");
        check(VideoFilters.videoRegions(2,null).contains("fps=30"),"speed conversion keeps its bounded export");
        int[][] hd=VideoFilters.regions(boxes,1280,720);check(hd[1][0]>1000,"region coordinates follow real resolution");
        int[][] tiny=VideoFilters.regions(suggested,180,320);for(int[] r:tiny)check(r[0]>=2&&r[1]>=2&&r[0]+r[2]<=178&&r[1]+r[3]<=318,"bottom-edge filter retains sampling border");
        Path folder=Paths.get(args[0]);Files.createDirectories(folder);Path input=folder.resolve("two-marks.mp4"),output=folder.resolve("repaired.mp4");
        run(Arrays.asList("ffmpeg","-hide_banner","-y","-loglevel","error","-f","lavfi","-i","color=blue:size=640x360:rate=30:duration=2","-f","lavfi","-i","sine=frequency=440:sample_rate=48000:duration=2","-vf","drawbox=x=20:y=312:w=132:h=32:color=white:t=fill,drawbox=x=530:y=312:w=85:h=32:color=white:t=fill,drawbox=x=235:y=240:w=170:h=25:color=yellow:t=fill","-c:v","libx264","-threads","2","-crf","18","-c:a","aac",input.toString()));
        List<String> command=new ArrayList<>();command.add("ffmpeg");command.addAll(VideoFilters.exportRegionArguments(input.toString(),output.toString(),1,regions,true));run(command);
        check(Files.size(output)>1000,"real two-region repair creates video");
        // Exercise the same production planner with eight distinct small logos.
        float[][] eight=new float[8][];StringBuilder draw=new StringBuilder();for(int i=0;i<8;i++){int x=20+i*72;eight[i]=new float[]{(x-3)/640f,307/360f,(x+39)/640f,347/360f};if(i>0)draw.append(',');draw.append("drawbox=x=").append(x).append(":y=312:w=36:h=30:color=white:t=fill");}
        Path marked8=folder.resolve("eight-marks.mp4"),repaired8=folder.resolve("eight-repaired.mp4");
        run(Arrays.asList("ffmpeg","-hide_banner","-y","-loglevel","error","-f","lavfi","-i","color=blue:size=640x360:rate=30:duration=1","-vf",draw.toString(),"-c:v","libx264","-threads","2",marked8.toString()));
        command=new ArrayList<>();command.add("ffmpeg");command.addAll(VideoFilters.exportRegionArguments(marked8.toString(),repaired8.toString(),1,VideoFilters.regions(eight,640,360),false));run(command);check(Files.size(repaired8)>1000,"eight-region filter runs successfully");
        Path highRate=folder.resolve("high-rate.mp4"),highRateRepaired=folder.resolve("high-rate-repaired.mp4");
        run(Arrays.asList("ffmpeg","-hide_banner","-y","-loglevel","error","-f","lavfi","-i","color=blue:size=960x540:rate=60:duration=0.5","-vf","drawbox=x=20:y=480:w=100:h=32:color=white:t=fill","-c:v","libx264","-threads","2",highRate.toString()));
        command=new ArrayList<>();command.add("ffmpeg");command.addAll(VideoFilters.exportRegionArguments(highRate.toString(),highRateRepaired.toString(),1,VideoFilters.regions(new float[][]{{.015f,.88f,.14f,.97f}},960,540),false));run(command);
        check(Files.size(highRateRepaired)>1000,"high-frame-rate repair creates video");
        System.out.println("PASS multi-region: "+checks+" checks; production filters used for 2 and 8 logos");
    }
}
