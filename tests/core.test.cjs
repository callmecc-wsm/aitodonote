const {test}=require('node:test');const assert=require('node:assert/strict');const c=require('../app/src/main/assets/core.js');
test('untrusted notes are rendered as text',()=>assert.equal(c.escape('<img src=x onerror="alert(1)">'), '&lt;img src=x onerror=&quot;alert(1)&quot;&gt;'));
test('explicit record-only setting wins',()=>assert.equal(c.classify('为什么开会让我情绪低落','note'),'note'));
test('questions and physical tasks are distinct',()=>{assert.equal(c.classify('为什么开会让我情绪低落'),'think');assert.equal(c.classify('去办一张银行卡'),'action');assert.equal(c.classify('今天的晚霞很好看'),'note');});
test('AI result never implies completion',()=>{const t={id:'1',kind:'think',done:false,lastReview:123,events:[{role:'assistant',summary:'new'}]};assert.equal(c.status(t),'有了新进展');assert.equal(c.latest(t).summary,'new');});
test('completion and snooze take priority over running state',()=>{assert.equal(c.status({id:'1',done:true},'1'),'已完成');assert.equal(c.status({id:'1',snooze:200},'1',100),'明天再看');});

test('continuing research has a visible schedule',()=>{assert.equal(c.status({kind:'think',lastReview:1,nextReview:200},'',100),'已安排后续研究');assert.equal(c.status({kind:'think',lastReview:1,nextReview:200},'',300),'等待继续研究');});
test('user input and round budget explain why AI is waiting',()=>{assert.equal(c.status({kind:'think',needsUser:true,lastReview:1}),'等你聊聊');assert.equal(c.status({kind:'think',rounds:3,lastReview:1}),'本轮已推进 3 步');});
test('provider failure remains visible before the next research date',()=>assert.equal(c.status({kind:'think',error:'network',nextReview:200},'',100),'需要重试'));
