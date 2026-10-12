package com.zizai.watermark;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Native neon HUD. The scanning light is decorative; percentages use transport measurements. */
final class SaveProgressView extends LinearLayout {
    private static final int CYAN=0xFF5EEBE1,PURPLE=0xFFA99CFF,INK=0xFF101724,MUTED=0xFF96AABC;
    private final TextView title,phase,amount,speed,remaining;
    private final Gauge gauge;
    final Button cancel;
    SaveProgressView(Context context,String label,Runnable cancellation){
        super(context);setOrientation(VERTICAL);setPadding(d(22),d(23),d(22),d(20));
        GradientDrawable panel=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF142433,INK,0xFF1E1936});panel.setCornerRadius(d(24));panel.setStroke(d(1),0xFF426879);setBackground(panel);
        TextView tag=text("●  TRANSFER CORE  /  媒体保存",10,CYAN,true);tag.setLetterSpacing(.12f);addView(tag);
        title=text("正在保存素材",23,0xFFF4F8FF,true);LayoutParams tp=new LayoutParams(-1,-2);tp.topMargin=d(12);addView(title,tp);
        phase=text(label,13,MUTED,false);phase.setMinHeight(d(44));phase.setMaxLines(3);addView(phase,new LayoutParams(-1,-2));
        gauge=new Gauge(context);addView(gauge,new LayoutParams(-1,d(185)));
        amount=text("等待素材大小",13,0xFFDCE7F5,false);amount.setGravity(Gravity.CENTER);addView(amount,new LayoutParams(-1,d(28)));
        LinearLayout row=new LinearLayout(context);row.setOrientation(HORIZONTAL);row.setPadding(0,d(17),0,d(15));
        speed=metric(row,"实时速度");remaining=metric(row,"预计剩余");addView(row,new LayoutParams(-1,-2));
        View rule=new View(context);rule.setBackgroundColor(0xFF2F4657);addView(rule,new LayoutParams(-1,d(1)));
        TextView note=text("下载 → 合并 / 校验 → 保存相册",11,MUTED,false);note.setGravity(Gravity.CENTER);addView(note,new LayoutParams(-1,d(38)));
        cancel=new Button(context);cancel.setText("取消保存");cancel.setTextColor(CYAN);cancel.setTextSize(13);cancel.setAllCaps(false);GradientDrawable b=new GradientDrawable();b.setColor(0xFF182D3A);b.setCornerRadius(d(13));b.setStroke(d(1),0xFF456A78);cancel.setBackground(b);addView(cancel,new LayoutParams(-1,d(44)));
        cancel.setOnClickListener(v->{cancel.setEnabled(false);cancel.setText("正在停止…");phase.setText("正在停止并清理未完成文件");cancellation.run();});
    }
    private TextView metric(LinearLayout row,String label){LinearLayout col=new LinearLayout(getContext());col.setOrientation(VERTICAL);TextView t=text(label,10,MUTED,false);t.setGravity(Gravity.CENTER);col.addView(t);TextView value=text("—",18,0xFFF4F8FF,true);value.setGravity(Gravity.CENTER);value.setTypeface(Typeface.MONOSPACE);col.addView(value,new LayoutParams(-1,d(32)));row.addView(col,new LayoutParams(0,-2,1));return value;}
    private TextView text(String s,int sp,int color,boolean bold){TextView v=new TextView(getContext());v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private int d(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    void label(String value){phase.setText(value);}
    void show(TransferStats.Snapshot s){if(!cancel.isEnabled()&&s.fraction<1)return;phase.setText(s.phase);amount.setText(s.amount);speed.setText(s.speed);remaining.setText(s.remaining);gauge.fraction=s.fraction;gauge.estimated=s.estimated;gauge.setContentDescription(s.phase+(s.fraction<0?"，进度未知":"，"+(int)(s.fraction*100)+"%"));gauge.invalidate();if(s.fraction==1){title.setText("保存完成");cancel.setVisibility(GONE);}else{title.setText("正在保存素材");cancel.setVisibility(VISIBLE);}}
    void exportPercent(String label,int percent){if(!cancel.isEnabled())return;title.setText("正在导出作品");phase.setText(label);gauge.fraction=Math.max(0,Math.min(99,percent))/100.0;gauge.estimated=false;gauge.invalidate();amount.setText("编码并写入作品文件");speed.setText("—");remaining.setText("—");cancel.setVisibility(VISIBLE);}
    void complete(){title.setText("保存完成");phase.setText("文件已完成媒体库登记");if(amount.getText().toString().contains("等待"))amount.setText("可在我的文件中查看作品");gauge.fraction=1;gauge.invalidate();cancel.setVisibility(GONE);}
    void processing(){title.setText("正在处理素材");cancel.setText("取消处理");}
    private final class Gauge extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);double fraction=-1;boolean estimated;private boolean running;
        Gauge(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();running=true;invalidate();}
        @Override protected void onDetachedFromWindow(){running=false;super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float w=getWidth(),h=getHeight(),cx=w/2,cy=d(75),r=d(57);long now=SystemClock.elapsedRealtime();float rotation=(now%5000)/5000f*360;
            p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d(.5f));p.setColor(0xFF1C3B49);
            for(float x=0;x<w;x+=d(20))canvas.drawLine(x,0,x,h-d(13),p);for(float y=0;y<h-d(13);y+=d(20))canvas.drawLine(0,y,w,y,p);
            RectF bounds=new RectF(cx-r,cy-r,cx+r,cy+r);p.setStrokeWidth(d(4));p.setStrokeCap(Paint.Cap.ROUND);
            for(int i=0;i<48;i++){boolean lit=fraction>=0&&i/48.0<fraction;p.setColor(lit?CYAN:0xFF29404D);if(lit)p.setShadowLayer(d(5),0,0,0x9947D9D8);else p.clearShadowLayer();canvas.drawArc(bounds,-90+i*7.5f,3.6f,false,p);}p.clearShadowLayer();
            p.setStrokeWidth(d(1));p.setColor(PURPLE);canvas.drawArc(new RectF(cx-r-d(8),cy-r-d(8),cx+r+d(8),cy+r+d(8)),rotation,38,false,p);
            p.setColor(0xFF294F5E);canvas.drawArc(new RectF(cx-r+d(8),cy-r+d(8),cx+r-d(8),cy+r-d(8)),-rotation,120,false,p);
            p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);p.setColor(0xFFF0FFFF);p.setTextSize(d(31));String value=fraction<0?"···":String.valueOf((int)Math.floor(fraction*100))+"%";canvas.drawText(value,cx,cy+d(5),p);
            p.setTextSize(d(9));p.setColor(MUTED);p.setTypeface(Typeface.MONOSPACE);canvas.drawText(fraction<0?"WAITING FOR DATA":estimated?"ESTIMATED TRANSFER":"CURRENT TRANSFER",cx,cy+d(23),p);
            float left=d(2),right=w-d(2),top=h-d(36),height=d(8);p.setColor(0xFF233D4D);canvas.drawRoundRect(left,top,right,top+height,d(4),d(4),p);
            if(fraction>=0){p.setShader(new LinearGradient(left,0,right,0,CYAN,PURPLE,Shader.TileMode.CLAMP));p.setShadowLayer(d(5),0,0,0x775EEBE1);canvas.drawRoundRect(left,top,left+(right-left)*(float)fraction,top+height,d(4),d(4),p);p.clearShadowLayer();p.setShader(null);}else{float travel=((now%1600)/1600f)*(right-left-d(45));p.setColor(CYAN);canvas.drawRoundRect(left+travel,top,left+travel+d(45),top+height,d(4),d(4),p);}
            p.setColor(MUTED);p.setTextSize(d(9));p.setTextAlign(Paint.Align.LEFT);canvas.drawText("0",left,top+d(24),p);p.setTextAlign(Paint.Align.RIGHT);canvas.drawText("100",right,top+d(24),p);
            if(running&&fraction!=1)postInvalidateDelayed(33);
        }
    }
}
