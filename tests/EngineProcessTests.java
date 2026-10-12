package com.zizai.watermark;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Real Python ZIP bootstrap, long metadata, and cancellation of a child process. */
public final class EngineProcessTests {
    private static int checks;
    private static void check(boolean valid,String why){checks++;if(!valid)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        Path script=Paths.get(args[0]).toAbsolutePath(),folder=Paths.get(args[1]).toAbsolutePath();Files.createDirectories(folder);
        AtomicInteger group=new AtomicInteger();List<String> lines=new ArrayList<>();
        NativeProcess.run(EngineCommand.build("python3",script.toString(),Arrays.asList("--version")),Collections.emptyMap(),new AtomicBoolean(),10_000,lines::add);
        check(lines.get(0).startsWith(EngineCommand.GROUP_PREFIX),"Python wrapper is isolated before yt-dlp");check(lines.contains("2026.08.19"),"real packaged yt-dlp ZIP executed");
        Path longJson=folder.resolve("long-json.py");Files.writeString(longJson,"import json\nprint(json.dumps({'title':'x'*30000}))\n");AtomicInteger length=new AtomicInteger();
        NativeProcess.run(EngineCommand.build("python3",longJson.toString(),Collections.emptyList()),Collections.emptyMap(),new AtomicBoolean(),10_000,line->{if(line.startsWith("{"))length.set(line.length());});check(length.get()>30000,"metadata line does not truncate at legacy log size");
        Path child=folder.resolve("child.py");Files.writeString(child,"import subprocess,time\np=subprocess.Popen(['python3','-c','import time; time.sleep(30)'])\nprint('CHILD='+str(p.pid),flush=True)\ntime.sleep(30)\n");
        AtomicBoolean cancel=new AtomicBoolean();AtomicInteger childPid=new AtomicInteger();long started=System.currentTimeMillis();
        try{
            NativeProcess.run(EngineCommand.build("python3",child.toString(),Collections.emptyList()),Collections.emptyMap(),cancel,35_000,line->{
                if(line.startsWith(EngineCommand.GROUP_PREFIX))group.set(Integer.parseInt(line.substring(EngineCommand.GROUP_PREFIX.length())));
                if(line.startsWith("CHILD=")){childPid.set(Integer.parseInt(line.substring(6)));cancel.set(true);}
            },()->{if(group.get()>1)try{new ProcessBuilder("kill","-KILL","--","-"+group.get()).start().waitFor();}catch(Exception error){throw new RuntimeException(error);}},null);
            throw new AssertionError("process group cancellation ignored");
        }catch(InterruptedIOException expected){checks++;}
        check(System.currentTimeMillis()-started<3000,"child group shutdown is prompt");check(group.get()>1&&childPid.get()>1,"real process IDs captured");
        Path status=Paths.get("/proc",String.valueOf(childPid.get()),"status");String state=Files.exists(status)?Files.readString(status):"State:\tZ";
        check(state.contains("State:\tZ")||!Files.exists(status),"FFmpeg-like child no longer running after cancel");
        Path localVideo=folder.resolve("../video/source.mp4").normalize();Path info=folder.resolve("local-info.json");StringBuilder json=new StringBuilder();
        NativeProcess.run(EngineCommand.build("python3",script.toString(),Arrays.asList("--enable-file-urls","--dump-single-json","--skip-download",localVideo.toUri().toString())),Collections.emptyMap(),new AtomicBoolean(),10_000,line->{if(line.startsWith("{"))json.append(line);});
        check(json.length()>0,"full real metadata recovered by runner");Files.writeString(info,json.toString());
        System.out.println("PASS engine process: "+checks+" checks; real ZIP, full metadata and child-group cleanup");
    }
}
