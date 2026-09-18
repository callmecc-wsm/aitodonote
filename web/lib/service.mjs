import {apply,endpoint,quiet,eligible,commitResult} from './domain.mjs';
import {review,post} from './model.mjs';
const encode=b=>btoa(String.fromCharCode(...new Uint8Array(b)));
const decode=s=>Uint8Array.from(atob(s),x=>x.charCodeAt(0));
async function cryptKey(secret){if(!secret)throw Error('密钥存储尚未配置');return crypto.subtle.importKey('raw',decode(secret),'AES-GCM',false,['encrypt','decrypt'])}
export async function seal(value,secret,owner){if(!value)return '';const iv=crypto.getRandomValues(new Uint8Array(12));const a=new TextEncoder().encode(owner);return encode(iv)+'.'+encode(await crypto.subtle.encrypt({name:'AES-GCM',iv,additionalData:a},await cryptKey(secret),new TextEncoder().encode(value)))}
export async function unseal(value,secret,owner){if(!value)return '';const [iv,v]=value.split('.');return new TextDecoder().decode(await crypto.subtle.decrypt({name:'AES-GCM',iv:decode(iv),additionalData:new TextEncoder().encode(owner)},await cryptKey(secret),decode(v)))}
export function publicData(d,busy=false){const c={...d.config};delete c.apiKey;delete c.searchKey;c.hasKey=!!d.config.apiKey;c.hasSearchKey=!!d.config.searchKey;c.ready=!!(c.hasKey&&c.baseUrl&&c.model);return {tasks:d.tasks,config:c,busy,native:true,activeId:''}}
export async function execute(store,method,p={},secret,fetcher=fetch){
 await store.row();let result={};
 if(method==='snapshot'){const r=await store.read();return {snapshot:publicData(r.data,r.busy)}}
 if(method==='settings'){
 const base=String(p.baseUrl||'').trim(),model=String(p.model||'').trim();if(base)endpoint(base);if(model.length>200)throw Error('模型名称过长');
 if(![1,3,6,12,24].includes(p.interval)||![p.quietFrom,p.quietTo].every(n=>Number.isInteger(n)&&n>=0&&n<=23))throw Error('时间设置无效');
 const keys={};for(const k of ['apiKey','searchKey'])if(p[k]){if(typeof p[k]!=='string'||p[k].length>4096||/[\r\n]/.test(p[k]))throw Error('密钥格式无效');keys[k]=await seal(p[k].trim(),secret,store.owner)}
 await store.mutate(d=>{const c=d.config;if(c.baseUrl!==base&&c.apiKey&&!keys.apiKey&&!p.clearKey)throw Error('更换服务地址时请重新填写 API Key');const changed=c.baseUrl!==base||c.model!==model||keys.apiKey||p.clearKey||keys.searchKey||p.clearSearchKey||c.search!==!!p.search;
 Object.assign(c,{baseUrl:base,model,enabled:!!p.enabled,search:!!p.search,interval:p.interval,quietFrom:p.quietFrom,quietTo:p.quietTo},keys);if(p.clearKey)c.apiKey='';if(p.clearSearchKey)c.searchKey='';if(c.search&&!c.searchKey)throw Error('请填写搜索密钥或关闭联网搜索');if(c.enabled&&!(c.apiKey&&base&&model))throw Error('开启回顾前请配置模型');c.revision=(c.revision||0)+1;if(changed){c.blocked='';for(const t of d.tasks)t.error=''}});
 }else if(method==='review'||method==='test'){
 const lock=await store.lock();if(!lock)throw Error('已有一次回顾或连接测试正在运行，请稍候');
 try{
 let {data:d}=await store.read();const c=d.config,configRevision=c.revision;
 if(!(c.apiKey&&c.baseUrl&&c.model))throw Error('先在设置中连接模型');
 if(p.automatic){const h=Number(p.hour);if(!Number.isInteger(h)||h<0||h>23)throw Error('本地时间无效');if(!c.enabled||c.blocked||quiet(c,h)||Date.now()-(c.lastRun||0)<c.interval*3600000)return {snapshot:publicData(d)}}
 const config={...c,apiKey:await unseal(c.apiKey,secret,store.owner),searchKey:await unseal(c.searchKey,secret,store.owner)};
 if(method==='test'){
 const r=await post(endpoint(c.baseUrl),config.apiKey,{model:c.model,messages:[{role:'user',content:'Reply OK.'}],max_tokens:64,stream:false},fetcher);if(!r.choices?.[0]?.message?.content?.trim())throw Error('模型没有返回文本');
 }else{
 if(p.id){await store.mutate(d=>{const t=d.tasks.find(t=>t.id===p.id);if(!t||t.done||t.kind!=='think')throw Error('该记录无法推进');if(p.revision!==t.revision)throw Error('记录已更新，请刷新后重试');Object.assign(t,{error:'',snooze:0,lastReview:0,nextReview:0,needsUser:false,rounds:0});t.revision++;d.config.blocked=''});d=(await store.read()).data}
 const report={time:Date.now(),attempted:0,completed:0,failed:0,discarded:0,status:'本轮没有需要推进的问题'};
 for(const original of [...d.tasks].reverse()){
 if(report.attempted>=3)break;if(p.id&&original.id!==p.id)continue;
 const current=(await store.read()).data;if(current.config.revision!==configRevision)break;
 const t=current.tasks.find(x=>x.id===original.id);if(!t||!eligible(t,Date.now()))continue;report.attempted++;
 try{const event=await review(t,config,fetcher);const ok=await store.mutate(d=>d.config.revision===configRevision&&commitResult(d,t.id,t.revision,event,Date.now()));if(ok)report.completed++;else report.discarded++}
 catch(e){report.failed++;report.status=e.message;await store.mutate(d=>{if(d.config.revision!==configRevision)return;d.config.blocked=e.message;const live=d.tasks.find(x=>x.id===t.id);if(live?.revision===t.revision)live.error=e.message});break}
 }
 if(!report.failed&&report.attempted)report.status=`完成 ${report.completed} 条推进`;
 await store.mutate(d=>{d.config.lastRun=Date.now();d.config.history=[report,...(d.config.history||[])].slice(0,20)});
 }
 }finally{await store.unlock(lock)}
 }else result=await store.mutate(d=>{const r=apply(d,method,p);Object.assign(d,r.data);return r.result});
 const r=await store.read();return {...result,snapshot:publicData(r.data,r.busy)};
}
