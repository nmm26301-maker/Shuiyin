package com.zizai.watermark;

import android.content.Context;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import org.json.*;
import java.util.*;

/** Store only this app's exports; no broad photo/storage permission is needed. */
final class ExportHistory {
    static class Item {
        String uri,name,mime; long time,size;
        Item(JSONObject o){uri=o.optString("uri");name=o.optString("name");mime=o.optString("mime");time=o.optLong("time");size=o.optLong("size");}
        JSONObject json() throws JSONException {return new JSONObject().put("uri",uri).put("name",name).put("mime",mime).put("time",time).put("size",size);}
    }
    static synchronized List<Item> read(Context c){
        List<Item> list=new ArrayList<>();
        try {JSONArray a=new JSONArray(c.getSharedPreferences("exports",0).getString("items","[]"));for(int i=0;i<a.length();i++)list.add(new Item(a.getJSONObject(i)));}catch(JSONException ignored){}
        return list;
    }
    static synchronized void record(Context c,Uri uri,String fallback) throws Exception {
        List<Item> items=read(c);JSONObject o=new JSONObject().put("uri",uri.toString()).put("name",fallback).put("mime",c.getContentResolver().getType(uri)).put("time",System.currentTimeMillis()).put("size",0);
        try(Cursor q=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
            if(q!=null&&q.moveToFirst()){o.put("name",q.getString(0));o.put("size",q.getLong(1));}
        }
        items.removeIf(i->i.uri.equals(uri.toString()));items.add(0,new Item(o));while(items.size()>300)items.remove(items.size()-1);write(c,items);
    }
    static synchronized void forget(Context c,String uri){List<Item> items=read(c);items.removeIf(i->i.uri.equals(uri));write(c,items);}
    private static void write(Context c,List<Item> items){
        JSONArray a=new JSONArray();try{for(Item i:items)a.put(i.json());}catch(JSONException ignored){}
        c.getSharedPreferences("exports",0).edit().putString("items",a.toString()).apply();
    }
}
