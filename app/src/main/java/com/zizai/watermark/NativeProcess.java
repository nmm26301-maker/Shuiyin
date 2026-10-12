package com.zizai.watermark;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Argument-vector execution; bounded logs, cooperative cancel and process cleanup. */
final class NativeProcess {
    interface Line {void accept(String line)throws Exception;}
    interface Guard {void check()throws Exception;}
    static String run(List<String> command,Map<String,String> environment,AtomicBoolean cancelled,long timeout,Line listener)throws Exception {
        return run(command,environment,cancelled,timeout,listener,null,null);
    }
    static String run(List<String> command,Map<String,String> environment,AtomicBoolean cancelled,long timeout,Line listener,Runnable terminateGroup,Guard guard)throws Exception {
        ProcessBuilder builder=new ProcessBuilder(command).redirectErrorStream(true);builder.environment().putAll(environment);
        Process process=builder.start();process.getOutputStream().close();StringBuilder tail=new StringBuilder();
        AtomicReference<Exception> readError=new AtomicReference<>();
        Thread reader=new Thread(()->{
            try(InputStreamReader input=new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8)){
                char[] buffer=new char[2048];StringBuilder line=new StringBuilder();int count;
                while((count=input.read(buffer))!=-1){
                    for(int i=0;i<count;i++){
                        char c=buffer[i];if(c=='\n'||c=='\r'){
                            if(line.length()>0){String text=line.toString();line.setLength(0);synchronized(tail){tail.append(text).append('\n');if(tail.length()>16000)tail.delete(0,tail.length()-16000);}if(listener!=null)listener.accept(text);}
                        }else{if(line.length()>=4_000_000)throw new IOException("处理组件输出过大");line.append(c);}
                    }
                }
                if(line.length()>0){String text=line.toString();synchronized(tail){tail.append(text);if(tail.length()>16000)tail.delete(0,tail.length()-16000);}if(listener!=null)listener.accept(text);}
            }catch(Exception e){readError.set(e);process.destroy();}
        },"native-output");reader.setDaemon(true);reader.start();long started=System.nanoTime();
        try {
            while(!process.waitFor(150,TimeUnit.MILLISECONDS)){
                if(cancelled!=null&&cancelled.get())throw new InterruptedIOException("已取消操作");
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("已取消操作");
                if(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)>timeout)throw new IOException("处理超时，请使用较短的视频");
                if(readError.get()!=null)throw readError.get();
                if(guard!=null)guard.check();
            }
            reader.join(3000);
            if(reader.isAlive())throw new IOException("处理组件输出未结束");
            if(cancelled!=null&&cancelled.get())throw new InterruptedIOException("已取消操作");
            if(readError.get()!=null)throw readError.get();String output;synchronized(tail){output=tail.toString();}
            if(process.exitValue()!=0)throw new IOException(output.isEmpty()?"视频处理组件失败":output.substring(Math.max(0,output.length()-1200)));
            return output;
        }finally{
            if(terminateGroup!=null)try{terminateGroup.run();}catch(Exception ignored){}
            if(process.isAlive())process.destroy();
            // Clean up even when Activity destruction interrupts the worker.
            boolean interrupted=Thread.interrupted();
            try{if(!process.waitFor(300,TimeUnit.MILLISECONDS)){process.destroyForcibly();process.waitFor(2000,TimeUnit.MILLISECONDS);}}
            catch(InterruptedException e){interrupted=true;process.destroyForcibly();}
            try{process.getInputStream().close();process.getErrorStream().close();}catch(IOException ignored){}
            reader.interrupt();if(interrupted)Thread.currentThread().interrupt();
        }
    }
}
