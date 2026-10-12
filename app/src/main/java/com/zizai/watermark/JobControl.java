package com.zizai.watermark;

import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cooperative per-operation cancellation; no cancellation state leaks into later jobs. */
final class JobControl {
    private static final ThreadLocal<AtomicBoolean> CURRENT=new ThreadLocal<>();
    static void attach(AtomicBoolean flag){CURRENT.set(flag);}
    static AtomicBoolean flag(){return CURRENT.get();}
    static void detach(){CURRENT.remove();}
    static void check()throws InterruptedIOException {
        AtomicBoolean flag=CURRENT.get();
        if(Thread.currentThread().isInterrupted()||(flag!=null&&flag.get()))throw new InterruptedIOException("已取消操作");
    }
    static void checkUnchecked(){try{check();}catch(InterruptedIOException e){throw new java.util.concurrent.CancellationException("已取消操作");}}
}
