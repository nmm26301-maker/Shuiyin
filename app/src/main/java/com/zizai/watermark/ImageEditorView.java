package com.zizai.watermark;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class ImageEditorView extends View {
    public static final class Mark {
        public final String text;
        public final float x,y,scale,angle;
        public final int opacity,color;
        public final boolean tiled;
        public final Bitmap logo;
        public Mark(String text,float x,float y,float scale,int opacity,int color,float angle,boolean tiled,Bitmap logo) {
            this.text=text==null?"":text; this.x=x; this.y=y; this.scale=scale;
            this.opacity=opacity; this.color=color; this.angle=angle; this.tiled=tiled; this.logo=logo;
        }
    }
    private static final class Stroke {
        final List<PointF> points=new ArrayList<>();
        final float width;
        final boolean rectangle;
        Stroke(float width,boolean rectangle) {this.width=width;this.rectangle=rectangle;}
    }
    private final Bitmap image;
    private final boolean eraseMode;
    private final RectF rect=new RectF();
    private final Paint bitmapPaint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final ArrayList<Stroke> strokes=new ArrayList<>(),redo=new ArrayList<>();
    private Stroke active;
    private float brushDp=34,fontSp=34,wmX=.5f,wmY=.85f,angle=0;
    private int opacity=180,color=Color.WHITE;
    private String text="我的原创作品";
    private Bitmap logo;
    private boolean tiled,rectangle,compare;
    public ImageEditorView(Context context,Bitmap image,boolean eraseMode) {
        super(context);this.image=image;this.eraseMode=eraseMode;
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        setContentDescription(eraseMode?"图片修复区域，滑动涂抹":"水印位置，滑动调整");
    }
    public void setBrushDp(float n){brushDp=n;invalidate();}
    public void setRectangle(boolean v){rectangle=v;}
    public void undo(){if(!strokes.isEmpty())redo.add(strokes.remove(strokes.size()-1));invalidate();}
    public void redo(){if(!redo.isEmpty())strokes.add(redo.remove(redo.size()-1));invalidate();}
    public void clear(){strokes.clear();redo.clear();invalidate();}
    public void setCompare(boolean v){compare=v;invalidate();}
    public void setFontSp(float n){fontSp=n;invalidate();}
    public void setWatermarkOpacity(int n){opacity=Math.max(0,Math.min(255,n));invalidate();}
    public void setWatermarkText(String s){text=s;invalidate();}
    public void setLogo(Bitmap b){logo=b;invalidate();}
    public void setColor(int c){color=c;invalidate();}
    public void setAngle(float n){angle=n;invalidate();}
    public void setTiled(boolean v){tiled=v;invalidate();}
    public Mark snapshotMark(){return new Mark(text,wmX,wmY,fontSp/360f,opacity,color,angle,tiled,logo);}
    public boolean hasSelection(){return !strokes.isEmpty();}
    private float clamp(float n){return Math.max(0,Math.min(1,n));}
    private void fit(){
        float s=Math.min((float)getWidth()/image.getWidth(),(float)getHeight()/image.getHeight());
        float w=s*image.getWidth(),h=s*image.getHeight();
        rect.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
    }
    @Override protected void onDraw(Canvas c){
        fit();c.drawColor(0xFF171A27);c.drawBitmap(image,null,rect,bitmapPaint);
        if(compare)return;
        c.save();c.clipRect(rect);
        if(eraseMode)drawStrokes(c,rect,0xA09979FF);
        else {c.translate(rect.left,rect.top);c.scale(rect.width()/image.getWidth(),rect.height()/image.getHeight());
            WatermarkPainter.draw(c,image.getWidth(),image.getHeight(),snapshotMark());}
        c.restore();
    }
    private void drawStrokes(Canvas c,RectF area,int color){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        for(Stroke s:strokes){
            if(s.points.isEmpty())continue;
            PointF first=s.points.get(0),last=s.points.get(s.points.size()-1);
            if(s.rectangle){p.setStyle(Paint.Style.FILL);c.drawRect(area.left+Math.min(first.x,last.x)*area.width(),area.top+Math.min(first.y,last.y)*area.height(),area.left+Math.max(first.x,last.x)*area.width(),area.top+Math.max(first.y,last.y)*area.height(),p);}
            else {p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(s.width*area.width());
                Path path=new Path();path.moveTo(area.left+first.x*area.width(),area.top+first.y*area.height());
                for(PointF pt:s.points)path.lineTo(area.left+pt.x*area.width(),area.top+pt.y*area.height());
                c.drawPath(path,p);
                if(s.points.size()==1){p.setStyle(Paint.Style.FILL);c.drawCircle(area.left+first.x*area.width(),area.top+first.y*area.height(),p.getStrokeWidth()/2,p);}
            }
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        fit();if(rect.width()==0)return false;
        float x=clamp((e.getX()-rect.left)/rect.width()),y=clamp((e.getY()-rect.top)/rect.height());
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                if(!rect.contains(e.getX(),e.getY()))return false;
                getParent().requestDisallowInterceptTouchEvent(true);
                if(eraseMode){if(strokes.size()>=500)return false;redo.clear();active=new Stroke(brushDp*getResources().getDisplayMetrics().density/rect.width(),rectangle);active.points.add(new PointF(x,y));strokes.add(active);}
                else {wmX=x;wmY=y;} break;
            case MotionEvent.ACTION_MOVE:
                if(eraseMode&&active!=null){if(active.rectangle&&active.points.size()>1)active.points.remove(1);if(active.points.size()<10000)active.points.add(new PointF(x,y));}
                else if(!eraseMode){wmX=x;wmY=y;}break;
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                active=null;getParent().requestDisallowInterceptTouchEvent(false);performClick();break;
            default:return true;
        }
        invalidate();return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    public Bitmap exportMask(){
        Bitmap b=Bitmap.createBitmap(image.getWidth(),image.getHeight(),Bitmap.Config.ARGB_8888);
        drawStrokes(new Canvas(b),new RectF(0,0,b.getWidth(),b.getHeight()),Color.WHITE);return b;
    }
}
