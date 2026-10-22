const fs=require('fs'),vm=require('vm'),assert=require('assert');
const script=fs.readFileSync(process.argv[2],'utf8');
let checks=0;const expected="https://x.com/a/status/1?text='\\\"\n中文";
class HTMLInputElement {
  constructor(visible=true){this.visible=visible;this.events=[];this.disabled=false;this.readOnly=false;}
  set value(value){this.current=value;}
  getClientRects(){return this.visible?[{}]:[];}
  dispatchEvent(event){this.events.push(event.type);}
}
class Event {constructor(type){this.type=type;}}
const hidden=new HTMLInputElement(false),disabled=new HTMLInputElement(),input=new HTMLInputElement();disabled.disabled=true;
function run(fields){return vm.runInNewContext(script,{HTMLInputElement,Event,document:{querySelectorAll:()=>fields}});}
assert.strictEqual(run([hidden,disabled,input]),true);checks++;
assert.strictEqual(input.current,expected);checks++;
assert.deepStrictEqual(input.events,['input','change']);checks++;
assert.strictEqual(hidden.current,undefined);checks++;
assert.strictEqual(disabled.current,undefined);checks++;
assert.strictEqual(run([]),false);checks++;
assert.ok(!script.includes('.submit(')&&!script.includes('.click('));checks++;
console.log('PASS website form script: '+checks+' checks; exact URL, visible field, input/change events, no automatic submission');
