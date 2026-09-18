import {defaults} from './domain.mjs';
export class Store{
 constructor(db,owner){this.db=db;this.owner=owner}
 async row(){await this.db.prepare('INSERT OR IGNORE INTO notebooks(owner,data) VALUES(?,?)').bind(this.owner,JSON.stringify(defaults())).run();return this.db.prepare('SELECT * FROM notebooks WHERE owner=?').bind(this.owner).first()}
 async read(){const r=await this.row();return {data:JSON.parse(r.data),version:r.version,busy:r.lease_until>Date.now()}}
 async mutate(fn){for(let i=0;i<8;i++){const r=await this.row(),data=JSON.parse(r.data),result=fn(data);if(new TextEncoder().encode(JSON.stringify(data)).length>1500000)throw Error('记录空间已满，请先导出备份并整理旧记录');const out=await this.db.prepare('UPDATE notebooks SET data=?,version=version+1 WHERE owner=? AND version=?').bind(JSON.stringify(data),this.owner,r.version).run();if(out.meta.changes)return result}throw Error('记录正在其他页面更新，请稍后重试')}
 async lock(){const token=crypto.randomUUID();const r=await this.db.prepare('UPDATE notebooks SET lease=?,lease_until=? WHERE owner=? AND lease_until<?').bind(token,Date.now()+600000,this.owner,Date.now()).run();return r.meta.changes?token:null}
 async unlock(token){await this.db.prepare('UPDATE notebooks SET lease=?,lease_until=0 WHERE owner=? AND lease=?').bind('',this.owner,token).run()}
}
