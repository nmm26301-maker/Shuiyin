package com.zizai.watermark;

import java.io.IOException;
import java.util.Map;

/** Bounded provider diagnostics that survive user-facing error normalization. */
final class SearchFailure extends IOException {
    SearchFailure(String title,Map<String,String> states){super(report(title,states));}
    private static String report(String title,Map<String,String> states){
        StringBuilder out=new StringBuilder(title);
        for(Map.Entry<String,String> state:states.entrySet()){
            if(out.length()>3600)break;
            out.append('\n').append(clean(state.getKey(),60)).append('：').append(clean(state.getValue(),800));
        }
        return out.toString();
    }
    static String clean(String text,int limit){
        if(text==null)return "未返回原因";
        String safe=text.replaceAll("(?i)https?://[^\\s<>\"']+","[链接已省略]")
            .replaceAll("(?im)[\"']?(?:cookie|set-cookie|authorization)[\"']?\\s*[:=][^\\r\\n]*","[会话已隐藏]")
            .replaceAll("(?i)[\"']?(?:api[-_ ]?key|access[-_ ]?token|token|signature|sig|xsec[-_ ]?token)[\"']?\\s*[:=]\\s*[^\\s；，\\r\\n]+","[凭据已隐藏]")
            .replaceAll("[\\p{Cc}&&[^\\n\\t]]","");
        return safe.length()>limit?safe.substring(0,limit)+"…":safe;
    }
}
