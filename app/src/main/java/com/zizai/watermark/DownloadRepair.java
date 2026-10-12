package com.zizai.watermark;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import java.io.IOException;

/** Automatic OCR by default; manual templates remain available as an explicit mode. */
final class DownloadRepair {
    interface Choice {float[][] select(Bitmap preview,float[][] suggestions)throws Exception;}
    interface Status {void update(String message);}
    static final class Report {String note="未执行去水印";int regions;}
    static Uri repair(Context context,Uri pending,LinkExtractor.Item item,Choice choice,Status status,Report report)throws Exception {
        if(item.kind==LinkExtractor.Kind.AUDIO){report.note="音频已保存";return pending;}
        android.content.SharedPreferences settings=context.getSharedPreferences("download_repair",0);
        if(!settings.getBoolean("enabled",true)){report.note="去水印已关闭";return pending;}
        if(item.kind==LinkExtractor.Kind.IMAGE&&"image/gif".equals(context.getContentResolver().getType(pending))){report.note="动态 GIF 已保留，未去水印";return pending;}
        boolean automatic=settings.getBoolean("automatic",true);
        if(status!=null)status.update(automatic?"准备自动识别水印…":"准备下载素材的去水印预览…");
        Bitmap image=null,mask=null,repaired=null;
        try {
            if(item.kind==LinkExtractor.Kind.IMAGE||!automatic)image=item.kind==LinkExtractor.Kind.IMAGE?ImageOps.readBitmap(context,pending,2560):MediaOps.frame(context,pending,0);
            JobControl.check();String key=image==null?"":RepairTemplate.key(item,image.getWidth(),image.getHeight());
            float[][] boxes;boolean remember=false;
            if(automatic){
                boxes=item.kind==LinkExtractor.Kind.VIDEO?WatermarkDetector.video(context,pending,status==null?null:status::update):WatermarkDetector.image(context,image,status==null?null:status::update);
                if(boxes==null){report.note="未识别到可靠水印，已保留原画面";return pending;}
            }else{
                boxes=RepairRegions.decode(settings.getString("multi2|"+key,""));
                if(boxes==null){boolean xhs=item.kind==LinkExtractor.Kind.VIDEO&&(XhsParser.matches(item.referer)||XhsParser.matches(item.url));boxes=choice.select(image,RepairRegions.suggestions(xhs));remember=boxes!=null;}
            }
            JobControl.check();if(boxes==null){report.note="已跳过去水印";return pending;}boxes=RepairRegions.validate(boxes);
            Uri output;
            if(item.kind==LinkExtractor.Kind.VIDEO){output=LocalVideoProcessor.exportRegions(context,pending,1,true,boxes,false,status==null?null:status::update);}
            else {
                String mime=context.getContentResolver().getType(pending);
                if("image/gif".equals(mime))throw new IOException("动态 GIF 暂不支持自动区域修复；可关闭保存去水印后保存原图");
                if(status!=null)status.update("正在修复图片水印区域…");
                mask=Bitmap.createBitmap(image.getWidth(),image.getHeight(),Bitmap.Config.ARGB_8888);
                Paint p=new Paint();p.setColor(Color.WHITE);Canvas canvas=new Canvas(mask);for(float[] box:boxes)canvas.drawRect(box[0]*image.getWidth(),box[1]*image.getHeight(),box[2]*image.getWidth(),box[3]*image.getHeight(),p);
                repaired=ImageOps.inpaint(image,mask);JobControl.check();
                output=MediaOps.saveBitmap(context,repaired,"repair_"+System.currentTimeMillis()+".png",false);
            }
            // Store only successful choices; failed exports must not install a bad template.
            if(remember)settings.edit().putString("multi2|"+key,RepairRegions.encode(boxes)).apply();
            report.regions=boxes.length;report.note=(automatic?"自动识别并修复 ":"已修复 ")+boxes.length+" 处水印，请检查效果";return output;
        } finally {if(image!=null)image.recycle();if(mask!=null)mask.recycle();if(repaired!=null)repaired.recycle();}
    }
}
