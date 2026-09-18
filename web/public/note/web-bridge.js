'use strict';
window.WebBridge=(()=>{
 let cache={tasks:[],config:{}},busy=false,testing=false,loaded=false,revision=-1;
 const draftKey='notenote-h5-drafts-v1';let local={draft:{text:'',kind:'auto',due:'',searchAllowed:true},workspace:{entries:{},resume:{},shares:[]}};
 try{const saved=JSON.parse(localStorage.getItem(draftKey)||'null');if(saved?.workspace&&saved?.draft)local=saved}catch{}
 const persist=()=>{try{localStorage.setItem(draftKey,JSON.stringify(local))}catch{throw Error('本机草稿存储失败，请勿关闭页面，先复制文字')}};
 let focus=new URLSearchParams(location.hash.slice(1));
 const api={onChange:null,call,init};
 window.addEventListener('hashchange',()=>{focus=new URLSearchParams(location.hash.slice(1));changed()});
 const changed=()=>api.onChange?.();
 async function request(method,params={}){
 let response;try{response=await fetch('/api/note',{method:'POST',headers:{'Content-Type':'application/json','X-NoteNote':'1'},body:JSON.stringify({method,params}),cache:'no-store'})}catch{throw Error('网络连接中断，草稿已保留，请联网后重试')}
 if(response.status===401){throw Error('登录已失效，请通过站点入口重新登录')}
 let r;try{r=await response.json()}catch{throw Error('暂时无法连接记录服务，请稍后重试')}
 if(!response.ok)throw Error(r.error||'操作未完成');if(r.snapshot){cache=r.snapshot;loaded=true;changed()}return r;
 }
 function call(method,p={}){
 if(method==='snapshot'){const focusId=focus.get('note'),focusReply=focus.get('reply')==='1';focus=new URLSearchParams();return {...cache,focusId,focusReply,native:true,busy:busy||cache.busy,testing,notifications:'Notification' in window&&Notification.permission==='granted',draft:local.draft,workspace:local.workspace};}
 if(method==='draft'){local.draft={...p};persist();return {}}
 if(method==='taskDraft'){local.workspace.entries[p.scope+':'+p.id]={...p};local.workspace.resume={scope:p.scope,id:p.id};persist();return {}}
 if(method==='discardDraft'){delete local.workspace.entries[p.scope+':'+p.id];local.workspace.resume={};persist();return {}}
 if(method==='leaveDraft'){local.workspace.resume={};persist();return {}}
 if(method==='takeShare'){if(local.draft.text.trim())throw Error('先保存当前草稿，再接收分享');const item=local.workspace.shares.find(x=>x.id===p.id);if(!item)throw Error('分享已接收');local.draft={text:item.text,kind:'auto',due:'',searchAllowed:true};local.workspace.shares=local.workspace.shares.filter(x=>x.id!==p.id);persist();return {draft:local.draft}}
 if(method==='open'){const u=new URL(p.url);if(u.protocol!=='https:')throw Error('链接不可打开');window.open(u.href,'_blank','noopener,noreferrer');return {}}
 if(method==='export')return exportBackup();
 if(method==='import')return importBackup();
 if(method==='notifications')return notifications();
 return mutate(method,p);
 }
 async function mutate(method,p){
 if(['save','change','delete','review','reminded'].includes(method)&&p.id){const t=cache.tasks.find(x=>x.id===p.id);p={...p,revision:t?.revision}}
 if(method==='settings'&&p.baseUrl!==cache.config.baseUrl&&cache.config.hasKey&&!p.apiKey&&!p.clearKey)throw Error('更换服务地址时请重新填写 API Key');
 if(method==='review'||method==='test'){if(busy||testing)throw Error('正在处理中，请稍候');busy=method==='review';testing=method==='test';changed()}
 try{const r=await request(method,p);
 if(method==='save'){if(p.id)delete local.workspace.entries['edit:'+p.id];else local.draft={text:'',kind:'auto',due:'',searchAllowed:true};persist()}
 if(method==='change'&&p.action==='reply'){delete local.workspace.entries['reply:'+p.id];persist()}
 if(method==='delete'){delete local.workspace.entries['reply:'+p.id];delete local.workspace.entries['edit:'+p.id];persist()}
 if(method==='review'){const h=cache.config.history?.[0];if(h?.failed)throw Error(h.status);await deliverNotifications()}
 if(method==='test')setTimeout(()=>window.toast?.('模型连接成功'),0);
 return r;
 }finally{if(method==='review'||method==='test'){busy=false;testing=false;changed()}}
 }
 async function exportBackup(){await request('snapshot');const blob=new Blob([JSON.stringify({format:'notenote-backup',version:1,tasks:cache.tasks},null,2)],{type:'application/json'}),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download='NoteNote-'+new Date().toISOString().slice(0,10)+'.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),30000);return {}}
 function importBackup(){const file=document.createElement('input');file.type='file';file.accept='.json,application/json';file.onchange=async()=>{const f=file.files?.[0];if(!f)return;try{if(f.size>9000000)throw Error('备份文件超过 9 MB');const backup=JSON.parse(await f.text());const r=await request('import',{backup});window.refresh?.(true);window.toast?.('已导入 '+r.count+' 条记录，已有记录保持不变')}catch(e){window.toast?.(e instanceof SyntaxError?'备份不是有效的 JSON 文件':e.message)}};file.click();return {}}
 async function notifications(){if(!('Notification'in window))throw Error('此浏览器不支持通知；iPhone 可尝试先添加到主屏幕，页面内仍会显示提醒');const p=await Notification.requestPermission();changed();if(p!=='granted')throw Error('通知未获允许，请在浏览器或系统设置中开启');window.toast?.('通知已开启；需保持页面运行');return {}}
 const sent=new Set();
 async function notify(id,title,body,noteId){if(sent.has(id))return;sent.add(id);window.toast?.(body);if('Notification'in window&&Notification.permission==='granted'){try{const reg=await navigator.serviceWorker?.getRegistration();if(reg)await reg.showNotification(title,{body,tag:id,icon:'/favicon.svg',data:{url:'/note/index.html#note='+encodeURIComponent(noteId)},actions:title.includes('进展')?[{action:'reply',title:'接着聊'}]:[]});else{const n=new Notification(title,{body,tag:id});n.onclick=()=>{location.hash='note='+encodeURIComponent(noteId);window.focus()}}}catch{}}}
 function quietNow(){const c=cache.config,h=new Date().getHours();return c.quietFrom===c.quietTo?false:c.quietFrom<c.quietTo?h>=c.quietFrom&&h<c.quietTo:h>=c.quietFrom||h<c.quietTo}
 async function deliverNotifications(){if(quietNow())return;for(const t of cache.tasks){if(t.done||t.snooze>Date.now())continue;if(t.kind==='action'&&t.due<=Date.now()&&Date.now()-(t.lastReminder||0)>86400000){await notify(t.id+'-'+new Date().toDateString(),'Note Note · 到时间了',t.text,t.id);await mutate('reminded',{id:t.id})}if(t.unread&&t.pendingNotification)await notify(t.pendingNotification,'Note Note · 有新进展',t.events.filter(e=>e.role==='assistant').at(-1)?.summary||'打开记录查看',t.id)}}
 let ticking=false;
 async function tick(){if(ticking||document.hidden||!navigator.onLine||busy||testing)return;ticking=true;try{await request('snapshot');await deliverNotifications();const c=cache.config;if(c.enabled&&c.ready&&!c.blocked&&!cache.busy&&!quietNow()&&Date.now()-(c.lastRun||0)>=c.interval*3600000)await mutate('review',{automatic:true,hour:new Date().getHours()})}catch(e){if(loaded)window.toast?.(e.message)}finally{ticking=false}}
 async function init(){
 await request('snapshot');
 const u=new URL(location.href);const shared=[u.searchParams.get('title'),u.searchParams.get('text'),u.searchParams.get('url')].filter(Boolean).join('\n').slice(0,10000);
 if(shared){if(local.workspace.shares.length<50)local.workspace.shares.push({id:crypto.randomUUID(),text:shared});persist();history.replaceState(null,'',location.pathname)}
 if('serviceWorker'in navigator)navigator.serviceWorker.register('/sw.js').catch(()=>{});
 setInterval(tick,30000);window.addEventListener('online',tick);document.addEventListener('visibilitychange',()=>{if(!document.hidden)tick()});setTimeout(tick,1000);
 return cache;
 }
 return api;
})();
if(window.visualViewport){const fit=()=>{const delta=Math.max(0,innerHeight-visualViewport.height-visualViewport.offsetTop);document.documentElement.style.setProperty('--keyboard',delta>100?delta+'px':'0px');document.body.classList.toggle('keyboard-open',delta>100)};visualViewport.addEventListener('resize',fit);visualViewport.addEventListener('scroll',fit)}
if(document.modelContext?.registerTool){
 const life=new AbortController();window.addEventListener('pagehide',()=>life.abort(),{once:true});
 Promise.resolve(document.modelContext.registerTool({name:'list_notes',description:'读取当前账号已加载的记录，不修改内容',inputSchema:{type:'object',properties:{},additionalProperties:false},annotations:{readOnlyHint:true,untrustedContentHint:true},execute(){return WebBridge.call('snapshot').tasks.map(t=>({id:t.id,text:t.text,kind:t.kind,done:t.done}))}},{signal:life.signal})).catch(()=>{});
 Promise.resolve(document.modelContext.registerTool({name:'create_note',description:'保存一条新记录到当前账号',inputSchema:{type:'object',properties:{text:{type:'string'},kind:{type:'string',enum:['think','action','note']}},required:['text','kind'],additionalProperties:false},annotations:{readOnlyHint:false,untrustedContentHint:true},async execute(p){if(typeof p.text!=='string'||!p.text.trim()||p.text.length>10000||!['think','action','note'].includes(p.kind))throw Error('记录内容或类型无效');const r=await WebBridge.call('save',{text:p.text,kind:p.kind,due:0,searchAllowed:true});window.refresh?.(true);return {id:r.task.id,text:r.task.text,kind:r.task.kind}}},{signal:life.signal})).catch(()=>{});
}
