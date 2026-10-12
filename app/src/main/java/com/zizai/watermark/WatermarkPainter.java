package com.zizai.watermark;

import android.graphics.*;

/** A single renderer shared by preview, photo export and video overlay textures. */
public final class WatermarkPainter {
    private WatermarkPainter() {}
    public static void draw(Canvas c, int width, int height, ImageEditorView.Mark mark) {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(mark.color); p.setAlpha(mark.opacity);
        p.setTextSize(width*mark.scale); p.setTextAlign(Paint.Align.CENTER);
        p.setShadowLayer(Math.max(1,width/600f),0,1,0x99000000);
        float itemW=mark.logo!=null ? width*mark.scale*5 : Math.max(p.measureText(mark.text),p.getTextSize());
        float itemH=mark.logo!=null ? itemW*mark.logo.getHeight()/mark.logo.getWidth() : p.getFontSpacing();
        if(mark.tiled) {
            float stepX=Math.max(width*.18f,itemW*1.45f),stepY=Math.max(height*.13f,itemH*2.8f);
            // Rotate the entire grid; include enough offscreen cells to cover all corners.
            c.save(); c.rotate(mark.angle,width/2f,height/2f);
            float diagonal=(float)Math.hypot(width,height);
            int row=0;
            for(float y=-diagonal;y<height+diagonal;y+=stepY,row++)
                for(float x=-diagonal+(row%2)*stepX/2;x<width+diagonal;x+=stepX)
                    one(c,p,mark,x,y,itemW,itemH,0);
            c.restore();
        } else one(c,p,mark,width*mark.x,height*mark.y,itemW,itemH,mark.angle);
    }
    private static void one(Canvas c,Paint p,ImageEditorView.Mark mark,float x,float y,float w,float h,float angle) {
        c.save(); c.rotate(angle,x,y);
        if(mark.logo!=null) {
            p.clearShadowLayer();
            c.drawBitmap(mark.logo,null,new RectF(x-w/2,y-h/2,x+w/2,y+h/2),p);
        } else {
            Paint.FontMetrics fm=p.getFontMetrics();
            c.drawText(mark.text,x,y-(fm.ascent+fm.descent)/2,p);
        }
        c.restore();
    }
}
