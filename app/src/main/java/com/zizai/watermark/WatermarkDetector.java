package com.zizai.watermark;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import com.googlecode.tesseract.android.*;
import java.io.*;
import java.util.*;

/** On-device OCR of several frames; classification and protected subtitle region are pure Java. */
final class WatermarkDetector {
    interface Status {void update(String message);}
    static float[][] image(Context context,Bitmap image,Status status)throws Exception {
        try(Reader reader=new Reader(context)){if(status!=null)status.update("自动识别平台标识和博主名字…");Bitmap scaled=scale(image);try{int[] bounds=bounds(scaled);return AutoWatermarkRules.detect(Collections.singletonList(reader.read(scaled)),scaled.getWidth(),scaled.getHeight(),bounds[0],bounds[1]);}finally{if(scaled!=image)scaled.recycle();}}
    }
    static float[][] video(Context context,Uri input,Status status)throws Exception {
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();
        try(Reader reader=new Reader(context)){
            retriever.setDataSource(context,input);long duration=0;try{duration=Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));}catch(Exception ignored){}
            long[] times=duration>1000?new long[]{Math.min(200,duration/10),duration/3,duration*2/3,Math.max(0,duration-250)}:new long[]{0};
            ArrayList<List<AutoWatermarkRules.Text>> frames=new ArrayList<>();int width=0,height=0,top=0,bottom=0;
            int sourceWidth=0,sourceHeight=0;
            try{sourceWidth=Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));sourceHeight=Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));}catch(Exception ignored){}
            for(int i=0;i<times.length;i++){
                JobControl.check();if(status!=null)status.update("自动识别水印 · 画面 "+(i+1)+" / "+times.length);
                int max=Math.max(sourceWidth,sourceHeight);
                Bitmap frame=max>1600?retriever.getScaledFrameAtTime(times[i]*1000,MediaMetadataRetriever.OPTION_CLOSEST,Math.max(1,sourceWidth*1600/max),Math.max(1,sourceHeight*1600/max)):retriever.getFrameAtTime(times[i]*1000,MediaMetadataRetriever.OPTION_CLOSEST);if(frame==null)continue;
                Bitmap scaled=scale(frame);try{if(width==0){width=scaled.getWidth();height=scaled.getHeight();int[] bounds=bounds(scaled);top=bounds[0];bottom=bounds[1];}if(scaled.getWidth()==width&&scaled.getHeight()==height)frames.add(reader.read(scaled));}
                finally{if(scaled!=frame)scaled.recycle();frame.recycle();}
            }
            JobControl.check();return AutoWatermarkRules.detect(frames,width,height,top,bottom);
        }finally{retriever.release();}
    }
    private static Bitmap scale(Bitmap b){int max=Math.max(b.getWidth(),b.getHeight());if(max<=1600)return b;float factor=1600f/max;return Bitmap.createScaledBitmap(b,Math.max(1,Math.round(b.getWidth()*factor)),Math.max(1,Math.round(b.getHeight()*factor)),true);}
    static int[] bounds(Bitmap b){int w=b.getWidth(),h=b.getHeight(),top=0,bottom=h;for(int y=0;y<h/5;y++){if(!blackRow(b,y,w))break;top=y+1;}for(int y=h-1;y>h*4/5;y--){if(!blackRow(b,y,w))break;bottom=y;}return new int[]{top,bottom};}
    private static boolean blackRow(Bitmap b,int y,int width){int black=0,total=0;for(int x=0;x<width;x+=Math.max(1,width/120)){int c=b.getPixel(x,y);if(((c>>16)&255)<14&&((c>>8)&255)<14&&(c&255)<14)black++;total++;}return black>=total*.96;}
    private static final class Reader implements AutoCloseable {
        final TessBaseAPI api=new TessBaseAPI();
        Reader(Context c)throws Exception {
            try {
            File eng=AutoModelStore.model(c,"tessdata/eng.traineddata","7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2");
            AutoModelStore.model(c,"tessdata/chi_sim.traineddata","a5fcb6f0db1e1d6d8522f39db4e848f05984669172e584e8d76b6b3141e1f730");
            if(!api.init(eng.getParentFile().getParent(),"chi_sim+eng",TessBaseAPI.OEM_LSTM_ONLY))throw new IOException("自动水印文字模型无法加载");
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT);
            }catch(Exception|Error error){api.recycle();throw error;}
        }
        List<AutoWatermarkRules.Text> read(Bitmap image)throws Exception {
            JobControl.check();api.setImage(image);api.getUTF8Text();ResultIterator it=api.getResultIterator();ArrayList<AutoWatermarkRules.Text> found=new ArrayList<>();
            if(it!=null)try{it.begin();do{JobControl.check();int level=TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE;int[] r=it.getBoundingBox(level);String text=it.getUTF8Text(level);if(r!=null&&r.length==4&&text!=null)found.add(new AutoWatermarkRules.Text(text,it.confidence(level),r[0],r[1],r[2],r[3]));}while(it.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE));}finally{it.delete();}
            // Word boxes also recover separate corner marks when OCR groups a whole bottom row.
            it=api.getResultIterator();
            if(it!=null)try{it.begin();do{JobControl.check();int level=TessBaseAPI.PageIteratorLevel.RIL_WORD;int[] r=it.getBoundingBox(level);String text=it.getUTF8Text(level);if(r!=null&&r.length==4&&text!=null)found.add(new AutoWatermarkRules.Text(text,it.confidence(level),r[0],r[1],r[2],r[3]));}while(it.next(TessBaseAPI.PageIteratorLevel.RIL_WORD));}finally{it.delete();}
            api.clear();return found;
        }
        public void close(){api.recycle();}
    }
}
