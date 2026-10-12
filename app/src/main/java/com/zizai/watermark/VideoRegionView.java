package com.zizai.watermark;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;

/** Eight independent normalized boxes with move and corner resize controls. */
final class VideoRegionView extends View {
    private Bitmap image;
    private final RectF bounds=new RectF();
    private final ArrayList<RectF> regions=new ArrayList<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private int active=-1,corner=-1;private boolean adding=true,drawing,moving;
    private float anchorX,anchorY,lastX,lastY;
    private Runnable change;
    VideoRegionView(Context context,Bitmap bitmap){super(context);image=bitmap;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    void setImage(Bitmap bitmap){image=bitmap;invalidate();}
    void onChanged(Runnable listener){change=listener;}
    private void changed(){invalidate();if(change!=null)change.run();}
    int count(){return regions.size();}
    int activeNumber(){return active<0?0:active+1;}
    void addRegion(){if(regions.size()<RepairRegions.MAX){adding=true;active=-1;changed();}}
    void removeSelected(){if(active>=0&&active<regions.size())regions.remove(active);active=regions.isEmpty()?-1:Math.min(active,regions.size()-1);adding=regions.isEmpty();changed();}
    void clear(){regions.clear();active=-1;adding=true;changed();}
    void setRegions(float[][] boxes){clear();if(boxes!=null)for(float[] r:boxes)if(r!=null&&r.length==4&&regions.size()<RepairRegions.MAX)regions.add(new RectF(r[0],r[1],r[2],r[3]));active=regions.isEmpty()?-1:0;adding=regions.isEmpty();changed();}
    float[] snapshot(){return active>=0?box(regions.get(active)):null;}
    float[][] snapshotAll(){if(regions.isEmpty())return null;float[][] result=new float[regions.size()][];for(int i=0;i<result.length;i++)result[i]=box(regions.get(i));return result;}
    private float[] box(RectF r){return new float[]{r.left,r.top,r.right,r.bottom};}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(0xFF11141E);if(image==null||image.isRecycled())return;
        float scale=Math.min((float)getWidth()/image.getWidth(),(float)getHeight()/image.getHeight());
        float w=image.getWidth()*scale,h=image.getHeight()*scale;bounds.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
        paint.setColor(Color.WHITE);paint.setAlpha(255);canvas.drawBitmap(image,null,bounds,paint);
        float density=getResources().getDisplayMetrics().density;
        for(int i=0;i<regions.size();i++){
            RectF r=pixels(regions.get(i));paint.setStyle(Paint.Style.FILL);paint.setColor(i==active?0x339475F5:0x2224ECDD);canvas.drawRect(r,paint);
            paint.setColor(i==active?0xFFF4C1FF:0xFF5EEBE1);paint.setStrokeWidth(2*density);paint.setStyle(Paint.Style.STROKE);canvas.drawRect(r,paint);paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(12*density);canvas.drawText(String.valueOf(i+1),r.left+3*density,Math.max(bounds.top+13*density,r.top-3*density),paint);
            if(i==active){canvas.drawCircle(r.left,r.top,4*density,paint);canvas.drawCircle(r.right,r.top,4*density,paint);canvas.drawCircle(r.left,r.bottom,4*density,paint);canvas.drawCircle(r.right,r.bottom,4*density,paint);}
        }
    }
    private RectF pixels(RectF r){return new RectF(bounds.left+r.left*bounds.width(),bounds.top+r.top*bounds.height(),bounds.left+r.right*bounds.width(),bounds.top+r.bottom*bounds.height());}
    private float clamp(float value){return Math.max(.002f,Math.min(.998f,value));}
    private int hitCorner(RectF r,float x,float y){float distance=12*getResources().getDisplayMetrics().density;float[] xs={r.left,r.right,r.left,r.right},ys={r.top,r.top,r.bottom,r.bottom};for(int i=0;i<4;i++)if(Math.abs(x-xs[i])<=distance&&Math.abs(y-ys[i])<=distance)return i;return -1;}
    @Override public boolean onTouchEvent(MotionEvent event){
        if(bounds.width()<=0||bounds.height()<=0)return false;
        float x=clamp((event.getX()-bounds.left)/bounds.width()),y=clamp((event.getY()-bounds.top)/bounds.height());
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                if(!bounds.contains(event.getX(),event.getY()))return false;
                if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
                moving=false;drawing=false;corner=-1;int found=-1;
                if(!adding)for(int i=regions.size()-1;i>=0;i--){RectF r=pixels(regions.get(i));int c=hitCorner(r,event.getX(),event.getY());if(c>=0||r.contains(event.getX(),event.getY())){found=i;corner=c;break;}}
                if(found>=0){active=found;moving=corner<0;}
                else if(regions.size()<RepairRegions.MAX){regions.add(new RectF(x,y,x,y));active=regions.size()-1;drawing=true;adding=false;}
                else {changed();return true;}
                anchorX=lastX=x;anchorY=lastY=y;changed();return true;
            case MotionEvent.ACTION_MOVE:
                if(active<0||active>=regions.size())return true;RectF r=regions.get(active);
                if(moving){float dx=Math.max(.002f-r.left,Math.min(.998f-r.right,x-lastX)),dy=Math.max(.002f-r.top,Math.min(.998f-r.bottom,y-lastY));r.offset(dx,dy);}
                else if(drawing)r.set(Math.min(anchorX,x),Math.min(anchorY,y),Math.max(anchorX,x),Math.max(anchorY,y));
                else if(corner>=0){if(corner==0||corner==2)r.left=Math.min(x,r.right-.005f);else r.right=Math.max(x,r.left+.005f);if(corner<2)r.top=Math.min(y,r.bottom-.005f);else r.bottom=Math.max(y,r.top+.005f);}
                lastX=x;lastY=y;changed();return true;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:
                if(active>=0&&(regions.get(active).width()<.005f||regions.get(active).height()<.005f))removeSelected();
                moving=false;drawing=false;corner=-1;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);changed();performClick();return true;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
