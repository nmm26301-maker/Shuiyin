package com.zizai.watermark;

import java.io.*;
import java.net.*;
import java.util.regex.*;

/** In-job resume; verifies representation identity and byte ranges before appending. */
final class RangeDownload {
    interface Open {HttpURLConnection connect(long offset,String validator)throws Exception;}
    interface Progress {void phase(String phase);void bytes(long bytes,long total);}
    static final class Result {final String mime;final long bytes;Result(String m,long b){mime=m;bytes=b;}}
    static Result fetch(File file,long limit,Open open,Progress progress)throws Exception {
        long total=-1;String validator="",mime="";
        for(int attempt=0;attempt<3;attempt++){
            JobControl.check();long offset=attempt>0&&!validator.isEmpty()?file.length():0;
            if(offset==0)try(RandomAccessFile reset=new RandomAccessFile(file,"rw")){reset.setLength(0);}
            HttpURLConnection c=null;
            try {
                if(progress!=null)progress.phase(attempt==0?"连接素材服务器":offset>0?"网络中断，正在续传":"正在重新连接素材服务器");
                c=open.connect(offset,validator);int code=c.getResponseCode();
                if(code!=200&&code!=206)throw new IOException("素材服务器返回 HTTP "+code);
                String encoding=c.getHeaderField("Content-Encoding");
                if(encoding!=null&&!encoding.isEmpty()&&!encoding.equalsIgnoreCase("identity"))throw new IOException("素材服务器返回压缩传输，无法安全续传");
                long expected;String nextValidator=validator(c);String contentType=c.getContentType();
                if(code==200){offset=0;expected=c.getContentLengthLong();total=expected;try(RandomAccessFile reset=new RandomAccessFile(file,"rw")){reset.setLength(0);}}
                else {
                    long[] range=range(c.getHeaderField("Content-Range"));
                    if(range==null||range[0]!=offset||range[1]!=range[2]-1)throw new IOException("续传响应范围无效，已停止以免损坏素材");
                    if(offset>0&&(validator.isEmpty()||!validator.equals(nextValidator)||total>0&&total!=range[2]))throw new IOException("素材已改变，请重新解析下载");
                    long length=c.getContentLengthLong();expected=range[1]-range[0]+1;
                    if(length>=0&&length!=expected)throw new IOException("续传响应长度无效");total=range[2];
                }
                if(total>limit)throw new IOException("素材超过下载上限");
                validator=nextValidator;mime=contentType==null?"":contentType;
                if(progress!=null){progress.phase(offset>0?"续传并校验素材":"下载并校验素材");progress.bytes(offset,total);}
                long written=offset,last=offset,start=offset;
                try(InputStream input=c.getInputStream();RandomAccessFile output=new RandomAccessFile(file,"rw")){
                    output.seek(offset);byte[] buffer=new byte[65536];int n;
                    while((n=input.read(buffer))!=-1){JobControl.check();written+=n;if(written>limit||total>=0&&written>total)throw new IOException("素材超过允许的长度");output.write(buffer,0,n);if(progress!=null&&written-last>=256*1024){progress.bytes(written,total);last=written;}}
                    if(expected>=0&&written-start!=expected)throw new EOFException("素材下载中断");
                    if(written==0)throw new IOException("素材文件为空");output.getFD().sync();
                }
                JobControl.check();if(progress!=null)progress.bytes(written,total);return new Result(mime,written);
            }catch(Exception e){JobControl.check();if(attempt==2||!transientError(e))throw e;}
            finally{if(c!=null)c.disconnect();}
        }
        throw new IOException("下载未完成");
    }
    private static boolean transientError(Exception e){return e instanceof SocketException||e instanceof SocketTimeoutException||e instanceof EOFException;}
    static String validator(HttpURLConnection c){
        String etag=c.getHeaderField("ETag");if(etag==null||etag.length()<2||etag.charAt(0)!=34||etag.charAt(etag.length()-1)!=34)return "";
        for(int i=1;i<etag.length()-1;i++){char ch=etag.charAt(i);if(ch<33||ch==34||ch==127)return "";}return etag;
    }
    static long[] range(String header){if(header==null)return null;Matcher m=Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)").matcher(header);if(!m.matches())return null;try{long a=Long.parseLong(m.group(1)),b=Long.parseLong(m.group(2)),n=Long.parseLong(m.group(3));return a<=b&&b<n?new long[]{a,b,n}:null;}catch(Exception e){return null;}}
}
