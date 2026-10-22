package com.zizai.watermark;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import java.io.IOException;

/** Local post-download repair with per-platform/type/aspect remembered user selection. */
final class DownloadRepair {
    interface Choice {float[][] select(Bitmap preview,float[][] suggestions)throws Exception;}
    interface Status {void update(String message);}
    static Uri repair(Context context,Uri pending,LinkExtractor.Item item,Choice choice,Status status)throws Exception {
        if(item.kind==LinkExtractor.Kind.AUDIO||!context.getSharedPreferences("download_repair",0).getBoolean("enabled",true))return pending;
        if(status!=null)status.update("准备下载素材的去水印预览…");
        Bitmap image=null,mask=null,repaired=null;
        try {
            image=item.kind==LinkExtractor.Kind.IMAGE?ImageOps.readBitmap(context,pending,2560):MediaOps.frame(context,pending,0);
            JobControl.check();String key=RepairTemplate.key(item,image.getWidth(),image.getHeight());
            android.content.SharedPreferences settings=context.getSharedPreferences("download_repair",0);
            float[][] boxes=RepairRegions.decode(settings.getString("multi2|"+key,""));boolean remember=false;
            if(boxes==null){boolean xhs=XhsParser.matches(item.source)||XhsParser.matches(item.referer)||XhsParser.matches(item.url);boxes=choice.select(image,RepairRegions.suggestions(xhs,item.kind==LinkExtractor.Kind.IMAGE));remember=boxes!=null;}
            JobControl.check();if(boxes==null)return pending;boxes=RepairRegions.validate(boxes);
            Uri output;
            if(item.kind==LinkExtractor.Kind.VIDEO){output=LocalVideoProcessor.exportRegions(context,pending,1,true,boxes,status==null?null:status::update);}
            else {
                String mime=context.getContentResolver().getType(pending);
                if("image/gif".equals(mime))throw new IOException("动态 GIF 暂不支持自动区域修复；可关闭保存去水印后保存原图");
                if(status!=null)status.update("正在修复图片水印区域…");
                mask=Bitmap.createBitmap(image.getWidth(),image.getHeight(),Bitmap.Config.ARGB_8888);
                Paint p=new Paint();p.setColor(Color.WHITE);Canvas canvas=new Canvas(mask);for(float[] box:boxes)canvas.drawRect(box[0]*image.getWidth(),box[1]*image.getHeight(),box[2]*image.getWidth(),box[3]*image.getHeight(),p);
                repaired=ImageOps.inpaint(image,mask);JobControl.check();
                output=MediaOps.saveBitmap(context,repaired,"repair_"+System.currentTimeMillis()+".png");
            }
            // Store only successful choices; failed exports must not install a bad template.
            if(remember)settings.edit().putString("multi2|"+key,RepairRegions.encode(boxes)).apply();return output;
        } finally {if(image!=null)image.recycle();if(mask!=null)mask.recycle();if(repaired!=null)repaired.recycle();}
    }
}
