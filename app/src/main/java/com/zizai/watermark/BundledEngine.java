package com.zizai.watermark;
import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import java.util.regex.*;
import java.security.MessageDigest;

/** Upgrade old app-installed scripts, while preserving a newer script installed by the user. */
final class BundledEngine {
    static final String VERSION="2026.08.19";
    static final String SHA256="1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6";
    private static boolean checked;
    static String version(File file){
        try(ZipFile zip=new ZipFile(file)){ZipEntry entry=zip.getEntry("yt_dlp/version.py");if(entry==null||entry.getSize()>16384)return "";
            try(InputStream in=zip.getInputStream(entry);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>16384)return "";}Matcher m=Pattern.compile("__version__\\s*=\\s*['\"]([^'\"]+)").matcher(out.toString("UTF-8"));return m.find()?m.group(1):"";}
        }catch(Exception e){return "";}
    }
    static boolean older(String existing,String bundled){Matcher old=Pattern.compile("^\\d{4}\\.\\d{2}\\.\\d{2}").matcher(existing);return !old.find()||old.group().compareTo(bundled.substring(0,10))<0;}
    static synchronized void ensure(Context context)throws Exception{
        if(checked)return;File script=new File(context.getNoBackupFilesDir(),"youtubedl-android/yt-dlp/yt-dlp");
        if(!older(version(script),VERSION)){checked=true;return;}
        try(InputStream source=context.getResources().openRawResource(R.raw.ytdlp)){install(script,source);}checked=true;
    }
    static void install(File script,InputStream source)throws Exception{
        JobControl.check();File temporary=File.createTempFile("engine-upgrade-",".zip",script.getParentFile());
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");try(OutputStream out=new FileOutputStream(temporary)){byte[] b=new byte[65536];int n;while((n=source.read(b))!=-1){JobControl.check();out.write(b,0,n);digest.update(b,0,n);}}
            StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(!SHA256.equals(hash.toString()))throw new IOException("内置解析脚本校验失败，请重新安装完整安装包");
            Files.move(temporary.toPath(),script.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        }finally{temporary.delete();}
    }
}
