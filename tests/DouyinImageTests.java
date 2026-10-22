package com.zizai.watermark;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.json.*;

public final class DouyinImageTests {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static String state(JSONObject item){return new JSONObject().put("loaderData",new JSONObject().put("video_(id)/page",new JSONObject().put("videoInfoRes",new JSONObject().put("item_list",new JSONArray().put(item))))).toString();}
    static JSONObject video()throws Exception{return new JSONObject("{\"aweme_id\":\"7312345678901234567\",\"desc\":\"含 } 和 undefined 的标题\",\"video\":{\"play_addr\":{\"url_list\":[\"http://aweme.snssdk.com/aweme/v1/playwm/?video_id=abc&sig=a%2Fb\"]}}}");}
    public static void main(String[] args)throws Exception {
        String share="https://www.douyin.com/video/7312345678901234567?x=1";
        check(DouyinParser.matches(share)&&DouyinParser.matches("https://www.iesdouyin.com/share/video/7312345678901234567/"),"Douyin hosts");check(!DouyinParser.matches("https://douyin.com.evil.org/video/7312345678901234567"),"host lookalike excluded");
        check(DouyinParser.sharePage(share).contains("iesdouyin.com/share/video/7312345678901234567"),"mobile share canonicalization");
        check(DouyinParser.id("https://www.douyin.com/?modal_id=7312345678901234567&x=1").equals("7312345678901234567"),"modal share ID");
        LinkExtractor.Result result=DouyinParser.parseHtml("<script>window._ROUTER_DATA="+state(video())+";</script>",share);
        check(result.items.size()==1&&result.items.get(0).url.startsWith("https://aweme.snssdk.com/aweme/v1/play/"),"public non-watermarked play candidate");check(result.items.get(0).url.endsWith("a%2Fb"),"query signature preserved");check(result.description.contains("undefined"),"quoted braces and literal undefined preserved");
        result=DouyinParser.parseHtml("<script id=\"RENDER_DATA\">"+URLEncoder.encode(state(video()),StandardCharsets.UTF_8.name())+"</script>",share);check(result.count(LinkExtractor.Kind.VIDEO)==1,"URL-encoded RENDER_DATA supported");
        JSONObject other=video().put("aweme_id","7312345678901234568");try{DouyinParser.parseHtml("window._ROUTER_DATA="+state(other),share);throw new AssertionError("wrong work");}catch(IOException expected){checks++;}
        JSONObject photo=video().put("images",new JSONArray().put(new JSONObject().put("url_list",new JSONArray().put("https://p3.douyinpic.com/a.jpg").put("https://p9.douyinpic.com/a.jpg"))));result=DouyinParser.parseHtml("window._ROUTER_DATA="+state(photo),share);check(result.count(LinkExtractor.Kind.IMAGE)==1&&result.count(LinkExtractor.Kind.VIDEO)==0,"one photo candidate per slide; no background music classified video");
        JSONObject drm=video();drm.getJSONObject("video").put("is_drm",1);try{DouyinParser.parseHtml("window._ROUTER_DATA="+state(drm),share);throw new AssertionError("DRM");}catch(IOException expected){checks++;}
        JSONObject image=new JSONObject().put("urlDefault","http://sns-webpic-qc.xhscdn.com/abc!nd_dft_wlteh_webp_3");
        check(XhsImages.primary(image).equals("https://sns-webpic-qc.xhscdn.com/abc"),"unsigned XHS transform removed for original attempt");check(XhsImages.fallback(image).contains("!nd_dft"),"original served URL retained as fallback");
        image.put("urlOriginal","https://cdn.example.org/full.jpg?sign=a%2Bb");check(XhsImages.primary(image).endsWith("sign=a%2Bb"),"published original preferred and signature preserved");
        String signed="https://sns-webpic-qc.xhscdn.com/abc!nd_dft_webp?sign=a%2Bb";check(XhsImages.originalCandidate(signed).equals(signed),"signed transform untouched");
        String unrelated="https://cdn.example.org/abc!nd_dft_webp";check(XhsImages.originalCandidate(unrelated).equals(unrelated),"only XHS CDN transformed");
        image=new JSONObject().put("urlDefault","https://sns-webpic-qc.xhscdn.com/preview!nd_dft_webp").put("infoList",new JSONArray().put(new JSONObject().put("imageScene","WB_DFT").put("url","https://sns-webpic-qc.xhscdn.com/large!nd_dft_webp")));
        check(XhsImages.primary(image).endsWith("/large"),"default large scene preferred over preview");
        float[][] boxes=RepairRegions.validate(RepairRegions.suggestions(true,true));check(boxes.length==1&&boxes[0][0]>.5f&&boxes[0][1]>.8f,"XHS photos suggest bottom-right watermark area");
        check(RepairRegions.suggestions(false,true)==null,"no assumed area on other photos");
        System.out.println("PASS Douyin and XHS photos: "+checks+" checks");
    }
}
