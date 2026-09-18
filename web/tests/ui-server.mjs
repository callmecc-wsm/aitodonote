// Isolated CI fixture server. Never imported by the deployed application.
import http from 'node:http';import {readFileSync} from 'node:fs';import {resolve,extname} from 'node:path';import {DatabaseSync} from 'node:sqlite';import {Store} from '../lib/store.mjs';import {execute} from '../lib/service.mjs';
const db=new DatabaseSync(':memory:');db.exec(readFileSync('drizzle/0000_real_slyde.sql','utf8'));
const d1={prepare(sql){const q=db.prepare(sql);return {bind(...args){return {async run(){const r=q.run(...args);return {meta:{changes:Number(r.changes)}}},async first(){return q.get(...args)}}}}}};
const store=new Store(d1,'ci-fixture');const secret=Buffer.alloc(32,7).toString('base64');
const fakeFetch=async(url,opts)=>url.includes('tavily')?Response.json({results:[{url:'https://example.com/source',title:'测试来源',content:'测试内容'}]}):Response.json({choices:[{message:{content:JSON.stringify({summary:'先区分训练与推理',detail:'这是自动化测试的固定回答，仅用于验证界面和交互。\n训练调整参数，推理使用已有参数。',question:'你更想了解哪个阶段？',nextAction:'ask_user',nextQuery:''})}}]});
http.createServer(async(req,res)=>{try{const pathname=new URL(req.url,'http://localhost').pathname;
 if(pathname==='/'){res.writeHead(302,{Location:'/note/index.html'});res.end();return}
 if(pathname==='/api/note'&&req.method==='POST'){let raw='';for await(const c of req)raw+=c;const {method,params}=JSON.parse(raw);if(req.headers['x-notenote']!=='1')throw Error('Missing request marker');const out=await execute(store,method,params,secret,fakeFetch);res.setHeader('content-type','application/json');res.end(JSON.stringify(out));return}
 const path=resolve('public','.'+(pathname==='/'?'/note/index.html':pathname));if(!path.startsWith(resolve('public')+'/'))throw Error('Invalid path');res.setHeader('Content-Type',({'.html':'text/html; charset=utf-8','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.webmanifest':'application/manifest+json'})[extname(path)]||'application/octet-stream');res.end(readFileSync(path));
 }catch(e){res.statusCode=400;res.setHeader('content-type','application/json');res.end(JSON.stringify({error:e.message}))}}).listen(4399,'127.0.0.1');
