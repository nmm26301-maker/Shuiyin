package com.zizai.watermark;

import java.io.*;
import java.security.*;

/** Append a legal ISO BMFF free box; encoded media samples stay byte-for-byte intact. */
public final class Mp4Fingerprint {
    private Mp4Fingerprint(){}
    public static void copyWithFreeBox(InputStream in,OutputStream out)throws IOException {
        byte[] header=new byte[12];int offset=0;
        while(offset<header.length){int n=in.read(header,offset,header.length-offset);if(n<0)throw new IOException("不是可读取的 MP4 文件");offset+=n;}
        if(header[4]!='f'||header[5]!='t'||header[6]!='y'||header[7]!='p')throw new IOException("此工具仅支持以 ftyp 开头的 MP4/MOV 文件");
        long first=((header[0]&255L)<<24)|((header[1]&255L)<<16)|((header[2]&255L)<<8)|(header[3]&255L);
        if(first<12||first==1)throw new IOException("暂不支持该 MP4 容器结构");
        // A top-level size=0 box extends to EOF, so appending would place bytes inside
        // that box (possibly mdat). Reject rather than silently corrupting it.
        out.write(header);long boxLeft=first-header.length;byte[] buffer=new byte[128*1024];
        while(true){
            while(boxLeft>0){int n=in.read(buffer,0,(int)Math.min(buffer.length,boxLeft));if(n<0)throw new IOException("MP4 文件不完整");out.write(buffer,0,n);boxLeft-=n;check();}
            int b=in.read();if(b<0)break;
            byte[] box=new byte[8];box[0]=(byte)b;readFully(in,box,1,7);
            long size=((box[0]&255L)<<24)|((box[1]&255L)<<16)|((box[2]&255L)<<8)|(box[3]&255L);
            if(size==0)throw new IOException("该 MP4 有延伸至结尾的数据块，不能安全添加校验标记");
            out.write(box);
            if(size==1){byte[] ext=new byte[8];readFully(in,ext,0,8);long large=0;for(byte v:ext)large=(large<<8)|(v&255L);if(large<16)throw new IOException("无效的扩展数据块");out.write(ext);boxLeft=large-16;}
            else {if(size<8)throw new IOException("无效的 MP4 数据块");boxLeft=size-8;}
        }
        byte[] random=new byte[16];new SecureRandom().nextBytes(random);
        out.write(new byte[]{0,0,0,24,'f','r','e','e'});out.write(random);out.flush();
    }
    private static void readFully(InputStream in,byte[] b,int off,int count)throws IOException {while(count>0){int n=in.read(b,off,count);if(n<0)throw new IOException("MP4 文件不完整");off+=n;count-=n;}}
    private static void check()throws IOException {JobControl.check();}
}
