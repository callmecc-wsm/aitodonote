import {env} from 'cloudflare:workers';
import {getChatGPTUser} from '../../chatgpt-auth';
import {Store} from '../../../lib/store.mjs';
import {execute} from '../../../lib/service.mjs';
export const dynamic='force-dynamic';
export async function POST(request:Request){
 const user=await getChatGPTUser();
 if(!user)return Response.json({error:'请登录后打开记录',signin:'/signin-with-chatgpt?return_to=%2Fnote%2Findex.html'},{status:401});
 if(request.headers.get('x-notenote')!=='1'||!request.headers.get('content-type')?.startsWith('application/json')||request.headers.get('sec-fetch-site')==='cross-site')return Response.json({error:'请求来源无效'},{status:403});
 try{
 const raw=await request.text();if(raw.length>9000000)return Response.json({error:'备份过大'},{status:413});
 const {method,params={}}=JSON.parse(raw);if(!['snapshot','save','change','delete','read','reminded','import','settings','review','test'].includes(method))throw Error('无效操作');
 const bindings=env as unknown as {DB:D1Database;NOTE_KEY:string};
 if(!bindings.DB)return Response.json({error:'记录服务暂时不可用，请稍后重试'},{status:503});
 const result=await execute(new Store(bindings.DB,user.userId),method,params,bindings.NOTE_KEY);
 return Response.json(result,{headers:{'Cache-Control':'no-store'}});
 }catch(e){const message=e instanceof Error?e.message:'';const safe=/SQLITE|D1_|database|syntax|JSON|decrypt|operation|fetch|binding/i.test(message)?'记录服务暂时不可用，请稍后重试':message||'操作未完成，请重试';return Response.json({error:safe},{status:400,headers:{'Cache-Control':'no-store'}})}
}
