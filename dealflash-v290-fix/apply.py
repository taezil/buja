from pathlib import Path
p=Path("dealflash_build_min/app/src/main/java/com/dealflash/app/MainActivity.java")
s=p.read_text()
s=s.replace("/** DealFlash v2.8.5: pull refresh, minute refresh and safer back navigation. */","/** DealFlash v2.9.0: notification deep-link recovery and deal evidence UX. */")
s=s.replace('String key=getIntent().getStringExtra("hotKey");if(key!=null)detail(key);','String key=getIntent().getStringExtra("hotKey");if(key!=null)openNotificationProduct(getIntent());',1)
s=s.replace('@Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);String k=i.getStringExtra("hotKey");if(k!=null)detail(k);}','@Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);String k=i.getStringExtra("hotKey");if(k!=null)openNotificationProduct(i);}')
anchor='    private void detail(String key){'
method=r'''    private void openNotificationProduct(Intent intent){
        final String key=intent.getStringExtra("hotKey");
        if(key==null||key.isEmpty())return;
        Map<String,HistoryStore.H> map=HistoryStore.load(this);
        HistoryStore.H ready=map.get(key);
        if(DataQuality.visible(ready)){detail(key);return;}
        HotDealStore.Hot hot=null;
        for(HotDealStore.Hot h:HotDealStore.all(this))if(key.equals(h.key)){hot=h;break;}
        if(hot!=null&&DataQuality.valid(hot.key,hot.source,hot.url,hot.title,hot.current,hot.dropPct)){
            long now=System.currentTimeMillis();HistoryStore.H h=new HistoryStore.H();
            h.key=hot.key;h.title=hot.title;h.url=hot.url;h.source=hot.source;h.imageUrl=hot.imageUrl;
            h.last=hot.current;h.low=hot.current;h.reference=hot.reference;h.declaredDiscountPct=hot.dropPct;
            h.firstSeen=hot.foundAt>0?hot.foundAt:now;h.lastSeen=hot.lastVerifiedAt>0?hot.lastVerifiedAt:now;h.trusted=true;
            DealScore.observe(h,h.last,h.lastSeen);map.put(key,h);HistoryStore.save(this,map);latestHistory=map;detail(key);return;
        }
        final String source=intent.getStringExtra("source"),url=intent.getStringExtra("url");
        if(ServerState.active(this)){
            Toast.makeText(this,"최신 상품 정보를 불러오는 중…",Toast.LENGTH_SHORT).show();
            ServerSync.refreshDeals(this,()->{
                HistoryStore.H x=HistoryStore.load(this).get(key);
                if(DataQuality.visible(x)){latestHistory=HistoryStore.load(this);detail(key);}
                else if(url!=null&&!url.isEmpty())AppLauncher.openProduct(this,source==null?"FALLCENT":source,url);
                else {showList("급락");Toast.makeText(this,"상품 목록을 새로고침했어요",Toast.LENGTH_SHORT).show();}
            });
        }else if(url!=null&&!url.isEmpty())AppLauncher.openProduct(this,source==null?"FALLCENT":source,url);
        else showList("급락");
    }

'''
if anchor not in s: raise SystemExit("detail anchor missing")
s=s.replace(anchor,method+anchor)
# Improve empty-state copy: no alarming false error.
s=s.replace('조건에 맞는 상품이 없어요 · 다음 수집을 기다려 주세요','현재 조건에 맞는 상품이 없어요 · 아래로 당겨 새로고침')
p.write_text(s)
g=Path("dealflash_build_min/app/build.gradle");t=g.read_text().replace("versionCode 40","versionCode 41").replace("versionName '2.8.5'","versionName '2.9.0'");g.write_text(t)
