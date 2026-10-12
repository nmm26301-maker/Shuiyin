package com.zizai.watermark;
import java.io.*;
import java.util.Random;
import android.graphics.Bitmap;
public final class GifTests {
    public static void main(String[] args)throws Exception{
        Random rng=new Random(420);int w=128,h=72;
        try(OutputStream out=new FileOutputStream(args[0]);DataOutputStream expected=new DataOutputStream(new FileOutputStream(args[1]))){
            GifWriter gif=new GifWriter(out,w,h,25);gif.begin();
            for(int frame=0;frame<40;frame++){
                int[] pixels=new int[w*h];
                for(int i=0;i<pixels.length;i++){
                    int raw=frame%3==0?0xFF2030B0:0xFF000000|rng.nextInt(1<<24);pixels[i]=raw;
                    int r=(((raw>>>16)&255)>>>5)*255/7,g=(((raw>>>8)&255)>>>5)*255/7,b=((raw&255)>>>6)*255/3;
                    expected.writeByte(r);expected.writeByte(g);expected.writeByte(b);
                }
                gif.addFrame(new Bitmap(w,h,pixels));
            }gif.finish();
        }
        System.out.println("GIF stress fixture produced: 40 palette-quantized frames");
    }
}
