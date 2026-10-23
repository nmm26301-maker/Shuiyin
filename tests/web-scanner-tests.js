const fs=require('fs'),vm=require('vm'),assert=require('assert');
let checks=0;function check(value,reason){assert.ok(value,reason);checks++;}
const source=fs.readFileSync(process.argv[2],'utf8');
function media(tag,current,src,sources=[]){return {tagName:tag,currentSrc:current,src,querySelectorAll:()=>sources.map(src=>({src}))};}
const elements=[media('VIDEO','https://cdn.example.org/play?id=7&token=a%2Fb','blob:opaque',['https://cdn.example.org/master.m3u8?sig=1']),media('AUDIO','https://cdn.example.org/music','https://cdn.example.org/music'),media('VIDEO','blob:not-downloadable','blob:not-downloadable')];
const child={querySelectorAll:()=>[media('VIDEO','https://cdn.example.org/frame.mp4','')]};
const blocked={get contentDocument(){throw new Error('cross origin');}};
const document={querySelectorAll(q){return q==='video,audio'?elements:q==='iframe'?[{contentDocument:child},blocked]:[{outerHTML:'<title>Current note</title>'}];}};
const resources=[{name:'https://cdn.example.org/played.MP4?keep=a%2Fb'},{name:'https://cdn.example.org/audio.aac'},{name:'https://account.example.org/settings.json'},{name:'http://cdn.example.org/unsafe.mp4'}];
const context={document,window:{__INITIAL_STATE__:{noteData:{data:{noteData:{title:'the displayed note'}}},account:{secret:'must not be exported'}}},location:{pathname:'/explore/abc123'},performance:{getEntriesByType:()=>resources},URL};
const result=JSON.parse(vm.runInNewContext(source,context));
check(result.media.some(x=>x.url==='https://cdn.example.org/play?id=7&token=a%2Fb'&&x.kind==='VIDEO'),'dynamic opaque currentSrc retained without changing signature');
check(result.media.filter(x=>x.url==='https://cdn.example.org/music').length===1,'duplicate playing sources removed');
check(!result.media.some(x=>!x.url.startsWith('https://')),'blob and insecure sources excluded');
check(result.media.some(x=>x.url==='https://cdn.example.org/frame.mp4'),'same origin frame included despite inaccessible sibling');
check(result.media.some(x=>x.url==='https://cdn.example.org/played.MP4?keep=a%2Fb'),'performance media source and signed query retained');
check(result.media.some(x=>x.url.endsWith('audio.aac')&&x.kind==='AUDIO'),'resource audio type recognized');
check(!JSON.stringify(result).includes('must not be exported')&&!result.media.some(x=>x.url.includes('settings.json')),'account state and unrelated API responses excluded');
check(result.html.includes('<title>Current note</title>')&&JSON.parse(result.state).noteData.data.noteData.title==='the displayed note','title and displayed XHS note retained');
check(result.media.find(x=>x.url==='https://cdn.example.org/play?id=7&token=a%2Fb').active===true,'DOM playback marked as current media');
check(result.media.find(x=>x.url==='https://cdn.example.org/played.MP4?keep=a%2Fb').active===false,'resource prefetch not marked as current playback');
console.log('PASS web scanner: '+checks+' checks; executed actual WebView script with DOM fixtures');
