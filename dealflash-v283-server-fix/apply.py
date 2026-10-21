from pathlib import Path
p=Path("server-v26/src/index.js")
s=p.read_text()

s=s.replace("const VERSION='2.6.0';","const VERSION='2.8.1';")

old_status="""async function status(env){
  const row=await env.DEALFLASH_DB.prepare('SELECT * FROM monitor_state WHERE id=1').first();
  return json({ok:true,version:VERSION,monitoring:true,push_ready:false,last_scan_at:Number(row?.last_scan_at||0),last_ok_at:Number(row?.last_ok_at||0),last_error:row?.last_error||'',markets:['FALLCENT','GMARKET','AUCTION','11ST']});
}"""
new_status="""async function status(env){
  const row=await env.DEALFLASH_DB.prepare('SELECT * FROM monitor_state WHERE id=1').first();
  const c=await env.DEALFLASH_DB.prepare(\`SELECT
    (SELECT COUNT(*) FROM products) products,
    (SELECT COUNT(*) FROM offers) offers,
    (SELECT COUNT(*) FROM price_history) price_history,
    (SELECT COUNT(*) FROM alerts) alerts,
    (SELECT COUNT(*) FROM products WHERE product_key LIKE 'fcfp:%') legacy_identity,
    (SELECT MIN(checked_at) FROM price_history) oldest_history,
    (SELECT MAX(checked_at) FROM price_history) newest_history\`).first();
  return json({ok:true,version:VERSION,monitoring:true,push_ready:false,last_scan_at:Number(row?.last_scan_at||0),last_ok_at:Number(row?.last_ok_at||0),last_error:row?.last_error||'',products:Number(c?.products||0),offers:Number(c?.offers||0),price_history:Number(c?.price_history||0),alerts:Number(c?.alerts||0),legacy_identity:Number(c?.legacy_identity||0),oldest_history:Number(c?.oldest_history||0),newest_history:Number(c?.newest_history||0),markets:['FALLCENT','GMARKET','AUCTION','11ST']});
}"""
assert old_status in s
s=s.replace(old_status,new_status)

needle="    await env.DEALFLASH_DB.prepare('UPDATE monitor_state SET last_scan_at=?,last_ok_at=?,last_error=?,products_found=?,products_accepted=?,version=? WHERE id=1').bind(Date.now(),Date.now(),'',found,accepted,VERSION).run();"
assert needle in s
s=s.replace(needle,"    await cleanupLegacyIdentity(env,30);\\n"+needle)

old_key="async function stableFallcentKey(title,href){const n=normalize(title);if(n.length>=8)return 'fcfp:'+await sha1(n,20);return 'fcurl:'+await sha1(href,20);}"
new_key=r"""async function stableFallcentKey(title,href){
  const stable=fallcentStableIdentity(href);
  if(stable)return 'fcid:'+await sha1(stable,20);
  const canonical=canonicalFallcentUrl(href);
  if(canonical)return 'fcurl:'+await sha1(canonical,20);
  const fp=identityTitle(title);
  return 'fcfp:'+await sha1(fp||normalize(title),20);
}
function fallcentStableIdentity(href){
  try{
    const u=new URL(String(href||'')),host=u.hostname.toLowerCase().replace(/^www\./,'');
    const path=u.pathname.replace(/\/+$/,'');
    if(host==='fallcent.com'){const m=path.match(/^\/product\/([^/]+)/i);if(m)return 'fallcent-product:'+m[1].toLowerCase();}
    if(host==='fcnt.link'&&path&&path!=='/')return 'fallcent-short:'+path.toLowerCase();
  }catch{}
  return '';
}
function canonicalFallcentUrl(href){
  try{const u=new URL(String(href||''));u.hash='';u.search='';u.hostname=u.hostname.toLowerCase();u.pathname=u.pathname.replace(/\/+$/,'')||'/';return u.origin+u.pathname;}catch{return '';}
}
function identityTitle(title){
  return normalize(String(title||'').replace(/(?:역대급|최저가\s*타이밍|타이밍|특가|핫딜|판매자로켓|로켓배송|로켓프레시|로켓직구)/gi,' '));
}
async function cleanupLegacyIdentity(env,limit=30){
  const r=await env.DEALFLASH_DB.prepare("SELECT product_key,title,canonical_url,image_url,category,source,first_seen,last_seen,watched FROM products WHERE product_key LIKE 'fcfp:%' ORDER BY last_seen DESC LIMIT ?").bind(limit).all();
  for(const p of r.results||[]){
    try{
      const target=await stableFallcentKey(p.title,p.canonical_url);
      if(!target||target===p.product_key||target.startsWith('fcfp:'))continue;
      await migrateProductKey(env,p,target);
    }catch{}
  }
}
async function migrateProductKey(env,p,target){
  const e=await env.DEALFLASH_DB.prepare('SELECT * FROM products WHERE product_key=?').bind(target).first();
  const firstSeen=Math.min(Number(e?.first_seen||p.first_seen||Date.now()),Number(p.first_seen||Date.now()));
  const lastSeen=Math.max(Number(e?.last_seen||0),Number(p.last_seen||0));
  const watched=(Number(e?.watched||0)||Number(p.watched||0))?1:0;
  await env.DEALFLASH_DB.prepare(\`INSERT INTO products(product_key,title,canonical_url,image_url,category,source,first_seen,last_seen,watched) VALUES(?,?,?,?,?,?,?,?,?)
    ON CONFLICT(product_key) DO UPDATE SET title=excluded.title,canonical_url=excluded.canonical_url,image_url=CASE WHEN excluded.image_url<>'' THEN excluded.image_url ELSE products.image_url END,category=excluded.category,source=excluded.source,first_seen=MIN(products.first_seen,excluded.first_seen),last_seen=MAX(products.last_seen,excluded.last_seen),watched=MAX(products.watched,excluded.watched)\`)
    .bind(target,String(e?.title||p.title||'상품'),String(e?.canonical_url||p.canonical_url||''),String(e?.image_url||p.image_url||''),String(e?.category||p.category||'기타'),String(e?.source||p.source||'FALLCENT'),firstSeen,lastSeen,watched).run();

  const os=await env.DEALFLASH_DB.prepare('SELECT * FROM offers WHERE product_key=? ORDER BY checked_at ASC').bind(p.product_key).all();
  for(const o of os.results||[]){
    const offerKey=target+'|'+o.market,cur=await env.DEALFLASH_DB.prepare('SELECT checked_at FROM offers WHERE offer_key=?').bind(offerKey).first();
    if(!cur||Number(o.checked_at)>=Number(cur.checked_at||0)){
      await env.DEALFLASH_DB.prepare(\`INSERT INTO offers(offer_key,product_key,market,title,url,price,shipping,total_price,rocket,match_confidence,checked_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)
        ON CONFLICT(offer_key) DO UPDATE SET product_key=excluded.product_key,title=excluded.title,url=excluded.url,price=excluded.price,shipping=excluded.shipping,total_price=excluded.total_price,rocket=excluded.rocket,match_confidence=excluded.match_confidence,checked_at=excluded.checked_at\`)
        .bind(offerKey,target,o.market,o.title,o.url,o.price,o.shipping,o.total_price,o.rocket,o.match_confidence,o.checked_at).run();
    }
  }

  await env.DEALFLASH_DB.prepare('INSERT OR IGNORE INTO price_history(product_key,market,price,shipping,total_price,checked_at) SELECT ?,market,price,shipping,total_price,checked_at FROM price_history WHERE product_key=?').bind(target,p.product_key).run();

  const aa=await env.DEALFLASH_DB.prepare('SELECT * FROM alerts WHERE product_key=? ORDER BY created_at ASC').bind(p.product_key).all();
  for(const a of aa.results||[]){
    const sig=target+'|'+Math.round(Number(a.price||0));
    await env.DEALFLASH_DB.prepare('INSERT OR IGNORE INTO alerts(alert_sig,product_key,source,product_name,product_url,price,drop_rate,title,body,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)').bind(sig,target,a.source,a.product_name,a.product_url,a.price,a.drop_rate,a.title,a.body,a.created_at).run();
  }

  await env.DEALFLASH_DB.prepare('DELETE FROM alerts WHERE product_key=?').bind(p.product_key).run();
  await env.DEALFLASH_DB.prepare('DELETE FROM price_history WHERE product_key=?').bind(p.product_key).run();
  await env.DEALFLASH_DB.prepare('DELETE FROM offers WHERE product_key=?').bind(p.product_key).run();
  await env.DEALFLASH_DB.prepare('DELETE FROM products WHERE product_key=?').bind(p.product_key).run();
}"""
assert old_key in s
s=s.replace(old_key,new_key)
p.write_text(s)

w=Path("server-v26/wrangler.jsonc")
ws=w.read_text().replace('"*/15 * * * *"','"*/5 * * * *"')
w.write_text(ws)

print("server identity + safe legacy cleanup patch applied")
