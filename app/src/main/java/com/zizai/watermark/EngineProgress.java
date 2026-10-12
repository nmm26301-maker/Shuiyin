package com.zizai.watermark;
import org.json.JSONObject;

/** Stable yt-dlp output contract: no parsing of its human-readable log. */
final class EngineProgress {
    static final String PREFIX="__WM_PROGRESS__";
    static final String TEMPLATE="download:"+PREFIX+"%(progress.{downloaded_bytes,total_bytes,total_bytes_estimate,speed,eta,status})j";
    static final String POST_TEMPLATE="postprocess:__WM_POST__%(progress.status)s";
    static final class Sample {
        final long bytes,total;final double speed,eta;final boolean estimated;
        Sample(long b,long t,double s,double e,boolean estimate){bytes=b;total=t;speed=s;eta=e;estimated=estimate;}
    }
    static Sample parse(String line){
        if(!line.startsWith(PREFIX))return null;
        try{JSONObject o=new JSONObject(line.substring(PREFIX.length()));long total=o.optLong("total_bytes",-1);boolean estimated=false;if(total<=0){total=o.optLong("total_bytes_estimate",-1);estimated=total>0;}return new Sample(Math.max(0,o.optLong("downloaded_bytes",0)),total,o.optDouble("speed",-1),o.optDouble("eta",-1),estimated);}catch(Exception e){return null;}
    }
}
