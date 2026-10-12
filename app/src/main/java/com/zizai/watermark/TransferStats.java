package com.zizai.watermark;
import java.util.Locale;

/** Download measurements. Time and bytes are supplied by the transport, never by animation. */
final class TransferStats {
    static final class Snapshot {
        final String phase,amount,speed,remaining;final double fraction;final boolean estimated;
        Snapshot(String p,String a,String s,String e,double f,boolean estimate){phase=p;amount=a;speed=s;remaining=e;fraction=f;estimated=estimate;}
    }
    private long lastBytes,lastTime;private double rate;private String phase="连接素材服务器";
    private Snapshot current=new Snapshot(phase,"等待素材大小","—","—",-1,false);
    synchronized Snapshot phase(String value){phase=value;lastBytes=0;lastTime=0;rate=0;current=new Snapshot(phase,current.fraction>=0?current.amount:"等待数据","—","—",-1,false);return current;}
    synchronized Snapshot bytes(long received,long total,long now){
        received=Math.max(0,received);
        if(lastTime==0||received<lastBytes){lastTime=now;lastBytes=received;rate=0;}
        else if(now-lastTime>=250){double sample=(received-lastBytes)*1000.0/(now-lastTime);rate=rate==0?sample:rate*0.65+sample*0.35;lastTime=now;lastBytes=received;}
        return sample(received,total,rate,total>0&&rate>0?(total-received)/rate:-1,false);
    }
    synchronized Snapshot nativeSample(long received,long total,double speed,double eta,boolean estimated){return sample(received,total,finite(speed),finite(eta),estimated);}
    private Snapshot sample(long received,long total,double speed,double eta,boolean estimated){
        double fraction=total>0?Math.max(0,Math.min(.99,(double)Math.max(0,received)/total)):-1;
        current=new Snapshot(phase,size(Math.max(0,received))+(total>0?" / "+(estimated?"约 ":"")+size(total):" · 大小未知"),speed>0?size((long)speed)+"/s":"—",eta>=0?time(eta):"—",fraction,estimated);return current;
    }
    synchronized Snapshot complete(){current=new Snapshot("保存完成",current.amount,"完成","00:00",1,false);return current;}
    synchronized Snapshot current(){return current;}
    private static double finite(double n){return Double.isFinite(n)&&n>=0?n:-1;}
    static String size(long bytes){double n=Math.max(0,bytes);String[] units={"B","KB","MB","GB"};int i=0;while(n>=1024&&i<3){n/=1024;i++;}return String.format(Locale.ROOT,i==0?"%.0f %s":"%.1f %s",n,units[i]);}
    static String time(double seconds){if(!Double.isFinite(seconds)||seconds<0)return "—";long n=(long)Math.min(359999,Math.ceil(seconds));return n>=3600?String.format(Locale.ROOT,"%02d:%02d:%02d",n/3600,(n/60)%60,n%60):String.format(Locale.ROOT,"%02d:%02d",n/60,n%60);}
}
