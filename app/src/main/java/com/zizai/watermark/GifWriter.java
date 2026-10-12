package com.zizai.watermark;

import android.graphics.Bitmap;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

/** Self-contained GIF89a encoder, fixed RGB332 palette + GIF LZW compression. */
public final class GifWriter {
    private final OutputStream output;
    private final int width,height,centis;
    private boolean begun;
    GifWriter(OutputStream output,int width,int height,int centis){
        this.output=output;this.width=width;this.height=height;this.centis=centis;
    }
    private void byteOut(int b)throws IOException {output.write(b&255);}
    private void shortOut(int s)throws IOException {byteOut(s);byteOut(s>>>8);}
    private void word(String s)throws IOException {output.write(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
    void begin()throws IOException {
        if(begun)throw new IllegalStateException("GIF already started");
        begun=true;word("GIF89a");shortOut(width);shortOut(height);
        byteOut(0xF7);byteOut(0);byteOut(0); // 256 color table
        for(int i=0;i<256;i++){
            int r=(i>>>5)&7,g=(i>>>2)&7,b=i&3;
            byteOut((r*255)/7);byteOut((g*255)/7);byteOut((b*255)/3);
        }
        byteOut(0x21);byteOut(0xFF);byteOut(11);word("NETSCAPE2.0");
        byteOut(3);byteOut(1);shortOut(0);byteOut(0);
    }
    void addFrame(Bitmap frame)throws IOException {
        if(!begun)throw new IllegalStateException("GIF not started");
        if(frame.getWidth()!=width || frame.getHeight()!=height)throw new IOException("GIF 帧大小不同");
        int[] pixels=new int[width*height];
        frame.getPixels(pixels,0,width,0,0,width,height);
        byte[] indexed=new byte[pixels.length];
        for(int i=0;i<pixels.length;i++) {
            int p=pixels[i];
            indexed[i]=(byte)((((p>>>16)&255)>>>5<<5)|((((p>>>8)&255)>>>5)<<2)|((p&255)>>>6));
        }
        // Graphics Control Extension: disposal method 2 (restore background), frame delay.
        byteOut(0x21);byteOut(0xF9);byteOut(4);byteOut(0x08);shortOut(centis);byteOut(0);byteOut(0);
        byteOut(0x2C);shortOut(0);shortOut(0);shortOut(width);shortOut(height);byteOut(0);
        byteOut(8); // LZW minimum code size for 8-bit palette
        lzw(indexed);
    }
    void finish()throws IOException{if(!begun)return;byteOut(0x3B);output.flush();begun=false;}
    private void lzw(byte[] colors)throws IOException {
        final int clear=256,stop=257;
        HashMap<Integer,Integer> dictionary=new HashMap<>(4096);
        int next=258,bits=9;
        BitPacker packer=new BitPacker(output);
        packer.write(clear,bits);
        int prefix=colors[0]&255;
        for(int i=1;i<colors.length;i++) {
            int color=colors[i]&255,key=(prefix<<8)|color;
            Integer match=dictionary.get(key);
            if(match!=null){prefix=match;continue;}
            packer.write(prefix,bits);
            if(next<4096) {
                dictionary.put(key,next++);
                // Decoder learns a new entry after consuming the preceding code.
                if(next==(1<<bits)+1 && bits<12)bits++;
            } else {
                packer.write(clear,bits);
                dictionary.clear();next=258;bits=9;
            }
            prefix=color;
        }
        packer.write(prefix,bits);packer.write(stop,bits);packer.finish();
    }
    private static class BitPacker {
        private final OutputStream output;
        private final byte[] block=new byte[255];
        private int blockSize=0,bitCount=0,bitBuffer=0;
        BitPacker(OutputStream out){output=out;}
        private void put(int b)throws IOException {
            block[blockSize++]=(byte)b;
            if(blockSize==255)flushBlock();
        }
        void write(int code,int width)throws IOException {
            bitBuffer |= (code<<bitCount);bitCount+=width;
            while(bitCount>=8){put(bitBuffer&255);bitBuffer >>>= 8;bitCount-=8;}
        }
        private void flushBlock()throws IOException {
            if(blockSize>0){output.write(blockSize);output.write(block,0,blockSize);blockSize=0;}
        }
        void finish()throws IOException {
            if(bitCount>0)put(bitBuffer&255);
            flushBlock();output.write(0);
        }
    }
}
