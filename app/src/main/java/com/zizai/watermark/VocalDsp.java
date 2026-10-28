package com.zizai.watermark;

import java.io.*;

/** Bounded stereo STFT/overlap-add for the Spleeter 2-stem model's magnitude interface. */
final class VocalDsp {
    static final int RATE=44100,FFT=4096,HOP=1024,FRAMES=512,BINS=1024,STEP=FRAMES*HOP,SIZE=STEP+FFT-HOP;
    interface Mask {float[] apply(float[] magnitude)throws Exception;}
    interface Progress {void update(int done,int total);}
    private static final float[] WINDOW=new float[FFT];
    static {for(int i=0;i<FFT;i++)WINDOW[i]=(float)(.5-.5*Math.cos(2*Math.PI*i/FFT));}
    static void separate(File input,File output,Mask mask,Progress progress)throws Exception {
        if(input.length()==0||input.length()%4!=0)throw new IOException("音频解码数据为空或不完整");
        long samples=input.length()/4;if(samples>RATE*180L)throw new IOException("去 BGM 暂支持 3 分钟以内的视频，请先裁剪");
        int total=(int)((samples+HOP-1)/HOP)+1,blocks=(total+FRAMES-1)/FRAMES;long written=0;
        float[][] tail=new float[2][FFT-HOP];float[] tailNorm=new float[FFT-HOP];
        try(RandomAccessFile source=new RandomAccessFile(input,"r");OutputStream out=new BufferedOutputStream(new FileOutputStream(output))){
            for(int block=0;block<blocks;block++){
                JobControl.check();int valid=Math.min(FRAMES,total-block*FRAMES);
                float[][] pcm=read(source,(long)block*STEP-FFT/2,samples);
                float[][] real=new float[2][FRAMES*(FFT/2+1)],imag=new float[2][FRAMES*(FFT/2+1)];float[] magnitude=new float[2*FRAMES*BINS];
                analyse(pcm,valid,real,imag,magnitude);JobControl.check();float[] gain=mask.apply(magnitude);
                if(gain.length!=magnitude.length)throw new IOException("人声模型输出尺寸无效");
                float[][] wave=new float[2][SIZE];float[] norm=new float[SIZE];synthesise(real,imag,gain,valid,wave,norm);
                for(int i=0;i<tailNorm.length;i++){norm[i]+=tailNorm[i];wave[0][i]+=tail[0][i];wave[1][i]+=tail[1][i];}
                int start=block==0?FFT/2:0,end=(int)Math.min(STEP,start+samples-written);
                write(out,wave,norm,start,end);written+=end-start;
                for(int i=0;i<tailNorm.length;i++){tailNorm[i]=norm[STEP+i];tail[0][i]=wave[0][STEP+i];tail[1][i]=wave[1][STEP+i];}
                if(progress!=null)progress.update(block+1,blocks);
            }
        }
        if(written!=samples||output.length()!=input.length())throw new IOException("人声输出长度不完整");
    }
    private static float[][] read(RandomAccessFile source,long start,long samples)throws Exception {
        float[][] pcm=new float[2][SIZE];int padding=(int)Math.max(0,-start);long first=Math.max(0,start);
        int count=(int)Math.max(0,Math.min(SIZE-padding,samples-first));source.seek(first*4);byte[] bytes=new byte[count*4];source.readFully(bytes);
        for(int i=0;i<count;i++)for(int c=0;c<2;c++){int p=i*4+c*2;pcm[c][padding+i]=(short)((bytes[p]&255)|(bytes[p+1]<<8))/32768f;}
        return pcm;
    }
    private static void analyse(float[][] pcm,int valid,float[][] real,float[][] imag,float[] magnitude)throws Exception {
        for(int c=0;c<2;c++)for(int frame=0;frame<valid;frame++){
            JobControl.check();float[] r=new float[FFT],im=new float[FFT];for(int i=0;i<FFT;i++)r[i]=pcm[c][frame*HOP+i]*WINDOW[i];fft(r,im,false);
            int offset=frame*(FFT/2+1);System.arraycopy(r,0,real[c],offset,FFT/2+1);System.arraycopy(im,0,imag[c],offset,FFT/2+1);
            for(int k=0;k<BINS;k++)magnitude[(c*FRAMES+frame)*BINS+k]=(float)Math.hypot(r[k],im[k]);
        }
    }
    private static void synthesise(float[][] real,float[][] imag,float[] gain,int valid,float[][] out,float[] norm)throws Exception {
        for(int frame=0;frame<valid;frame++){
            JobControl.check();for(int i=0;i<FFT;i++)norm[frame*HOP+i]+=WINDOW[i]*WINDOW[i];
            for(int c=0;c<2;c++){
                float[] r=new float[FFT],im=new float[FFT];int offset=frame*(FFT/2+1);
                for(int k=0;k<BINS;k++){
                    float g=gain[(c*FRAMES+frame)*BINS+k];if(!Float.isFinite(g)||g<0||g>1)throw new IOException("人声模型掩码无效");
                    r[k]=real[c][offset+k]*g;im[k]=imag[c][offset+k]*g;
                    if(k>0){r[FFT-k]=r[k];im[FFT-k]=-im[k];}
                }
                fft(r,im,true);for(int i=0;i<FFT;i++)out[c][frame*HOP+i]+=r[i]*WINDOW[i];
            }
        }
    }
    private static void write(OutputStream out,float[][] wave,float[] norm,int start,int end)throws Exception {
        byte[] bytes=new byte[(end-start)*4];
        for(int i=start;i<end;i++){if((i&8191)==0)JobControl.check();for(int c=0;c<2;c++){
            float value=norm[i]>1e-8f?wave[c][i]/norm[i]:0;int sample=Math.round(Math.max(-1,Math.min(32767/32768f,value))*32768);int p=(i-start)*4+c*2;bytes[p]=(byte)sample;bytes[p+1]=(byte)(sample>>8);
        }}out.write(bytes);
    }
    static void fft(float[] real,float[] imag,boolean inverse){
        int n=real.length;
        for(int i=1,j=0;i<n;i++){int bit=n>>1;for(;(j&bit)!=0;bit>>=1)j^=bit;j^=bit;if(i<j){float v=real[i];real[i]=real[j];real[j]=v;v=imag[i];imag[i]=imag[j];imag[j]=v;}}
        for(int length=2;length<=n;length<<=1){double angle=(inverse?2:-2)*Math.PI/length,cr=Math.cos(angle),ci=Math.sin(angle);
            for(int start=0;start<n;start+=length){double wr=1,wi=0;
                for(int j=0;j<length/2;j++){int a=start+j,b=a+length/2;float tr=(float)(wr*real[b]-wi*imag[b]),ti=(float)(wr*imag[b]+wi*real[b]);
                    real[b]=real[a]-tr;imag[b]=imag[a]-ti;real[a]+=tr;imag[a]+=ti;double next=wr*cr-wi*ci;wi=wr*ci+wi*cr;wr=next;}
            }
        }
        if(inverse)for(int i=0;i<n;i++){real[i]/=n;imag[i]/=n;}
    }
}
