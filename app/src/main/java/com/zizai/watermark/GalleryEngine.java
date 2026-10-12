package com.zizai.watermark;
import android.content.Context;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;

/** Run the bundled, pinned gallery-dl with the app's embedded Python. */
final class GalleryEngine {
    static final String VERSION="1.32.16";
    static final String SHA="d43f22bd877185ce93720efa6890da719537a535d8b17fc6b683d03f3ab6cd1e";
    private static synchronized File install(Context context)throws Exception {
        File target=new File(context.getNoBackupFilesDir(),"gallery-"+SHA);
        if(new File(target,".ready").isFile())return target;
        File staging=new File(context.getNoBackupFilesDir(),"gallery-staging"),archive=new File(context.getCacheDir(),"gallery-install.zip");
        delete(staging);if(!staging.mkdirs())throw new IOException("图集组件无法解压，请检查空间");
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=context.getResources().openRawResource(R.raw.gallery_bundle);OutputStream out=new FileOutputStream(archive)){
                byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1){JobControl.check();digest.update(buf,0,n);out.write(buf,0,n);}
            }
            StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
            if(!SHA.equals(hash.toString()))throw new IOException("图集组件校验失败");
            long size=0;int count=0;String prefix=staging.getCanonicalPath()+File.separator;
            try(ZipInputStream in=new ZipInputStream(new FileInputStream(archive))){ZipEntry entry;byte[] buf=new byte[65536];
                while((entry=in.getNextEntry())!=null){JobControl.check();if(++count>10000)throw new IOException("组件文件过多");
                    File file=new File(staging,entry.getName());if(!file.getCanonicalPath().startsWith(prefix))throw new IOException("组件路径无效");
                    if(entry.isDirectory()){file.mkdirs();continue;}File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("组件目录无法创建");
                    try(OutputStream out=new FileOutputStream(file)){int n;while((n=in.read(buf))!=-1){JobControl.check();size+=n;if(size>32*1024*1024)throw new IOException("组件内容过大");out.write(buf,0,n);}}
                }
            }
            if(!new File(staging,"gallery_runner.py").isFile()||!new File(staging,"gallery_dl/__init__.py").isFile())throw new IOException("图集组件不完整");
            try(FileOutputStream out=new FileOutputStream(new File(staging,".ready"))){out.write(SHA.getBytes("UTF-8"));out.getFD().sync();}
            delete(target);if(!staging.renameTo(target))throw new IOException("图集组件安装失败");return target;
        }finally{archive.delete();delete(staging);}
    }
    static LinkExtractor.Result parse(Context context,String url,PlatformEngine.Status status)throws Exception {
        LinkExtractor.ensurePublic(url);PlatformEngine.init(context,status);File gallery=install(context);
        if(status!=null)status.update("正在提取图集原图与混合素材…");
        File base=new File(context.getNoBackupFilesDir(),"youtubedl-android"),bin=new File(context.getApplicationInfo().nativeLibraryDir);
        Map<String,String> env=new HashMap<>();env.put("LD_LIBRARY_PATH",new File(base,"packages/python/usr/lib").getAbsolutePath());
        env.put("PYTHONHOME",new File(base,"packages/python/usr").getAbsolutePath());env.put("TMPDIR",context.getCacheDir().getAbsolutePath());
        env.put("SSL_CERT_FILE",new File(gallery,"certifi/cacert.pem").getAbsolutePath());env.put("REQUESTS_CA_BUNDLE",new File(gallery,"certifi/cacert.pem").getAbsolutePath());
        env.put("PYTHONPATH",gallery.getAbsolutePath());env.put("PYTHONDONTWRITEBYTECODE","1");
        List<String> args=new ArrayList<>();File session=null,cookies=new File(context.getFilesDir(),"link-cookies.txt");
        LinkExtractor.Result result=new LinkExtractor.Result();result.source=url;result.notice="图集引擎 gallery-dl "+VERSION+" · 单次最多 24 项；登录内容可能需要本人 Cookie。";
        AtomicInteger group=new AtomicInteger();
        try {
            if(!cookies.isFile()){
                String text=SessionCookieJar.netscape(url,WebSessionCookies.header(url));
                if(!text.isEmpty()){session=File.createTempFile("gallery-session-",".txt",context.getCacheDir());try(OutputStream out=new FileOutputStream(session)){out.write(text.getBytes("UTF-8"));}cookies=session;}
            }
            if(cookies.isFile()){args.add("--cookies");args.add(cookies.getAbsolutePath());}args.add(url);
            NativeProcess.run(EngineCommand.build(new File(bin,"libpython.so").getAbsolutePath(),new File(gallery,"gallery_runner.py").getAbsolutePath(),args),env,JobControl.flag(),120_000,line->{
                if(line.startsWith(EngineCommand.GROUP_PREFIX)){try{int pid=Integer.parseInt(line.substring(EngineCommand.GROUP_PREFIX.length()));if(pid>1)group.set(pid);}catch(NumberFormatException ignored){}}
                else GalleryMetadata.add(result,line);
            },()->{int pid=group.get();if(pid>1)try{android.system.Os.kill(-pid,9);}catch(Exception ignored){}},()->{});
            JobControl.check();if(result.items.isEmpty())throw new IOException("没有提取到图集原图；请检查链接或登录状态");return result;
        }finally{if(session!=null)session.delete();}
    }
    private static void delete(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)delete(child);file.delete();}
}
