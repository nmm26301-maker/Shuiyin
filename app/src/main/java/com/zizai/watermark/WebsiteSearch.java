package com.zizai.watermark;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.webkit.*;
import java.io.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.*;

/** Uses each site's own form. No guessed JSON endpoints or challenge bypass. */
final class WebsiteSearch {
    private static final String[] INPUT={"#link-area","#main_page_text","input.n-input__input-el"};
    private static final String[] BUTTON={"#download-button","#submit","button.button-1"};
    static LinkExtractor.Result parse(Activity activity,int index,String source)throws Exception {
        JobControl.check();DownloadSites.Site site=DownloadSites.ALL.get(index);
        String script;try(InputStream in=activity.getResources().openRawResource(R.raw.search_sites);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);script=out.toString("UTF-8");
        }
        JSONObject config=new JSONObject().put("source",source).put("input",INPUT[index]).put("button",BUTTON[index]);
        Session session=new Session(activity,site,source,script.replace("/*CONFIG*/",config.toString()));
        session.ui.post(session::start);
        try{
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(38);
            while(!session.latch.await(150,TimeUnit.MILLISECONDS)){JobControl.check();if(System.nanoTime()>deadline)break;}
            JobControl.check();synchronized(session.result){if(!session.result.items.isEmpty()){LinkExtractor.Result copy=new LinkExtractor.Result();copy.source=session.result.source;copy.title=session.result.title;for(LinkExtractor.Item item:session.result.items)copy.addItem(item);return copy;}}
            throw new IOException(session.error.isEmpty()?"网站超时或未返回可下载素材":session.error);
        }finally{session.ui.post(session::close);}
    }
    private static final class Session {
        final Activity activity;final DownloadSites.Site site;final String script;final Handler ui=new Handler(Looper.getMainLooper());
        final CountDownLatch latch=new CountDownLatch(1);final LinkExtractor.Result result=new LinkExtractor.Result();
        WebView view;boolean closed;volatile String error="";int stable;final Runnable tick=this::poll;
        Session(Activity a,DownloadSites.Site s,String source,String script){activity=a;site=s;this.script=script;result.source=source;result.title=s.name+" 素材";}
        void start(){
            if(closed||activity.isDestroyed()||activity.isFinishing()){close();return;}
            try{
                view=new WebView(activity);WebSettings settings=view.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
                settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setMediaPlaybackRequiresUserGesture(true);
                CookieManager.getInstance().setAcceptThirdPartyCookies(view,false);WebSessionCookies.enable();
                view.setDownloadListener((url,agent,disposition,mime,length)->capture(url,mime,disposition,agent));
                view.setWebViewClient(new WebViewClient(){
                    @Override public boolean shouldOverrideUrlLoading(WebView browser,WebResourceRequest request){
                        String url=request.getUrl().toString();if(!DownloadSites.mediaKind(url,"","").isEmpty()){capture(url,"","",settings.getUserAgentString());return true;}
                        return !site.owns(url);
                    }
                    @Override public WebResourceResponse shouldInterceptRequest(WebView browser,WebResourceRequest request){
                        try{LinkExtractor.ensurePublic(request.getUrl().toString());return null;}catch(Exception e){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
                    }
                    @Override public void onPageFinished(WebView browser,String page){if(!closed&&site.owns(page)){ui.removeCallbacks(tick);poll();}}
                    @Override public void onReceivedError(WebView browser,WebResourceRequest request,WebResourceError e){if(request.isForMainFrame()){error="网站加载失败";close();}}
                });
                view.loadUrl(site.url);
            }catch(RuntimeException e){error="网页解析组件不可用";close();}
        }
        void capture(String url,String mime,String disposition,String agent){
            String kind=DownloadSites.mediaKind(url,mime,disposition);if(kind.isEmpty())return;
            synchronized(result){result.addItem(new LinkExtractor.Item(LinkExtractor.Kind.valueOf(kind),url,"网站下载素材",site.url,false,0,agent,result.source));}
        }
        void poll(){
            if(closed||view==null)return;
            view.evaluateJavascript(script,value->{
                if(closed||view==null)return;
                try{Object raw=new JSONTokener(value).nextValue();if(raw instanceof String&&((String)raw).length()<500000){
                    JSONObject object=new JSONObject((String)raw);error=object.optString("error");JSONArray items=object.optJSONArray("items");int before;
                    synchronized(result){before=result.items.size();if(items!=null)for(int i=0;i<Math.min(24,items.length());i++){
                        JSONObject item=items.optJSONObject(i);if(item==null)continue;try{String url=item.optString("url");if(EngineMetadata.https(url))result.addItem(new LinkExtractor.Item(LinkExtractor.Kind.valueOf(item.optString("kind")),url,item.optString("label","网站素材"),site.url,false,0,view.getSettings().getUserAgentString(),result.source));}catch(IllegalArgumentException ignored){}
                    }stable=result.items.size()>0&&result.items.size()==before?stable+1:0;}
                    if(stable>=3||!error.isEmpty()){close();return;}
                }}catch(Exception ignored){}
                ui.postDelayed(tick,500);
            });
        }
        void close(){if(closed)return;closed=true;ui.removeCallbacks(tick);if(view!=null){view.stopLoading();view.destroy();view=null;}latch.countDown();}
    }
}
