(function(config){
  if(window.__wmSearchPoll)return window.__wmSearchPoll();
  var media=[],submitted=!!config.resume,filledInput=null,error='',description='',started=Date.now(),baseline=new Set();
  function kind(url,label){
    var s=String(label||'').toLowerCase();try{var p=new URL(url,location.href).pathname.toLowerCase();
      if(/\.(mp3|m4a|aac|ogg|opus|wav|flac)$/.test(p)||/mp3|audio|音频/.test(s))return 'AUDIO';
      if(/\.(jpe?g|png|webp|gif|avif)$/.test(p)||/image|photo|图片|原图/.test(s))return 'IMAGE';
      if(/\.(mp4|m4v|mov|webm|m3u8|mpd)$/.test(p)||/video|mp4|视频/.test(s))return 'VIDEO';
    }catch(e){}return '';
  }
  function add(url,type,label){
    if(!submitted||typeof url!=='string'||media.length>=24)return;
    try{var u=new URL(url,location.href);if(u.protocol!=='https:'||u.username||u.password||u.href===config.source||baseline.has(u.href))return;
      type=type||kind(u.href,label);if(!/^(VIDEO|AUDIO|IMAGE)$/.test(type))return;
      if(!media.some(x=>x.kind===type&&x.url===u.href))media.push({kind:type,url:u.href,label:String(label||'网站素材').slice(0,160)});
    }catch(e){}
  }
  function links(doc){doc.querySelectorAll('a[href]').forEach(a=>{
    var label=(a.textContent||'')+' '+(a.getAttribute('type')||''),download=a.hasAttribute('download');
    if(!download&&!/download|下载|无水印|MP4|MP3/i.test(label))return;
    var type=kind(a.href,label);if(!type&&download)type=kind(a.href,a.getAttribute('download'));
    add(a.href,type,label);
  });}
  function object(value,depth,context){
    if(!value||typeof value!=='object'||depth>6)return;
    if(Array.isArray(value)){value.slice(0,30).forEach(v=>{if(typeof v==='string'&&/^(images|photos|pics)$/.test(context))add(v,'IMAGE','图片');else object(v,depth+1,context);});return;}
    // Only complete Cobalt outputs: separated tracks are not complete downloadable videos.
    if(value.status==='local-processing'){error='该站返回分离音视频，请用原解析引擎合并';return;}
    if(typeof value.title==='string'&&/^(data|result)$/.test(context))description=value.title.slice(0,6000);
    if(value.code!==undefined&&Number(value.code)!==200&&Number(value.code)!==0&&value.msg)error='网站返回：'+String(value.msg).slice(0,180);
    if(value.status==='error'){error='网站解析失败或要求验证';return;}
    if(value.status==='redirect'||value.status==='tunnel')add(value.url,kind(value.url,value.filename)||'VIDEO',value.filename);
    if(value.success&&value.links&&typeof value.links==='object')Object.keys(value.links).slice(0,24).forEach(label=>{
      if(/watermark/i.test(label)&&!/no watermark|without watermark/i.test(label))return;
      add(value.links[label],kind(value.links[label],label),label);
    });
    var label=String(value.type||value.media_type||value.mimeType||value.mime||value.format||context||'');
    ['download_url','downloadUrl','video_url','videoUrl','play_url','playUrl','resource_url','resourceUrl','live_photo_url'].forEach(k=>{if(typeof value[k]==='string')add(value[k],(/video|play|live_photo/i.test(k)?'VIDEO':kind(value[k],label)),label);});
    if(typeof value.url==='string'&&/video|audio|image|photo|mp4|mp3/i.test(label))add(value.url,kind(value.url,label),label);
    if(value.status==='picker'){
      add(value.audio,'AUDIO',value.audioFilename||'图集背景音频');
      if(Array.isArray(value.picker))value.picker.forEach(v=>add(v.url,kind(v.url,v.type),v.type));
    }
    Object.keys(value).slice(0,80).forEach(k=>{if(/token|cookie|authorization|account|session/i.test(k))return;var v=value[k];
      if(typeof v==='string'&&/^(html|data|result)$/.test(k)&&/<a\b/i.test(v))links(new DOMParser().parseFromString(v,'text/html'));
      else if(typeof v==='object')object(v,depth+1,k);
    });
  }
  function response(text,type){if(!submitted||typeof text!=='string'||text.length>1500000)return;
    try{if(type.indexOf('json')>=0||/^\s*[\[{]/.test(text))object(JSON.parse(text),0,'');else if(type.indexOf('html')>=0)links(new DOMParser().parseFromString(text,'text/html'));}catch(e){}
  }
  if(window.fetch){var originalFetch=window.fetch;window.fetch=function(){return originalFetch.apply(this,arguments).then(r=>{
    if(submitted&&r.ok){var ct=r.headers.get('content-type')||'';if(/json|html/.test(ct))r.clone().text().then(t=>response(t,ct)).catch(()=>{});}return r;
  });};}
  if(window.XMLHttpRequest){var send=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.send=function(){this.addEventListener('load',()=>{
    try{if(this.status>=200&&this.status<300){if(this.responseType==='json')object(this.response,0,'');else if(!this.responseType||this.responseType==='text')response(this.responseText,this.getResponseHeader('content-type')||'');}}catch(e){}
  });return send.apply(this,arguments);};}
  if(window.HTMLAnchorElement){var click=HTMLAnchorElement.prototype.click;HTMLAnchorElement.prototype.click=function(){
    if(submitted&&(this.hasAttribute('download')||/download|下载|mp4|mp3/i.test(this.textContent||''))){
      if(String(this.href).startsWith('blob:')){error='该网站使用 blob 下载，未返回可复用的媒体直链';return;}
      add(this.href,kind(this.href,this.getAttribute('download')||this.textContent),'网站下载');return;
    }return click.apply(this,arguments);
  };}
  var generated=new Set();
  function greenResult(){
    // Read only the selected work's dedicated video store after the site's own verification.
    if(location.hostname!=='greenvideo.cc')return;
    try{var app=document.querySelector('#__nuxt'),pinia=app&&app.__vue_app__&&app.__vue_app__.config.globalProperties.$pinia;
      var store=pinia&&pinia._s&&pinia._s.get('video');
      if(!store||!store.videoExtractVisible||store.inputUrl!==config.source)return;
      var info=store.videoExtractInfo||{},rows=(info.videoItemVoList||[]).slice();(info.videoInfoVoList||[]).slice(0,24).forEach(group=>{if(group.videoItemVoList&&group.videoItemVoList[0])rows.push(group.videoItemVoList[0]);});
      rows.slice(0,24).forEach(row=>{var label=row.qualityAlias||'';if(/文本|text/i.test(label))return;var type=kind(row.baseUrl||'',label);
        if(row.downloadUrl)add(row.downloadUrl,type||'VIDEO',label);
        else if(row.canDirectDownload&&row.baseUrl)add(row.baseUrl,type||'VIDEO',label);
        else if(label&&!generated.has(label)){
          var button=Array.from(document.querySelectorAll('button')).find(b=>(b.textContent||'').trim()===label&&!b.disabled);
          if(button&&generated.size<2){generated.add(label);button.click();}
        }
      });
    }catch(e){}
  }
  function kukuResult(){
    // Read the site's rendered download component props after its normal parsing/verification.
    // Do not reproduce signing/decryption or traverse account/session state.
    if(location.hostname!=='dy.kukutool.com')return;
    document.querySelectorAll('button').forEach(button=>{
      try{var key=Object.keys(button).find(k=>k.indexOf('__reactFiber$')===0),fiber=key&&button[key];
        for(var depth=0;fiber&&depth<6;depth++,fiber=fiber.return){
          var props=fiber.memoizedProps||{},type=String(props.type||'').toUpperCase();
          if(/^(VIDEO|AUDIO|IMAGE)$/.test(type)){
            add(props.url,type,type==='IMAGE'?'网站图片':type==='AUDIO'?'网站音频':'网站视频');
            if(Array.isArray(props.files))props.files.slice(0,24).forEach(file=>{if(file&&typeof file==='object')add(file.url,type,'网站素材');});
          }
        }
      }catch(e){}
    });
  }
  window.__wmSearchPoll=function(){
    if(!submitted){
      var input=document.querySelector(config.input),button=config.button?document.querySelector(config.button):null;
      if(!button&&config.buttonText)button=Array.from(document.querySelectorAll('button')).find(b=>(b.textContent||'').trim()===config.buttonText);
      if(input&&button&&!input.disabled){
        if(filledInput!==input){
          var setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;setter.call(input,config.source);
          input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));filledInput=input;
        }
        if(!button.disabled){
          document.querySelectorAll('a[href]').forEach(a=>baseline.add(a.href));document.querySelectorAll('video,audio').forEach(v=>{if(v.src)baseline.add(v.src);if(v.currentSrc)baseline.add(v.currentSrc);});
          submitted=true;setTimeout(()=>{if(!button.disabled)button.click();else submitted=false;},100);
        }
      }
    }else{
      links(document);greenResult();kukuResult();document.querySelectorAll('#target video,#target audio,.result video,.result audio').forEach(v=>{var type=v.tagName==='AUDIO'?'AUDIO':'VIDEO';add(v.currentSrc,type,'网站播放素材');add(v.src,type,'网站播放素材');});
    }
    var text=(document.body&&document.body.innerText||'').slice(0,12000);
    if(submitted){var failure=document.querySelector('#errorContainer,.n-message--error .n-message__content,.alert-danger');
      if(failure&&failure.textContent&&failure.textContent.trim())error='网站返回：'+failure.textContent.trim().slice(0,180);
    }
    if(Date.now()-started>8000&&/verify you are human|just a moment|请完成验证|请输入验证码/i.test(text))error='网站要求人工验证';
    return JSON.stringify({submitted:submitted,items:media,error:error,description:description});
  };
  return window.__wmSearchPoll();
})(/*CONFIG*/)
