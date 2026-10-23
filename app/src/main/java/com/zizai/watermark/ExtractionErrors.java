package com.zizai.watermark;
import java.util.Locale;
final class ExtractionErrors {
    static String message(Throwable error){
        if(error instanceof SearchFailure)return error.getMessage();
        String raw=error==null?"":String.valueOf(error.getMessage());String s=raw.toLowerCase(Locale.ROOT);
        if(s.contains("timed out")||s.contains("timeout")||s.contains("超时"))return "访问平台超时，尚未提取到素材。请重新解析，或用网页辅助打开链接后提取。";
        if(s.contains("cookies")||s.contains("login")||s.contains("sign in")||s.contains("登录"))return "平台需要有效的访问会话。请用网页辅助打开笔记，或在菜单导入本人导出的 cookies.txt 后重试。";
        if(s.contains("unsupported url"))return "当前引擎尚未识别这类链接，请尝试网页辅助。";
        if(s.contains("invalid data found")||s.contains("error opening input"))return "下载内容未通过媒体校验，请重新解析更新地址，或尝试本人登录会话。";
        if(s.contains("certificate")||s.contains("ssl"))return "平台安全连接失败，请检查手机时间和网络后重试。";
        if(s.contains("unable to download")||s.contains("connection")||s.contains("network")||s.contains("unknownhost"))return "无法连接素材服务器，请重新解析或尝试网页辅助。";
        if(raw.matches("(?s).*\\p{IsHan}.*")&&!raw.contains("__WM_GROUP__")&&!raw.contains("Traceback")&&!raw.contains("ERROR:")&&!raw.contains("/data/")&&raw.length()<300)return raw;
        return "平台未返回可下载的素材。请用网页辅助打开链接确认可访问，再提取。";
    }
}
