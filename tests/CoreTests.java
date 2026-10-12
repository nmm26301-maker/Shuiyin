package com.zizai.watermark;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public final class CoreTests {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static void near(float a,float b){check(Math.abs(a-b)<.00001,"coordinate "+a+" != "+b);}
    private static byte[] box(String type,byte[] payload)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);d.writeInt(payload.length+8);d.writeBytes(type);d.write(payload);return b.toByteArray();}
    private static void rejects(byte[] bytes)throws Exception{try{Mp4Fingerprint.copyWithFreeBox(new ByteArrayInputStream(bytes),new ByteArrayOutputStream());throw new AssertionError("bad MP4 accepted");}catch(IOException expected){checks++;}}
    public static void main(String[] args)throws Exception {
        float[] uv=VideoGeometry.coordinates(1920,1080,0,0,false);check(Arrays.equals(uv,new float[]{0,0,1,0,0,1,1,1}),"identity");
        uv=VideoGeometry.coordinates(1920,1080,0,0,true);check(Arrays.equals(uv,new float[]{1,0,0,0,1,1,0,1}),"mirror");
        uv=VideoGeometry.coordinates(1920,1080,90,0,false);check(Arrays.equals(uv,new float[]{1,0,1,1,0,0,0,1}),"clockwise rotation");
        uv=VideoGeometry.coordinates(1920,1080,270,0,false);check(Arrays.equals(uv,new float[]{0,1,0,0,1,1,1,0}),"counterclockwise rotation");
        uv=VideoGeometry.coordinates(1920,1080,0,1,false);near(uv[0],.21875f);near(uv[2],.78125f);near(uv[1],0);near(uv[5],1);
        for(int rotation:new int[]{0,90,180,270})for(float aspect:new float[]{0,1,9f/16,16f/9})for(boolean mirror:new boolean[]{false,true}){
            uv=VideoGeometry.coordinates(1920,1080,rotation,aspect,mirror);for(float n:uv)check(n>=0&&n<=1,"uv out of bounds");
        }
        ByteArrayOutputStream source=new ByteArrayOutputStream();source.write(box("ftyp",new byte[]{'i','s','o','m',0,0,0,0}));source.write(box("mdat",new byte[]{3,4,5}));
        byte[] original=source.toByteArray();ByteArrayOutputStream out=new ByteArrayOutputStream();Mp4Fingerprint.copyWithFreeBox(new ByteArrayInputStream(original),out);
        byte[] edited=out.toByteArray();check(edited.length==original.length+24,"box size");check(Arrays.equals(original,Arrays.copyOf(edited,original.length)),"source bytes changed");
        check(!Arrays.equals(MessageDigest.getInstance("MD5").digest(original),MessageDigest.getInstance("MD5").digest(edited)),"fingerprint unchanged");
        rejects(new byte[12]);rejects(Arrays.copyOf(original,original.length-1));
        byte[] toEOF=original.clone();Arrays.fill(toEOF,16,20,(byte)0);rejects(toEOF);
        byte[] broken=original.clone();broken[19]=4;rejects(broken);
        if(args.length==2){try(InputStream in=Files.newInputStream(Paths.get(args[0]));OutputStream file=Files.newOutputStream(Paths.get(args[1]))){Mp4Fingerprint.copyWithFreeBox(in,file);}}
        System.out.println("PASS: "+checks+" checks (video geometry, valid MP4 preservation, malformed container rejection)");
    }
}
