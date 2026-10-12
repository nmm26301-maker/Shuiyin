package com.zizai.watermark;

/** Coordinates before the decoder SurfaceTexture transform. Pure Java for tests. */
public final class VideoGeometry {
    private VideoGeometry(){}
    public static float[] coordinates(int width,int height,int rotation,float targetAspect,boolean mirror){
        float displayAspect=(rotation==90||rotation==270)?(float)height/width:(float)width/height;
        float spanX=1,spanY=1;
        if(targetAspect>0){if(displayAspect>targetAspect)spanX=targetAspect/displayAspect;else spanY=displayAspect/targetAspect;}
        float[] input={0,0,1,0,0,1,1,1},out=new float[8];
        for(int i=0;i<8;i+=2){
            float u=.5f+(input[i]-.5f)*spanX,v=.5f+(input[i+1]-.5f)*spanY;
            if(mirror)u=1-u;
            if(rotation==90){out[i]=1-v;out[i+1]=u;}
            else if(rotation==180){out[i]=1-u;out[i+1]=1-v;}
            else if(rotation==270){out[i]=v;out[i+1]=1-u;}
            else {out[i]=u;out[i+1]=v;}
        }return out;
    }
}
