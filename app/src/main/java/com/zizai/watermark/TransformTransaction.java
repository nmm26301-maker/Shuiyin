package com.zizai.watermark;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Owns unpublished intermediate outputs; the caller still owns the input. */
final class TransformTransaction {
    interface Work<T> {
        T repair(T input) throws Exception;
        T audio(T input) throws Exception;
        void publish(T result) throws Exception;
        void discard(T intermediate);
    }
    static <T> T run(T input, Work<T> work) throws Exception {
        List<T> owned=new ArrayList<>();T result=input;boolean success=false;
        try {
            JobControl.check();result=work.repair(input);
            if(result==null)throw new IOException("去水印未生成输出");
            if(!result.equals(input))owned.add(result);
            JobControl.check();T next=work.audio(result);
            if(next==null)throw new IOException("音频处理未生成输出");
            result=next;if(!result.equals(input)&&!owned.contains(result))owned.add(result);
            JobControl.check();if(!result.equals(input))work.publish(result);
            success=true;return result;
        } finally {
            for(T output:owned)if(!success||!output.equals(result))try{work.discard(output);}catch(Exception ignored){}
        }
    }
}
