package com.zizai.watermark;

import org.json.*;

/** Shared result decoding for automatic and visible original-page extraction. */
final class DouyinBrowserMedia {
    static LinkExtractor.Result parse(JSONObject object,String source,String page,String agent)throws Exception {
        LinkExtractor.Result result=new LinkExtractor.Result();result.source=source;result.title="抖音网页素材";
        if(!PlaybackRequests.onWorkPage(source,page))return result;
        String state=object.optString("douyin","");
        if(!state.isEmpty())result=DouyinParser.parseState(new JSONObject(state),page);
        result.source=source;
        JSONArray playing=object.optJSONArray("media");
        if(playing!=null)for(int i=0;i<Math.min(48,playing.length());i++){
            JSONObject item=playing.optJSONObject(i);if(item==null||!"VIDEO".equals(item.optString("kind"))||!item.optBoolean("active",false))continue;
            String url=item.optString("url");if(EngineMetadata.https(url))result.add(LinkExtractor.Kind.VIDEO,url,"抖音当前页面视频");
        }
        LinkExtractor.Result copy=new LinkExtractor.Result();copy.source=source;copy.title=result.title;copy.description=result.description;
        for(LinkExtractor.Item item:result.items)copy.addItem(new LinkExtractor.Item(item.kind,item.url,item.label,page,false,0,agent,source));
        return copy;
    }
}
