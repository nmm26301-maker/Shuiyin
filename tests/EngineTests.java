package com.zizai.watermark;
import java.nio.file.*;
import com.yausername.youtubedl_android.YoutubeDLRequest;

public final class EngineTests {
    static int checks;
    static void check(boolean valid,String message){checks++;if(!valid)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        String share="https://example.org/shared";
        LinkExtractor.Result r=EngineMetadata.parse("{\"title\":\"标题 <保留>\",\"description\":\"第一行\\n第二行\",\"formats\":[{\"vcodec\":\"avc1\",\"acodec\":\"none\"},{\"vcodec\":\"none\",\"acodec\":\"aac\"}],\"thumbnail\":\"https://cdn.example.org/cover.jpg\"}",share);
        check(r.title.equals("标题 <保留>"),"caption is text, not HTML");
        check(r.description.contains("\n"),"multiline description retained");
        check(r.count(LinkExtractor.Kind.VIDEO)==1&&r.count(LinkExtractor.Kind.AUDIO)==1&&r.count(LinkExtractor.Kind.IMAGE)==1,"split AV formats produce video and audio choices");
        check(r.items.get(0).engine&&r.items.get(0).url.equals(share),"download re-extracts source instead of expired stream URL");
        r=EngineMetadata.parse("{\"entries\":[{\"title\":\"one\",\"vcodec\":\"h264\"},{\"title\":\"two\",\"vcodec\":\"h264\"},null]}",share);
        check(r.count(LinkExtractor.Kind.VIDEO)==2,"multiple source entries retained");
        check(r.items.get(0).engineIndex==1&&r.items.get(1).engineIndex==2,"entry selectors preserved for individual downloads");
        r=EngineMetadata.parse("{\"ext\":\"jpg\",\"url\":\"https://cdn.example.org/photo.jpg\",\"thumbnail\":\"file:///secret.jpg\"}",share);
        check(r.items.size()==1&&r.items.get(0).kind==LinkExtractor.Kind.IMAGE&&!r.items.get(0).engine,"original image and unsafe thumbnail handling");
        r=EngineMetadata.parse("{\"acodec\":\"mp3\",\"vcodec\":\"none\",\"thumbnails\":[{\"url\":\"https://cdn.example.org/art.jpg\"}]}",share);
        check(r.count(LinkExtractor.Kind.VIDEO)==0&&r.count(LinkExtractor.Kind.AUDIO)==1,"audio-only media never becomes fake video");
        check(r.count(LinkExtractor.Kind.IMAGE)==1,"thumbnail-array fallback");
        try{EngineMetadata.parse("{\"title\":\"empty\"}",share);throw new AssertionError("empty result accepted");}catch(java.io.IOException expected){checks++;}
        r=EngineMetadata.parse("{\"formats\":[{\"ext\":\"mhtml\",\"vcodec\":\"images\",\"acodec\":\"none\"}],\"thumbnail\":\"https://cdn.example.org/cover.jpg\"}",share);check(r.count(LinkExtractor.Kind.VIDEO)==0&&r.count(LinkExtractor.Kind.IMAGE)==1,"storyboards never become downloadable videos");
        r=EngineMetadata.parse("{\"formats\":[{\"ext\":\"mp4\"}]}",share);check(r.count(LinkExtractor.Kind.VIDEO)==1,"codec-less direct media format retained");
        r=EngineMetadata.parse("{\"formats\":[{\"ext\":\"m4a\"}]}",share);check(r.count(LinkExtractor.Kind.VIDEO)==0&&r.count(LinkExtractor.Kind.AUDIO)==1,"codec-less audio does not become a fake video");
        r=EngineMetadata.parse(Files.readString(Paths.get(args[0])),"https://media.w3.org/2010/05/sintel/trailer.mp4");
        check(r.count(LinkExtractor.Kind.VIDEO)==1,"real upstream generic extractor metadata");
        YoutubeDLRequest request=new YoutubeDLRequest(share);request.addOption("--dump-single-json");request.addOption("--playlist-items",2);
        java.util.List<String> command=request.buildCommand();
        check(command.get(command.size()-1).equals(share)&&command.contains("2"),"real wrapper request ABI and option serialization");
        check(!EngineMetadata.https("https://u:p@example.org/a"),"credentials URL excluded");
        System.out.println("PASS engine integration: "+checks+" checks");
    }
}
