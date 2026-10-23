package com.zizai.watermark;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_FILE = 1201, PICK_LOGO=1202, PICK_BATCH=1203, PICK_BATCH_VIDEO=1205;
    private static final int BG = 0xFF0D101C, PANEL = 0xFF202536, WHITE = 0xFFF5F4FF;
    private static final int MUTED = 0xFFBAB8C3, PINK = 0xFF9475F5;
    private enum Tool { ERASE, IMAGE_WATERMARK, AUDIO, FRAME, GIF, TRIM, MUTE, VIDEO_REMOVE, VIDEO_WATERMARK, COMPRESS, RESIZE, SPEED, MIRROR, MD5 }
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService previews=Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Tool chosenTool;
    private Uri chosenFile;
    private LinearLayout root;
    private ImageEditorView editor;
    private Bitmap openedImage;
    private EditText startInput, endInput;
    private android.app.Dialog progress;
    private SaveProgressView transferView;
    private TransferStats activeTransfer;
    private boolean busy;
    private boolean filesScreen;
    private VideoView videoPreview;
    private android.widget.CheckBox muteCheck;
    private android.widget.Spinner qualitySpinner,ratioSpinner,speedSpinner;
    private int fileFilter;
    private long filePageGeneration,linkGeneration;
    private LinkExtractor.Result extracted;
    private EditText linkInput;
    private String incomingLink="";
    private boolean linkScreen;
    private android.webkit.WebView webPreview;
    private DownloadSites.Site pendingSearchSite;
    private java.util.concurrent.atomic.AtomicBoolean jobCancel;
    private java.util.List<Uri> batchFiles=new java.util.ArrayList<>();
    private boolean batchMode;
    private boolean batchVideoMode;
    private java.util.List<Uri> batchVideos=new java.util.ArrayList<>();
    private VideoRegionView videoRegion;
    private EditText repairTime;
    private AlertDialog repairChoiceDialog;



    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try{android.webkit.CookieManager.getInstance();WebSessionCookies.enable();WebSessionCookies.configureAgent(android.webkit.WebSettings.getDefaultUserAgent(this));}catch(RuntimeException ignored){}
        LinkQueueStore.recover(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if(state!=null){incomingLink=state.getString("link","");}
        if(state!=null&&state.containsKey("tool")){
            try{chosenTool=Tool.valueOf(state.getString("tool"));}catch(Exception ignored){}
        }
        if(!handleSharedIntent(getIntent())){if(!incomingLink.isEmpty())showLinkExtract();else showHome();}
    }

    private int dp(float d) { return (int) (d * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable round(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }
    private GradientDrawable grad(int a, int b, float radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] { a,b });
        d.setCornerRadius(dp(radius));
        return d;
    }
    private TextView text(String message, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(message);
        v.setTextColor(color);
        v.setTextSize(size);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)); }
    private void pad(View v, int a, int b, int c, int d) { v.setPadding(dp(a),dp(b),dp(c),dp(d)); }
    private void gap(LinearLayout parent, int height) { View v = new View(this); parent.addView(v, lp(1,height)); }
    private void initRoot() {
        root = vertical(); root.setBackgroundColor(BG);
        setContentView(root);
    }
    private void header(String title, boolean home) {
        LinearLayout bar = horizontal(); bar.setGravity(Gravity.CENTER_VERTICAL);
        pad(bar, 20, 12, 20, 12);
        if (!home) {
            TextView back = text("‹", 38, WHITE, false);
            back.setGravity(Gravity.CENTER);
            bar.addView(back, lp(38,48));
            back.setOnClickListener(v->{if(!busy)showHome();});
        }
        TextView name = text(title, 23, WHITE, true);
        name.setGravity(Gravity.CENTER);
        bar.addView(name, new LinearLayout.LayoutParams(0,dp(48),1));
        TextView side = text(home ? "⚙" : title.contains("提取视频")?"☰":"", 26, WHITE, true);
        side.setGravity(Gravity.CENTER);
        bar.addView(side, lp(38,48));
        if(home)side.setOnClickListener(v->showAbout());else if(title.contains("提取视频"))side.setOnClickListener(v->linkMenu());
        root.addView(bar, lp(-1,72));
    }
    private LinearLayout scrollBody() {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(false); s.setVerticalScrollBarEnabled(false);
        root.addView(s, new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content = vertical(); pad(content, 16, 8, 16, 24);
        s.addView(content);
        return content;
    }
    private void showHome() {
        if(busy)return;
        stopVideoPreview();stopWebPreview();linkScreen=false;batchMode=false;batchFiles.clear();batchVideoMode=false;batchVideos.clear();videoRegion=null;chosenFile=null;openedImage=null;editor=null;filesScreen=false;filePageGeneration++;
        initRoot(); header("水印工坊", true);
        LinearLayout content = scrollBody();
        LinearLayout banner = vertical(); pad(banner,23,24,20,24);
        banner.setBackground(grad(0xFFFFADBD,0xFFDBB4FB,23));
        TextView label = text("图片擦除物体",27,0xFF773747,true);
        banner.addView(label,lp(-1,44));
        banner.addView(text("涂抹或框选区域 · 本地像素修复",15,0xFF773747,false),lp(-1,38));
        gap(banner,16);
        TextView action = text("开始图片修复  →",17,0xFF713445,true);
        action.setBackground(round(Color.WHITE,40)); action.setGravity(Gravity.CENTER);
        banner.addView(action,lp(196,46));
        action.setOnClickListener(v-> choose(Tool.ERASE));
        content.addView(banner,lp(-1,194)); gap(content,16);

        LinearLayout big = horizontal();
        big.addView(tile("链接提取", "视频 / 图片 / 音频", "↓", 0xFF7E82F0, 0xFF5B50C5, ()-> showLinkExtract()), new LinearLayout.LayoutParams(0,dp(126),1));
        View sp = new View(this); big.addView(sp,lp(12,1));
        big.addView(tile("图片", "擦除物体", "✦", 0xFFD489F2,0xFFF069D5, ()->choose(Tool.ERASE)), new LinearLayout.LayoutParams(0,dp(126),1));
        content.addView(big); gap(content,12);

        LinearLayout tri = horizontal();
        tri.addView(tile("视频修复", "固定区域", "✧", 0xFFFC827D,0xFFF26D70, ()->choose(Tool.VIDEO_REMOVE)),new LinearLayout.LayoutParams(0,dp(130),1));
        tri.addView(spaceWidth(10));
        tri.addView(tile("视频加水印", "", "+", 0xFF6AADEB,0xFF508ED1, ()->choose(Tool.VIDEO_WATERMARK)),new LinearLayout.LayoutParams(0,dp(130),1));
        tri.addView(spaceWidth(10));
        tri.addView(tile("图片加水印", "", "♡", 0xFFF7A15F,0xFFF18E4C, ()->choose(Tool.IMAGE_WATERMARK)),new LinearLayout.LayoutParams(0,dp(130),1));
        content.addView(tri); gap(content,27);
        content.addView(text("视频剪辑工具",24,WHITE,true),lp(-1,43)); gap(content,9);
        LinearLayout toolbox = vertical(); toolbox.setBackground(round(PANEL,22)); pad(toolbox,10,22,10,14);
        LinearLayout r1=horizontal(),r2=horizontal(),r3=horizontal();
        addTool(r1,"▥","提取音频",Tool.AUDIO); addTool(r1,"GIF","视频转GIF",Tool.GIF);
        addTool(r1,"▣","视频压缩",Tool.COMPRESS); addTool(r1,"↻","修改MD5",Tool.MD5);
        addTool(r2,"⌛","裁剪时长",Tool.TRIM); addTool(r2,"◩","裁剪尺寸",Tool.RESIZE);
        addTool(r2,"◉","视频变速",Tool.SPEED); addTool(r2,"◀▶","镜像对称",Tool.MIRROR);
        addTool(r3,"▣","视频取帧",Tool.FRAME); addTool(r3,"◌","视频静音",Tool.MUTE);
        addTool(r3,"✦","图片修复",Tool.ERASE); addTool(r3,"✎","图片水印",Tool.IMAGE_WATERMARK);
        toolbox.addView(r1,lp(-1,104)); toolbox.addView(r2,lp(-1,104)); toolbox.addView(r3,lp(-1,104));
        content.addView(toolbox); gap(content,16);
        Button batch=button("批量图片加水印 · 最多 30 张",PANEL);content.addView(batch,lp(-1,48));batch.setOnClickListener(v->chooseBatchImages());gap(content,10);
        Button batchVideo=button("批量视频处理 · 最多 10 个",PANEL);content.addView(batchVideo,lp(-1,48));batchVideo.setOnClickListener(v->chooseBatchVideos());gap(content,10);
        TextView note = text("本地编辑 · 链接提取需要联网",13,MUTED,false);
        note.setGravity(Gravity.CENTER); content.addView(note,lp(-1,38));
        LinearLayout nav=horizontal(); nav.setBackgroundColor(0xFF2D2B37);
        TextView tab1=text("◆  首页",16,PINK,true); tab1.setGravity(Gravity.CENTER);
        TextView tab2=text("▣  我的文件",16,MUTED,true); tab2.setGravity(Gravity.CENTER);
        nav.addView(tab1,new LinearLayout.LayoutParams(0,dp(60),1));
        nav.addView(tab2,new LinearLayout.LayoutParams(0,dp(60),1));
        tab2.setOnClickListener(v->showFiles());
        root.addView(nav);
    }
    private View spaceWidth(int width) { View v =new View(this); v.setLayoutParams(lp(width,1)); return v; }
    private View tile(String name, String second, String glyph,int start,int end,Runnable click) {
        FrameLayout card=new FrameLayout(this); card.setBackground(grad(start,end,21));
        LinearLayout in=vertical(); pad(in,14,16,7,12);
        TextView mark=text(glyph,30,0xEFFFFFFF,true); mark.setGravity(Gravity.RIGHT);
        in.addView(mark,new LinearLayout.LayoutParams(-1,0,1));
        TextView t=text(name, second.isEmpty()?16:20,Color.WHITE,true);
        t.setGravity(Gravity.CENTER); in.addView(t,lp(-1,29));
        if (!second.isEmpty()) { TextView sub=text(second,15,Color.WHITE,true); sub.setGravity(Gravity.CENTER); in.addView(sub,lp(-1,28)); }
        card.addView(in,new FrameLayout.LayoutParams(-1,-1));
        card.setOnClickListener(v-> click.run());
        return card;
    }
    private void addTool(LinearLayout row,String icon,String name, Tool tool) {
        LinearLayout item=vertical(); item.setGravity(Gravity.CENTER);
        TextView pict=text(icon, icon.length()>2?17:30,0xFF30D794,true);
        pict.setGravity(Gravity.CENTER); item.addView(pict,lp(52,49));
        gap(item,7);
        TextView label=text(name,13,WHITE,false); label.setGravity(Gravity.CENTER);
        item.addView(label,lp(-1,29));
        row.addView(item,new LinearLayout.LayoutParams(0,-1,1));
        item.setOnClickListener(v->choose(tool));
    }
    private void showExtractMenu() {
        new AlertDialog.Builder(this).setTitle("选择提取内容")
            .setItems(new String[] {"从视频提取音频 (M4A)","从视频提取画面 (PNG)"},(d,w)->choose(w==0?Tool.AUDIO:Tool.FRAME)).show();
    }
    private String clipLink() {
        try {
            android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(cm!=null&&cm.hasPrimaryClip()) {
                android.content.ClipData clip=cm.getPrimaryClip();
                if(clip!=null&&clip.getItemCount()>0) {
                    CharSequence txt=clip.getItemAt(0).coerceToText(this);
                    return LinkExtractor.extractLink(txt==null?"":txt.toString());
                }
            }
        }catch(Exception ignored){}
        return "";
    }
    private void showLinkExtract() {
        if(busy)return;stopVideoPreview();stopWebPreview();linkScreen=true;filesScreen=false;linkGeneration++;
        initRoot();header("提取视频图片音频文案",false);
        LinearLayout content=scrollBody();
        TextView categories=text("视频  ·  图片  ·  音频  ·  文案",13,0xFFB8A1EF,false);
        categories.setGravity(Gravity.CENTER);
        content.addView(categories,lp(-1,32));
        LinearLayout main=vertical();pad(main,16,21,16,22);
        main.setBackground(round(PANEL,19));
        TextView intro=text("贴一次分享链接，四路同时搜索素材",17,WHITE,true);
        main.addView(intro,lp(-1,39));
        main.addView(text("同时交给原解析及三个网站搜索 · 统一保存",12,MUTED,false),lp(-1,26));
        gap(main,12);
        LinearLayout entry=horizontal();entry.setBackground(round(WHITE,32));
        linkInput=new EditText(this);
        linkInput.setSingleLine(true);linkInput.setTextSize(14);
        linkInput.setHint("请粘贴分享链接");
        linkInput.setTextColor(0xFF262333);linkInput.setHintTextColor(0xFF99949F);
        linkInput.setBackgroundColor(Color.TRANSPARENT);pad(linkInput,16,0,4,0);
        entry.addView(linkInput,new LinearLayout.LayoutParams(0,dp(55),1));
        Button paste=button("粘贴",0xFFF156A5);paste.setTextSize(14);
        entry.addView(paste,lp(88,54));main.addView(entry,lp(-1,55));
        paste.setOnClickListener(v->{String url=clipLink();if(url.isEmpty())toast("剪贴板中没有有效分享链接");else linkInput.setText(url);});
        linkInput.setText(incomingLink);
        incomingLink=LinkExtractor.extractLink(incomingLink);
        gap(main,19);
        Button parse=button("→  四路搜索素材",0xFF8F61DF);
        main.addView(parse,lp(-1,54));
        parse.setOnClickListener(v->parseSharedLink(linkInput.getText().toString()));
        content.addView(main,lp(-1,-2));gap(content,10);
        Button images=button("提取图集原图 · Instagram / Pinterest / Pixiv",0xFF233B4B);content.addView(images,lp(-1,48));images.setOnClickListener(v->parseSharedLink(linkInput.getText().toString(),true));gap(content,10);
        Button queue=button("多链接保存队列 · 暂停 / 继续 / 重试",0xFF6650BA);content.addView(queue,lp(-1,48));queue.setOnClickListener(v->showLinkQueue());gap(content,10);
        Button assist=button("网页辅助 · 打开页面后播放视频",PANEL);content.addView(assist,lp(-1,46));
        assist.setOnClickListener(v->{String url=LinkExtractor.extractLink(linkInput.getText().toString());if(url.isEmpty())toast("请先粘贴 HTTPS 分享链接");else{incomingLink=url;showWebAssist(url);}});gap(content,16);
        Button sites=button("下载入口 · 搜索 Cobalt / TikTokio / GreenVideo",0xFF6650BA);content.addView(sites,lp(-1,48));sites.setOnClickListener(v->{incomingLink=LinkExtractor.extractLink(linkInput.getText().toString());showDownloadSites();});gap(content,10);
        Button platforms=button("国内外平台 · 查看解析能力",0xFF233B4B);content.addView(platforms,lp(-1,44));platforms.setOnClickListener(v->{incomingLink=LinkExtractor.extractLink(linkInput.getText().toString());showPlatformCoverage();});gap(content,14);
        LinearLayout results=vertical();content.addView(results,lp(-1,-2));
        if(extracted!=null){showLinkResults(results,extracted);}
        else {
            TextView lead=text("一个链接，整理需要的素材",18,WHITE,true);
            lead.setGravity(Gravity.CENTER);content.addView(lead,lp(-1,52));
            TextView sub=text("三步完成 · 分享链接 → 复制 → 粘贴解析",13,MUTED,false);
            sub.setGravity(Gravity.CENTER);content.addView(sub,lp(-1,31));
            String[] titles={"01  分享内容","02  复制链接","03  解析与保存"};
            String[] detail={"在原平台使用分享入口","选择复制链接，或直接分享到水印工坊","预览视频 / 图片 / 音频 / 文案并手动下载"};
            for(int i=0;i<titles.length;i++) {
                LinearLayout row=vertical();pad(row,14,14,14,14);row.setBackground(round(PANEL,14));
                row.addView(text(titles[i],15,0xFFC9B6FF,true),lp(-1,30));
                row.addView(text(detail[i],12,MUTED,false),lp(-1,-2));
                content.addView(row,lp(-1,-2));gap(content,10);
            }
        }
        gap(content,16);
        content.addView(text("原解析与 Cobalt、TikTokio、GreenVideo 同时搜索，合并结果后统一下载。网站验证、超时或不支持会单独显示；封面仍标注为封面。",12,MUTED,false),lp(-1,-2));
    }
    private void parseSharedLink(String input) {parseSharedLink(input,false);}
    private void parseSharedLink(String input,boolean images) {
        if(busy)return;String url=LinkExtractor.extractLink(input);
        if(url.isEmpty()) {toast("请输入有效的 HTTPS 链接");return;}
        extracted=null;incomingLink=url;
        pendingSearchSite=null;startBusy("四路同时搜索素材…");
        Button verify=button("网站验证 / 查看失败原因",PANEL);transferView.addView(verify,lp(-1,44));verify.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("停止本次搜索并打开网站").setItems(new String[]{"Cobalt","TikTokio","GreenVideo"},(d,k)->{DownloadSites.Site site=DownloadSites.ALL.get(k);if(busy){pendingSearchSite=site;jobCancel.set(true);}else openWebAssist(site.url,site,url);}).setNegativeButton("继续搜索",null).show());
        runJob(()->{
            try {
                LinkExtractor.Result r=parseCombinedLink(url,images);
                LinkExtractor.Result parsed=r;
                mainHandler.post(()->{if(isFinishing()||isDestroyed())return;stopBusy();if(cancelled()){if(!openPendingSearchSite(url))toast("已取消解析");return;}extracted=parsed;saveLinkRecord(url,parsed.title);showLinkExtract();});
            }catch(Exception error){mainHandler.post(()->{if(isFinishing()||isDestroyed())return;stopBusy();if(cancelled()&&openPendingSearchSite(url))return;showLinkExtract();if(!cancelled())new AlertDialog.Builder(this).setTitle("尚未提取到素材").setMessage(ExtractionErrors.message(error)).setPositiveButton("网页辅助",(d,w)->showWebAssist(url)).setNeutralButton("更多下载入口",(d,w)->showDownloadSites()).setNegativeButton("返回",null).show();});}
        });
    }
    private boolean openPendingSearchSite(String url){
        if(pendingSearchSite==null)return false;DownloadSites.Site site=pendingSearchSite;pendingSearchSite=null;openWebAssist(site.url,site,url);return true;
    }
    private LinkExtractor.Result parseCombinedLink(String url,boolean images)throws Exception {
        LinkExtractor.ensurePublic(url);JobControl.check();
        String endpoint=getSharedPreferences("link_settings",0).getString("endpoint","");
        java.util.List<ParallelSearch.Source> sources=new java.util.ArrayList<>();
        sources.add(new ParallelSearch.Source("原解析",()->{
            if(DouyinParser.matches(url))return parseDouyinLink(url,endpoint);
            if(!endpoint.isEmpty())try{return LinkExtractor.parsePlatform(url,endpoint);}catch(Exception error){JobControl.check();}
            return parseLocalLink(url,images);
        }));
        for(int i=0;i<DownloadSites.ALL.size();i++){final int index=i;sources.add(new ParallelSearch.Source(DownloadSites.ALL.get(i).name,()->WebsiteSearch.parse(this,index,url)));}
        return ParallelSearch.run(url,sources,JobControl.flag(),45000,this::updateProgress);
    }
    private LinkExtractor.Result parseLocalLink(String url)throws Exception {return parseLocalLink(url,false);}
    private LinkExtractor.Result parseDouyinLink(String share,String endpoint)throws Exception {
        updateProgress("正在同时尝试抖音分享页、平台引擎和原作品网页…");
        java.util.List<ParallelSearch.Source> attempts=new java.util.ArrayList<>(java.util.Arrays.asList(
            new ParallelSearch.Source("分享页",()->LinkExtractor.parseDouyin(this,share)),
            new ParallelSearch.Source("平台引擎",()->{
                String resolved=PlatformRoutes.shortLink(share)?LinkExtractor.resolveShare(this,share):share;
                String id=DouyinParser.id(resolved);if(id.isEmpty())throw new java.io.IOException("短链接未返回抖音作品编号");
                return PlatformEngine.parse(this,"https://www.douyin.com/video/"+id,this::updateProgress,28000);
            }),
            new ParallelSearch.Source("原作品网页",()->DouyinWebSearch.parse(this,share))));
        if(!endpoint.isEmpty())attempts.add(new ParallelSearch.Source("专用接口",()->LinkExtractor.parsePlatform(share,endpoint)));
        return MediaSearchRace.run(attempts,37000);
    }
    private LinkExtractor.Result parseLocalLink(String url,boolean images)throws Exception {
        if(DouyinParser.matches(url))return parseDouyinLink(url,"");
        if(PlatformRoutes.shortLink(url)){
            updateProgress("正在展开 "+PlatformRoutes.name(url)+" 分享链接…");
            try{url=LinkExtractor.resolveShare(this,url);}catch(Exception error){JobControl.check();updateProgress("短链接展开暂未成功，继续尝试平台引擎…");}
        }
        if(XhsParser.matches(url))try{updateProgress("正在解析小红书原图与视频…");return LinkExtractor.parseXhs(this,url);}catch(Exception error){JobControl.check();}
        boolean triedGallery=images||GalleryRoutes.prefer(url);Exception galleryError=null;
        if(triedGallery)try{return GalleryEngine.parse(this,url,this::updateProgress);}catch(Exception e){JobControl.check();galleryError=e;}
        try{
            LinkExtractor.Result nativeResult=PlatformEngine.parse(this,url,this::updateProgress);
            if(!triedGallery&&LinkSaveQueue.choose(nativeResult,LinkSaveQueue.Mode.MEDIA).isEmpty())
                try{return GalleryEngine.parse(this,url,this::updateProgress);}catch(Exception e){JobControl.check();}
            return nativeResult;
        }catch(Exception engineError){JobControl.check();
            if(!triedGallery)try{return GalleryEngine.parse(this,url,this::updateProgress);}catch(Exception e){JobControl.check();galleryError=e;}
            updateProgress("尝试公开网页素材…");
            try{LinkExtractor.Result fallback=LinkExtractor.parse(url);fallback.notice=fallback.items.isEmpty()?"仅获取到网页文案，尚未获取到可下载素材。":"已获取网页中的素材地址。";return fallback;}
            catch(Exception pageError){throw images&&galleryError!=null?galleryError:engineError;}
        }
    }
    private String queueDraft="";
    private void showLinkQueue(){
        if(busy)return;stopVideoPreview();stopWebPreview();linkScreen=true;initRoot();header("多链接保存队列",false);LinearLayout box=scrollBody();
        box.addView(text("粘贴多条分享文案 · 最多 20 条",18,WHITE,true),lp(-1,42));
        box.addView(text("依次解析保存，失败可重试；再次打开时保留记录。图片和视频沿用多区域去水印设置。队列暂停后，已保存的成品会保留。",12,MUTED,false),lp(-1,-2));gap(box,10);
        EditText input=new EditText(this);input.setTextColor(WHITE);input.setHintTextColor(MUTED);input.setHint("每行一条链接，或直接粘贴多段分享文案");input.setMinLines(3);input.setMaxLines(5);input.setText(queueDraft);box.addView(input,lp(-1,120));
        android.widget.Spinner mode=selector(box,"保存内容",new String[]{"视频 / 图片优先","只保存图集图片","只保存音频"});
        LinearLayout actions=horizontal();smallButton(actions,"粘贴",()->input.setText(clipboardText()));
        smallButton(actions,"添加队列",()->{try{java.util.List<String> urls=LinkExtractor.extractLinks(input.getText().toString());if(urls.isEmpty()){toast("没有找到有效分享链接");return;}
            LinkSaveQueue queue=LinkQueueStore.read(this);int added=queue.add(urls,LinkSaveQueue.Mode.values()[mode.getSelectedItemPosition()]);LinkQueueStore.save(this,queue);queueDraft="";showLinkQueue();toast("已添加 "+added+" 条，重复链接已跳过");
        }catch(Exception e){toast(e.getMessage());}});box.addView(actions,lp(-1,48));
        LinearLayout run=horizontal();smallButton(run,"开始 / 继续",()->runLinkQueue(false));smallButton(run,"重试失败",()->runLinkQueue(true));
        smallButton(run,"清理完成",()->{try{LinkSaveQueue q=LinkQueueStore.read(this);q.clearDone();LinkQueueStore.save(this,q);showLinkQueue();}catch(Exception e){toast(e.getMessage());}});box.addView(run,lp(-1,48));
        LinkSaveQueue queue=LinkQueueStore.read(this);int done=0,saved=0;for(LinkSaveQueue.Entry e:queue.entries){if(e.state==LinkSaveQueue.State.DONE)done++;saved+=e.saved.size();}
        box.addView(text(queue.entries.size()+" 条链接 · 完成 "+done+" 条 · 已保存 "+saved+" 项",13,0xFF5EEBE1,true),lp(-1,42));
        if(queue.entries.isEmpty())box.addView(text("添加链接后点开始。取消时可停止当前保存，稍后继续。",13,MUTED,false),lp(-1,60));
        for(LinkSaveQueue.Entry e:queue.entries){
            LinearLayout card=vertical();pad(card,14,12,14,12);card.setBackground(round(PANEL,12));
            card.addView(text(e.title.isEmpty()?PlatformRoutes.name(e.url)+" 分享链接":e.title,16,WHITE,true),lp(-1,-2));
            card.addView(text(LinkSaveQueue.label(e.state)+" · 已保存 "+e.saved.size()+" 项",12,e.state==LinkSaveQueue.State.FAILED?0xFFD9B87A:0xFF5EEBE1,false),lp(-1,30));
            card.addView(text(e.url.length()>140?e.url.substring(0,140)+"…":e.url,11,MUTED,false),lp(-1,-2));
            if(!e.error.isEmpty())card.addView(text(e.error,12,0xFFD9B87A,false),lp(-1,-2));
            LinearLayout options=horizontal();
            if(e.state==LinkSaveQueue.State.FAILED||e.state==LinkSaveQueue.State.PAUSED)smallButton(options,"重试此项",()->{try{e.retry();LinkQueueStore.save(this,queue);runLinkQueue(false);}catch(Exception ex){toast(ex.getMessage());}});
            smallButton(options,"移出队列",()->{try{queue.entries.remove(e);LinkQueueStore.save(this,queue);showLinkQueue();}catch(Exception ex){toast(ex.getMessage());}});
            card.addView(options,lp(-1,44));box.addView(card,lp(-1,-2));gap(box,9);
        }
        Button files=button("查看已保存作品",0xFF6650BA);box.addView(files,lp(-1,48));files.setOnClickListener(v->showFiles());
    }
    private String clipboardText(){
        try{android.content.ClipboardManager manager=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(manager!=null&&manager.hasPrimaryClip()){CharSequence value=manager.getPrimaryClip().getItemAt(0).coerceToText(this);return value==null?"":value.toString();}
        }catch(Exception ignored){}return "";
    }
    private boolean queueOutputExists(String value){
        try(android.os.ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(Uri.parse(value),"r")){return fd!=null&&fd.getStatSize()!=0;}catch(Exception e){return false;}
    }
    private SavedLink saveQueueItem(LinkExtractor.Item item,LinkSaveQueue.Mode mode,String label)throws Exception {
        if(mode!=LinkSaveQueue.Mode.AUDIO||item.kind!=LinkExtractor.Kind.VIDEO)return saveDownloadedItem(item,label);
        LinkExtractor.Progress measured=downloadProgress(label);
        if(item.engine){LinkExtractor.Item audio=new LinkExtractor.Item(LinkExtractor.Kind.AUDIO,item.url,"音频",item.referer,true,item.engineIndex,item.userAgent);return new SavedLink(LinkExtractor.download(this,audio,measured),false);}
        Uri out=SaveTransaction.run(new SaveTransaction.Work<Uri>(){
            public Uri download()throws Exception{return LinkExtractor.download(MainActivity.this,item,measured,false);}
            public Uri repair(Uri pending)throws Exception{return extractLocalAudio(pending,label+" · ");}
            public void publish(Uri pending)throws Exception{MediaOps.finish(MainActivity.this,pending);}
            public void discard(Uri pending){try{getContentResolver().delete(pending,null,null);}catch(Exception ignored){}}
            public void complete(){measured.complete();}
        });return new SavedLink(out,false);
    }
    private void runLinkQueue(boolean retryFailed){
        if(busy)return;LinkSaveQueue queue=LinkQueueStore.read(this);queue.recover();if(retryFailed)queue.retryFailed();
        else for(LinkSaveQueue.Entry e:queue.entries)if(e.state==LinkSaveQueue.State.PAUSED)e.retry();
        boolean waiting=false;for(LinkSaveQueue.Entry e:queue.entries)waiting|=e.state==LinkSaveQueue.State.WAITING;
        if(!waiting){toast("没有等待保存的链接");return;}
        try{LinkQueueStore.save(this,queue);}catch(Exception e){toast(e.getMessage());return;}
        startBusy("队列保存 · 正在准备…");runJob(()->{
            int newlySaved=0,failed=0,unrepaired=0;boolean stop=false;
            try{
                for(int index=0;index<queue.entries.size();index++){
                    LinkSaveQueue.Entry entry=queue.entries.get(index);if(entry.state!=LinkSaveQueue.State.WAITING)continue;
                    if(cancelled()){stop=true;break;}String prefix="链接 "+(index+1)+" / "+queue.entries.size()+" · ";
                    try{
                        entry.state=LinkSaveQueue.State.RESOLVING;entry.error="";LinkQueueStore.save(this,queue);updateProgress(prefix+"重新获取素材地址");
                        LinkExtractor.Result result=parseCombinedLink(entry.url,entry.mode==LinkSaveQueue.Mode.IMAGES);entry.title=result.title;
                        java.util.List<LinkExtractor.Item> items=LinkSaveQueue.choose(result,entry.mode);
                        if(items.isEmpty())throw new java.io.IOException(entry.mode==LinkSaveQueue.Mode.IMAGES?"没有提取到图集原图，封面不会当作图集保存":"没有提取到所选类型的素材");
                        entry.state=LinkSaveQueue.State.SAVING;LinkQueueStore.save(this,queue);int errors=0;String lastError="";
                        for(int n=0;n<items.size();n++){
                            JobControl.check();LinkExtractor.Item item=items.get(n);String key=LinkSaveQueue.key(item),existing=entry.saved.get(key);
                            if(existing!=null&&queueOutputExists(existing))continue;
                            try{SavedLink saved=saveQueueItem(item,entry.mode,prefix+"素材 "+(n+1)+" / "+items.size());rememberOutput(saved.uri);entry.saved.put(key,saved.uri.toString());LinkQueueStore.save(this,queue);newlySaved++;if(entry.mode!=LinkSaveQueue.Mode.AUDIO&&item.kind!=LinkExtractor.Kind.AUDIO&&!saved.repaired)unrepaired++;}
                            catch(Exception error){JobControl.check();errors++;lastError=ExtractionErrors.message(error);}
                        }
                        if(errors>0){entry.state=LinkSaveQueue.State.FAILED;entry.error=errors+" 项未保存。"+lastError;failed++;}
                        else {entry.state=LinkSaveQueue.State.DONE;entry.error="";}
                    }catch(Exception error){if(cancelled()){entry.state=LinkSaveQueue.State.PAUSED;entry.error="已暂停，继续时会重新解析";stop=true;}else {entry.state=LinkSaveQueue.State.FAILED;entry.error=ExtractionErrors.message(error);failed++;}}
                    LinkQueueStore.save(this,queue);if(stop)break;
                }
            }catch(Exception error){for(LinkSaveQueue.Entry e:queue.entries)if(e.state==LinkSaveQueue.State.RESOLVING||e.state==LinkSaveQueue.State.SAVING){e.state=LinkSaveQueue.State.PAUSED;e.error="保存记录中断，请检查空间后继续";}try{LinkQueueStore.save(this,queue);}catch(Exception ignored){}}
            final int savedCount=newlySaved,failedCount=failed,unrepairedCount=unrepaired;final boolean paused=stop||cancelled();
            mainHandler.post(()->{if(isFinishing()||isDestroyed())return;stopBusy();showLinkQueue();new AlertDialog.Builder(this).setTitle(paused?"队列已暂停":"队列处理结束")
                .setMessage("本次新增保存 "+savedCount+" 项，失败链接 "+failedCount+" 条。"+(unrepairedCount>0?"其中 "+unrepairedCount+" 项图片/视频未执行去水印。":"")+"已保存成品保留在作品列表，失败和暂停项目可重新解析后继续。").setPositiveButton("查看队列",null).setNeutralButton("我的文件",(d,w)->showFiles()).show();});
        });
    }
    private void showLinkResults(LinearLayout box,LinkExtractor.Result r) {
        LinearLayout summary=vertical();pad(summary,16,16,16,16);summary.setBackground(round(0xFF302C47,16));
        boolean nativeResult=false;for(LinkExtractor.Item item:r.items)nativeResult|=item.engine;
        summary.addView(text(r.items.isEmpty()?"仅获取到文案":XhsParser.matches(r.source)?"解析完成 · 小红书素材":nativeResult?"解析完成 · 平台素材":"解析完成 · 网页素材",16,0xFFD0B6FF,true),lp(-1,34));
        if(!r.notice.isEmpty())summary.addView(text(r.notice,12,MUTED,false),lp(-1,-2));
        summary.addView(text(r.title,16,WHITE,true),lp(-1,-2));
        gap(summary,7);
        summary.addView(text("已发现：视频 "+r.count(LinkExtractor.Kind.VIDEO)+"  ·  图片 "+r.count(LinkExtractor.Kind.IMAGE)+"  ·  音频 "+r.count(LinkExtractor.Kind.AUDIO),12,MUTED,false),lp(-1,27));
        box.addView(summary,lp(-1,-2));gap(box,12);
        LinearLayout tabs=horizontal();box.addView(tabs,lp(-1,42));
        LinearLayout list=vertical();box.addView(list,lp(-1,-2));
        String[] labels={"视频","图片","音频","文案"};
        for(int i=0;i<labels.length;i++) {
            final int tab=i;
            TextView btn=text(labels[i],13,i==0?WHITE:MUTED,i==0);btn.setGravity(Gravity.CENTER);
            btn.setBackground(round(i==0?0xFF7E5BC2:0xFF363240,10));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(40),1);
            params.setMargins(0,0,dp(5),0);tabs.addView(btn,params);
            btn.setOnClickListener(v->{
                for(int j=0;j<tabs.getChildCount();j++) {
                    TextView current=(TextView)tabs.getChildAt(j);
                    current.setBackground(round(j==tab?0xFF7E5BC2:0xFF363240,10));
                    current.setTextColor(j==tab?WHITE:MUTED);
                }
                fillLinkTab(list,r,tab);
            });
        }
        fillLinkTab(list,r,0);
        gap(box,12);
        if(!r.items.isEmpty()) {
            Button all=button(getSharedPreferences("download_repair",0).getBoolean("enabled",true)?"保存全部 · 自动去水印（最多 24 项）":"保存全部可下载素材（最多 24 项）",0xFF6650BA);
            box.addView(all,lp(-1,51));all.setOnClickListener(v->downloadAll(r));
        }
    }
    private void fillLinkTab(LinearLayout parent,LinkExtractor.Result result,int index) {
        final long generation=++linkGeneration;parent.removeAllViews();
        if(index==3) {
            String words=result.title+(result.description.isEmpty()?"":"\n\n"+result.description);
            LinearLayout card=vertical();pad(card,16,14,16,16);card.setBackground(round(PANEL,14));
            card.addView(text("笔记标题及描述（不代表视频完整字幕）",12,MUTED,false),lp(-1,36));
            TextView body=text(words,15,WHITE,false);body.setTextIsSelectable(true);
            card.addView(body,lp(-1,-2));gap(card,14);
            Button copy=button("复制文案",0xFF6252AB);card.addView(copy,lp(-1,43));
            copy.setOnClickListener(v->{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if(cm!=null){cm.setPrimaryClip(android.content.ClipData.newPlainText("网页文案",words));toast("文案已复制");}});
            parent.addView(card,lp(-1,-2));return;
        }
        LinkExtractor.Kind target=index==0?LinkExtractor.Kind.VIDEO:index==1?LinkExtractor.Kind.IMAGE:LinkExtractor.Kind.AUDIO;
        int n=0;
        for(LinkExtractor.Item item:result.items) {
            if(item.kind!=target)continue;
            n++;
            LinearLayout card=vertical();pad(card,14,13,14,13);card.setBackground(round(PANEL,14));
            card.addView(text(item.label,16,WHITE,true),lp(-1,31));
            String previewUrl="";
            if(item.kind==LinkExtractor.Kind.IMAGE)previewUrl=item.url;
            else if(item.kind==LinkExtractor.Kind.VIDEO) {
                for(LinkExtractor.Item candidate:result.items)if(candidate.kind==LinkExtractor.Kind.IMAGE) {
                    previewUrl=candidate.url;break;
                }
            }
            if(!previewUrl.isEmpty()) {
                android.widget.ImageView preview=new android.widget.ImageView(this);
                preview.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                preview.setBackground(round(0xFF262E42,12));
                card.addView(preview,lp(-1,168));gap(card,9);
                String imageUrl=previewUrl;
                previews.execute(()->{
                    Bitmap img=LinkExtractor.loadPreview(imageUrl);
                    if(img!=null)mainHandler.post(()->{
                        if(!isFinishing()&&!isDestroyed()&&linkGeneration==generation)preview.setImageBitmap(img);else img.recycle();
                    });
                });
            }
            TextView url=text(Uri.parse(item.url).getHost()+" · "+item.kind.name(),11,MUTED,false);url.setMaxLines(2);
            card.addView(url,lp(-1,-2));gap(card,8);
            LinearLayout actions=horizontal();
            Button save=button(item.kind==LinkExtractor.Kind.AUDIO||!getSharedPreferences("download_repair",0).getBoolean("enabled",true)?"保存到本机":"保存并去水印",0xFF8B5DDD);actions.addView(save,new LinearLayout.LayoutParams(0,dp(44),1));
            actions.addView(spaceWidth(8));
            Button browser=button("浏览器打开",0xFF565065);actions.addView(browser,new LinearLayout.LayoutParams(0,dp(44),1));
            card.addView(actions);parent.addView(card,lp(-1,-2));gap(parent,9);
            save.setOnClickListener(v->downloadOne(item));
            browser.setText("预览 / 打开");browser.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(item.url)));}catch(Exception e){toast("找不到可打开链接的应用");}});
            if(item.kind==LinkExtractor.Kind.VIDEO){gap(card,8);Button audio=button(item.engine?"直接下载音频":"下载视频并提取音频",PANEL);card.addView(audio,lp(-1,44));audio.setOnClickListener(v->downloadAudio(item));}
        }
        if(index==2&&n==0){
            for(LinkExtractor.Item video:result.items)if(video.kind==LinkExtractor.Kind.VIDEO){
                Button extractAudio=button("从视频下载并提取音频",PINK);parent.addView(extractAudio,lp(-1,54));extractAudio.setOnClickListener(v->downloadAudio(video));break;
            }
        }
        if(n==0) {
            TextView empty=text("未检测到可直接提取的"+(index==0?"视频":index==1?"图片":"音频")+"地址。\n可能是动态资源或网站限制；尝试原平台的授权保存方式。",13,MUTED,false);
            empty.setGravity(Gravity.CENTER);pad(empty,15,30,15,30);parent.addView(empty,lp(-1,-2));
            if(index==0&&DouyinParser.matches(result.source)){
                Button play=button("打开这条抖音作品 · 播放后提取",0xFF6650BA);parent.addView(play,lp(-1,48));play.setOnClickListener(v->showWebAssist(result.source));
            }
        }
    }
    private static final class SavedLink {final Uri uri;final boolean repaired;SavedLink(Uri u,boolean r){uri=u;repaired=r;}}
    private SavedLink saveDownloadedItem(LinkExtractor.Item item,String label)throws Exception {
        LinkExtractor.Progress measured=downloadProgress(label);
        boolean[] repaired={false};Uri output=SaveTransaction.run(new SaveTransaction.Work<Uri>(){
            public Uri download()throws Exception{return LinkExtractor.download(MainActivity.this,item,measured,false);}
            public Uri repair(Uri pending)throws Exception{
                measured.phase("准备保存去水印");
                Uri result=DownloadRepair.repair(MainActivity.this,pending,item,MainActivity.this::selectDownloadRepairRegion,message->updateLocalVideoProgress("去水印 · ",message));repaired[0]=result!=pending;return result;
            }
            public void publish(Uri pending)throws Exception{measured.phase("正在登记成品到相册");MediaOps.finish(MainActivity.this,pending);}
            public void discard(Uri pending){try{getContentResolver().delete(pending,null,null);}catch(Exception ignored){}}
            public void complete(){measured.complete();}
        });return new SavedLink(output,repaired[0]);
    }
    private float[][] selectDownloadRepairRegion(Bitmap preview,float[][] suggestions)throws Exception {
        java.util.concurrent.CompletableFuture<float[][]> choice=new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.atomic.AtomicBoolean token=JobControl.flag();
        mainHandler.post(()->{
            if(isFinishing()||isDestroyed()||token==null||token.get()){choice.completeExceptionally(new java.io.InterruptedIOException("已取消操作"));return;}
            if(progress!=null)progress.hide();
            LinearLayout box=vertical();pad(box,14,10,14,10);box.setBackgroundColor(PANEL);
            box.addView(text("最多框选 8 处水印，一起修复。只框平台标识、博主头像与名字，保留中间字幕。成功后记住这组位置。",13,MUTED,false),lp(-1,-2));gap(box,8);
            VideoRegionView selection=new VideoRegionView(this,preview);selection.setRegions(suggestions);box.addView(selection,lp(-1,300));regionControls(box,selection);
            if(suggestions!=null)box.addView(text(suggestions.length==1?"已预选图片右下水印区域；若原图已无水印，请点跳过修复。有水印时核对并调整选区。":"已预选左下博主标识、右下平台标识，请核对并调整框的位置。",12,0xFF5EEBE1,false),lp(-1,-2));
            box.addView(text("固定区域修复可能留下模糊痕迹；水印位置改变时请清除记忆后重新框选。",12,MUTED,false),lp(-1,-2));
            ScrollView selectionScroll=new ScrollView(this);selectionScroll.addView(box);
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("保存前多区域去水印").setView(selectionScroll).setCancelable(false)
                .setPositiveButton("修复保存",null).setNeutralButton("跳过修复",(d,w)->{selection.setImage(null);choice.complete(null);})
                .setNegativeButton("取消",(d,w)->{token.set(true);selection.setImage(null);choice.completeExceptionally(new java.io.InterruptedIOException("已取消操作"));}).create();
            repairChoiceDialog=dialog;dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{float[][] region=RepairRegions.validate(selection.snapshotAll());selection.setImage(null);choice.complete(region);dialog.dismiss();}catch(Exception e){toast(e.getMessage());}});
        });
        try{
            while(true){JobControl.check();try{return choice.get(250,java.util.concurrent.TimeUnit.MILLISECONDS);}catch(java.util.concurrent.TimeoutException waiting){}}
        }catch(java.util.concurrent.ExecutionException e){throw new java.io.IOException(e.getCause().getMessage(),e.getCause());}
        finally{mainHandler.post(()->{if(repairChoiceDialog!=null){repairChoiceDialog.dismiss();repairChoiceDialog=null;}if(progress!=null&&!isDestroyed()&&jobCancel==token&&!token.get())progress.show();});}
    }
    private void regionControls(LinearLayout parent,VideoRegionView selection){
        TextView count=text("",12,0xFF5EEBE1,false);parent.addView(count,lp(-1,32));
        selection.onChanged(()->count.setText("已选 "+selection.count()+" / 8 处"+(selection.activeNumber()>0?" · 当前第 "+selection.activeNumber()+" 处":" · 在画面上拖动添加")));
        count.setText("已选 "+selection.count()+" / 8 处");
        LinearLayout controls=horizontal();
        smallButton(controls,"添加区域",()->{if(selection.count()>=RepairRegions.MAX){toast("最多 8 处，请删除或调整已有区域");return;}selection.addRegion();});
        smallButton(controls,"删除选中",selection::removeSelected);smallButton(controls,"清空",selection::clear);parent.addView(controls,lp(-1,48));
    }
    private void showDownloadRepairSettings(){
        android.content.SharedPreferences settings=getSharedPreferences("download_repair",0);LinearLayout form=vertical();pad(form,18,10,18,10);form.setBackgroundColor(PANEL);
        android.widget.CheckBox enabled=check("下载后自动执行区域去水印",settings.getBoolean("enabled",true));form.addView(enabled,lp(-1,48));
        form.addView(text("首次保存可框选最多 8 处水印。成功后记住同平台、同素材类型和同画面比例的整组选区；后续单项和批量保存直接处理。图片输出 PNG，长边最多 2560；视频固定区域修复输出 SDR MP4。关闭后直接保存解析到的原片。",13,MUTED,false),lp(-1,-2));
        new AlertDialog.Builder(this).setTitle("保存去水印设置").setView(form).setNegativeButton("取消",null)
            .setNeutralButton("清除记住的位置",(d,w)->{settings.edit().clear().putBoolean("enabled",enabled.isChecked()).apply();toast("已清除，下次保存重新框选");})
            .setPositiveButton("保存",(d,w)->{settings.edit().putBoolean("enabled",enabled.isChecked()).apply();toast("保存去水印设置已更新");}).show();
    }
    private void downloadOne(LinkExtractor.Item item) {
        if(busy)return;startBusy("正在下载素材（最大 2GB）…");
        runJob(()->{try{SavedLink result=saveDownloadedItem(item,"下载素材");
            succeed(result.uri,item.kind==LinkExtractor.Kind.AUDIO?"音频已保存":result.repaired?"已执行多区域修复并保存，请打开检查效果":"原片已保存，本次未执行去水印");}
            catch(Exception e){fail(e);}});
    }
    private void downloadAll(LinkExtractor.Result r) {
        if(busy||r.items.isEmpty())return;
        startBusy("正在依次保存本批素材，最多 24 项…");
        runJob(()->{
            java.util.ArrayList<Uri> uris=new java.util.ArrayList<>();int failed=0;
            for(LinkExtractor.Item item:r.items) {
                if(cancelled())break;
                try{SavedLink saved=saveDownloadedItem(item,"保存第 "+(uris.size()+failed+1)+" / "+r.items.size()+" 项");rememberOutput(saved.uri);uris.add(saved.uri);}catch(Exception ex){if(cancelled())break;failed++;}
            }
            int errors=failed;
            mainHandler.post(()->{
                if(isFinishing()||isDestroyed())return;stopBusy();
                new AlertDialog.Builder(this).setTitle(cancelled()?"已停止后续下载":"下载完成")
                    .setMessage("已保存 "+uris.size()+" 项，失败 "+errors+" 项，未处理 "+Math.max(0,r.items.size()-uris.size()-errors)+" 项。\n已完成素材保留在我的文件。")
                    .setPositiveButton("查看我的文件",(d,w)->showFiles())
                    .setNegativeButton("继续",null).show();
            });
        });
    }
    private void choose(Tool tool) {
        if(busy)return;stopWebPreview();linkScreen=false;batchMode=false;batchFiles.clear();batchVideoMode=false;batchVideos.clear();videoRegion=null;stopVideoPreview();startInput=null;endInput=null;editor=null;openedImage=null;chosenTool=tool;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType((tool==Tool.ERASE || tool==Tool.IMAGE_WATERMARK)?"image/*":"video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent,PICK_FILE); }
        catch (Exception e) { toast("无法打开系统文件选择器"); }
    }
    @Override protected void onActivityResult(int requestCode,int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null)return;
        if(requestCode!=PICK_BATCH&&requestCode!=PICK_BATCH_VIDEO&&data.getData()==null)return;
        if(requestCode==PICK_BATCH||requestCode==PICK_BATCH_VIDEO){
            java.util.LinkedHashSet<Uri> selection=new java.util.LinkedHashSet<>();
            android.content.ClipData clip=data.getClipData();if(clip!=null)for(int i=0;i<clip.getItemCount();i++)selection.add(clip.getItemAt(i).getUri());
            if(data.getData()!=null)selection.add(data.getData());
            selection.remove(null);if(selection.isEmpty())return;
            if(requestCode==PICK_BATCH_VIDEO){batchVideos=new java.util.ArrayList<>(selection);if(batchVideos.size()>10){batchVideos=new java.util.ArrayList<>(batchVideos.subList(0,10));toast("已选择前 10 个视频");}batchVideoMode=true;batchMode=false;loadSelected(batchVideos.get(0));return;}
            batchVideoMode=false;batchVideos.clear();
            batchFiles=new java.util.ArrayList<>(selection);if(batchFiles.size()>30)batchFiles=new java.util.ArrayList<>(batchFiles.subList(0,30));
            batchMode=true;chosenTool=Tool.IMAGE_WATERMARK;loadSelected(batchFiles.get(0));return;
        }
        Uri selected=data.getData();
        if(requestCode==1204){
            startBusy("正在导入 Cookie 文件…");runJob(()->{try{PlatformEngine.importCookies(this,selected);mainHandler.post(()->{if(isDestroyed())return;stopBusy();toast("已导入，仅用于本机平台解析");});}catch(Exception e){fail(e);}});return;
        }
        if(requestCode==PICK_LOGO){
            if(editor==null)return;
            ImageEditorView target=editor;
            startBusy("正在打开 Logo…");runJob(()->{
                try {Bitmap b=ImageOps.readBitmap(this,selected,1024);mainHandler.post(()->{if(isDestroyed()){b.recycle();return;}stopBusy();if(!cancelled()&&editor==target)target.setLogo(b);else b.recycle();});}
                catch(OutOfMemoryError e){fail(new Exception("内存不足，请使用较小的素材"));}catch(Exception e){fail(e);}
            });return;
        }
        if(requestCode!=PICK_FILE)return;
        loadSelected(selected);
    }
    private void loadSelected(Uri uri){
        chosenFile=uri;
        try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        if(chosenTool==Tool.ERASE||chosenTool==Tool.IMAGE_WATERMARK||chosenTool==Tool.VIDEO_WATERMARK||chosenTool==Tool.VIDEO_REMOVE){
            startBusy("正在读取素材…");Tool action=chosenTool;
            runJob(()->{
                try{
                    Bitmap b;
                    if(action==Tool.VIDEO_WATERMARK||action==Tool.VIDEO_REMOVE){
                        b=MediaOps.frame(this,uri,0);int max=Math.max(b.getWidth(),b.getHeight());
                        if(max>1280){Bitmap resized=Bitmap.createScaledBitmap(b,Math.max(1,b.getWidth()*1280/max),Math.max(1,b.getHeight()*1280/max),true);b.recycle();b=resized;}
                    }else b=ImageOps.readBitmap(this,uri,action==Tool.ERASE?1800:3072);
                    JobControl.check();Bitmap ready=b;
                    mainHandler.post(()->{if(isDestroyed()){ready.recycle();return;}stopBusy();if(cancelled()){ready.recycle();return;}openedImage=ready;if(action==Tool.VIDEO_WATERMARK||action==Tool.VIDEO_REMOVE)showVideoTool();else showImageEditor();});
                }catch(OutOfMemoryError e){fail(new Exception("内存不足，请使用较小的素材"));}catch(Exception e){fail(e);}
            });
        }else showVideoTool();
    }
    private Button button(String text, int color) {
        Button btn=new Button(this);
        btn.setAllCaps(false); btn.setText(text); btn.setTextSize(16);
        btn.setTextColor(WHITE); btn.setBackground(round(color,13));
        return btn;
    }
    private void showImageEditor() {
        initRoot();header(chosenTool==Tool.ERASE?"图片擦除":"图片加水印",false);
        LinearLayout panel=vertical();pad(panel,16,0,16,8);root.addView(panel,new LinearLayout.LayoutParams(-1,0,1));
        panel.addView(text(chosenTool==Tool.ERASE?"涂抹小面积区域 · 复杂背景修复效果有限":"拖动调整位置 · 预览与导出使用相同参数",13,MUTED,false),lp(-1,34));
        editor=new ImageEditorView(this,openedImage,chosenTool==Tool.ERASE);
        panel.addView(editor,new LinearLayout.LayoutParams(-1,0,1));gap(panel,8);
        panel.addView(text("导出 PNG · "+openedImage.getWidth()+" × "+openedImage.getHeight()+" 像素",12,MUTED,false),lp(-1,22));
        ScrollView controls=new ScrollView(this);LinearLayout list=vertical();pad(list,4,4,4,8);controls.addView(list);
        int controlHeight=chosenTool==Tool.ERASE?185:260;
        panel.addView(controls,lp(-1,controlHeight));
        if(chosenTool==Tool.ERASE){
            android.widget.CheckBox rectangle=check("框选模式（关闭后使用画笔）",false);
            rectangle.setOnCheckedChangeListener((v,on)->editor.setRectangle(on));list.addView(rectangle,lp(-1,44));
            slider(list,"画笔大小",95,28,n->editor.setBrushDp(6+n));
            LinearLayout row=horizontal();
            smallButton(row,"撤销",()->editor.undo());smallButton(row,"重做",()->editor.redo());smallButton(row,"清空",()->editor.clear());
            list.addView(row,lp(-1,46));
        }else watermarkControls(list,editor,true);
        Button compare=button("按住查看原图",PANEL);list.addView(compare,lp(-1,42));
        compare.setOnTouchListener((v,e)->{editor.setCompare(e.getAction()!=android.view.MotionEvent.ACTION_UP&&e.getAction()!=android.view.MotionEvent.ACTION_CANCEL);return true;});
        gap(panel,8);Button save=button(batchMode?"统一水印 · 保存 "+batchFiles.size()+" 张图片":"保存到相册",PINK);panel.addView(save,lp(-1,54));save.setOnClickListener(v->{if(batchMode)saveBatchImages();else saveImage();});
    }
    private android.widget.CheckBox check(String label,boolean initial){
        android.widget.CheckBox c=new android.widget.CheckBox(this);c.setText(label);c.setTextColor(WHITE);c.setTextSize(14);c.setChecked(initial);return c;
    }
    private void smallButton(LinearLayout row,String label,Runnable run){
        Button b=button(label,PANEL);LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(0,-1,1);l.setMargins(dp(3),0,dp(3),0);row.addView(b,l);b.setOnClickListener(v->run.run());
    }
    private void slider(LinearLayout list,String label,int max,int initial,ProgressChange change){
        TextView t=text(label+" · "+initial,13,MUTED,false);list.addView(t,lp(-1,26));
        SeekBar bar=new SeekBar(this);bar.setMax(max);bar.setProgress(initial);change.change(initial);
        bar.setOnSeekBarChangeListener(seek(n->{change.change(n);t.setText(label+" · "+n);}));list.addView(bar,lp(-1,40));
    }
    private void watermarkControls(LinearLayout list,ImageEditorView target,boolean allowLogo){
        android.content.SharedPreferences preferences=getSharedPreferences("watermark_template",0);
        EditText input=new EditText(this);input.setTextColor(WHITE);input.setTextSize(16);input.setSingleLine(true);input.setHint("水印文字");input.setHintTextColor(MUTED);
        input.setText(preferences.getString("text","我的原创作品"));target.setWatermarkText(input.getText().toString());
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});list.addView(input,lp(-1,50));
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){target.setWatermarkText(s.toString());}});
        android.widget.CheckBox tiled=check("全图平铺水印",preferences.getBoolean("tiled",false));target.setTiled(tiled.isChecked());
        tiled.setOnCheckedChangeListener((v,on)->target.setTiled(on));list.addView(tiled,lp(-1,42));
        slider(list,"水印大小",68,preferences.getInt("size",22),n->target.setFontSp(12+n));
        slider(list,"不透明度",255,preferences.getInt("opacity",180),target::setWatermarkOpacity);
        slider(list,"角度（数值减 90°）",180,preferences.getInt("angle",90),n->target.setAngle(n-90));
        int savedColor=preferences.getInt("color",android.graphics.Color.WHITE);target.setColor(savedColor);
        LinearLayout colors=horizontal();int[] choices={android.graphics.Color.WHITE,android.graphics.Color.BLACK,0xFF9575F5,0xFFFFC864,0xFFF77CAB};
        for(int color:choices){TextView swatch=text("●",28,color,true);swatch.setGravity(Gravity.CENTER);colors.addView(swatch,new LinearLayout.LayoutParams(0,dp(44),1));swatch.setContentDescription("水印颜色 "+Integer.toHexString(color));swatch.setOnClickListener(v->target.setColor(color));}list.addView(colors);
        LinearLayout row=horizontal();
        if(allowLogo){smallButton(row,"选择 Logo",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_LOGO);});smallButton(row,"使用文字",()->target.setLogo(null));}
        smallButton(row,"存文字模板",()->{ImageEditorView.Mark m=target.snapshotMark();
            preferences.edit().putString("text",m.text).putBoolean("tiled",m.tiled).putInt("size",Math.round(m.scale*360)-12).putInt("opacity",m.opacity).putInt("angle",Math.round(m.angle)+90).putInt("color",m.color).apply();toast("文字样式已保存，下次打开会自动载入");});
        list.addView(row,lp(-1,46));
    }
    private interface ProgressChange { void change(int v); }
    private SeekBar.OnSeekBarChangeListener seek(ProgressChange change) {
        return new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s,int v,boolean user){change.change(v);}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
        };
    }
    private void chooseBatchImages(){
        if(busy)return;stopVideoPreview();stopWebPreview();linkScreen=false;startInput=null;endInput=null;batchVideoMode=false;batchVideos.clear();
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        try{startActivityForResult(i,PICK_BATCH);}catch(Exception e){toast("无法打开图片选择器");}
    }
    private void chooseBatchVideos(){
        if(busy)return;
        Tool[] tools={Tool.VIDEO_WATERMARK,Tool.VIDEO_REMOVE,Tool.COMPRESS,Tool.SPEED,Tool.RESIZE,Tool.MIRROR,Tool.TRIM,Tool.MUTE,Tool.AUDIO,Tool.MD5,Tool.GIF,Tool.FRAME};
        String[] names={"统一添加文字 / Logo 水印","统一固定区域修复","统一压缩","统一倍速（可保留声音）","统一裁剪尺寸","统一镜像","统一时长裁剪","统一静音","提取音频","修改 MD5","生成 GIF","指定时间取帧"};
        new AlertDialog.Builder(this).setTitle("选择批量视频功能").setItems(names,(d,k)->{
            stopVideoPreview();stopWebPreview();linkScreen=false;batchMode=false;batchFiles.clear();batchVideoMode=false;batchVideos.clear();editor=null;videoRegion=null;openedImage=null;startInput=null;endInput=null;chosenTool=tools[k];
            Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
            try{startActivityForResult(pick,PICK_BATCH_VIDEO);}catch(Exception e){toast("无法打开视频选择器");}
        }).show();
    }
    private void saveBatchImages(){
        if(busy||editor==null||batchFiles.isEmpty())return;
        ImageEditorView.Mark mark=editor.snapshotMark();java.util.List<Uri> files=new java.util.ArrayList<>(batchFiles);
        startBusy("批量水印 · 正在准备…");runJob(()->{
            int saved=0,failed=0;
            for(Uri uri:files){
                if(cancelled())break;Bitmap source=null,result=null;
                try{updateProgress("批量水印 · "+(saved+failed+1)+" / "+files.size());source=ImageOps.readBitmap(this,uri,3072);JobControl.check();result=ImageOps.watermark(source,mark);JobControl.check();Uri out=MediaOps.saveBitmap(this,result,"batch_"+System.currentTimeMillis()+"_"+saved+".png");rememberOutput(out);saved++;}
                catch(Exception e){if(cancelled())break;failed++;}catch(OutOfMemoryError e){failed++;}
                finally{if(result!=null)result.recycle();if(source!=null)source.recycle();}
            }
            finishBatch("批量水印",saved,failed,files.size());
        });
    }
    private void saveImage() {
        if (busy || openedImage==null) return;
        if(chosenTool==Tool.ERASE&&!editor.hasSelection()){toast("请先涂抹或框选区域");return;}
        Bitmap source=openedImage;
        boolean erase=chosenTool==Tool.ERASE;
        Bitmap temporaryMask;
        try{temporaryMask=erase?editor.exportMask():null;}catch(OutOfMemoryError e){toast("内存不足，请用较小图片");return;}
        final Bitmap mask=temporaryMask;
        ImageEditorView.Mark mark=erase?null:editor.snapshotMark();
        startBusy(erase?"正在修复像素…":"正在生成水印图片…");
        runJob(()->{
            try {
                Bitmap result=erase?ImageOps.inpaint(source,mask):ImageOps.watermark(source,mark);
                Uri saved;
                try{saved=MediaOps.saveBitmap(this,result,"watermark_"+System.currentTimeMillis()+".png");}
                finally{if(result!=source)result.recycle();}
                succeed(saved,"已保存到相册 · 水印工坊");
            } catch(OutOfMemoryError e){fail(new Exception("素材太大，内存不足；请使用较小图片"));} catch(Exception e){ fail(e); }
            finally{if(mask!=null&&!mask.isRecycled())mask.recycle();}
        });
    }
    private String title(Tool t) {
        switch(t) {
            case AUDIO:return "提取音频"; case FRAME:return "视频取帧";case GIF:return "视频转 GIF";
            case TRIM:return "裁剪时长";case MUTE:return "视频静音";case VIDEO_WATERMARK:return "视频加水印";case COMPRESS:return "视频压缩";case RESIZE:return "裁剪尺寸";case SPEED:return "视频倍速";case VIDEO_REMOVE:return "视频固定区域修复";case MIRROR:return "水平镜像";case MD5:return "修改文件 MD5";default:return "视频工具";
        }
    }
    private void showVideoTool() {
        startInput=null;endInput=null;muteCheck=null;videoRegion=null;repairTime=null;
        initRoot();header(title(chosenTool),false);
        LinearLayout content=scrollBody();
        TextView label=text(displayName(chosenFile),16,WHITE,true);pad(label,4,4,4,4); content.addView(label,lp(-1,48));
        if(batchVideoMode){content.addView(text("已选 "+batchVideos.size()+" 个视频 · 以首个视频预览设置统一参数",13,0xFFD0B6FF,true),lp(-1,38));}
        if(chosenTool==Tool.VIDEO_WATERMARK){
            content.addView(text("首帧位置预览 · 水印会叠加到整段视频",13,MUTED,false),lp(-1,32));
            editor=new ImageEditorView(this,openedImage,false);content.addView(editor,lp(-1,280));
            watermarkControls(content,editor,true);
        }else if(chosenTool==Tool.VIDEO_REMOVE){
            content.addView(text("多区域去水印 · 框内移动 · 拖动四角调整大小",12,MUTED,false),lp(-1,40));
            videoRegion=new VideoRegionView(this,openedImage);content.addView(videoRegion,lp(-1,320));regionControls(content,videoRegion);
            repairTime=numberInput("0");content.addView(text("查看指定时间画面（秒）",13,MUTED,false),lp(-1,30));content.addView(repairTime,lp(-1,48));
            LinearLayout controls=horizontal();smallButton(controls,"查看画面",()->previewRepairFrame());smallButton(controls,"清除框选",()->videoRegion.clear());content.addView(controls,lp(-1,48));
        }else{
            videoPreview=new VideoView(this);videoPreview.setBackgroundColor(Color.BLACK);content.addView(videoPreview,lp(-1,230));
            VideoView currentPreview=videoPreview;videoPreview.setVideoURI(chosenFile);videoPreview.setOnPreparedListener(mp->{mp.setLooping(true);if(!busy&&videoPreview==currentPreview)currentPreview.start();});
            videoPreview.setOnErrorListener((mp,what,extra)->{toast("预览无法播放；可以尝试导出处理");return true;});
            android.widget.MediaController controller=new android.widget.MediaController(this);controller.setAnchorView(videoPreview);videoPreview.setMediaController(controller);
        }
        gap(content,16);
        TextView intro=text(helpFor(chosenTool),15,MUTED,false);
        intro.setGravity(Gravity.TOP); content.addView(intro,lp(-1,-2));
        if(chosenTool==Tool.TRIM) {
            content.addView(text("起点（秒）",14,WHITE,true),lp(-1,28));
            startInput=numberInput("0");content.addView(startInput,lp(-1,54));
            content.addView(text("终点（秒，0 表示直到结尾）",14,WHITE,true),lp(-1,28));
            endInput=numberInput("0");content.addView(endInput,lp(-1,54));
        } else if(chosenTool==Tool.FRAME) {
            content.addView(text("取帧时间（秒）",14,WHITE,true),lp(-1,28));
            startInput=numberInput("1");content.addView(startInput,lp(-1,54));
        }
        if(chosenTool==Tool.COMPRESS){qualitySpinner=selector(content,"输出分辨率（长边）",new String[]{"1280 · 约 2.5 Mbps","720 · 约 1.2 Mbps","1920 · 约 6 Mbps"});}
        if(chosenTool==Tool.RESIZE){ratioSpinner=selector(content,"中心裁剪比例",new String[]{"1:1 正方形","9:16 竖屏","16:9 横屏"});}
        if(chosenTool==Tool.SPEED){speedSpinner=selector(content,"播放倍速（保留声音时维持音调）",new String[]{"0.5×","0.75×","1.25×","1.5×","2×","3×","4×"});speedSpinner.setSelection(4);}
        if(isRenderTool(chosenTool)||chosenTool==Tool.VIDEO_REMOVE){muteCheck=check(chosenTool==Tool.SPEED?"移除声音（默认保留声音）":"移除声音",false);content.addView(muteCheck,lp(-1,48));}
        gap(content,20);
        Button go=button(batchVideoMode?"开始处理 "+batchVideos.size()+" 个视频":"开始处理并保存",PINK);content.addView(go,lp(-1,60));
        go.setOnClickListener(v->processVideo());
        gap(content,16);
        TextView info=text("在本机处理，不上传视频。输出到系统相册或音乐目录。",13,MUTED,false);
        content.addView(info,lp(-1,50));
    }
    private String helpFor(Tool t) {
        switch(t) {
            case AUDIO:return "AAC 音轨无损提取；其他可解码音轨在本机转为 AAC，导出 M4A。无音轨时会提示。";
            case FRAME:return "截取指定时间的一帧画面，输出 PNG 图片。";
            case GIF:return "取视频前 8 秒制作 320 像素宽的动态 GIF，约 4 帧/秒。";
            case TRIM:return "在关键帧附近切取片段，不重新编码，适用于常见 MP4 视频。";
            case MUTE:return "移除音轨，保留原视频画面，不重新压缩。";
            case VIDEO_WATERMARK:return "水印叠加到每一帧。输出 H.264 MP4，长边最多 1920，最高 30 帧/秒。";
            case COMPRESS:return "重新编码为 H.264 MP4。压缩结果取决于原视频码率，不保证比原文件更小。";
            case MIRROR:return "水平翻转画面，导出 H.264 MP4。长边最多 1920，最高 30 帧/秒。";
            case RESIZE:return "围绕画面中心裁剪为指定比例，导出 H.264 MP4，长边最多 1920。";
            case SPEED:return "调整画面和声音的速度。保留声音时使用变速滤镜维持音调，导出 H.264 + AAC MP4；本地处理可能需要较长时间。";
            case VIDEO_REMOVE:return "同时填补最多 8 个固定水印区域，可分别框选平台标识和博主名字，保留字幕。适合固定位置和简单背景。不是 AI 物体跟踪；复杂背景可能留下模糊区域。选区应用到整段视频；支持 SDR 素材。";
            case MD5:return "给 MP4/MOV 添加合法的 free 数据块，改变文件 MD5，原始音视频数据保持不变。不会移除版权或平台识别信息。";
            default:return "本地视频处理";
        }
    }
    private EditText numberInput(String initial) {
        EditText e=new EditText(this);
        e.setSingleLine(true);e.setText(initial);e.setTextColor(WHITE);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }
    private double number(EditText e) {
        if (e==null) return 0;
        try {return Double.parseDouble(e.getText().toString().trim());}
        catch(Exception ignored) {return Double.NaN;}
    }
    private void previewRepairFrame(){
        if(busy||videoRegion==null)return;double seconds=number(repairTime);
        if(!Double.isFinite(seconds)||seconds<0||seconds>604800){toast("请输入有效秒数");return;}
        VideoRegionView target=videoRegion;Uri input=chosenFile;startBusy("读取指定时间画面…");runJob(()->{
            try{Bitmap frame=MediaOps.frame(this,input,(long)(seconds*1_000_000));int side=Math.max(frame.getWidth(),frame.getHeight());
                if(side>1280){Bitmap scaled=Bitmap.createScaledBitmap(frame,Math.max(1,frame.getWidth()*1280/side),Math.max(1,frame.getHeight()*1280/side),true);frame.recycle();frame=scaled;}
                Bitmap ready=frame;mainHandler.post(()->{if(isDestroyed()){ready.recycle();return;}stopBusy();if(cancelled()||videoRegion!=target){ready.recycle();return;}Bitmap previous=openedImage;openedImage=ready;target.setImage(ready);if(previous!=null&&previous!=ready)previous.recycle();});
            }catch(Exception e){fail(e);}
        });
    }
    private void processVideo() {
        if(busy)return;Tool action=chosenTool;Uri input=chosenFile;
        VideoTranscoder.Options options=new VideoTranscoder.Options();options.mute=muteCheck!=null&&muteCheck.isChecked();
        if(isRenderTool(action)){
            options.mirror=action==Tool.MIRROR;
            if(action==Tool.COMPRESS){int index=qualitySpinner.getSelectedItemPosition();options.maxSide=new int[]{1280,720,1920}[index];options.bitrate=new int[]{2_500_000,1_200_000,6_000_000}[index];}
            if(action==Tool.RESIZE)options.cropAspect=new float[]{1,9f/16,16f/9}[ratioSpinner.getSelectedItemPosition()];
            if(action==Tool.SPEED)options.speed=new float[]{.5f,.75f,1.25f,1.5f,2,3,4}[speedSpinner.getSelectedItemPosition()];
            if(action==Tool.VIDEO_WATERMARK)options.mark=editor.snapshotMark();
        }
        float[][] region=action==Tool.VIDEO_REMOVE&&videoRegion!=null?videoRegion.snapshotAll():null;
        if(action==Tool.VIDEO_REMOVE)try{region=RepairRegions.validate(region);}catch(Exception e){toast(e.getMessage());return;}
        final float[][] chosenRegions=region;
        if(action==Tool.VIDEO_REMOVE&&region==null){toast("请先在视频画面上框选修复区域");return;}
        double startSeconds=number(startInput),endSeconds=number(endInput);
        if(!Double.isFinite(startSeconds)||!Double.isFinite(endSeconds)||startSeconds<0||endSeconds<0||startSeconds>604800||endSeconds>604800){toast("请输入有效秒数（0 到 604800）");return;}
        long startUs=(long)(startSeconds*1_000_000),endUs=(long)(endSeconds*1_000_000);
        if(action==Tool.TRIM&&endUs!=0&&endUs<=startUs){toast("结束时间需要晚于起点");return;}
        java.util.List<Uri> files=batchVideoMode?new java.util.ArrayList<>(batchVideos):java.util.Collections.singletonList(input);
        boolean multiple=batchVideoMode;stopVideoPreview();startBusy("正在处理视频…");runJob(()->{
            int saved=0,failed=0;StringBuilder errors=new StringBuilder();
            for(int i=0;i<files.size();i++){
                if(cancelled())break;Uri source=files.get(i);String prefix=multiple?"第 "+(i+1)+" / "+files.size()+" 个 · ":"";
                try{updateProgress(prefix+"准备处理…");Uri out=processSingleVideo(action,source,options,chosenRegions,startUs,endUs,prefix);rememberOutput(out);saved++;
                    if(!multiple){succeed(out,"处理成功，文件已保存");return;}
                }catch(Exception e){if(cancelled())break;failed++;if(!multiple){fail(e);return;}if(errors.length()<1500)errors.append(displayName(source)).append("：").append(e.getMessage()).append("\n");}
                catch(OutOfMemoryError e){failed++;if(!multiple){fail(new Exception("内存不足，请使用较小素材"));return;}if(errors.length()<1500)errors.append(displayName(source)).append("：内存不足\n");}
            }
            if(multiple)finishVideoBatch(saved,failed,files.size(),errors.toString());else if(cancelled())fail(new java.io.InterruptedIOException("已取消"));
        });
    }
    private Uri processSingleVideo(Tool action,Uri input,VideoTranscoder.Options options,float[][] region,long startUs,long endUs,String prefix)throws Exception {
        if(action==Tool.AUDIO)return extractLocalAudio(input,prefix);
        if(action==Tool.MUTE)return MediaOps.saveRemux(this,input,false,true,0,0);
        if(action==Tool.TRIM)return MediaOps.saveRemux(this,input,false,false,startUs,endUs);
        if(action==Tool.FRAME){Bitmap bitmap=MediaOps.frame(this,input,startUs);try{return MediaOps.saveBitmap(this,bitmap,"frame_"+System.currentTimeMillis()+".png");}finally{bitmap.recycle();}}
        if(action==Tool.GIF)return MediaOps.saveGif(this,input);
        if(action==Tool.MD5)return MediaOps.changeFingerprint(this,input);
        if(action==Tool.VIDEO_REMOVE)return LocalVideoProcessor.exportRegions(this,input,1,!options.mute,region,message->updateLocalVideoProgress(prefix,message));
        if(action==Tool.SPEED&&!options.mute)return LocalVideoProcessor.export(this,input,options.speed,true,null,message->updateLocalVideoProgress(prefix,message));
        if(isRenderTool(action))return VideoTranscoder.export(this,input,options,n->updateExportProgress(prefix+"导出视频",n));
        throw new java.io.IOException("暂不支持此操作");
    }
    private Uri extractLocalAudio(Uri input,String prefix)throws Exception {
        try{return MediaOps.saveRemux(this,input,true,false,0,0);}catch(Exception error){
            JobControl.check();if(!"当前视频没有可无损提取的 AAC 音轨".equals(error.getMessage()))throw error;
            return LocalVideoProcessor.exportAudio(this,input,message->updateLocalVideoProgress(prefix,message));
        }
    }
    private void finishVideoBatch(int saved,int failed,int requested,String errors){
        boolean stopped=cancelled();mainHandler.post(()->{
            if(isDestroyed())return;stopBusy();new AlertDialog.Builder(this).setTitle(stopped?"已停止后续视频":"批量视频处理完成")
                .setMessage("已保存 "+saved+" 个，失败 "+failed+" 个，未处理 "+Math.max(0,requested-saved-failed)+" 个。\n已完成作品保留在我的文件。"+(errors.isEmpty()?"":"\n\n"+errors))
                .setPositiveButton("我的文件",(d,w)->showFiles()).setNegativeButton("继续",null).show();
        });
    }
    private boolean isRenderTool(Tool t){return t==Tool.VIDEO_WATERMARK||t==Tool.COMPRESS||t==Tool.RESIZE||t==Tool.MIRROR||t==Tool.SPEED;}
    private android.widget.Spinner selector(LinearLayout content,String label,String[] values){
        content.addView(text(label,14,WHITE,true),lp(-1,32));android.widget.Spinner s=new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> a=new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,values){
            @Override public View getView(int position,View reuse,ViewGroup parent){TextView t=(TextView)super.getView(position,reuse,parent);t.setTextColor(WHITE);t.setTextSize(16);return t;}
        };a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);content.addView(s,lp(-1,52));return s;
    }
    private void showAbout(){new AlertDialog.Builder(this).setTitle("水印工坊 · 0.6.10")
        .setMessage("照片与视频在本机处理。四路搜索会访问原网站，并将分享链接同时交给 Cobalt、TikTokio、GreenVideo；配置专用服务后也会向该服务发送链接。\n\n内置 yt-dlp、Python 与 FFmpeg，支持链接解析、视频 / 音频下载、图片和文案保存。已加入批量图片及视频 Logo 水印、作品管理和视频工具。\n\n本版本按 GPL-3.0 分发，完整工程与依赖构建说明随源码提供。开源组件：yausername/youtubedl-android、yt-dlp/yt-dlp、FFmpeg。\n\n图片擦除与视频多区域修复使用周围像素填补（最多 8 处），移动物体跟踪暂未实现。已加入保留声音的倍速及批量视频。视频渲染暂不支持 HDR。")
        .setPositiveButton("关闭",null).show();}
    private void stopVideoPreview(){if(videoPreview!=null){try{videoPreview.stopPlayback();}catch(Exception ignored){}videoPreview=null;}}
    private void showFiles(){
        if(busy)return;stopVideoPreview();stopWebPreview();linkScreen=false;filesScreen=true;editor=null;openedImage=null;
        long generation=++filePageGeneration;initRoot();header("我的文件",false);LinearLayout content=scrollBody();
        java.util.List<ExportHistory.Item> items=ExportHistory.read(this);long size=0;for(ExportHistory.Item i:items)size+=i.size;
        LinearLayout summary=vertical();pad(summary,18,18,18,18);summary.setBackground(grad(0xFF40315F,0xFF262B40,22));
        summary.addView(text("本地作品库",16,WHITE,true),lp(-1,28));summary.addView(text(items.size()+" 个记录  ·  "+formatSize(size),25,WHITE,true),lp(-1,50));
        summary.addView(text("作品保存在系统相册或音乐目录",13,MUTED,false),lp(-1,24));content.addView(summary);gap(content,16);
        LinearLayout tabs=horizontal();String[] names={"全部","图片","视频","音频"};
        for(int k=0;k<names.length;k++){final int filter=k;Button b=button(names[k],fileFilter==k?PINK:PANEL);tabs.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));b.setOnClickListener(v->{fileFilter=filter;showFiles();});}content.addView(tabs);gap(content,16);
        int shown=0;
        for(ExportHistory.Item item:items){
            if(fileFilter==1&&!item.mime.startsWith("image/"))continue;if(fileFilter==2&&!item.mime.startsWith("video/"))continue;if(fileFilter==3&&!item.mime.startsWith("audio/"))continue;shown++;
            LinearLayout card=horizontal();card.setGravity(Gravity.CENTER_VERTICAL);pad(card,12,12,8,12);card.setBackground(round(PANEL,18));
            android.widget.ImageView thumb=new android.widget.ImageView(this);thumb.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);thumb.setBackground(round(0xFF393251,12));card.addView(thumb,lp(62,62));card.addView(spaceWidth(12));
            LinearLayout details=vertical();TextView name=text(item.name,14,WHITE,true);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);details.addView(name);
            String date=new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new java.util.Date(item.time));details.addView(text(date+"  ·  "+formatSize(item.size),12,MUTED,false),lp(-1,28));card.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            TextView more=text("⋯",26,MUTED,true);more.setGravity(Gravity.CENTER);card.addView(more,lp(38,62));more.setContentDescription("作品操作");more.setOnClickListener(v->fileActions(item));card.setOnClickListener(v->openFile(Uri.parse(item.uri)));content.addView(card,lp(-1,-2));gap(content,10);
            if(!item.mime.startsWith("audio/")){Uri uri=Uri.parse(item.uri);previews.execute(()->{try{Bitmap b=getContentResolver().loadThumbnail(uri,new android.util.Size(124,124),null);mainHandler.post(()->{if(!isDestroyed()&&filePageGeneration==generation)thumb.setImageBitmap(b);else b.recycle();});}catch(Exception ignored){}});}
            else thumb.setImageResource(android.R.drawable.ic_media_play);
        }
        if(shown==0){gap(content,36);content.addView(text("还没有作品 · 完成处理后会显示在这里",15,MUTED,false),lp(-1,70));}
    }
    private String formatSize(long n){return n>=1024*1024?String.format(Locale.CHINA,"%.1f MB",n/1048576d):String.format(Locale.CHINA,"%.1f KB",n/1024d);}
    private void fileActions(ExportHistory.Item item){
        new AlertDialog.Builder(this).setTitle(item.name).setItems(new String[]{"打开","分享","删除导出文件","移除列表记录"},(d,k)->{
            Uri uri=Uri.parse(item.uri);
            if(k==0)openFile(uri);
            else if(k==1)shareFile(uri,item.mime);
            else if(k==2)new AlertDialog.Builder(this).setTitle("删除这个导出文件？").setMessage("将删除相册中的这份作品，原始素材不会受影响。").setNegativeButton("取消",null).setPositiveButton("删除",(a,b)->{
                try{getContentResolver().delete(uri,null,null);ExportHistory.forget(this,item.uri);showFiles();}catch(Exception e){toast("无法删除，请在系统相册删除或移除列表记录");}}).show();
            else {ExportHistory.forget(this,item.uri);showFiles();}
        }).show();
    }
    private void openFile(Uri uri){try{Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,getContentResolver().getType(uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Exception e){toast("文件无法打开，请检查系统相册或选择支持该格式的播放器");}}
    private void shareFile(Uri uri,String mime){try{Intent i=new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(android.content.ClipData.newRawUri("作品",uri));startActivity(Intent.createChooser(i,"分享作品"));}catch(Exception e){toast("无法分享此文件");}}
    private boolean handleSharedIntent(Intent intent){
        if(!Intent.ACTION_SEND.equals(intent.getAction()))return false;
        String sharedText=intent.getStringExtra(Intent.EXTRA_TEXT);
        if(sharedText!=null&&!LinkExtractor.extractLink(sharedText).isEmpty()){incomingLink=sharedText;extracted=null;try{if(LinkExtractor.extractLinks(sharedText).size()>1){queueDraft=sharedText;showLinkQueue();return true;}}catch(Exception ignored){}showLinkExtract();return true;}
        Uri uri=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(uri==null)return false;
        String mime=intent.getType();if(mime==null)return false;
        if(mime.startsWith("image/"))chosenTool=Tool.IMAGE_WATERMARK;
        else if(mime.startsWith("video/"))chosenTool=Tool.VIDEO_WATERMARK;else return false;
        loadSelected(uri);return true;
    }
    private void startBusy(String message) {
        if(busy)return;busy=true;
        jobCancel=new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean flag=jobCancel;
        activeTransfer=null;transferView=new SaveProgressView(this,message,()->flag.set(true));
        if(!message.contains("下载")&&!message.contains("保存")&&!message.contains("导出"))transferView.processing();
        progress=new android.app.Dialog(this);progress.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);progress.setCancelable(false);progress.setCanceledOnTouchOutside(false);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(transferView);progress.setContentView(scroll);
        progress.show();
        android.view.Window window=progress.getWindow();if(window!=null){window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(36),dp(408)),android.view.WindowManager.LayoutParams.WRAP_CONTENT);}
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void stopBusy() {
        busy=false;getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (progress!=null) { try {progress.dismiss();}catch(Exception ignored){} progress=null; }
        transferView=null;activeTransfer=null;
    }
    private void succeed(Uri uri,String info) {
        rememberOutput(uri);mainHandler.post(()->{
            if(isFinishing()||isDestroyed())return;TransferStats.Snapshot snapshot=activeTransfer==null?null:activeTransfer.complete();stopBusy();
            SaveProgressView done=new SaveProgressView(this,info,()->{});if(snapshot!=null)done.show(snapshot);done.complete();done.label(info);
            ScrollView completed=new ScrollView(this);completed.addView(done);
            AlertDialog savedDialog=new AlertDialog.Builder(this).setView(completed)
                .setNegativeButton("作品列表",(d,w)->showFiles())
                .setNeutralButton("分享",(d,w)->shareFile(uri,getContentResolver().getType(uri)))
                .setPositiveButton("打开",(d,w)->openFile(uri)).create();savedDialog.show();
            if(savedDialog.getWindow()!=null)savedDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF101724));
            for(int which:new int[]{AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_POSITIVE})savedDialog.getButton(which).setTextColor(0xFF5EEBE1);
            View bar=(View)savedDialog.getButton(AlertDialog.BUTTON_POSITIVE).getParent();bar.setBackgroundColor(0xFF101724);
        });
    }
    private void fail(Exception e) {
        mainHandler.post(()->{
            if(isFinishing()||isDestroyed())return;
            boolean wasCancelled=cancelled();stopBusy();
            if(wasCancelled){toast("已停止操作，已完成作品会保留");return;}
            new AlertDialog.Builder(this).setTitle("处理失败")
                .setMessage(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage())
                .setPositiveButton("确认",null).show();
        });
    }

    private void linkMenu(){new AlertDialog.Builder(this).setTitle("链接提取").setItems(new String[]{"最近提取链接","更新解析引擎","导入本人 cookies.txt","清除导入的 Cookie","专用解析服务设置","国内外平台与解析能力","保存去水印设置","搜索下载入口"},(d,k)->{
        if(k==0)showLinkRecords();else if(k==1){startBusy("正在更新解析引擎…");runJob(()->{try{String result=PlatformEngine.update(this);mainHandler.post(()->{if(isDestroyed())return;stopBusy();toast(result);});}catch(Exception e){fail(e);}});}
        else if(k==2)new AlertDialog.Builder(this).setTitle("导入本人 Cookie").setMessage("选择你本人导出的 Netscape 格式 cookies.txt。文件仅保存在本应用私有目录，供内置引擎访问对应平台使用，可随时在此菜单清除。").setNegativeButton("取消",null).setPositiveButton("选择文件",(dialog,which)->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("*/*");pick.addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(pick,1204);}catch(Exception e){toast("无法打开文件选择器");}}).show();
        else if(k==3){PlatformEngine.clearCookies(this);toast("已清除 Cookie 文件");}else if(k==4)showPlatformSettings();else if(k==5)showPlatformCoverage();else if(k==6)showDownloadRepairSettings();else showDownloadSites();
    }).show();}
    private void showPlatformCoverage(){
        if(busy)return;stopVideoPreview();stopWebPreview();linkScreen=true;initRoot();header("国内外平台",false);LinearLayout box=scrollBody();
        try{
            org.json.JSONObject catalog;try(java.io.InputStream in=getResources().openRawResource(R.raw.platforms);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);catalog=new org.json.JSONObject(out.toString("UTF-8"));}
            box.addView(text("全球素材 · 一个入口",22,WHITE,true),lp(-1,46));
            Button full=button("视频 / 音频规则 · 搜索与更新",0xFF6650BA);box.addView(full,lp(-1,48));full.setOnClickListener(v->showFullPlatformCatalog());gap(box,10);
            Button galleries=button("图集引擎规则 · 搜索全部图片站点",0xFF233B4B);box.addView(galleries,lp(-1,48));galleries.setOnClickListener(v->showFullPlatformCatalog(true));gap(box,10);
            box.addView(text("内置 "+catalog.optInt("extractorCount")+" 条解析规则 · 不限保存次数",13,0xFF5EEBE1,false),lp(-1,34));
            box.addView(text("一个平台可对应多条规则。下列平台已纳入路由；内置适配表示有站点解析器，实际可用性取决于链接、地区和登录状态。",12,MUTED,false),lp(-1,-2));gap(box,14);
            EditText search=new EditText(this);search.setSingleLine(true);search.setTextColor(WHITE);search.setHintTextColor(MUTED);search.setHint("搜索平台 · YouTube / 抖音 / Instagram");search.setTextSize(13);search.setBackground(round(PANEL,12));pad(search,12,0,12,0);box.addView(search,lp(-1,48));gap(box,14);
            LinearLayout siteRows=vertical();box.addView(siteRows,lp(-1,-2));
            LinearLayout rows=vertical();box.addView(rows,lp(-1,-2));org.json.JSONArray entries=catalog.getJSONArray("platforms");Runnable fill=()->{
                rows.removeAllViews();String q=search.getText().toString().toLowerCase(Locale.ROOT).trim();int visible=addDownloadSiteRows(siteRows,q);
                for(int i=0;i<entries.length();i++){org.json.JSONObject entry=entries.optJSONObject(i);if(entry==null)continue;String name=entry.optString("displayName"),mode=entry.optString("mode");if(!(name+" "+entry.optString("name")+" "+entry.optString("region")).toLowerCase(Locale.ROOT).contains(q))continue;
                    visible++;LinearLayout card=vertical();pad(card,15,12,15,12);card.setBackground(round(PANEL,14));card.addView(text(name,17,WHITE,true),lp(-1,30));card.addView(text(entry.optString("region")+" · "+mode,12,mode.equals("网页辅助")?0xFFD9B87A:0xFF5EEBE1,false),lp(-1,26));
                    String details=entry.optBoolean("upstreamBroken")?"上游已标记这条规则暂时故障，可尝试更新引擎或网页辅助。":mode.equals("网页辅助")?"打开页面或播放后提取；该站专用适配尚未完成。":"已接入站点解析器，下载成功率待真实链接验证；需要登录时使用本人的有效会话。";card.addView(text(details,12,MUTED,false),lp(-1,-2));rows.addView(card,lp(-1,-2));gap(rows,9);
                }if(visible==0)rows.addView(text("未找到该名称；其他站点仍可直接粘贴链接尝试解析。",13,MUTED,false),lp(-1,58));
            };fill.run();search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){fill.run();}public void afterTextChanged(Editable e){}});
            Button back=button("粘贴链接并提取",0xFF8B5DDD);box.addView(back,lp(-1,48));back.setOnClickListener(v->showLinkExtract());
        }catch(Exception e){toast("平台列表暂时无法读取");}
    }
    private void showFullPlatformCatalog(){showFullPlatformCatalog(false);}
    private void showFullPlatformCatalog(boolean galleries){
        if(busy)return;stopVideoPreview();stopWebPreview();linkScreen=true;initRoot();header("全部平台解析规则",false);LinearLayout box=scrollBody();
        String raw=galleries?"":getSharedPreferences("platform_catalog",0).getString("rules","");
        String version=galleries?"gallery-dl "+GalleryEngine.VERSION:getSharedPreferences("platform_catalog",0).getString("version",BundledEngine.VERSION);
        if(raw.isEmpty())try(java.io.InputStream in=getResources().openRawResource(galleries?R.raw.gallery_rules:R.raw.extractor_rules);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);raw=out.toString("UTF-8");
        }catch(Exception e){toast("目录读取失败");return;}
        final String[] all=raw.trim().split("\n");
        box.addView(text("引擎 "+version+" · "+all.length+" 条规则",18,WHITE,true),lp(-1,40));
        box.addView(text("包含短视频、社交、新闻、音频和地区站点。规则数不等于平台数；有解析器也需用实际链接验证，登录、地区或加密保护会影响保存。",12,MUTED,false),lp(-1,-2));gap(box,12);
        if(!galleries){Button sync=button("同步当前引擎目录",0xFF6650BA);box.addView(sync,lp(-1,46));
        sync.setOnClickListener(v->{if(busy)return;startBusy("读取当前引擎支持目录…");runJob(()->{try{PlatformEngine.syncRules(this);mainHandler.post(()->{stopBusy();if(!cancelled()&&!isDestroyed())showFullPlatformCatalog();});}catch(Exception e){fail(e);}});});
        box.addView(text("在设置中更新解析引擎后，可同步新增规则。",12,MUTED,false),lp(-1,34));}
        else box.addView(text("图集引擎随安装包更新。提取单条作品，最多 24 项；列表包含上游规则，实际可用性需要链接验证。",12,MUTED,false),lp(-1,-2));
        EditText search=new EditText(this);search.setSingleLine(true);search.setTextColor(WHITE);search.setHintTextColor(MUTED);search.setHint("搜索全部规则 · youtube / weibo / BBC");box.addView(search,lp(-1,48));
        TextView status=text("",12,0xFF5EEBE1,false);box.addView(status,lp(-1,36));
        LinearLayout navigation=horizontal();Button previous=button("上一页",PANEL),next=button("下一页",PANEL);navigation.addView(previous,new LinearLayout.LayoutParams(0,dp(44),1));navigation.addView(next,new LinearLayout.LayoutParams(0,dp(44),1));box.addView(navigation,lp(-1,48));
        LinearLayout siteRows=vertical();box.addView(siteRows,lp(-1,-2));
        LinearLayout rows=vertical();box.addView(rows,lp(-1,-2));final int[] page={0};
        Runnable fill=()->{
            java.util.ArrayList<String> found=new java.util.ArrayList<>();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
            addDownloadSiteRows(siteRows,q);
            for(String rule:all)if(rule.toLowerCase(Locale.ROOT).contains(q))found.add(rule);
            int pages=Math.max(1,(found.size()+39)/40);page[0]=Math.min(page[0],pages-1);rows.removeAllViews();
            status.setText("找到 "+found.size()+" 条 · 第 "+(page[0]+1)+" / "+pages+" 页");previous.setEnabled(page[0]>0);next.setEnabled(page[0]+1<pages);
            for(int i=page[0]*40;i<Math.min(found.size(),(page[0]+1)*40);i++){
                String rule=found.get(i);TextView row=text(rule,14,rule.contains("CURRENTLY BROKEN")?0xFFD9B87A:WHITE,false);pad(row,12,6,12,6);row.setBackground(round(PANEL,8));rows.addView(row,lp(-1,44));gap(rows,4);
            }
        };
        previous.setOnClickListener(v->{page[0]=Math.max(0,page[0]-1);fill.run();});next.setOnClickListener(v->{page[0]++;fill.run();});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){page[0]=0;fill.run();}});fill.run();
        Button parse=button("粘贴链接并提取",0xFF8B5DDD);box.addView(parse,lp(-1,48));parse.setOnClickListener(v->showLinkExtract());
    }
    private void showDownloadSites(){
        if(busy)return;stopVideoPreview();stopWebPreview();linkScreen=true;initRoot();header("搜索下载入口",false);LinearLayout box=scrollBody();
        box.addView(text("粘贴分享链接，选择网站解析，再将生成的媒体保存到手机。",14,MUTED,false),lp(-1,-2));gap(box,10);
        EditText source=new EditText(this);source.setTextColor(WHITE);source.setHintTextColor(MUTED);source.setHint("分享链接（可选）");source.setSingleLine(true);source.setText(incomingLink);box.addView(source,lp(-1,52));
        EditText search=new EditText(this);search.setTextColor(WHITE);search.setHintTextColor(MUTED);search.setHint("搜索网站 / 推特 / 抖音 / 小红书 / B站");search.setSingleLine(true);box.addView(search,lp(-1,52));
        LinearLayout rows=vertical();box.addView(rows,lp(-1,-2));
        Runnable fill=()->{incomingLink=LinkExtractor.extractLink(source.getText().toString());int count=addDownloadSiteRows(rows,search.getText().toString());if(count==0)rows.addView(text("没有匹配入口，请更换站名或平台关键词。",13,MUTED,false),lp(-1,54));};
        TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){fill.run();}};
        source.addTextChangedListener(watcher);search.addTextChangedListener(watcher);fill.run();
        Button back=button("返回链接下载",0xFF8B5DDD);box.addView(back,lp(-1,48));back.setOnClickListener(v->showLinkExtract());
    }
    private int addDownloadSiteRows(LinearLayout rows,String query){
        rows.removeAllViews();int count=0;DownloadSites.Site recommended=DownloadSites.recommended(incomingLink);
        for(DownloadSites.Site site:DownloadSites.ALL){if(!site.matches(query))continue;count++;
            LinearLayout card=vertical();pad(card,14,12,14,12);card.setBackground(round(PANEL,14));
            card.addView(text(site.name+(site==recommended?" · 对应平台入口":""),17,WHITE,true),lp(-1,32));
            card.addView(text(site.url+"\n"+site.detail,12,MUTED,false),lp(-1,-2));
            Button open=button("打开解析并下载",0xFF6650BA);card.addView(open,lp(-1,46));open.setOnClickListener(v->openDownloadSite(site));rows.addView(card,lp(-1,-2));gap(rows,10);
        }return count;
    }
    private void openDownloadSite(DownloadSites.Site site){
        if(busy)return;openWebAssist(site.url,site,incomingLink);
    }
    private void copyDownloadSource(String source){
        android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(cm!=null){cm.setPrimaryClip(android.content.ClipData.newPlainText("分享链接",source));toast("分享链接已复制");}
    }
    private void offerWebsiteDownload(android.webkit.WebView view,LinkExtractor.Result session,Button results,String url,String agent,String disposition,String mime){
        if(webPreview!=view||busy)return;String kind=DownloadSites.mediaKind(url,mime,disposition);
        if(kind.isEmpty()){toast("该下载未识别为 HTTPS 媒体；可用外部浏览器继续下载。");return;}
        LinkExtractor.Item item=new LinkExtractor.Item(LinkExtractor.Kind.valueOf(kind),url,"网站下载素材",view.getUrl(),false,0,agent,session.source);
        synchronized(session){session.addItem(item);}results.setText("查看已发现素材 · "+session.items.size()+" 项");
        new AlertDialog.Builder(this).setTitle("保存网站素材").setMessage("保存到手机媒体库，沿用当前去水印设置。")
            .setNegativeButton("暂不保存",null).setPositiveButton("保存到手机",(d,w)->{extracted=session;downloadOne(item);}).show();
    }
    private void saveLinkRecord(String url,String title){
        android.content.SharedPreferences p=getSharedPreferences("link_settings",0);
        try{org.json.JSONArray old=new org.json.JSONArray(p.getString("history","[]")),list=new org.json.JSONArray();list.put(new org.json.JSONObject().put("url",url).put("title",title));
            for(int i=0;i<old.length()&&list.length()<20;i++)if(!url.equals(old.getJSONObject(i).optString("url")))list.put(old.getJSONObject(i));p.edit().putString("history",list.toString()).apply();}catch(Exception ignored){}
    }
    private void showLinkRecords(){
        try{org.json.JSONArray list=new org.json.JSONArray(getSharedPreferences("link_settings",0).getString("history","[]"));if(list.length()==0){toast("还没有提取记录");return;}String[] labels=new String[list.length()];for(int i=0;i<labels.length;i++)labels[i]=list.getJSONObject(i).optString("title");
            new AlertDialog.Builder(this).setTitle("最近提取链接").setItems(labels,(d,k)->{try{incomingLink=list.getJSONObject(k).getString("url");extracted=null;showLinkExtract();}catch(Exception ignored){}}).setNegativeButton("关闭",null).show();}catch(Exception ignored){}
    }
    private void showPlatformSettings(){
        android.content.SharedPreferences p=getSharedPreferences("link_settings",0);LinearLayout form=vertical();pad(form,20,12,20,8);form.setBackgroundColor(PANEL);
        form.addView(text("平台解析服务可以处理专用平台链接。启用后会把你提交的链接发给该服务。留空使用手机内置引擎与网页解析。",14,MUTED,false),lp(-1,-2));
        EditText endpoint=new EditText(this);endpoint.setSingleLine(true);endpoint.setTextColor(WHITE);endpoint.setHintTextColor(MUTED);endpoint.setHint("https://你的服务/api/parse");endpoint.setText(p.getString("endpoint",""));form.addView(endpoint,lp(-1,58));
        form.addView(text("Cobalt 自动接口：填写自建或已获授权的实例。留空走网站辅助；网站首页不是 API。",12,MUTED,false),lp(-1,-2));
        EditText cobalt=new EditText(this);cobalt.setSingleLine(true);cobalt.setTextColor(WHITE);cobalt.setHintTextColor(MUTED);cobalt.setHint("https://你的 Cobalt 实例/");cobalt.setText(p.getString("cobalt_endpoint",""));form.addView(cobalt,lp(-1,52));
        EditText key=new EditText(this);key.setSingleLine(true);key.setTextColor(WHITE);key.setHintTextColor(MUTED);key.setHint("Cobalt API Key（实例要求时填写）");key.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);key.setText(p.getString("cobalt_key",""));form.addView(key,lp(-1,52));
        ScrollView settingsScroll=new ScrollView(this);settingsScroll.addView(form);
        new AlertDialog.Builder(this).setTitle("平台解析设置").setView(settingsScroll).setNegativeButton("取消",null).setNeutralButton("清除接口配置",(d,w)->p.edit().remove("endpoint").remove("cobalt_endpoint").remove("cobalt_key").apply()).setPositiveButton("保存",(d,w)->{
            String address=endpoint.getText().toString().trim(),cobaltAddress=cobalt.getText().toString().trim(),secret=key.getText().toString().trim();try{if(!address.isEmpty()&&!EngineMetadata.https(address))throw new Exception();if(!cobaltAddress.isEmpty())ProviderHttp.cobaltRequest("https://example.org/",cobaltAddress,secret);p.edit().putString("endpoint",address).putString("cobalt_endpoint",cobaltAddress).putString("cobalt_key",secret).apply();toast("解析方式已保存");}catch(Exception e){toast("请检查 HTTPS 接口地址及 API Key 格式");}
        }).show();
    }
    private void runJob(Runnable work){
        java.util.concurrent.atomic.AtomicBoolean flag=jobCancel;
        worker.execute(()->{JobControl.attach(flag);try{work.run();}catch(OutOfMemoryError error){fail(new Exception("内存不足，请使用较小素材"));}catch(LinkageError error){fail(new Exception("运行组件加载失败，请重新安装完整安装包"));}finally{JobControl.detach();}});
    }
    private boolean cancelled(){return jobCancel!=null&&jobCancel.get();}
    private void updateProgress(String label){java.util.concurrent.atomic.AtomicBoolean token=jobCancel;mainHandler.post(()->{if(progress!=null&&jobCancel==token&&transferView!=null)transferView.label(label);});}
    private void updateExportProgress(String label,int percent){java.util.concurrent.atomic.AtomicBoolean token=jobCancel;mainHandler.post(()->{if(progress!=null&&jobCancel==token&&transferView!=null&&!token.get())transferView.exportPercent(label,percent);});}
    private void updateLocalVideoProgress(String prefix,String label){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("^正在处理视频 · (\\d+)%$").matcher(label);
        if(m.find())updateExportProgress(prefix+"正在导出作品",Integer.parseInt(m.group(1)));else updateProgress(prefix+label);
    }
    private LinkExtractor.Progress downloadProgress(String label){
        TransferStats stats=new TransferStats();java.util.concurrent.atomic.AtomicBoolean token=jobCancel;
        return new LinkExtractor.Progress(){
            private long posted;private boolean nativeStarted;
            private void render(TransferStats.Snapshot s,boolean force){long now=android.os.SystemClock.elapsedRealtime();if(!force&&now-posted<100)return;posted=now;mainHandler.post(()->{if(progress!=null&&transferView!=null&&jobCancel==token&&!token.get()){activeTransfer=stats;transferView.show(s);}});}
            public void phase(String phase){nativeStarted=false;render(stats.phase(label+" · "+phase),true);}
            public void update(long bytes,long total){render(stats.bytes(bytes,total,android.os.SystemClock.elapsedRealtime()),bytes==total);}
            public void nativeSample(long bytes,long total,double speed,double eta,boolean estimated){if(!nativeStarted){stats.phase(label+" · 平台媒体流下载");nativeStarted=true;}render(stats.nativeSample(bytes,total,speed,eta,estimated),false);}
            public void complete(){render(stats.complete(),true);}
        };
    }
    private void rememberOutput(Uri uri){try{ExportHistory.record(this,uri,"导出作品");}catch(Exception ignored){}}
    private void finishBatch(String label,int saved,int failed,int requested){
        boolean wasCancelled=cancelled();mainHandler.post(()->{
            if(isFinishing()||isDestroyed())return;stopBusy();
            new AlertDialog.Builder(this).setTitle(wasCancelled?"已停止后续处理":label+"完成")
                .setMessage("已保存 "+saved+" 项，失败 "+failed+" 项，未处理 "+Math.max(0,requested-saved-failed)+" 项。已完成作品保留在文件列表。")
                .setPositiveButton("我的文件",(d,w)->showFiles()).setNegativeButton("继续",null).show();
        });
    }
    private void downloadAudio(LinkExtractor.Item video){
        if(busy)return;startBusy("正在下载视频，再提取音轨…");runJob(()->{
            try{if(video.engine){LinkExtractor.Item audioItem=new LinkExtractor.Item(LinkExtractor.Kind.AUDIO,video.url,"音频",video.referer,true,video.engineIndex,video.userAgent);Uri audio=LinkExtractor.download(this,audioItem,downloadProgress("下载音频"));succeed(audio,"音频已保存到系统媒体库");return;}
                LinkExtractor.Progress audioProgress=downloadProgress("音频导出");Uri source=LinkExtractor.download(this,video,audioProgress);rememberOutput(source);JobControl.check();audioProgress.phase("视频已保存，正在提取音轨");
                Uri audio=extractLocalAudio(source,"");succeed(audio,"音频已保存；下载的原视频也在我的文件中");
            }catch(Exception e){fail(e);}
        });
    }
    private void stopWebPreview(){if(webPreview!=null){webPreview.stopLoading();webPreview.destroy();webPreview=null;}}
    private void showWebAssist(String url){
        if(busy)return;incomingLink=url;startBusy("正在检查分享链接…");runJob(()->{
            try{LinkExtractor.ensurePublic(url);JobControl.check();mainHandler.post(()->{if(isDestroyed())return;stopBusy();if(!cancelled())openWebAssist(url);});}catch(Exception e){fail(e);}
        });
    }
    private void openWebAssist(String url){openWebAssist(url,null,"");}
    private void openWebAssist(String url,DownloadSites.Site site,String source){
        stopVideoPreview();stopWebPreview();linkScreen=true;initRoot();header("网页播放辅助",false);
        TextView hint=text(site==null?"打开笔记或播放视频；需要登录时用本人账号登录，再点提取当前页面":"点击填入链接后，在网站完成解析；点击下载保存到手机。",13,MUTED,false);pad(hint,16,0,16,0);root.addView(hint,lp(-1,50));
        LinearLayout actions=horizontal();pad(actions,12,4,12,8);
        Button results=button("查看已发现素材",PINK);actions.addView(results,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(actions);
        Button scan=button("提取当前页面",0xFF6650BA);actions.addView(scan,new LinearLayout.LayoutParams(0,dp(48),1));
        java.util.concurrent.atomic.AtomicReference<String> currentPage=new java.util.concurrent.atomic.AtomicReference<>(url);
        LinkExtractor.Result session=new LinkExtractor.Result();session.source=site!=null&&!source.isEmpty()?source:url;session.title=site==null?"网页播放发现的素材":site.name+" 下载素材";
        if(extracted!=null&&extracted.source.equals(url)){for(LinkExtractor.Item item:extracted.items)session.addItem(item);session.description=extracted.description;session.title=extracted.title;}
        results.setOnClickListener(v->{
            if(session.items.isEmpty()&&session.description.isEmpty()){toast("尚未发现素材，请打开笔记或播放视频，再点提取当前页面");return;}
            LinkExtractor.Result snapshot=new LinkExtractor.Result();
            synchronized(session){snapshot.source=session.source;snapshot.title=session.title;snapshot.description=session.description;for(LinkExtractor.Item item:session.items)snapshot.addItem(item);}
            extracted=snapshot;stopWebPreview();showLinkExtract();
        });
        android.webkit.WebView view=new android.webkit.WebView(this);webPreview=view;root.addView(view,new LinearLayout.LayoutParams(-1,0,1));
        scan.setOnClickListener(v->readWebMetadata(view,session,results,view.getUrl(),site!=null));
        if(site!=null){
            LinearLayout tools=horizontal();Button fill=button("填入分享链接",0xFF6650BA),external=button("外部浏览器",PANEL);
            tools.addView(fill,new LinearLayout.LayoutParams(0,dp(44),1));tools.addView(external,new LinearLayout.LayoutParams(0,dp(44),1));root.addView(tools,root.indexOfChild(view));
            fill.setOnClickListener(v->{if(source.isEmpty()){toast("请返回下载入口先粘贴分享链接");return;}if(!site.owns(view.getUrl())){toast("请回到原下载网站后填入链接");return;}view.evaluateJavascript(DownloadSites.fillScript(source),value->toast("true".equals(value)?"已填入，请在网站点击解析":"输入框尚未就绪，可复制链接手动粘贴"));});
            external.setOnClickListener(v->{if(!source.isEmpty())copyDownloadSource(source);try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(site.url)));}catch(Exception e){toast("未找到浏览器");}});
            view.setDownloadListener((download,agent,disposition,mime,length)->offerWebsiteDownload(view,session,results,download,agent,disposition,mime));
        }
        Button retry=button("完成验证 / 登录后重试四路搜索",0xFF6650BA);root.addView(retry,root.indexOfChild(view));
        retry.setOnClickListener(v->{android.webkit.CookieManager.getInstance().flush();String original=source.isEmpty()?url:source;stopWebPreview();parseSharedLink(original);});
        view.getSettings().setJavaScriptEnabled(true);view.getSettings().setDomStorageEnabled(true);view.getSettings().setAllowFileAccess(false);view.getSettings().setAllowContentAccess(false);
        view.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        view.getSettings().setMediaPlaybackRequiresUserGesture(true);
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(view,false);
        WebSessionCookies.enable();
        view.setWebViewClient(new android.webkit.WebViewClient(){
            @Override public void onPageStarted(android.webkit.WebView browser,String page,Bitmap icon){currentPage.set(page);}
            @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView browser,android.webkit.WebResourceRequest request){
                String next=request.getUrl().toString();if(next.startsWith("https://"))return false;toast("应用内只打开 HTTPS 页面，请留在网页继续操作");return true;
            }
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(android.webkit.WebView browser,android.webkit.WebResourceRequest request){
                String next=request.getUrl().toString();
                try{
                    LinkExtractor.ensurePublic(next);
                    if(!PlaybackRequests.onWorkPage(session.source,currentPage.get()))return null;
                    LinkExtractor.Kind kind=PlaybackRequests.kind(next,session.source);
                    if(kind!=null){String referer=PlaybackRequests.header(request.getRequestHeaders(),"Referer",url),agent=PlaybackRequests.header(request.getRequestHeaders(),"User-Agent",WebSessionCookies.userAgent(""));
                        synchronized(session){session.addItem(new LinkExtractor.Item(kind,next,"网页播放媒体直链",referer,false,0,agent,session.source));}
                        mainHandler.post(()->{if(webPreview==view)results.setText("查看已发现素材 · "+session.items.size()+" 项");});
                    }
                    return null;
                }catch(Exception e){return new android.webkit.WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));}
            }
            @Override public void onPageFinished(android.webkit.WebView browser,String page){
                if(webPreview!=view)return;currentPage.set(page);if(DouyinParser.matches(session.source)&&DouyinParser.id(session.source).isEmpty()&&DouyinParser.matches(page)&&!DouyinParser.id(page).isEmpty())session.source=page;android.webkit.CookieManager.getInstance().flush();String title=browser.getTitle();if(title!=null&&!title.isEmpty())session.title=title;
                if(site==null)readWebMetadata(browser,session,results,page);
            }
        });
        view.loadUrl(url);
    }
    private void readWebMetadata(android.webkit.WebView view,LinkExtractor.Result session,Button results,String page){readWebMetadata(view,session,results,page,false);}
    private void readWebMetadata(android.webkit.WebView view,LinkExtractor.Result session,Button results,String page,boolean downloads){
        if(webPreview!=view||page==null||!PlaybackRequests.onWorkPage(session.source,page))return;
        // Read the displayed note, excluding cookies, account stores and comments.
        String script=WebMediaScanner.script();
        view.evaluateJavascript(script,value->{
            if(webPreview!=view)return;
            try{Object raw=new org.json.JSONTokener(value).nextValue();if(!(raw instanceof String)||((String)raw).length()>1_500_000)return;
                org.json.JSONObject object=new org.json.JSONObject((String)raw);LinkExtractor.Result metadata;
                if(downloads){metadata=new LinkExtractor.Result();metadata.source=page;String title=view.getTitle();if(title!=null)metadata.title=title;}
                else if(DouyinParser.matches(page))metadata=DouyinBrowserMedia.parse(object,session.source,page,view.getSettings().getUserAgentString());
                else if(XhsParser.matches(page)&&!object.optString("state").isEmpty()){
                    LinkExtractor.Result candidates=XhsParser.parseState(object.optString("state"),page);metadata=new LinkExtractor.Result();metadata.source=page;metadata.title=candidates.title;metadata.description=candidates.description;boolean video=false;
                    for(LinkExtractor.Item item:candidates.items){if(item.kind==LinkExtractor.Kind.IMAGE)metadata.addItem(item);else if(item.kind==LinkExtractor.Kind.VIDEO&&!video&&!item.label.contains("原始")){metadata.addItem(item);video=true;}}
                    if(!video)for(LinkExtractor.Item item:candidates.items)if(item.kind==LinkExtractor.Kind.VIDEO){metadata.addItem(item);break;}
                }else {try{metadata=LinkExtractor.parsePublicHtml(object.optString("html"),page);}catch(Exception noMetadata){if(object.optJSONArray("media")==null||object.getJSONArray("media").length()==0)throw noMetadata;metadata=new LinkExtractor.Result();metadata.source=page;String title=view.getTitle();if(title!=null)metadata.title=title;}}
                org.json.JSONArray playing=object.optJSONArray("media");if(playing!=null)for(int i=0;i<playing.length();i++){org.json.JSONObject source=playing.optJSONObject(i);if(source==null)continue;try{metadata.add(LinkExtractor.Kind.valueOf(source.optString("kind")),source.optString("url"),"当前页面播放素材");}catch(IllegalArgumentException ignored){}}

                synchronized(session){session.title=metadata.title;session.description=metadata.description;for(LinkExtractor.Item item:metadata.items)session.addItem(item);}
                results.setText("查看已发现素材 · "+session.items.size()+" 项");
                if(downloads&&session.items.isEmpty())toast("尚未发现媒体，请先在网站解析并点击下载；也可使用外部浏览器。");
            }catch(Exception ignored){toast("页面尚未返回素材，请打开具体笔记或播放后再提取");}
        });
    }
    private void toast(String m) {Toast.makeText(this,m,Toast.LENGTH_LONG).show();}
    private String displayName(Uri uri) {
        if(uri==null)return "未选择文件";
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(c!=null && c.moveToFirst())return c.getString(0);
        } catch(Exception ignored) {}
        return "已选择视频";
    }
    @Override public void onBackPressed(){if(busy)return;if(webPreview!=null){stopWebPreview();showLinkExtract();return;}if(linkScreen&&extracted!=null){extracted=null;showLinkExtract();return;}showHome();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(chosenTool!=null)state.putString("tool",chosenTool.name());state.putString("link",incomingLink);}
    @Override protected void onPause(){super.onPause();if(videoPreview!=null)videoPreview.pause();}
    @Override protected void onDestroy(){if(repairChoiceDialog!=null)repairChoiceDialog.dismiss();stopVideoPreview();stopWebPreview();if(jobCancel!=null)jobCancel.set(true);previews.shutdownNow();worker.shutdownNow();if(progress!=null){progress.dismiss();progress=null;}super.onDestroy();}
}
