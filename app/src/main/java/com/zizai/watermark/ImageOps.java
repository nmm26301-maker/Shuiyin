package com.zizai.watermark;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.IOException;

/** Lightweight CPU-only photo tools. No network requests or model files. */
public class ImageOps {
    public static Bitmap readBitmap(Context context, Uri uri, int maxSide) throws IOException {
        if(Build.VERSION.SDK_INT>=28) {
            ImageDecoder.Source src=ImageDecoder.createSource(context.getContentResolver(),uri);
            return ImageDecoder.decodeBitmap(src,(decoder,info,source)->{
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                int w=info.getSize().getWidth(),h=info.getSize().getHeight();
                int sample=Math.max(1,(int)Math.ceil((double)Math.max(w,h)/maxSide));
                decoder.setTargetSampleSize(sample);
            });
        }
        throw new IOException("需要 Android 9 或更高版本");
    }
    public static Bitmap watermark(Bitmap source,ImageEditorView.Mark mark) {
        Bitmap output=source.copy(Bitmap.Config.ARGB_8888,true);
        Canvas canvas=new Canvas(output);
        WatermarkPainter.draw(canvas,source.getWidth(),source.getHeight(),mark);
        return output;
    }
    /**
     * Nearest-source fill followed by masked neighborhood smoothing.
     * Effective on small marks; does not synthesize complex textures or faces.
     */
    public static Bitmap inpaint(Bitmap bitmap, Bitmap mask) {
        if(mask.getWidth()!=bitmap.getWidth() || mask.getHeight()!=bitmap.getHeight())
            throw new IllegalArgumentException("修复区域尺寸不匹配");
        int w=bitmap.getWidth(),h=bitmap.getHeight(),n=w*h;
        int[] source=new int[n], alpha=new int[n];
        bitmap.getPixels(source,0,w,0,0,w,h);
        mask.getPixels(alpha,0,w,0,0,w,h);
        boolean[] covered=new boolean[n];
        int count=0;
        for(int i=0;i<n;i++){covered[i]=((alpha[i]>>>24)&255)>30;if(covered[i])count++;}
        if(count==0) throw new IllegalArgumentException("请先涂抹或框选修复区域");
        if(count==n)throw new IllegalArgumentException("不能擦除整张图片");
        int[] output=source.clone();
        int[] queue=new int[n];
        // Seed only the masked boundary, with neighboring original colors.
        int tail=0,head=0;
        boolean[] assigned=new boolean[n];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            int i=y*w+x;
            if(!covered[i])continue;
            int color=0,neighbors=0;
            if(x>0&&!covered[i-1]){color=source[i-1];neighbors++;}
            if(x<w-1&&!covered[i+1]){color=source[i+1];neighbors++;}
            if(y>0&&!covered[i-w]){color=source[i-w];neighbors++;}
            if(y<h-1&&!covered[i+w]){color=source[i+w];neighbors++;}
            if(neighbors>0) {output[i]=color;assigned[i]=true;queue[tail++]=i;}
        }
        if(tail==0)throw new IllegalArgumentException("未找到有效的修复边界");
        // Multi-source breadth-first expansion inside only the painted mask.
        while(head<tail) {
            if((head&4095)==0)JobControl.checkUnchecked();
            int i=queue[head++];int x=i%w;
            int left=x>0?i-1:-1,right=x<w-1?i+1:-1;
            int top=i>=w?i-w:-1,bottom=i<n-w?i+w:-1;
            if(left>=0&&covered[left]&&!assigned[left]) {assigned[left]=true;output[left]=output[i];queue[tail++]=left;}
            if(right>=0&&covered[right]&&!assigned[right]) {assigned[right]=true;output[right]=output[i];queue[tail++]=right;}
            if(top>=0&&covered[top]&&!assigned[top]) {assigned[top]=true;output[top]=output[i];queue[tail++]=top;}
            if(bottom>=0&&covered[bottom]&&!assigned[bottom]) {assigned[bottom]=true;output[bottom]=output[i];queue[tail++]=bottom;}
        }
        // Blur only repaired pixels, keeping the real image intact.
        int[] temp=output.clone();
        for(int pass=0;pass<6;pass++) {
            JobControl.checkUnchecked();
            for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                int i=y*w+x;if(!covered[i])continue;
                int a=0,r=0,g=0,b=0,c=0;
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                    int xx=x+dx,yy=y+dy;
                    if(xx<0||xx>=w||yy<0||yy>=h)continue;
                    int p=output[yy*w+xx];
                    a+=(p>>>24)&255;r+=(p>>>16)&255;g+=(p>>>8)&255;b+=p&255;c++;
                }
                temp[i]=Color.argb(a/c,r/c,g/c,b/c);
            }
            int[] swap=output;output=temp;temp=swap;
        }
        Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        result.setPixels(output,0,w,0,0,w,h);
        return result;
    }
}
