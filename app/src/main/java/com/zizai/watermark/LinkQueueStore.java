package com.zizai.watermark;
import android.content.Context;
import java.io.IOException;
final class LinkQueueStore {
    static LinkSaveQueue read(Context context){return LinkSaveQueue.decode(context.getSharedPreferences("link_queue",0).getString("data",""));}
    static void save(Context context,LinkSaveQueue queue)throws Exception {
        if(!context.getSharedPreferences("link_queue",0).edit().putString("data",queue.encode()).commit())throw new IOException("队列记录写入失败，请检查手机空间");
    }
    static void recover(Context context){try{LinkSaveQueue queue=read(context);queue.recover();save(context,queue);}catch(Exception ignored){}}
}
