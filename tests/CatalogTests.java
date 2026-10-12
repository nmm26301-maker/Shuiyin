package com.zizai.watermark;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.yausername.youtubedl_android.YoutubeDLRequest;
public final class CatalogTests {
    public static void main(String[] args)throws Exception {
        Path engine=Paths.get(args[0]),catalog=Paths.get(args[1]);List<String> actual=new ArrayList<>();
        YoutubeDLRequest request=new YoutubeDLRequest("--list-extractors");
        NativeProcess.run(EngineCommand.build("python3",engine.toAbsolutePath().toString(),request.buildCommand()),Collections.emptyMap(),new AtomicBoolean(),20_000,line->{if(ExtractorCatalog.line(line))actual.add(line);});
        if(!actual.equals(Files.readAllLines(catalog)))throw new AssertionError("catalog differs from actual library request output");
        if(actual.size()<1700)throw new AssertionError("truncated rule directory");
        if(actual.stream().noneMatch(s->s.contains("CURRENTLY BROKEN")))throw new AssertionError("upstream broken status lost");
        if(ExtractorCatalog.line("ERROR: a website failed")||ExtractorCatalog.line("WARNING: something changed"))throw new AssertionError("diagnostics mistaken for rules");
        System.out.println("PASS catalog: 4 checks; actual YoutubeDLRequest and Python bootstrap return "+actual.size()+" complete rules with broken status");
    }
}
