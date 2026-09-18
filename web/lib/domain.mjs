export const DAY=86400000;
export const defaults=()=>({tasks:[],config:{interval:6,quietFrom:22,quietTo:8,enabled:false,search:false,baseUrl:'',model:'',lastRun:0,blocked:'',history:[],revision:0}});
export const classify=(t,k)=>['think','action','note'].includes(k)?k:/为什么|怎么|如何|研究|分析|思考|纠结|情绪|不理解|搞懂|不知道|调研/.test(t)?'think':/提醒|预约|办理|办卡|银行卡|买|缴费|取快递|寄快递|去医院|打电话|提交|报销|开会|还书/.test(t)?'action':'note';
export function text(v,max=10000){if(typeof v!=='string'||!v.trim()||v.length>max)throw Error('文字不能为空，且不能超过 '+max+' 字');return v.trim()}
export function safeUrl(v){try{const u=new URL(v);return u.protocol==='https:'&&!u.username&&!u.password&&!u.hash&&!/^(localhost|.*\.localhost|.*\.local|.*\.internal|.*\.test)$/.test(u.hostname)&&u.hostname.includes('.')&&!/^[\d.]+$/.test(u.hostname)&&!u.hostname.includes(':')}catch{return false}}
export function endpoint(v){if(!safeUrl(v))throw Error('请填写公开服务的 HTTPS 地址');const u=new URL(v);if(u.search)throw Error('服务地址不能包含查询参数');return v.replace(/\/+$/,'').replace(/\/chat\/completions$/,'')+'/chat/completions'}
export function quiet(c,h){return c.quietFrom===c.quietTo?false:c.quietFrom<c.quietTo?h>=c.quietFrom&&h<c.quietTo:h>=c.quietFrom||h<c.quietTo}
export function eligible(t,now){return t.kind==='think'&&!t.done&&!(t.snooze>now)&&!t.error&&!t.needsUser&&(t.rounds||0)<3&&(!t.lastReview||t.nextReview>0&&t.nextReview<=now)}
export function apply(data,method,p,now=Date.now()){
 const d=structuredClone(data),t=d.tasks.find(t=>t.id===p.id);let result={};
 if(method==='save'){
 const value=text(p.text);if(p.id&&!t)throw Error('记录已被删除，请把草稿保存为新记录');
 if(t&&p.revision!==t.revision)throw Error('这条记录已在其他页面更新，请刷新后再保存；草稿已保留');
 if(p.due!=null&&(!Number.isFinite(p.due)||p.due<0))throw Error('提醒时间无效');
 const item=t||{id:crypto.randomUUID(),created:now,events:[],done:false,unread:false,lastReminder:0};
 Object.assign(item,{text:value,kind:classify(value,p.kind),due:p.due||0,searchAllowed:p.searchAllowed!==false,updated:now,revision:(item.revision||0)+1,lastReview:0,error:'',nextReview:0,nextQuery:'',needsUser:false,rounds:0,snooze:0});
 if(item.kind==='action'&&!item.due)item.due=now+DAY;
 if(!t)d.tasks.unshift(item);result={task:item};
 }else if(method==='change'){
 if(!t)throw Error('记录不存在');if(p.revision!==t.revision)throw Error('记录已更新，请刷新后重试');
 if(p.action==='done'){t.done=!t.done;if(t.done){t.unread=false;t.pendingNotification=''}}
 else if(p.action==='snooze')t.snooze=now+DAY;
 else if(p.action==='resume')t.snooze=0;
 else if(p.action==='reply'){if(t.kind!=='think'||t.done)throw Error('此记录不能继续补充');t.events.push({id:crypto.randomUUID(),role:'user',detail:text(p.content),time:now});Object.assign(t,{lastReview:0,nextReview:0,nextQuery:'',needsUser:false,rounds:0,error:''})}
 else if(p.action==='retry')Object.assign(t,{lastReview:0,nextReview:0,needsUser:false,rounds:0,error:'',snooze:0});
 else throw Error('无效操作');t.revision++;t.updated=now;
 }else if(method==='delete'){if(t&&p.revision!==t.revision)throw Error('记录已更新，请刷新后删除');d.tasks=d.tasks.filter(t=>t.id!==p.id)}
 else if(method==='read'){if(t){const last=t.events.filter(e=>e.role==='assistant').at(-1);if(last?.id===p.eventId){t.unread=false;t.pendingNotification=''}}}
 else if(method==='reminded'){if(t&&t.revision===p.revision&&!t.done&&t.due<=now&&t.snooze<=now)t.lastReminder=now}
 else if(method==='import'){
 const tasks=validateBackup(p.backup);const ids=new Set(d.tasks.map(t=>t.id));let n=0;
 for(const item of tasks)if(!ids.has(item.id)){d.tasks.push(item);ids.add(item.id);n++}
 d.tasks.sort((a,b)=>b.created-a.created);result={count:n};
 }else throw Error('无效操作');
 if(d.tasks.length>5000||JSON.stringify(d).length>8000000)throw Error('记录过多，请先备份并整理');return {data:d,result};
}
export function validateBackup(b){
 if(b?.format!=='notenote-backup'||b.version!==1||!Array.isArray(b.tasks)||b.tasks.length>5000)throw Error('不是支持的 Note Note 备份');
 const seen=new Set();return b.tasks.map(t=>{
 if(!t||typeof t.id!=='string'||!t.id||t.id.length>128||seen.has(t.id)||!['think','action','note'].includes(t.kind)||!Array.isArray(t.events)||t.events.length>1000)throw Error('备份包含无效或重复记录');seen.add(t.id);
 const n={id:t.id,text:text(t.text),kind:t.kind,done:t.done===true,searchAllowed:t.searchAllowed!==false,events:[],revision:1,error:'',snooze:0,due:0,created:0,updated:0,lastReview:0,nextReview:0,lastReminder:0,rounds:0,needsUser:t.needsUser===true,unread:t.unread===true,nextQuery:typeof t.nextQuery==='string'?t.nextQuery.slice(0,400):''};
 for(const k of ['snooze','due','created','updated','lastReview','nextReview','lastReminder','rounds']){const v=t[k]??0;if(!Number.isSafeInteger(v)||v<0)throw Error('备份时间或计数无效');n[k]=v}
 n.events=t.events.map(e=>{if(!e||!['user','assistant'].includes(e.role)||typeof e.id!=='string'||e.id.length>128||!Number.isSafeInteger(e.time)||e.time<0)throw Error('备份进展无效');return {id:e.id,role:e.role,time:e.time,detail:text(e.detail,12000),summary:typeof e.summary==='string'?e.summary.slice(0,180):'',question:typeof e.question==='string'?e.question.slice(0,2000):'',research:typeof e.research==='string'?e.research.slice(0,200):'',sources:(Array.isArray(e.sources)?e.sources:[]).slice(0,5).filter(s=>s&&safeUrl(s.url)).map((s,i)=>({id:i+1,url:s.url.slice(0,4000),title:String(s.title||'来源').slice(0,1000)}))}});return n;
 });
}
export function commitResult(d,id,revision,event,now){const t=d.tasks.find(t=>t.id===id);if(!t||t.done||t.kind!=='think'||t.revision!==revision)return false;t.events.push(event);t.lastReview=now;t.rounds=(t.rounds||0)+1;t.needsUser=event.nextAction==='ask_user';t.nextReview=event.nextAction==='research'&&t.rounds<3?now+DAY:0;t.nextQuery=t.nextReview?event.nextQuery:'';t.error='';t.unread=true;t.pendingNotification=event.id;return true}
