package com.zizai.watermark;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

/** Normalized, explicitly chosen repair boxes; never guesses a watermark location. */
final class RepairTemplate {
    static String key(LinkExtractor.Item item,int width,int height) {
        String origin=item.source.isEmpty()?(item.referer.isEmpty()?item.url:item.referer):item.source;
        String platform=PlatformRoutes.name(origin);
        if(platform.equals("网页"))try{platform=new URI(origin).getHost();}catch(Exception ignored){}
        return platform+"|"+item.kind+"|"+Math.round(width*100.0/Math.max(1,height));
    }
    static float[] validate(float[] r)throws IOException {
        if(r==null||r.length!=4)throw new IOException("请先框选水印位置");
        for(float f:r)if(!Float.isFinite(f))throw new IOException("水印选区无效");
        if(r[0]<0||r[1]<0||r[2]>1||r[3]>1||r[2]-r[0]<.005f||r[3]-r[1]<.005f)
            throw new IOException("请框选有效的水印位置");
        if((r[2]-r[0])*(r[3]-r[1])>.20f)throw new IOException("选区过大，请只框选水印，避免损伤主体画面");
        return r.clone();
    }
    static String encode(float[] r)throws IOException {r=validate(r);return String.format(Locale.ROOT,"%.6f,%.6f,%.6f,%.6f",r[0],r[1],r[2],r[3]);}
    static float[] decode(String value){try{String[] parts=value.split(",",-1);if(parts.length!=4)return null;float[] r=new float[4];for(int i=0;i<4;i++)r[i]=Float.parseFloat(parts[i]);return validate(r);}catch(Exception e){return null;}}
}
