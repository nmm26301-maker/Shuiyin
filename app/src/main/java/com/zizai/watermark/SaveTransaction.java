package com.zizai.watermark;

/** Owns a pending download until it is published or replaced by a repaired result. */
final class SaveTransaction {
    interface Work<T> {
        T download() throws Exception;
        T repair(T pending) throws Exception;
        void publish(T pending) throws Exception;
        void discard(T pending);
        void complete();
    }
    static <T> T run(Work<T> work) throws Exception {
        T pending=work.download();boolean keep=false;
        try {
            JobControl.check();T result=work.repair(pending);
            if(result==null)throw new java.io.IOException("处理未生成素材");
            if(result==pending){JobControl.check();work.publish(pending);keep=true;}
            work.complete();return result;
        } finally {if(!keep)work.discard(pending);}
    }
}
