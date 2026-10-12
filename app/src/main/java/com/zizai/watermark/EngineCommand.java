package com.zizai.watermark;

import java.util.ArrayList;
import java.util.List;

/** Isolate yt-dlp and its FFmpeg children in a process group before running the ZIP. */
final class EngineCommand {
    static final String GROUP_PREFIX="__WM_GROUP__";
    private static final String BOOTSTRAP="import os,sys,runpy\ntry:\n os.setsid()\nexcept OSError:\n pass\nprint('__WM_GROUP__'+str(os.getpgrp() if os.getpgrp()==os.getpid() else 0),flush=True)\nscript=sys.argv[1]\nsys.argv=sys.argv[1:]\nrunpy.run_path(script,run_name='__main__')\n";
    static List<String> build(String python,String script,List<String> arguments){
        List<String> command=new ArrayList<>();java.util.Collections.addAll(command,python,"-c",BOOTSTRAP,script);command.addAll(arguments);return command;
    }
}
