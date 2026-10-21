from pathlib import Path
p=Path("dealflash_build_min/app/src/main/java/com/dealflash/app/DealFlashMessagingService.java")
s=p.read_text()
old='''        if(!"TEST".equals(kind))HotDealStore.put(this,key,product==null?"상품":product,url,src,price,0,drop,null);
        String body="TEST".equals(kind)?d.get("body"):(product==null?"상품":product)+" · "+won(price)+" · 내 알림 "+threshold+"%+";'''
new='''        if(!"TEST".equals(kind)){
            String name=product==null?"상품":product;
            HotDealStore.put(this,key,name,url,src,price,0,drop,null);
            // FCM is itself a fresh, server-authoritative observation. Persist it in the same store the main feed renders
            // so a temporary /api/deals sync failure can never produce "push arrived, app is empty".
            try{
                synchronized(HistoryStore.class){
                    java.util.Map<String,HistoryStore.H> map=HistoryStore.load(this);
                    HistoryStore.H h=map.get(key);long now=System.currentTimeMillis();
                    if(h==null){h=new HistoryStore.H();h.key=key;h.firstSeen=now;}
                    if(h.last>0&&h.last!=price){h.prevPrice=h.last;h.prevSeen=h.lastSeen;}
                    h.title=name;h.url=url;h.source=src;h.last=price;h.declaredDiscountPct=drop;
                    h.lastSeen=now;h.trusted=true;DealScore.observe(h,price,now);map.put(key,h);HistoryStore.save(this,map);
                }
            }catch(Throwable ignored){}
            ServerSync.refreshDeals(getApplicationContext(),null);
        }
        String body="TEST".equals(kind)?d.get("body"):(product==null?"상품":product)+" · "+won(price)+" · 내 알림 "+threshold+"%+";'''
if old not in s: raise SystemExit("FCM anchor missing")
s=s.replace(old,new)
p.write_text(s)

p=Path("dealflash_build_min/app/src/main/java/com/dealflash/app/ServerSync.java")
s=p.read_text()
s=s.replace('/** v2.8.4 HTTP bridge. Discovery signals are independent from per-device alert settings. */','/** v2.9.1 HTTP bridge. Discovery sync is observable and FCM can seed the rendered store. */')
s=s.replace('''        }catch(Exception ignored){}finally{DEAL_SYNCING.set(false);if(done!=null){try{new android.os.Handler(android.os.Looper.getMainLooper()).post(done);}catch(Exception ignored){done.run();}}}}).start();''','''        }catch(Exception error){
            c.getSharedPreferences(PREF,0).edit().putString("deals_sync_error",String.valueOf(error.getMessage())).putLong("deals_sync_error_at",System.currentTimeMillis()).apply();
        }finally{DEAL_SYNCING.set(false);if(done!=null){try{new android.os.Handler(android.os.Looper.getMainLooper()).post(done);}catch(Exception ignored){done.run();}}}}).start();''',1)
p.write_text(s)

g=Path("dealflash_build_min/app/build.gradle")
t=g.read_text().replace("versionCode 41","versionCode 42").replace("versionName '2.9.0'","versionName '2.9.1'")
g.write_text(t)
