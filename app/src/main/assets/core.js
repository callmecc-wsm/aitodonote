(function(root) {
  'use strict';
  const escape = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const classify = (text, kind='auto') => {
    if (['think','action','note'].includes(kind)) return kind;
    if (/为什么|怎么|如何|研究|分析|思考|纠结|情绪|不理解|搞懂|不知道|调研/.test(text)) return 'think';
    if (/提醒|预约|办理|办卡|银行卡|买|缴费|取快递|寄快递|去医院|打电话|提交|报销|开会|还书/.test(text)) return 'action';
    return 'note';
  };
  const status = (t,activeId='',now=Date.now()) => t.done ? '已完成' : t.snooze>now ? '明天再看' : activeId===t.id ? '正在思考' : t.error ? '需要重试' : t.kind==='think' ? (t.lastReview ? '有了新进展' : '待 AI 推进') : t.kind==='action' ? (t.due<=now ? '到时间了' : '等你行动') : '安静保存';
  const latest = t => [...(t.events||[])].reverse().find(e=>e.role==='assistant');
  const api={escape,classify,status,latest};
  if(typeof module==='object') module.exports=api;
  root.NoteCore=api;
})(typeof window!=='undefined'?window:globalThis);
