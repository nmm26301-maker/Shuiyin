package com.zizai.watermark;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded fan-out: providers only search; all returned items use the existing downloader. */
final class ParallelSearch {
    interface Work {LinkExtractor.Result run()throws Exception;}
    interface Status {void update(String message);}
    static final class Source {
        final String name;final Work work;
        Source(String name,Work work){this.name=name;this.work=work;}
    }
    private static final class Outcome {
        final String name;final LinkExtractor.Result result;final String error;
        Outcome(String name,LinkExtractor.Result result,String error){this.name=name;this.result=result;this.error=error;}
    }
    static LinkExtractor.Result run(String share,List<Source> sources,AtomicBoolean cancelled,long timeoutMs,Status status)throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(sources.size());
        CompletionService<Outcome> finished=new ExecutorCompletionService<>(pool);
        List<Future<Outcome>> jobs=new ArrayList<>();List<AtomicBoolean> flags=new ArrayList<>();
        Map<String,LinkExtractor.Result> found=new HashMap<>();Map<String,String> states=new LinkedHashMap<>();LinkExtractor.Result combined=new LinkExtractor.Result();combined.source=share;
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        try{
            for(Source source:sources){
                states.put(source.name,"搜索中");AtomicBoolean flag=new AtomicBoolean();flags.add(flag);
                jobs.add(finished.submit(()->{JobControl.attach(flag);try{return new Outcome(source.name,source.work.run(),"");}
                    catch(Exception e){return new Outcome(source.name,null,ExtractionErrors.message(e));}catch(LinkageError e){return new Outcome(source.name,null,"解析组件不可用");}finally{JobControl.detach();}}));
            }
            int remaining=sources.size();
            while(remaining>0){
                JobControl.check();if(cancelled!=null&&cancelled.get())throw new java.io.InterruptedIOException("已取消搜索");
                long left=deadline-System.nanoTime();if(left<=0)break;
                Future<Outcome> job=finished.poll(Math.min(left,TimeUnit.MILLISECONDS.toNanos(200)),TimeUnit.NANOSECONDS);if(job==null)continue;
                Outcome outcome=job.get();remaining--;LinkExtractor.Result result=outcome.result;
                if(result!=null&&!result.items.isEmpty()){
                    found.put(outcome.name,result);
                    states.put(outcome.name,"找到 "+result.items.size()+" 项");
                }else states.put(outcome.name,outcome.error.isEmpty()?"未找到素材":outcome.error);
                if(status!=null)status.update(summary(states));
            }
            for(Source source:sources){LinkExtractor.Result result=found.get(source.name);if(result==null)continue;
                for(LinkExtractor.Item item:result.items)combined.addItem(new LinkExtractor.Item(item.kind,item.url,source.name+" · "+item.label,item.referer,item.engine,item.engineIndex,item.userAgent,share,item.fallbackUrl));
                if(combined.title.equals("网页素材")&&!result.title.isEmpty())combined.title=result.title;
                if(combined.description.isEmpty())combined.description=result.description;
            }
            states.replaceAll((name,state)->state.equals("搜索中")?"搜索超时":state);combined.notice=summary(states);
            if(combined.items.isEmpty())throw new SearchFailure("四路搜索未取得素材",states);
            return combined;
        }finally{
            for(AtomicBoolean flag:flags)flag.set(true);for(Future<?> job:jobs)job.cancel(true);pool.shutdownNow();
        }
    }
    private static String summary(Map<String,String> states){StringBuilder text=new StringBuilder();for(Map.Entry<String,String> e:states.entrySet()){if(text.length()>0)text.append('\n');text.append(e.getKey()).append("：").append(e.getValue());}return text.toString();}
}
