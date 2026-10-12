package com.zizai.watermark;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AutoProcessingTests {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static AutoWatermarkRules.Text text(String s,int l,int t,int r,int b){return new AutoWatermarkRules.Text(s,92,l,t,r,b);}
    static List<AutoWatermarkRules.Text> frame(){return Arrays.asList(text("@creator",30,930,260,970),text("TikTok",800,930,965,970),text("字幕不能擦除",390,940,610,985));}
    static final class Work implements TransformTransaction.Work<String> {
        final List<String> events=new ArrayList<>();boolean skipRepair,skipAudio,failAudio,failPublish,cancel;
        public String repair(String in){events.add("repair "+in);return skipRepair?in:"repaired";}
        public String audio(String in)throws Exception{events.add("audio "+in);if(failAudio)throw new IOException("audio failed");if(cancel)JobControl.flag().set(true);return skipAudio?in:"vocals";}
        public void publish(String out)throws Exception{events.add("publish "+out);if(failPublish)throw new IOException("publish failed");}
        public void discard(String out){events.add("discard "+out);}
    }
    public static void main(String[] args)throws Exception {
        float[][] boxes=AutoWatermarkRules.detect(Arrays.asList(frame(),frame(),frame(),frame()),1000,1000,0,1000);
        check(boxes!=null&&boxes.length==2,"both platform and creator found without selecting rectangles");
        RepairRegions.validate(boxes);for(float[] b:boxes)check(b[2]<=.38||b[0]>=.62,"central subtitles untouched");
        check(AutoWatermarkRules.detect(Collections.singletonList(Collections.singletonList(text("ordinary caption",10,935,260,975))),1000,1000,0,1000)==null,"arbitrary edge text is not an image watermark");
        check(AutoWatermarkRules.detect(Collections.singletonList(Collections.singletonList(text("TikTok",400,935,610,975))),1000,1000,0,1000)==null,"even platform words in central subtitles are protected");
        check(AutoWatermarkRules.detect(Arrays.asList(frame(),Collections.emptyList(),Collections.emptyList(),Collections.emptyList()),1000,1000,0,1000)==null,"a transient video label is not applied to the whole video");
        List<AutoWatermarkRules.Text> letterbox=Arrays.asList(text("TikTok",800,830,965,865),text("@author",20,835,210,865));
        check(AutoWatermarkRules.detect(Arrays.asList(letterbox,letterbox),1000,1000,100,880).length==2,"letterbox active area coordinates survive");
        List<AutoWatermarkRules.Text> duplicate=new ArrayList<>(frame());duplicate.add(text("TikTok",802,930,964,970));
        check(AutoWatermarkRules.detect(Collections.singletonList(duplicate),1000,1000,0,1000).length==2,"line and word OCR boxes deduplicate");
        check(AutoWatermarkRules.detect(Collections.singletonList(Collections.singletonList(new AutoWatermarkRules.Text("TikTok",20,800,930,960,970))),1000,1000,0,1000)==null,"low confidence OCR is not erased");
        check(AutoWatermarkRules.detect(Collections.emptyList(),1000,1000,0,1000)==null,"no decoded frames is not success");
        check(AutoWatermarkRules.detect(Collections.singletonList(frame()),1000,1000,500,500)==null,"invalid active area rejected");
        Work w=new Work();check(TransformTransaction.run("original",w).equals("vocals"),"combined result returned");
        check(w.events.equals(Arrays.asList("repair original","audio repaired","publish vocals","discard repaired")),"only final result published; intermediate discarded");
        w=new Work();w.skipAudio=true;check(TransformTransaction.run("original",w).equals("repaired"),"repair only works");check(!w.events.contains("discard repaired"),"keeps final repaired output");
        w=new Work();w.skipRepair=w.skipAudio=true;check(TransformTransaction.run("original",w).equals("original"),"unmodified input delegated to outer save transaction");check(w.events.size()==2,"no duplicate publish or deletion of input");
        for(int failure=0;failure<3;failure++){
            w=new Work();w.failAudio=failure==0;w.failPublish=failure==1;w.cancel=failure==2;AtomicBoolean token=new AtomicBoolean();JobControl.attach(token);
            try{TransformTransaction.run("original",w);throw new AssertionError("expected failure");}catch(IOException expected){checks++;}finally{JobControl.detach();}
            check(w.events.contains("discard repaired"),"failed transaction removes intermediate");
            check(!w.events.contains("discard original"),"input remains owned by caller");
            if(failure>0)check(w.events.contains("discard vocals"),"uncommitted final output removed");
        }
        if(args.length>0){Path dir=Paths.get(args[0]);Files.createDirectories(dir);
            Files.writeString(dir.resolve("decode.json"),new org.json.JSONArray(BgmCommands.decode(dir.resolve("input.mp4").toString(),dir.resolve("audio.f32").toString())).toString());
            Files.writeString(dir.resolve("mux.json"),new org.json.JSONArray(BgmCommands.mux(dir.resolve("input.mp4").toString(),dir.resolve("audio.f32").toString(),dir.resolve("output.mp4").toString())).toString());
        }
        System.out.println("PASS automatic processing: "+checks+" checks");
    }
}
