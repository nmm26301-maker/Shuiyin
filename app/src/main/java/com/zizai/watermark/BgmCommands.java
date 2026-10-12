package com.zizai.watermark;

import java.util.Arrays;
import java.util.List;

/** Shared production FFmpeg arguments, also exercised on real host media. */
final class BgmCommands {
    static final long MAX_SECONDS=30*60, MAX_PCM_BYTES=MAX_SECONDS*44100*8;
    static List<String> decode(String input,String pcm) {
        return Arrays.asList("-hide_banner","-nostdin","-y","-loglevel","warning","-threads","2",
            "-i",input,"-map","0:a:0","-vn","-af","aresample=44100:async=1:first_pts=0",
            "-ac","2","-ar","44100","-c:a","pcm_f32le","-f","f32le",
            "-progress","pipe:1","-nostats",pcm);
    }
    static List<String> mux(String input,String pcm,String output) {
        return Arrays.asList("-hide_banner","-nostdin","-y","-loglevel","warning","-threads","2",
            "-i",input,"-f","f32le","-ar","44100","-ac","2","-i",pcm,
            "-map","0:v:0","-map","1:a:0","-c:v","copy","-c:a","aac","-b:a","160k",
            "-af","apad","-shortest","-map_metadata","0","-movflags","+faststart",
            "-progress","pipe:1","-nostats",output);
    }
}
