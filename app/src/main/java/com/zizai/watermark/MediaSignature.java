package com.zizai.watermark;

/** Rejects HTML/error bodies masquerading as media; this is not a full decoder. */
final class MediaSignature {
    private static boolean text(byte[] b,int n,int at,String s){if(at+s.length()>n)return false;for(int i=0;i<s.length();i++)if((b[at+i]&255)!=s.charAt(i))return false;return true;}
    static boolean accepts(String mime,byte[] b,int n){
        if(n<4)return false;
        boolean mp4=text(b,n,4,"ftyp")||text(b,n,4,"moov")||text(b,n,4,"mdat");
        boolean webm=(b[0]&255)==0x1a&&(b[1]&255)==0x45&&(b[2]&255)==0xdf&&(b[3]&255)==0xa3;
        if(mime.startsWith("video/"))return mp4||webm||text(b,n,0,"OggS")||(text(b,n,0,"RIFF")&&text(b,n,8,"AVI "));
        if(mime.startsWith("audio/"))return mp4||webm||text(b,n,0,"ID3")||text(b,n,0,"fLaC")||text(b,n,0,"OggS")||(text(b,n,0,"RIFF")&&text(b,n,8,"WAVE"))||((b[0]&255)==255&&((b[1]&255)&0xe0)==0xe0);
        if(mime.startsWith("image/"))return ((b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255)||text(b,n,0,"\u0089PNG")||text(b,n,0,"GIF8")||text(b,n,0,"BM")||(text(b,n,0,"RIFF")&&text(b,n,8,"WEBP"))||mp4;
        return false;
    }
}
