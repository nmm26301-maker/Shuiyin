package com.zizai.watermark;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import org.json.*;

/** Persisted per-link work, with partial completion retained across retry. */
final class LinkSaveQueue {
    static final int MAX_LINKS=20;
    enum State {WAITING,RESOLVING,SAVING,DONE,FAILED,PAUSED}
    enum Mode {MEDIA,IMAGES,AUDIO}
    static final class Entry {
        final String id,url;final Mode mode;
        State state=State.WAITING;String title="",error="";
        final LinkedHashMap<String,String> saved=new LinkedHashMap<>();
        Entry(String url,Mode mode){this(UUID.randomUUID().toString(),url,mode);}
        Entry(String id,String url,Mode mode){this.id=id;this.url=url;this.mode=mode;}
        void retry(){if(state==State.FAILED||state==State.PAUSED){state=State.WAITING;error="";}}
    }
    final ArrayList<Entry> entries=new ArrayList<>();
    int add(List<String> urls,Mode mode)throws IOException {
        ArrayList<String> fresh=new ArrayList<>();for(String url:urls){boolean exists=false;for(Entry e:entries)if(e.url.equals(url)&&e.mode==mode){exists=true;break;}if(!exists&&!fresh.contains(url))fresh.add(url);}
        if(entries.size()+fresh.size()>MAX_LINKS)throw new IOException("队列最多保留 20 条链接，请先清理完成项目");
        for(String url:fresh){if(!EngineMetadata.https(url))throw new IOException("请添加有效 HTTPS 分享链接");}
        for(String url:fresh)entries.add(new Entry(url,mode));return fresh.size();
    }
    void recover(){for(Entry e:entries)if(e.state==State.RESOLVING||e.state==State.SAVING){e.state=State.PAUSED;e.error="上次处理中断，点继续后重新解析";}}
    void retryFailed(){for(Entry e:entries)e.retry();}
    void clearDone(){entries.removeIf(e->e.state==State.DONE);}
    static List<LinkExtractor.Item> choose(LinkExtractor.Result result,Mode mode){
        ArrayList<LinkExtractor.Item> selected=new ArrayList<>();boolean hasVideo=result.count(LinkExtractor.Kind.VIDEO)>0;
        for(LinkExtractor.Item item:result.items){
            if(mode==Mode.AUDIO){if(item.kind==LinkExtractor.Kind.AUDIO)selected.add(item);}
            else if(mode==Mode.IMAGES){if(item.kind==LinkExtractor.Kind.IMAGE&&!cover(item))selected.add(item);}
            else if(item.kind==LinkExtractor.Kind.VIDEO||item.kind==LinkExtractor.Kind.IMAGE&&!cover(item)||item.kind==LinkExtractor.Kind.AUDIO&&!hasVideo)selected.add(item);
        }
        // Audio-only mode can reuse the existing video-to-audio download path.
        if(mode==Mode.AUDIO&&selected.isEmpty())for(LinkExtractor.Item item:result.items)if(item.kind==LinkExtractor.Kind.VIDEO)selected.add(item);
        return selected;
    }
    static boolean cover(LinkExtractor.Item item){return item.label.contains("封面")||item.label.contains("缩略图");}
    static String key(LinkExtractor.Item item){
        if(item.engine)return item.kind+"|engine|"+item.engineIndex;
        try{URI uri=new URI(item.url);return item.kind+"|"+item.label+"|"+uri.getHost()+"|"+uri.getRawPath();}catch(Exception e){return item.kind+"|"+item.label+"|"+item.url;}
    }
    String encode()throws JSONException {
        JSONArray array=new JSONArray();for(Entry e:entries){JSONObject saves=new JSONObject();for(Map.Entry<String,String> s:e.saved.entrySet())saves.put(s.getKey(),s.getValue());
            array.put(new JSONObject().put("id",e.id).put("url",e.url).put("mode",e.mode.name()).put("state",e.state.name()).put("title",e.title).put("error",e.error).put("saved",saves));}
        return new JSONObject().put("schema",1).put("entries",array).toString();
    }
    static LinkSaveQueue decode(String value){
        LinkSaveQueue queue=new LinkSaveQueue();try{JSONObject root=new JSONObject(value);if(root.optInt("schema")!=1)return queue;JSONArray entries=root.optJSONArray("entries");if(entries==null)return queue;
            for(int i=0;i<Math.min(MAX_LINKS,entries.length());i++)try{JSONObject j=entries.getJSONObject(i);String url=j.getString("url");if(!EngineMetadata.https(url))continue;
                Entry e=new Entry(j.getString("id"),url,Mode.valueOf(j.getString("mode")));e.state=State.valueOf(j.getString("state"));e.title=j.optString("title");e.error=j.optString("error");
                JSONObject saved=j.optJSONObject("saved");if(saved!=null){Iterator<String> keys=saved.keys();while(keys.hasNext()&&e.saved.size()<24){String key=keys.next();String uri=saved.optString(key);if(uri.startsWith("content://"))e.saved.put(key,uri);}}
                queue.entries.add(e);
            }catch(Exception ignored){}
        }catch(Exception ignored){}return queue;
    }
    static String label(State state){switch(state){case WAITING:return "等待保存";case RESOLVING:return "正在解析";case SAVING:return "正在保存";case DONE:return "保存完成";case FAILED:return "保存失败";default:return "已暂停 · 可继续";}}
}
