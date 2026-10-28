package com.zizai.watermark;

import java.io.*;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

/** Checks a small prefix of direct video candidates before they win a search. */
final class VideoCandidates {
    interface Opener {HttpURLConnection open(LinkExtractor.Item item,long deadline)throws Exception;}
    static LinkExtractor.Result select(LinkExtractor.Result candidates,Opener opener,long budgetMs)throws Exception {
        JobControl.check();if(candidates.count(LinkExtractor.Kind.VIDEO)==0)return candidates;
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(budgetMs);
        LinkExtractor.Result result=new LinkExtractor.Result();result.source=candidates.source;result.title=candidates.title;result.description=candidates.description;result.notice=candidates.notice;
        Set<String> tried=new HashSet<>();Exception last=null;int attempts=0;
        for(LinkExtractor.Item item:candidates.items){
            if(item.kind!=LinkExtractor.Kind.VIDEO||!tried.add(item.url))continue;
            if(attempts++>=24||System.nanoTime()>=deadline)break;
            JobControl.check();
            try{
                // Segmented streams and native selectors retain their existing engine path.
                if(!item.engine&&!LinkExtractor.segmented(item.url))probe(item,opener,deadline);
                JobControl.check();result.addItem(item);
                for(LinkExtractor.Item other:candidates.items)if(other.kind!=LinkExtractor.Kind.VIDEO)result.addItem(other);
                return result;
            }catch(Exception error){JobControl.check();last=error;}
        }
        throw new IOException("已找到视频候选，但直链预检未通过："+(last==null?"预检超时":ExtractionErrors.message(last)),last);
    }
    static LinkExtractor.Result verifyAll(LinkExtractor.Result candidates,Opener opener,long budgetMs)throws Exception {
        JobControl.check();long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(budgetMs);
        LinkExtractor.Result result=new LinkExtractor.Result();result.source=candidates.source;result.title=candidates.title;result.description=candidates.description;result.notice=candidates.notice;
        Exception last=null;int rejected=0;
        for(LinkExtractor.Item item:candidates.items){
            if(item.kind!=LinkExtractor.Kind.VIDEO){result.addItem(item);continue;}
            try{JobControl.check();if(System.nanoTime()>=deadline)throw new IOException("视频预检超时");
                if(!item.engine)probe(item,opener,deadline);result.addItem(item);
            }catch(Exception error){JobControl.check();last=error;rejected++;}
        }
        if(result.items.isEmpty()&&rejected>0)throw new IOException("网站候选未通过媒体预检，可能返回网页或地址已过期",last);
        if(rejected>0)result.notice=(result.notice.isEmpty()?"":result.notice+"\n")+"已过滤 "+rejected+" 个无效视频候选";
        return result;
    }
    private static void probe(LinkExtractor.Item item,Opener opener,long deadline)throws Exception {
        HttpURLConnection connection=null;
        try{
            connection=opener.open(item,deadline);JobControl.check();
            int status=connection.getResponseCode();if(status!=200&&status!=206)throw new IOException("视频候选返回 HTTP "+status);
            if(System.nanoTime()>=deadline)throw new IOException("视频预检超时");
            String encoding=connection.getContentEncoding();if(encoding!=null&&!encoding.isEmpty()&&!encoding.equalsIgnoreCase("identity"))throw new IOException("视频候选返回压缩传输");
            long length=connection.getContentLengthLong(),total=length;
            if(status==206){
                Matcher range=Pattern.compile("bytes 0-(\\d+)/(\\d+|\\*)",Pattern.CASE_INSENSITIVE).matcher(String.valueOf(connection.getHeaderField("Content-Range")));
                if(!range.matches())throw new IOException("视频候选返回错误的起始范围");
                long end=Long.parseLong(range.group(1));total=range.group(2).equals("*")?-1:Long.parseLong(range.group(2));
                if(total>=0&&end>=total||length>=0&&length!=end+1)throw new IOException("视频候选范围无效");
            }
            if(total==0||total>MediaLimits.DOWNLOAD)throw new IOException("视频候选为空或超过 2GB 上限");
            String mime=connection.getContentType();mime=mime==null?"":mime.toLowerCase(Locale.ROOT);
            boolean manifest=mime.contains("mpegurl")||mime.contains("dash+xml")||LinkExtractor.segmented(item.url);
            if(mime.contains("html")||mime.contains("json")||mime.startsWith("text/")&&!manifest)throw new IOException("视频候选返回网页或错误响应");
            byte[] bytes=new byte[64];int count=0;
            try(InputStream in=connection.getInputStream()){
                while(count<bytes.length){JobControl.check();if(System.nanoTime()>=deadline)throw new IOException("视频预检超时");int n=in.read(bytes,count,bytes.length-count);if(n<0)break;count+=n;}
            }
            if(System.nanoTime()>=deadline)throw new IOException("视频预检超时");
            if(manifest){String prefix=new String(bytes,0,count,java.nio.charset.StandardCharsets.UTF_8).replace("\uFEFF","").trim();
                if(prefix.startsWith("#EXTM3U")||prefix.contains("<MPD"))return;
                throw new IOException("视频清单地址未返回有效清单");
            }
            if(count<12||!MediaSignature.accepts("video/mp4",bytes,count))throw new IOException("视频候选未返回媒体文件");
        }finally{if(connection!=null)connection.disconnect();}
    }
}
