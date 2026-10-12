package com.zizai.watermark;

import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Actual host Tesseract output -> production classification -> production video filters. */
public final class OcrFixtureTest {
    public static void main(String[] args)throws Exception {
        JSONArray rows=new JSONArray(Files.readString(Paths.get(args[0])));List<AutoWatermarkRules.Text> texts=new ArrayList<>();
        for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);texts.add(new AutoWatermarkRules.Text(r.getString("text"),(float)r.getDouble("confidence"),r.getInt("left"),r.getInt("top"),r.getInt("right"),r.getInt("bottom")));}
        float[][] boxes=AutoWatermarkRules.detect(Arrays.asList(texts,texts,texts,texts),1000,1000,0,1000);
        if(boxes==null||boxes.length!=2)throw new AssertionError("Expected platform and creator boxes: "+Arrays.deepToString(boxes));
        for(float[] b:boxes)if(b[0]<.63&&b[2]>.37)throw new AssertionError("subtitle was selected");
        Files.writeString(Paths.get(args[1]),new JSONArray(VideoFilters.exportRegionArguments(args[2],args[3],1,VideoFilters.regions(boxes,1000,1000),false)).toString());
        System.out.println("PASS actual OCR: "+rows.length()+" recognized entries -> two automatic repair boxes, central subtitle protected");
    }
}
