const fs=require('fs'),vm=require('vm'),assert=require('assert');
const template=fs.readFileSync(process.argv[2],'utf8');let checks=0;
function check(value,why){assert.ok(value,why);checks++;}
function fixture(host='tiktokio.bio',resume=false){
  const state={submitted:0,fields:[],anchors:[],body:'',response:{},store:null,queries:[],buttons:[]};
  class HTMLInputElement {set value(v){state.value=v;}dispatchEvent(e){state.fields.push(e.type);}}
  class HTMLAnchorElement {constructor(url,text='',download=''){this.href=url;this.textContent=text;this.attrs={};if(download!==null)this.attrs.download=download;}
    hasAttribute(k){return Object.hasOwn(this.attrs,k);}getAttribute(k){return this.attrs[k]||'';}click(){state.originalClick=true;}}
  class Event {constructor(type){this.type=type;}}
  const input=new HTMLInputElement(),button={textContent:'解析',click(){state.submitted++;}};
  const nuxt={__vue_app__:{config:{globalProperties:{$pinia:{_s:{get(id){assert.strictEqual(id,'video');return state.store;}}}}}}};
  const document={body:{get innerText(){return state.body;}},querySelector(q){state.queries.push(q);if(q===config.input)return input;if(config.button&&q===config.button)return button;if(q==='#__nuxt')return nuxt;return null;},querySelectorAll(q){if(q==='a[href]')return state.anchors;if(q==='button')return [button,...state.buttons];return [];}};
  const window={HTMLAnchorElement,fetch:async()=>({ok:true,headers:{get:()=> 'application/json'},clone:()=>({text:async()=>JSON.stringify(state.response)})})};
  const config={source:'https://www.tiktok.com/@test/video/7312345678901234567?sig=a%2Fb',input:'#input',button:'#button',resume};
  const context=vm.createContext({window,document,HTMLInputElement,HTMLAnchorElement,Event,location:{href:'https://'+host+'/',hostname:host},URL,Date,Set,setTimeout:f=>f(),DOMParser:class{parseFromString(){return {querySelectorAll:()=>[]};}}});
  const poll=()=>JSON.parse(vm.runInContext(template.replace('/*CONFIG*/',JSON.stringify(config)),context));
  async function response(data){state.response=data;await window.fetch('/parse');await new Promise(resolve=>setImmediate(resolve));return poll();}
  return {state,config,poll,response,context,HTMLAnchorElement,button};
}
(async()=>{
  let blocked=fixture();blocked.button.disabled=true;blocked.poll();check(blocked.state.value===blocked.config.source&&blocked.state.submitted===0,'fill URL even when submit initially disabled');
  blocked.button.disabled=false;blocked.poll();check(blocked.state.submitted===1&&blocked.state.fields.length===2,'submit once after framework enables button without repeated input events');
  let f=fixture();f.poll();check(f.state.submitted===1,'one submission on initial poll');check(f.state.value===f.config.source,'share link and signature filled once');
  check(f.state.fields.join(',')==='input,change','framework form events dispatched');f.poll();check(f.state.submitted===1,'polling never resubmits');
  let r=await f.response({success:true,links:{'Video (No Watermark)':'https://cdn.example.org/media?sig=a%2Fb','Video (Watermark)':'https://cdn.example.org/marked.mp4','MP3':'https://cdn.example.org/audio?id=1'}});
  check(r.items.length===2,'TikTokio actual links schema video and MP3');check(r.items.some(x=>x.kind==='VIDEO'&&x.url.endsWith('a%2Fb')),'opaque signed video retained');check(!r.items.some(x=>x.url.includes('marked')),'watermarked alternative excluded');
  r=await f.response({success:true,links:{'Video (No Watermark)':'https://cdn.example.org/media?sig=a%2Fb'}});check(r.items.length===2,'returned URL deduplicated');
  f=fixture('cobalt.tools');f.poll();r=await f.response({status:'tunnel',url:'https://api.example.org/tunnel?id=2&sig=a%2Fb',filename:'video.mp4'});check(r.items.length===1&&r.items[0].kind==='VIDEO','Cobalt tunnel response routed to old downloader');
  f=fixture('cobalt.tools');f.poll();r=await f.response({status:'picker',picker:[{type:'photo',url:'https://cdn.example.org/one.jpg'},{type:'video',url:'https://cdn.example.org/two.mp4'}]});check(r.items.length===2,'Cobalt picker media');
  f=fixture('cobalt.tools');f.poll();r=await f.response({status:'local-processing',tunnel:['https://api.example.org/video','https://api.example.org/audio']});check(r.items.length===0&&r.error,'separated tracks never masquerade as complete video');
  f=fixture('cobalt.tools');f.poll();r=await f.response({status:'error',error:{code:'api.auth.jwt.missing'}});check(r.items.length===0&&r.error,'authentication error surfaced without bypass');
  f=fixture('greenvideo.cc');f.poll();f.state.store={inputUrl:f.config.source,videoExtractVisible:false,videoExtractInfo:{videoItemVoList:[{qualityAlias:'视频',baseUrl:'https://cdn.example.org/movie',canDirectDownload:true}]}};
  r=f.poll();check(r.items.length===0,'GreenVideo verification must complete');f.state.store.videoExtractVisible=true;r=f.poll();check(r.items.length===1,'GreenVideo current work store yields direct media');
  f.state.store.inputUrl='https://other.example.org/post';f.state.store.videoExtractInfo.videoItemVoList.push({qualityAlias:'图片',baseUrl:'https://cdn.example.org/other.jpg',canDirectDownload:true});r=f.poll();check(r.items.length===1,'other work state excluded');
  f=fixture();f.state.anchors=[new f.HTMLAnchorElement('https://ad.example.org/video.mp4','Download MP4')];f.poll();r=f.poll();check(r.items.length===0,'preexisting page download ads excluded');
  f.state.anchors.push(new f.HTMLAnchorElement('https://cdn.example.org/fresh.mp4?sig=a%2Fb','Download MP4'));r=f.poll();check(r.items.length===1,'new download result anchors captured');
  let a=new f.HTMLAnchorElement('blob:https://tiktokio.bio/id','','');a.click();r=f.poll();check(r.error.includes('blob'),'blob download explicitly unsupported, no fake media');
  f=fixture();f.poll();r=await f.response({success:true,links:{'Video (No Watermark)':'http://cdn.example.org/a.mp4','MP3':'https://user:secret@cdn.example.org/a.mp3'}});check(r.items.length===0,'HTTP and URL credentials rejected');
  f=fixture('cobalt.tools');f.poll();r=await f.response({status:'picker',audio:'https://cdn.example.org/music?sig=a%2Fb',audioFilename:'background.mp3',picker:[{type:'photo',url:'https://cdn.example.org/photo.jpg'}]});
  check(r.items.length===2&&r.items.some(x=>x.kind==='AUDIO'&&x.url.endsWith('a%2Fb')&&x.label==='background.mp3'),'documented Cobalt album audio and filename retained');
  f=fixture('cobalt.tools');f.poll();r=await f.response({status:'picker',audio:'https://user:secret@cdn.example.org/music',picker:[{type:'photo',url:'https://cdn.example.org/photo.jpg'}]});
  check(r.items.length===1&&r.items[0].kind==='IMAGE','unsafe background audio excluded without losing photos');
  f=fixture('parse.ideaflow.top');f.config.button='';f.config.buttonText='解析';f.poll();check(f.state.submitted===1,'IdeaFlow visible parse button selected by exact label');
  r=await f.response({code:200,data:{title:'work caption',cover_url:'https://cdn.example.org/cover.jpg',video_url:'https://cdn.example.org/play?sig=a%2Fb',images:[{url:'https://cdn.example.org/image.jpg',live_photo_url:'https://cdn.example.org/live?sig=1'},'https://cdn.example.org/second.jpg']}});
  check(r.items.filter(x=>x.kind==='VIDEO').length===2&&r.items.filter(x=>x.kind==='IMAGE').length===2,'IdeaFlow response photo and live video collected, opaque URLs retained');
  check(!r.items.some(x=>x.url.includes('cover.jpg'))&&r.description==='work caption','no cover masquerading as photo; caption retained');
  f=fixture('dy.kukutool.com');f.poll();r=await f.response({code:500,msg:'需要人工验证'});check(r.items.length===0&&r.error.includes('需要人工验证'),'new provider errors surfaced without false media');
  f=fixture('dy.kukutool.com');f.poll();r=await f.response({status:0,encrypt:true,data:'opaque-site-response'});check(r.items.length===0,'encrypted response not decoded or mistaken for a URL');
  f.state.buttons.push({'__reactFiber$fixture':{memoizedProps:{type:'video',url:'https://cdn.example.org/rendered?sig=a%2Fb',account:{token:'never-export'}},return:{memoizedProps:{type:'image',files:[{url:'https://cdn.example.org/p.jpg'}]},return:null}}});
  r=f.poll();check(r.items.length===2&&r.items.some(x=>x.kind==='VIDEO'&&x.url.endsWith('a%2Fb')),'KuKu normal rendered download props captured after site processing');
  check(!JSON.stringify(r).includes('never-export')&&!JSON.stringify(r).includes('opaque-site-response'),'rendered prop collector exports only whitelisted public media');
  f=fixture('twittervideodownloader.com',true);f.state.anchors=[new f.HTMLAnchorElement('https://video.twimg.com/clip.mp4?tag=12','Download Video')];r=f.poll();
  check(r.items.length===1&&r.items[0].url.includes('tag=12'),'full-page POST navigation resumes collection on result page');
  check(f.state.submitted===0,'result page never submits another form');
  f=fixture('twittervideodownloader.com',true);f.state.anchors=[new f.HTMLAnchorElement('https://twittervideodownloader.com/en/','Twitter Video Downloader'),new f.HTMLAnchorElement('https://twittervideodownloader.com/download?tweet=123','Download Video')];r=f.poll();
  check(r.items.length===0,'regression: navigation/footer and HTML result pages are not video candidates');
  f.state.anchors.push(new f.HTMLAnchorElement('https://video.twimg.com/valid.mp4?sig=a%2Fb','Download Video'));r=f.poll();check(r.items.length===1&&r.items[0].url.includes('video.twimg.com'),'real CDN video survives navigation filtering');
  f=fixture('twittervideodownloader.com',true);let pageLink=new f.HTMLAnchorElement('https://twittervideodownloader.com/download?tweet=123','Download Video');pageLink.click();r=f.poll();
  check(r.items.length===0&&f.state.originalClick,'HTML intermediary click navigates normally instead of becoming fake media');
  if(process.argv[3])for(const site of JSON.parse(fs.readFileSync(process.argv[3],'utf8'))){
    f=fixture(new URL(site.url).hostname);Object.assign(f.config,site);f.poll();check(f.state.submitted===1,'production form config submits '+site.url);
  }
  console.log('PASS parallel website scripts: '+checks+' checks');
})().catch(e=>{console.error(e);process.exit(1);});
