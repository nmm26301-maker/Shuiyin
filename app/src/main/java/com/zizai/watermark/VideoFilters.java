package com.zizai.watermark;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure filter planning, shared by Android export and real FFmpeg fixture tests. */
final class VideoFilters {
    static String tempo(double speed)throws IOException {
        if(!Double.isFinite(speed)||speed<.5||speed>4)throw new IOException("倍速支持 0.5× 到 4×");
        List<String> filters=new ArrayList<>();double remaining=speed;
        while(remaining>2){filters.add("atempo=2");remaining/=2;}
        filters.add("atempo="+number(remaining));return String.join(",",filters);
    }
    static int[] region(float[] rectangle,int width,int height)throws IOException {
        if(rectangle==null||rectangle.length!=4||width<16||height<16)throw new IOException("请先框选修复区域");
        for(float f:rectangle)if(!Float.isFinite(f))throw new IOException("修复区域无效");
        float left=Math.min(rectangle[0],rectangle[2]),top=Math.min(rectangle[1],rectangle[3]);
        float right=Math.max(rectangle[0],rectangle[2]),bottom=Math.max(rectangle[1],rectangle[3]);
        if(left<0||top<0||right>1||bottom>1||right-left<.005||bottom-top<.005)throw new IOException("请框选有效的修复区域");
        int x=Math.max(2,Math.min(width-6,Math.round(left*width))),y=Math.max(2,Math.min(height-6,Math.round(top*height)));
        int edgeX=Math.max(x+4,Math.min(width-2,Math.round(right*width))),edgeY=Math.max(y+4,Math.min(height-2,Math.round(bottom*height)));
        return new int[]{x,y,edgeX-x,edgeY-y};
    }
    static String video(double speed,int[] rectangle)throws IOException {return videoRegions(speed,rectangle==null?null:new int[][]{rectangle});}
    static String videoRegions(double speed,int[][] rectangles)throws IOException {
        tempo(speed);List<String> filters=new ArrayList<>();
        if(rectangles!=null)for(int[] r:rectangles)filters.add("delogo=x="+r[0]+":y="+r[1]+":w="+r[2]+":h="+r[3]+":show=0");
        filters.add("setpts=PTS/"+number(speed));
        filters.add("scale=w='min(iw,1920)':h='min(ih,1920)':force_original_aspect_ratio=decrease:force_divisible_by=2");
        filters.add("fps=30");return String.join(",",filters);
    }
    static int[][] regions(float[][] boxes,int width,int height)throws IOException {
        boxes=RepairRegions.validate(boxes);int[][] result=new int[boxes.length][];
        for(int i=0;i<boxes.length;i++)result[i]=region(boxes[i],width,height);return result;
    }
    static List<String> exportArguments(String input,String output,double speed,int[] rectangle,boolean audio)throws IOException {
        return exportRegionArguments(input,output,speed,rectangle==null?null:new int[][]{rectangle},audio);
    }
    static List<String> exportRegionArguments(String input,String output,double speed,int[][] rectangles,boolean audio)throws IOException {
        List<String> args=new ArrayList<>();java.util.Collections.addAll(args,"-hide_banner","-nostdin","-y","-loglevel","warning","-threads","2","-filter_threads","2","-i",input,"-map","0:v:0","-vf",videoRegions(speed,rectangles));
        if(audio){java.util.Collections.addAll(args,"-map","0:a:0?","-af","asetpts=PTS/"+number(speed)+","+tempo(speed),"-c:a","aac","-b:a","160k");}
        else args.add("-an");
        java.util.Collections.addAll(args,"-c:v","libx264","-preset","veryfast","-crf","23","-pix_fmt","yuv420p","-threads","2","-map_metadata","-1","-metadata:s:v:0","rotate=0","-movflags","+faststart","-progress","pipe:1","-nostats",output);return args;
    }
    static List<String> audioArguments(String input,String output){
        return java.util.Arrays.asList("-hide_banner","-nostdin","-y","-loglevel","warning","-threads","2","-i",input,"-map","0:a:0","-vn","-af","asetpts=PTS-STARTPTS","-c:a","aac","-b:a","160k","-movflags","+faststart","-progress","pipe:1","-nostats",output);
    }
    private static String number(double value){return String.format(Locale.ROOT,"%.8f",value).replaceAll("0+$","").replaceAll("\\.$","");}
}
