package com.zizai.watermark;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.webkit.*;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.io.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.*;

/** Uses each site's own form. No guessed JSON endpoints or challenge bypass. */
final class WebsiteSearch {
    static LinkExtractor.Result parse(Activity activity,int index,String source)throws Exception {
        long started=System.nanoTime();JobControl.check();DownloadSites.Site site=DownloadSites.SEARCH.get(index);String apiError=index==0?"未配置授权 Cobalt API，当前使用网页辅助":"";
        android.content.SharedPreferences prefs=activity.getSharedPreferences("link_settings",0);
        try{
            if(site.url.equals("https://parse.ideaflow.top/"))return LinkExtractor.verifyWebsiteCandidates(activity,ProviderHttp.ideaFlow(source),8000);
            if(index==1)return LinkExtractor.verifyWebsiteCandidates(activity,ProviderHttp.tiktokio(source),8000);
            if(index==0&&!prefs.getString("cobalt_endpoint","").isEmpty())return LinkExtractor.verifyWebsiteCandidates(activity,ProviderHttp.cobalt(source,prefs.getString("cobalt_endpoint",""),prefs.getString("cobalt_key","")),8000);
        }catch(Exception error){JobControl.check();apiError=error.getMessage()==null?"接口请求失败":error.getMessage();}
        JobControl.check();
        String script;try(InputStream in=activity.getResources().openRawResource(R.raw.search_sites);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);script=out.toString("UTF-8");
        }
        JSONObject config=new JSONObject().put("source",source).put("input",site.input).put("button",site.button).put("buttonText",site.buttonText);
        Session session=new Session(activity,site,source,script,config);
        session.ui.post(session::start);
        try{
            long deadline=started+TimeUnit.SECONDS.toNanos(38);
            while(!session.latch.await(150,TimeUnit.MILLISECONDS)){JobControl.check();if(System.nanoTime()>deadline)break;}
            JobControl.check();LinkExtractor.Result copy=null;synchronized(session.result){if(!session.result.items.isEmpty()){copy=new LinkExtractor.Result();copy.source=session.result.source;copy.title=session.result.title;copy.description=session.result.description;for(LinkExtractor.Item item:session.result.items)copy.addItem(item);}}
            if(copy!=null){session.ui.post(session::close);long left=Math.max(0,Math.min(8000,TimeUnit.NANOSECONDS.toMillis(started+TimeUnit.SECONDS.toNanos(44)-System.nanoTime())));return LinkExtractor.verifyWebsiteCandidates(activity,copy,left);}
            throw new IOException((apiError.isEmpty()?"":apiError+"；")+(session.error.isEmpty()?"网页未返回素材，可用网站验证后重试":session.error));
        }finally{session.ui.post(session::close);}
    }
    private static final class Session {
        final Activity activity;final DownloadSites.Site site;final String script;final JSONObject config;boolean submitted;final Handler ui=new Handler(Looper.getMainLooper());
        final CountDownLatch latch=new CountDownLatch(1);final LinkExtractor.Result result=new LinkExtractor.Result();
        WebView view;boolean closed;volatile String error="";int stable;final Runnable tick=this::poll;
        Session(Activity a,DownloadSites.Site s,String source,String script,JSONObject config){activity=a;site=s;this.script=script;this.config=config;result.source=source;result.title=s.name+" 素材";}
        void start(){
            if(closed||activity.isDestroyed()||activity.isFinishing()){close();return;}
            try{
                view=new WebView(activity);
                ViewGroup host=activity.findViewById(android.R.id.content);
                if(host==null)throw new IllegalStateException("No window host");
                host.addView(view,0,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
                WebSettings settings=view.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
                settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setMediaPlaybackRequiresUserGesture(true);
                CookieManager.getInstance().setAcceptThirdPartyCookies(view,false);WebSessionCookies.enable();
                view.setDownloadListener((url,agent,disposition,mime,length)->capture(url,mime,disposition,agent));
                view.setWebViewClient(new WebViewClient(){
                    @Override public boolean shouldOverrideUrlLoading(WebView browser,WebResourceRequest request){
                        String url=request.getUrl().toString();if(!request.isForMainFrame())return !EngineMetadata.https(url);if(!DownloadSites.mediaKind(url,"","").isEmpty()){capture(url,"","",settings.getUserAgentString());return true;}
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
        void sessionDescription(JSONObject object){synchronized(result){String text=object.optString("description");if(!text.isEmpty())result.description=text.substring(0,Math.min(6000,text.length()));}}
        void capture(String url,String mime,String disposition,String agent){
            String kind=DownloadSites.mediaKind(url,mime,disposition);if(kind.isEmpty())return;
            synchronized(result){result.addItem(new LinkExtractor.Item(LinkExtractor.Kind.valueOf(kind),url,"网站下载素材",site.url,false,0,agent,result.source));}
        }
        void poll(){
            if(closed||view==null)return;
            String code;try{config.put("resume",submitted);code=script.replace("/*CONFIG*/",config.toString());}catch(JSONException e){error="网站表单配置失败";close();return;}
            view.evaluateJavascript(code,value->{
                if(closed||view==null)return;
                try{Object raw=new JSONTokener(value).nextValue();if(raw instanceof String&&((String)raw).length()<500000){
                    JSONObject object=new JSONObject((String)raw);submitted|=object.optBoolean("submitted");sessionDescription(object);error=object.optString("error");JSONArray items=object.optJSONArray("items");int before;
                    synchronized(result){before=result.items.size();if(items!=null)for(int i=0;i<Math.min(24,items.length());i++){
                        JSONObject item=items.optJSONObject(i);if(item==null)continue;try{String url=item.optString("url");if(EngineMetadata.https(url))result.addItem(new LinkExtractor.Item(LinkExtractor.Kind.valueOf(item.optString("kind")),url,item.optString("label","网站素材"),site.url,false,0,view.getSettings().getUserAgentString(),result.source));}catch(IllegalArgumentException ignored){}
                    }stable=result.items.size()>0&&result.items.size()==before?stable+1:0;}
                    if(stable>=3||!error.isEmpty()){close();return;}
                }}catch(Exception ignored){}
                ui.postDelayed(tick,500);
            });
        }
        void close(){if(closed)return;closed=true;ui.removeCallbacks(tick);if(view!=null){view.stopLoading();if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);view.destroy();view=null;}latch.countDown();}
    }
}
