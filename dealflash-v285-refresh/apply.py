from pathlib import Path
p=Path("dealflash_build_min/app/src/main/java/com/dealflash/app/MainActivity.java")
s=p.read_text()
s=s.replace("/** DealFlash v2.8.4: discovery filter correctness, threshold hardening and UI stability. */","/** DealFlash v2.8.5: pull refresh, minute refresh and safer back navigation. */")
s=s.replace("private long renderedAt=-1,lastForegroundSync=0;","private long renderedAt=-1,lastForegroundSync=0,backPressedAt=0;\n    private float pullStartY=Float.NaN;\n    private boolean manualRefreshInFlight=false;")
needle='list.setOnItemClickListener((parent,view,pos,id)->{if(pos>=0&&pos<shown.size())detail(shown.get(pos).key);});reload();'
repl='''list.setOnItemClickListener((parent,view,pos,id)->{if(pos>=0&&pos<shown.size())detail(shown.get(pos).key);});
        list.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                boolean atTop=list.getFirstVisiblePosition()==0&&(list.getChildCount()==0||list.getChildAt(0).getTop()>=0);
                pullStartY=atTop?e.getY():Float.NaN;
            }else if(e.getAction()==MotionEvent.ACTION_UP){
                if(!Float.isNaN(pullStartY)&&e.getY()-pullStartY>=dp(90))refreshFromPull();
                pullStartY=Float.NaN;
            }else if(e.getAction()==MotionEvent.ACTION_CANCEL)pullStartY=Float.NaN;
            return false;
        });reload();'''
if needle not in s: raise SystemExit("list hook not found")
s=s.replace(needle,repl)
needle2='    private void reload(){'
method='''    private void refreshFromPull(){
        if(manualRefreshInFlight)return;
        manualRefreshInFlight=true;
        if(count!=null)count.setText("새로고침 중…");
        if(ServerState.active(this)){
            lastForegroundSync=System.currentTimeMillis();
            ServerSync.refreshDeals(this,()->{manualRefreshInFlight=false;reload();Toast.makeText(this,"최신 상품으로 새로고침했어요",Toast.LENGTH_SHORT).show();});
        }else{
            manualRefreshInFlight=false;reload();
            Toast.makeText(this,"저장된 최신 상품을 새로고침했어요",Toast.LENGTH_SHORT).show();
        }
    }

'''
if needle2 not in s: raise SystemExit("reload hook not found")
s=s.replace(needle2,method+needle2)
old='@Override public void onBackPressed(){if(screen.equals("상세")){showList("급락");return;}if(!screen.equals("급락")){showList("급락");return;}super.onBackPressed();}'
new='''@Override public void onBackPressed(){
        if(screen.equals("상세")){showList("급락");return;}
        if(!screen.equals("급락")){showList("급락");return;}
        long now=System.currentTimeMillis();
        if(now-backPressedAt>2000L){backPressedAt=now;Toast.makeText(this,"한 번 더 누르면 앱이 종료됩니다",Toast.LENGTH_SHORT).show();return;}
        super.onBackPressed();
    }'''
if old not in s: raise SystemExit("back hook not found")
s=s.replace(old,new)
p.write_text(s)
g=Path("dealflash_build_min/app/build.gradle")
t=g.read_text().replace("versionCode 39","versionCode 40").replace("versionName '2.8.4'","versionName '2.8.5'")
g.write_text(t)
