package com.zizai.watermark;
import android.content.Context;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
final class AutoModelStore {
    static final String UVR_SHA="9d78f8566fa8198065214ab628be1de966a500c57786695aa4b13e2b27a7727d";
    static synchronized File model(Context context,String name,String sha)throws Exception {
        File root=new File(context.getNoBackupFilesDir(),"auto-models-v1"),target=new File(root,name);
        if(target.isFile()&&hash(target).equals(sha))return target;
        if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("无法准备本机模型，请检查空间");
        File temporary=new File(target.getParentFile(),target.getName()+"."+UUID.randomUUID()+".tmp");
        try{
            try(InputStream in=context.getAssets().open("auto_models/"+name);FileOutputStream out=new FileOutputStream(temporary)){
                byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){JobControl.check();out.write(b,0,n);}out.getFD().sync();
            }
            if(!hash(temporary).equals(sha))throw new IOException("本机模型校验失败，请重新安装完整安装包");
            JobControl.check();if(!temporary.renameTo(target))throw new IOException("模型准备失败");return target;
        }finally{temporary.delete();}
    }
    static String hash(File file)throws Exception {MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){JobControl.check();d.update(b,0,n);}}StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
}
