package com.zizai.watermark;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.*;
import android.view.ViewGroup;
import android.webkit.*;
import android.widget.FrameLayout;
import java.io.*;
import java.util.concurrent.*;
import org.json.*;

/** Loads the ordinary public work page; uses only its selected-work media. */
final class DouyinWebSearch {
    static LinkExtractor.Result parse(Activity activity,String source)throws Exception {
        JobControl.check();
        Session session=new Session(activity,source);session.ui.post(session::start);
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(34);
        try{
            while(!session.done.await(100,TimeUnit.MILLISECONDS)){JobControl.check();if(System.nanoTime()>=end)break;}
            JobControl.check();LinkExtractor.Result copy=null;synchronized(session.result){
                if(!LinkSaveQueue.choose(session.result,LinkSaveQueue.Mode.MEDIA).isEmpty()){
                    copy=new LinkExtractor.Result();copy.source=source;copy.title=session.result.title;copy.description=session.result.description;
                    for(LinkExtractor.Item item:session.result.items)copy.addItem(item);
                }
            }
            if(copy!=null){session.ui.post(session::close);long remaining=Math.max(0,Math.min(8000,TimeUnit.NANOSECONDS.toMillis(end-System.nanoTime())));return LinkExtractor.verifyVideoCandidates(activity,copy,remaining);}
            throw new IOException("抖音网页尚未返回这条作品的媒体，需要时打开原作品完成正常验证或播放");
        }finally{session.ui.post(session::close);}
    }
    private static final class Session {
        final Activity activity;final String source;final Handler ui=new Handler(Looper.getMainLooper());
        final CountDownLatch done=new CountDownLatch(1);final LinkExtractor.Result result=new LinkExtractor.Result();
        volatile String page,workSource;volatile boolean closed;WebView view;final Runnable tick=this::poll;
        Session(Activity activity,String source){this.activity=activity;this.source=source;page=source;workSource=source;result.source=source;result.title="抖音网页素材";}
        void start(){
            if(closed||activity.isFinishing()||activity.isDestroyed()){close();return;}
            try{
                view=new WebView(activity);ViewGroup host=activity.findViewById(android.R.id.content);
                if(host==null)throw new IllegalStateException("No window host");
                host.addView(view,0,new FrameLayout.LayoutParams(-1,-1));
                WebSettings settings=view.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
                settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
                settings.setMediaPlaybackRequiresUserGesture(true);CookieManager.getInstance().setAcceptThirdPartyCookies(view,false);
                view.setWebViewClient(new WebViewClient(){
                    @Override public void onPageStarted(WebView browser,String url,Bitmap icon){visit(url);}
                    @Override public void doUpdateVisitedHistory(WebView browser,String url,boolean reload){visit(url);if(!closed){ui.removeCallbacks(tick);poll();}}
                    @Override public boolean shouldOverrideUrlLoading(WebView browser,WebResourceRequest request){
                        String url=request.getUrl().toString();return !EngineMetadata.https(url)||(request.isForMainFrame()&&!DouyinParser.matches(url));
                    }
                    @Override public WebResourceResponse shouldInterceptRequest(WebView browser,WebResourceRequest request){
                        try{LinkExtractor.ensurePublic(request.getUrl().toString());return null;
                        }catch(Exception e){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
                    }
                    @Override public void onPageFinished(WebView browser,String url){visit(url);if(!closed){ui.removeCallbacks(tick);poll();}}
                    @Override public void onReceivedError(WebView browser,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())close();}
                });
                String share=DouyinParser.sharePage(source);view.loadUrl(share.isEmpty()?source:share);
            }catch(RuntimeException e){close();}
        }
        void visit(String url){workSource=PlaybackRequests.adoptWork(workSource,url);page=url;}
        void poll(){
            if(closed||view==null)return;String current=page;
            if(!PlaybackRequests.onWorkPage(workSource,current)){ui.postDelayed(tick,500);return;}
            view.evaluateJavascript(WebMediaScanner.script(),value->{
                if(closed||view==null||!current.equals(page))return;
                try{Object raw=new JSONTokener(value).nextValue();if(raw instanceof String&&((String)raw).length()<1_500_000){
                    LinkExtractor.Result found=DouyinBrowserMedia.parse(new JSONObject((String)raw),workSource,current,view.getSettings().getUserAgentString());
                    synchronized(result){result.title=found.title;result.description=found.description;for(LinkExtractor.Item item:found.items)result.addItem(item);
                        if(!LinkSaveQueue.choose(result,LinkSaveQueue.Mode.MEDIA).isEmpty()){done.countDown();return;}}
                }}catch(Exception ignored){}
                ui.postDelayed(tick,500);
            });
        }
        void close(){if(closed)return;closed=true;ui.removeCallbacks(tick);if(view!=null){view.stopLoading();if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);view.destroy();view=null;}done.countDown();}
    }
}
