package com.zizai.watermark;
import java.util.*;

/** Conservative OCR classification; never treat a central subtitle as a watermark. */
final class AutoWatermarkRules {
    static final class Text {
        final String value;final float confidence;final int left,top,right,bottom;
        Text(String v,float c,int l,int t,int r,int b){value=v;confidence=c;left=l;top=t;right=r;bottom=b;}
    }
    static boolean platform(String text){String s=text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");for(String name:new String[]{"小红书","红书","抖音","快手","西瓜视频","哔哩哔哩","微博","视频号","tiktok","douyin","kuaishou","xiaohongshu","bilibili","instagram","youtube","kwai","capcut","剪映","美拍","秒拍","火山","微视","bigo","snapchat","threads","weibo"})if(s.contains(name))return true;return false;}
    static float[][] detect(List<List<Text>> frames,int width,int height,int activeTop,int activeBottom){
        if(width<32||height<32||frames==null||frames.isEmpty()||activeTop<0||activeBottom>height||activeBottom-activeTop<32)return null;
        ArrayList<float[]> boxes=new ArrayList<>();ArrayList<Set<Integer>> seen=new ArrayList<>();ArrayList<Boolean> named=new ArrayList<>();
        float activeHeight=activeBottom-activeTop;
        for(int frame=0;frame<frames.size();frame++){
        boolean platformInFrame=false;
        for(Text candidate:frames.get(frame))if(candidate.value!=null&&candidate.confidence>=45&&platform(candidate.value)
            &&(candidate.right<width*.4f||candidate.left>width*.6f)
            &&(candidate.top<activeTop+activeHeight*.12f||candidate.bottom>activeTop+activeHeight*.88f))platformInFrame=true;
        for(Text text:frames.get(frame)){
            if(text.value==null||text.value.trim().isEmpty()||!Float.isFinite(text.confidence)||text.confidence<35)continue;
            float l=(float)text.left/width,r=(float)text.right/width,t=(text.top-activeTop)/activeHeight,b=(text.bottom-activeTop)/activeHeight;
            if(l<0||r>1||t<0||b>1||r<=l||b<=t||b-t>.09f||r-l>.43f)continue;
            // A protected central column includes subtitles, faces and centered titles.
            if(r>.37f&&l<.63f)continue;
            boolean logo=platform(text.value),bottom=b>.91f,top=t<.09f,handle=text.value.trim().startsWith("@");
            if(!bottom&&!top)continue;
            if(!logo&&(!handle&&!platformInFrame||!bottom&&!handle||text.confidence<50||text.value.trim().length()>30))continue;
            float px=.012f,py=.006f;
            // A creator avatar is usually immediately left of the bottom-left name.
            float avatar=bottom&&l<.26f&&!logo?Math.min(.075f,(b-t)*activeHeight/width*1.8f):0;
            float[] box={Math.max(.001f,l-px-avatar),Math.max(.001f,(float)text.top/height-py),Math.min(.999f,r+px),Math.min(.999f,(float)text.bottom/height+py)};
            int match=-1;for(int i=0;i<boxes.size();i++)if(overlap(box,boxes.get(i))>.25f){match=i;break;}
            if(match<0){boxes.add(box);seen.add(new HashSet<>());named.add(logo);match=boxes.size()-1;}
            else {float[] old=boxes.get(match);old[0]=Math.min(old[0],box[0]);old[1]=Math.min(old[1],box[1]);old[2]=Math.max(old[2],box[2]);old[3]=Math.max(old[3],box[3]);named.set(match,named.get(match)||logo);}
            seen.get(match).add(frame);
        }
        }
        ArrayList<float[]> result=new ArrayList<>();for(int i=0;i<boxes.size()&&result.size()<8;i++)if(frames.size()==1||seen.get(i).size()>=2)result.add(boxes.get(i));
        return result.isEmpty()?null:result.toArray(new float[0][]);
    }
    private static float overlap(float[] a,float[] b){float w=Math.max(0,Math.min(a[2],b[2])-Math.max(a[0],b[0])),h=Math.max(0,Math.min(a[3],b[3])-Math.max(a[1],b[1]));float area=w*h;return area/Math.max(.000001f,Math.min((a[2]-a[0])*(a[3]-a[1]),(b[2]-b[0])*(b[3]-b[1])));}
}
