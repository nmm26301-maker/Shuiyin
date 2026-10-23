package com.zizai.watermark;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Independent attempts: a slow or empty result cannot hold up working media. */
final class MediaSearchRace {
    static boolean usable(LinkExtractor.Result result){
        if(result==null)return false;for(LinkExtractor.Item item:result.items)if(item.kind==LinkExtractor.Kind.VIDEO||item.kind==LinkExtractor.Kind.IMAGE&&!LinkSaveQueue.cover(item))return true;return false;
    }
    static LinkExtractor.Result run(List<ParallelSearch.Source> sources,long timeoutMs)throws Exception {
        JobControl.check();
        ExecutorService pool=Executors.newFixedThreadPool(sources.size());
        CompletionService<LinkExtractor.Result> done=new ExecutorCompletionService<>(pool);
        List<Future<LinkExtractor.Result>> jobs=new ArrayList<>();List<AtomicBoolean> flags=new ArrayList<>();
        Map<String,String> errors=Collections.synchronizedMap(new LinkedHashMap<>());
        long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        try{
            for(ParallelSearch.Source source:sources){errors.put(source.name,"搜索超时");AtomicBoolean flag=new AtomicBoolean();flags.add(flag);
                jobs.add(done.submit(()->{JobControl.attach(flag);try{
                    LinkExtractor.Result result=source.work.run();
                    if(usable(result))return result;
                    errors.put(source.name,"未返回视频或图集");
                }catch(Exception e){errors.put(source.name,ExtractionErrors.message(e));}
                catch(LinkageError e){errors.put(source.name,"解析组件不可用");}finally{JobControl.detach();}return null;}));
            }
            int remaining=sources.size();
            while(remaining>0){JobControl.check();long left=end-System.nanoTime();if(left<=0)break;
                Future<LinkExtractor.Result> job=done.poll(Math.min(left,TimeUnit.MILLISECONDS.toNanos(100)),TimeUnit.NANOSECONDS);
                if(job==null)continue;remaining--;LinkExtractor.Result result=job.get();if(result!=null)return result;
            }
            synchronized(errors){throw new SearchFailure("抖音未返回可下载视频或图集。可打开原作品播放后提取。",new LinkedHashMap<>(errors));}
        }finally{for(AtomicBoolean flag:flags)flag.set(true);for(Future<?> job:jobs)job.cancel(true);pool.shutdownNow();}
    }
}
