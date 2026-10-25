package com.zizai.watermark;

/** Reads only the selected public work; account state never leaves the page. */
final class DouyinPageState {
    static String script(){return """
        (function(){
          var id=(location.pathname.match(/\\/(?:video|note|share\\/video|share\\/slides)\\/(\\d+)/)||[])[1]||((location.search||'').match(/[?&](?:modal_id|aweme_id)=(\\d+)(?:&|$)/)||[])[1];
          if(!id)return '';
          var visited=0;
          function selected(o,d){
            if(!o||typeof o!=='object'||d>12||++visited>10000)return null;
            if(String(o.aweme_id||o.awemeId)===id)return o;
            var keys=Object.keys(o).slice(0,100);
            for(var key of keys){if(/recommend/i.test(key))continue;var found=selected(o[key],d+1);if(found)return found;}
            return null;
          }
          var item=selected(window._ROUTER_DATA,0),rd=document.getElementById&&document.getElementById('RENDER_DATA');
          if(!item&&rd)try{var text=rd.textContent.trim();item=selected(JSON.parse(text[0]==='{'?text:decodeURIComponent(text)),0);}catch(e){}
          // Flight chunks can contain a line prefix before JSON. Never evaluate page text.
          var budget=1500000;
          function chunk(text){
            if(typeof text!=='string'||text.length>budget)return null;budget-=text.length;
            try{text=decodeURIComponent(text);}catch(e){}
            var start=0,roots=0;
            while(start<text.length&&roots++<64){
              start=text.indexOf('{',start);if(start<0)break;
              var quote=false,escape=false,depth=0,end=start;
              for(;end<text.length;end++){
                var c=text[end];
                if(quote){if(escape)escape=false;else if(c==='\\\\')escape=true;else if(c==='"')quote=false;}
                else if(c==='"')quote=true;else if(c==='{')depth++;else if(c==='}'&&--depth===0)break;
              }
              if(end>=text.length)break;
              try{var found=selected(JSON.parse(text.slice(start,end+1)),0);if(found)return found;}catch(e){}
              start=end+1;
            }
            return null;
          }
          var chunks=window.__pace_f;
          if(!item&&Array.isArray(chunks))for(var entry of chunks.slice(0,64)){
            if(!Array.isArray(entry))continue;
            for(var value of entry){item=chunk(value);if(item)break;}
            if(item||budget<=0)break;
          }
          if(!item)return '';
          var v=item.video||{},video={};
          for(var key of ['is_drm','drm_type','isDrm','drmType','play_addr','play_addr_h264','bit_rate','playAddr','playAddrH264','bitRateList'])if(v[key]!==undefined)video[key]=v[key];
          return JSON.stringify({item_list:[{aweme_id:item.aweme_id||item.awemeId,desc:item.desc,is_private:item.is_private,is_prohibited:item.is_prohibited,isPrivate:item.isPrivate,isProhibited:item.isProhibited,video:video,images:item.images,image_post_info:item.image_post_info}]});
        })()
        """;}
}
