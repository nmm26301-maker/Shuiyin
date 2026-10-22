package com.zizai.watermark;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Multiple chosen boxes. Legacy single templates require confirmation again. */
final class RepairRegions {
    static final int MAX=8;
    static float[][] validate(float[][] regions)throws IOException {
        if(regions==null||regions.length==0)throw new IOException("请至少框选一处水印");
        if(regions.length>MAX)throw new IOException("最多同时处理 8 处水印");
        float[][] copy=new float[regions.length][];float area=0;
        for(int i=0;i<regions.length;i++){copy[i]=RepairTemplate.validate(regions[i]);area+=(copy[i][2]-copy[i][0])*(copy[i][3]-copy[i][1]);}
        if(area>.30f)throw new IOException("总选区过大，请缩小水印框，保留主体和字幕");
        return copy;
    }
    static String encode(float[][] regions)throws IOException {
        List<String> parts=new ArrayList<>();for(float[] r:validate(regions))parts.add(RepairTemplate.encode(r));
        return "v2;"+String.join(";",parts);
    }
    static float[][] decode(String value){
        try{if(!value.startsWith("v2;"))return null;String[] parts=value.substring(3).split(";",-1);float[][] regions=new float[parts.length][];
            for(int i=0;i<parts.length;i++)regions[i]=RepairTemplate.decode(parts[i]);return validate(regions);
        }catch(Exception e){return null;}
    }
    static float[][] suggestions(boolean xhsVideo){return suggestions(xhsVideo,false);}
    static float[][] suggestions(boolean xhs,boolean photo){
        // User-confirmed suggestion, not automatic watermark detection.
        if(xhs&&photo)return new float[][]{{.60f,.86f,.995f,.995f}};
        return xhs?new float[][]{{.018f,.94f,.25f,.995f},{.845f,.94f,.988f,.995f}}:null;
    }
}
