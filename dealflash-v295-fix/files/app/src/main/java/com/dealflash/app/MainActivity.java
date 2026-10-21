package com.dealflash.app;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.text.*;
import java.util.*;

/** DealFlash v2.9.0: notification deep-link recovery and deal evidence UX. */
public class MainActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout root,body,bottomNav;
    private TextView status,statusPill,count,screenTitle;
    private ListView list;
    private ProductAdapter adapter;
    private String screen="급락",category="전체",signalFilter="전체",query="";
    private int sort=0,minDrop=0,minSaving=0;
    private boolean evidenceOnly=false,favoritesOnly=false;
    private int bg,card,ink,muted,border,soft,accentSoft,green,greenSoft,amber,amberSoft;
    private final int accent=0xFFFF5A45;
    private final int electric=0xFF8B7CFF;
    private long renderedAt=-1,lastForegroundSync=0,backPressedAt=0;
    private float pullStartY=Float.NaN;
    private boolean manualRefreshInFlight=false;
    private boolean loading,reloadPending;
    private final java.util.concurrent.ExecutorService reads=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final List<HistoryStore.H> shown=new ArrayList<>();
    private final Map<String,HotDealStore.Hot> hot=new HashMap<>();
    private final Map<String,DealScore.Evidence> evidence=new HashMap<>();
    private final Map<String,Integer> urgency=new HashMap<>();
    private Map<String,HistoryStore.H> latestHistory=new HashMap<>();
    private static final String[] SORTS={"추천순","할인율 높은순","절약금액 높은순","최근 발견순","신호 많은순","가격 낮은순"};
    private static final String[] CATS={"전체","식품","생활/주방","가전/디지털","패션/의류","뷰티","육아/유아","스포츠/레저","자동차","반려동물","가구/인테리어","건강","기타"};
    private static final String[] SIGNAL_FILTERS={"전체","실시간 인기","오늘 인기","지금이 타이밍","카테고리 급락"};
    private final Runnable tick=new Runnable(){public void run(){refreshStatus();long now=System.currentTimeMillis();if(now-lastForegroundSync>=60_000L){lastForegroundSync=now;ServerSync.refreshDeals(MainActivity.this,()->reload());}long t=Math.max(ScannerPrefs.lastCheck(MainActivity.this),ServerSync.lastDealSync(MainActivity.this));if(list!=null&&t!=renderedAt){renderedAt=t;reload();}handler.postDelayed(this,2000);}};

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        boolean dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        bg=dark?0xFF080A0F:0xFFF6F7FA;card=dark?0xFF12161D:Color.WHITE;ink=dark?0xFFF7F8FB:0xFF11151D;muted=dark?0xFF98A2B1:0xFF667085;border=dark?0xFF242B36:0xFFE5E8EE;
        soft=dark?0xFF1A202A:0xFFF0F2F6;accentSoft=dark?0xFF351D1B:0xFFFFECE9;green=dark?0xFF57D895:0xFF138A4B;greenSoft=dark?0xFF123127:0xFFE8F8EF;amber=dark?0xFFFFC766:0xFFAD6900;amberSoft=dark?0xFF342815:0xFFFFF4DF;
        getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        DataMigration.run(this);ServerSync.restoreDeals(this);NotificationHelper.ensureChannels(this);PushBootstrap.start(this);
        android.content.SharedPreferences pref=getSharedPreferences("ui_v25",0);
        sort=pref.getInt("sort",0);minDrop=pref.getInt("drop",0);minSaving=pref.getInt("saving",0);category=pref.getString("category","전체");signalFilter=pref.getString("signal","전체");
        build();showList("급락");
        ServerSync.refreshStatus(this,()->{
            if(ServerState.active(this)){stopService(new Intent(this,MonitorService.class));ServerPollJobService.schedule(this);}
            else if(ScannerPrefs.enabled(this))start(false);
            refreshStatus();reload();
        });
        String key=getIntent().getStringExtra("hotKey");if(key!=null)openNotificationProduct(getIntent());
    }
    @Override protected void onResume(){super.onResume();lastForegroundSync=0;handler.removeCallbacks(tick);handler.post(tick);ServerSync.refreshDeals(this,()->reload());}
    @Override protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);String k=i.getStringExtra("hotKey");if(k!=null)openNotificationProduct(i);}

    private void build(){
        root=column();root.setBackgroundColor(bg);root.setPadding(dp(12),dp(7),dp(12),0);
        LinearLayout head=row();head.setPadding(0,dp(4),0,dp(2));
        TextView logo=text("⚡",20,true,Color.WHITE);logo.setGravity(Gravity.CENTER);GradientDrawable logoBg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{accent,electric});logoBg.setCornerRadius(dp(14));logo.setBackground(logoBg);head.addView(logo,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout brandBox=column();brandBox.setPadding(dp(10),0,0,0);TextView brand=text("DealFlash",24,true,ink);brand.setPadding(0,0,0,0);brandBox.addView(brand);screenTitle=text("가격 급락 레이더",11,false,muted);screenTitle.setPadding(0,0,0,0);brandBox.addView(screenTitle);head.addView(brandBox,new LinearLayout.LayoutParams(0,-2,1));
        statusPill=pill("● 준비 중",11,muted,soft,false);head.addView(statusPill,new LinearLayout.LayoutParams(-2,dp(34)));root.addView(head);
        status=text("가격 정보를 준비하고 있어요",11,false,muted);status.setPadding(dp(2),dp(3),0,dp(7));root.addView(status);
        body=column();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        bottomNav=row();bottomNav.setPadding(0,dp(6),0,dp(8));bottomNav.setBackgroundColor(bg);root.addView(bottomNav,new LinearLayout.LayoutParams(-1,dp(68)));buildNav();
        setContentView(root);
    }

    private void buildNav(){
        bottomNav.removeAllViews();
        addNav("⚡","급락",()->{favoritesOnly=false;showList("급락");});
        addNav("✨","발견",()->{favoritesOnly=false;showList("발견");});
        addNav("⌕","검색",()->{favoritesOnly=false;showList("검색");});
        addNav("♡","보관",()->{favoritesOnly=false;showList("보관");});
        addNav("⚙","설정",this::settings);
    }
    private void addNav(String icon,String label,Runnable action){
        boolean active=screen.equals(label)||(screen.equals("상세")&&label.equals("급락"));
        LinearLayout n=column();n.setGravity(Gravity.CENTER);n.setPadding(dp(3),dp(3),dp(3),dp(3));n.setBackground(shape(active?accentSoft:Color.TRANSPARENT,14));
        TextView i=text(icon,18,true,active?accent:muted);i.setGravity(Gravity.CENTER);i.setPadding(0,0,0,0);TextView t=text(label,10,active,active?accent:muted);t.setGravity(Gravity.CENTER);t.setPadding(0,1,0,0);n.addView(i);n.addView(t);n.setOnClickListener(v->action.run());bottomNav.addView(n,new LinearLayout.LayoutParams(0,-1,1));
    }

    private void reset(String name){screen=name;list=null;body.removeAllViews();if(bottomNav!=null){bottomNav.setVisibility(name.equals("상세")?View.GONE:View.VISIBLE);buildNav();}if(screenTitle!=null)screenTitle.setText(titleSubtitle(name));}
    private String titleSubtitle(String name){if("급락".equals(name))return "실시간 급락 · 자동 포착";if("발견".equals(name))return "실시간 인기 · 오늘 인기 · 타이밍 신호";if("검색".equals(name))return "상품명·모델명으로 찾기";if("보관".equals(name))return "다시 볼 딜만 모아보기";if("설정".equals(name))return "서버 감시 · 알림 기준";return "가격 근거를 한눈에 확인";}

    private void showList(String name){
        reset(name);
        boolean searchMode="검색".equals(name);
        EditText search=new EditText(this);search.setSingleLine();search.setTextColor(ink);search.setHintTextColor(muted);search.setTextSize(14);search.setHint("⌕  상품명 또는 모델명 검색");search.setPadding(dp(15),0,dp(15),0);search.setBackground(stroked(card,16,border,1));search.setText(query);body.addView(search,new LinearLayout.LayoutParams(-1,dp(50)));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int n){query=s.toString();reload();}public void afterTextChanged(Editable e){}});
        if(searchMode){
            LinearLayout saved=row();saved.setPadding(0,dp(7),0,0);TextView save=pill("＋ 검색어 저장",11,ink,soft,true);save.setOnClickListener(v->{String q=search.getText().toString().trim();if(!q.isEmpty()){KeywordStore.add(this,q);Toast.makeText(this,"검색어를 저장했어요",Toast.LENGTH_SHORT).show();}});saved.addView(save);body.addView(saved);
            search.requestFocus();search.postDelayed(()->{try{((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search,InputMethodManager.SHOW_IMPLICIT);}catch(Exception ignored){}},250);
        }
        if("발견".equals(name)){
            TextView signalLabel=text("DEAL SIGNALS · 알림과 무관한 발견 신호",10,true,muted);signalLabel.setPadding(dp(2),dp(9),0,0);body.addView(signalLabel);
            HorizontalScrollView signalScroll=new HorizontalScrollView(this);signalScroll.setHorizontalScrollBarEnabled(false);LinearLayout signalChips=row();
            for(String sig:SIGNAL_FILTERS){TextView c=chip(sig,sig.equals(signalFilter));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(36));lp.setMargins(0,dp(6),dp(7),dp(5));signalChips.addView(c,lp);c.setOnClickListener(v->{signalFilter=sig;persist();showList(screen);});}signalScroll.addView(signalChips);body.addView(signalScroll);
        }
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);LinearLayout cats=row();
        for(String nameCat:CATS){TextView c=chip(nameCat,nameCat.equals(category));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(36));lp.setMargins(0,dp(5),dp(7),dp(8));cats.addView(c,lp);c.setOnClickListener(v->{category=nameCat;persist();showList(screen);});}scroll.addView(cats);body.addView(scroll);
        LinearLayout actions=row();TextView order=actionChip(SORTS[Math.max(0,Math.min(5,sort))]+"  ▾"),filter=actionChip("☷  필터"+((minDrop>0||minSaving>0||evidenceOnly||favoritesOnly)?" · 적용":""));
        actions.addView(order,new LinearLayout.LayoutParams(0,dp(40),1));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-2,dp(40));fp.setMargins(dp(8),0,0,0);actions.addView(filter,fp);body.addView(actions);
        order.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("정렬 기준").setSingleChoiceItems(SORTS,sort,(d,which)->{sort=which;persist();d.dismiss();showList(screen);}).show());filter.setOnClickListener(v->filters());
        count=text("불러오는 중…",11,false,muted);count.setPadding(dp(2),dp(8),0,dp(5));body.addView(count);
        list=new ListView(this);list.setDivider(null);list.setDividerHeight(dp(11));list.setClipToPadding(false);list.setPadding(0,dp(2),0,dp(22));list.setSelector(android.R.color.transparent);
        adapter=new ProductAdapter();list.setAdapter(adapter);body.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((parent,view,pos,id)->{if(pos>=0&&pos<shown.size())detail(shown.get(pos).key);});
        list.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                boolean atTop=list.getFirstVisiblePosition()==0&&(list.getChildCount()==0||list.getChildAt(0).getTop()>=0);
                pullStartY=atTop?e.getY():Float.NaN;
            }else if(e.getAction()==MotionEvent.ACTION_UP){
                if(!Float.isNaN(pullStartY)&&e.getY()-pullStartY>=dp(90))refreshFromPull();
                pullStartY=Float.NaN;
            }else if(e.getAction()==MotionEvent.ACTION_CANCEL)pullStartY=Float.NaN;
            return false;
        });reload();
        if("발견".equals(name))ServerSync.syncDiscovery(this,signalFilter,()->reload());
    }

    private void refreshFromPull(){
        if(manualRefreshInFlight)return;
        manualRefreshInFlight=true;
        if(count!=null)count.setText("새로고침 중…");
        lastForegroundSync=System.currentTimeMillis();
        ServerSync.manualScanAndRefresh(this,()->{manualRefreshInFlight=false;reload();Toast.makeText(this,"새로고침 완료 · "+SyncDiag.oneLine(this),Toast.LENGTH_SHORT).show();});
    }

    private void reload(){
        if(list==null)return;
        if(loading){reloadPending=true;return;}
        final ListView target=list;final String targetScreen=screen;loading=true;reloadPending=false;
        try{reads.execute(()->{
            try{
                final Map<String,HistoryStore.H> snapshot=HistoryStore.load(this);
                final List<HotDealStore.Hot> hotSnapshot="급락".equals(targetScreen)?HotDealStore.recent(this):Collections.emptyList();
                runOnUiThread(()->{
                    loading=false;
                    if(isDestroyed())return;
                    if(list==target)applySnapshot(snapshot,hotSnapshot);
                    if(reloadPending||list!=target){reloadPending=false;if(list!=null)reload();}
                });
            }catch(Throwable error){
                runOnUiThread(()->{
                    loading=false;
                    if(isDestroyed())return;
                    if(list==target&&count!=null)count.setText("목록을 다시 불러오는 중…");
                    reloadPending=false;if(list!=null)reload();
                });
            }
        });}catch(Exception ignored){loading=false;reloadPending=false;}
    }

    private void applySnapshot(Map<String,HistoryStore.H> history,List<HotDealStore.Hot> hotSnapshot){
        if(list==null)return;latestHistory=new HashMap<>(history);
        int pos=list.getFirstVisiblePosition(),top=list.getChildCount()>0?list.getChildAt(0).getTop():0;
        hot.clear();for(HotDealStore.Hot d:hotSnapshot)hot.put(d.key,d);
        shown.clear();evidence.clear();urgency.clear();long now=System.currentTimeMillis();
        FeedFilter.Params fp=new FeedFilter.Params();fp.screen=screen;fp.category=category;fp.signal=signalFilter;fp.query=query;fp.minDrop=minDrop;fp.minSaving=minSaving;
        fp.evidenceOnly=evidenceOnly;fp.favoritesOnly=favoritesOnly;fp.hotKeys=hot.keySet();fp.favorite=this::favorite;fp.categoryOf=this::categoryOf;
        for(HistoryStore.H x:FeedFilter.cards(history.values(),fp,now,evidence)){urgency.put(x.key,DealUrgency.score(x,evidence.get(x.key),now));shown.add(x);}
        shown.sort((a,b)->{DealScore.Evidence x=evidence.get(a.key),y=evidence.get(b.key);int ux=urgency.get(a.key),uy=urgency.get(b.key),c=0;switch(sort){case 1:c=Integer.compare(b.declaredDiscountPct,a.declaredDiscountPct);break;case 2:c=Double.compare(y.count>=3?y.saving:0,x.count>=3?x.saving:0);break;case 3:c=Long.compare(b.firstSeen,a.firstSeen);break;case 4:c=Integer.compare(signalCount(b),signalCount(a));break;case 5:c=Double.compare(a.last,b.last);break;default:c=Integer.compare(uy,ux);if(c==0)c=Boolean.compare(y.errorCandidate,x.errorCandidate);if(c==0)c=Integer.compare(y.score,x.score);break;}if(c==0)c=Long.compare(b.firstSeen,a.firstSeen);return c==0?a.key.compareTo(b.key):c;});
        String signalSuffix="발견".equals(screen)&&!"전체".equals(signalFilter)?" · "+signalFilter:"";String alertSuffix="급락".equals(screen)?" · 알림 "+ScannerPrefs.threshold(this)+"%+":"";
        int variants=0;for(HistoryStore.H h:history.values())if(DataQuality.visible(h)&&now-h.lastSeen<=24L*60*60*1000)variants++;String tally=variants>shown.size()?"상품 "+shown.size()+"개 · 옵션 "+variants+"개":"현재 "+shown.size()+"개 포착";count.setText(shown.isEmpty()?emptyMessage():tally+signalSuffix+alertSuffix);adapter.notifyDataSetChanged();list.setSelectionFromTop(pos,top);
    }
    private String emptyMessage(){if("보관".equals(screen))return "아직 보관한 상품이 없어요";if(!query.isEmpty())return "검색 결과가 없어요 · 다른 단어로 찾아보세요";String why=SyncDiag.oneLine(this);if("발견".equals(screen))return "현재 수집된 발견 신호가 없어요 · 아래로 당겨 즉시 스캔\n["+why+"]";return "현재 조건에 맞는 상품이 없어요 · 아래로 당겨 새로고침\n["+why+"]";}
    private DealScore.Evidence evaluate(HistoryStore.H x){HistoryStore.H prior=new HistoryStore.H();prior.observations=x.observations;prior.last=x.prevPrice;prior.lastSeen=x.prevSeen;return DealScore.evaluate(prior,x.last,x.declaredDiscountPct,x.lastSeen);}

    private class Holder {ImageView image;TextView name,price,bigHot,discount,categoryLine,signalLine,ref,saving,meta,heart,trendLabel;LinearLayout badges,container;}
    private class ProductAdapter extends BaseAdapter {
        public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return p;}
        public View getView(int position,View recycled,android.view.ViewGroup parent){
            Holder h;
            if(recycled==null){
                LinearLayout c=column();c.setPadding(dp(14),dp(13),dp(14),dp(13));c.setBackground(stroked(card,20,border,1));c.setElevation(dp(1));h=new Holder();h.container=c;
                h.badges=row();h.bigHot=badge("🔥 BIG HOT DEAL",Color.WHITE,accent);h.discount=badge("",accent,accentSoft);h.badges.addView(h.bigHot);h.badges.addView(space(6,1));h.badges.addView(h.discount);c.addView(h.badges);
                LinearLayout r=row();r.setGravity(Gravity.TOP);r.setPadding(0,dp(11),0,0);h.image=new ImageView(MainActivity.this);h.image.setScaleType(ImageView.ScaleType.FIT_CENTER);h.image.setBackground(shape(0xFFF7F7F8,16));int size=getResources().getConfiguration().screenWidthDp<360?104:116;r.addView(h.image,new LinearLayout.LayoutParams(dp(size),dp(size)));
                LinearLayout info=column();info.setPadding(dp(13),0,0,0);LinearLayout catRow=row();h.categoryLine=text("",11,false,muted);catRow.addView(h.categoryLine,new LinearLayout.LayoutParams(0,-2,1));h.heart=text("♡",25,false,muted);h.heart.setGravity(Gravity.CENTER);h.heart.setPadding(dp(8),0,dp(2),0);catRow.addView(h.heart,new LinearLayout.LayoutParams(dp(42),dp(38)));info.addView(catRow);
                h.name=text("",15,true,ink);h.name.setMaxLines(2);h.name.setEllipsize(TextUtils.TruncateAt.END);h.name.setLineSpacing(dp(2),1);info.addView(h.name);
                h.signalLine=text("",11,true,amber);h.signalLine.setMaxLines(2);h.signalLine.setPadding(0,dp(3),0,0);info.addView(h.signalLine);
                h.price=text("",25,true,accent);h.price.setPadding(0,dp(5),0,0);info.addView(h.price);
                LinearLayout refRow=row();h.ref=text("",11,false,muted);h.saving=text("",11,true,green);refRow.addView(h.ref,new LinearLayout.LayoutParams(0,-2,1));refRow.addView(h.saving);info.addView(refRow);r.addView(info,new LinearLayout.LayoutParams(0,-2,1));c.addView(r);
                h.meta=text("",11,false,muted);h.meta.setPadding(0,dp(10),0,0);h.meta.setMaxLines(2);c.addView(h.meta);
                c.setTag(h);recycled=c;
            }else h=(Holder)recycled.getTag();
            HistoryStore.H x=shown.get(position);DealScore.Evidence e=evidence.get(x.key);int u=urgency.containsKey(x.key)?urgency.get(x.key):DealUrgency.score(x,e,System.currentTimeMillis());
            boolean bigHot=x.declaredDiscountPct>=60;
            h.bigHot.setVisibility(bigHot?View.VISIBLE:View.GONE);h.container.setBackground(stroked(bigHot?accentSoft:card,20,bigHot?accent:border,bigHot?2:1));
            h.discount.setText("▼ "+x.declaredDiscountPct+"%");
            h.categoryLine.setText(categoryOf(x)+"  ·  "+sourceLabel(x));h.name.setText(ProductUX.clean(x.title));String sigText=signalText(x);h.signalLine.setText(sigText);h.signalLine.setVisibility(sigText.isEmpty()?View.GONE:View.VISIBLE);h.price.setText(money(x.last));
            if(e.count>=3&&e.reference>x.last){h.ref.setText("기준 "+money(e.reference));h.ref.setPaintFlags(h.ref.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG);h.saving.setText("  "+money(e.saving)+" 절약");}else{h.ref.setText("가격 이력 수집 중");h.ref.setPaintFlags(h.ref.getPaintFlags()&~Paint.STRIKE_THRU_TEXT_FLAG);h.saving.setText("");}
            h.meta.setText(ago(x.firstSeen)+" 발견 · "+ago(x.lastSeen)+" 확인"+(System.currentTimeMillis()-x.lastSeen>10*60*1000?" · 업데이트 지연":""));
            h.heart.setText(favorite(x.key)?"♥":"♡");h.heart.setTextColor(favorite(x.key)?accent:muted);h.heart.setOnClickListener(v->{boolean next=!favorite(x.key);getSharedPreferences("favorites",0).edit().putBoolean(x.key,next).apply();h.heart.setText(next?"♥":"♡");h.heart.setTextColor(next?accent:muted);if("보관".equals(screen))reload();});
            h.image.setContentDescription(ProductUX.clean(x.title));ProductImages.load(MainActivity.this,h.image,x.imageUrl,x.key);return recycled;
        }
    }


    private void setConfidenceBadge(TextView v,String confidence){int fg=muted,bg2=soft;if("신뢰도 높음".equals(confidence)){fg=green;bg2=greenSoft;}else if("신뢰도 보통".equals(confidence)){fg=amber;bg2=amberSoft;}v.setText(confidence);v.setTextColor(fg);v.setBackground(shape(bg2,10));}

    private void filters(){
        LinearLayout form=column();form.setPadding(dp(20),dp(8),dp(20),0);EditText drop=input("최소 표기 할인율 (0~99)",String.valueOf(minDrop)),saving=input("최소 관측 절약금액 (원)",String.valueOf(minSaving));form.addView(drop);form.addView(saving);
        CheckBox confirmed=new CheckBox(this);confirmed.setText("가격 이력 3회 이상만");confirmed.setChecked(evidenceOnly);form.addView(confirmed);CheckBox fav=new CheckBox(this);fav.setText("북마크 상품만");fav.setChecked(favoritesOnly);form.addView(fav);
        new AlertDialog.Builder(this).setTitle("빠르게 골라보기").setView(form).setNeutralButton("초기화",(d,w)->{minDrop=0;minSaving=0;evidenceOnly=false;favoritesOnly=false;persist();showList(screen);}).setNegativeButton("취소",null).setPositiveButton("적용",(d,w)->{minDrop=Math.min(99,number(drop));minSaving=number(saving);evidenceOnly=confirmed.isChecked();favoritesOnly=fav.isChecked();persist();showList(screen);}).show();
    }

    private void openNotificationProduct(Intent intent){
        final String key=intent.getStringExtra("hotKey");
        if(key==null||key.isEmpty())return;
        Map<String,HistoryStore.H> map=HistoryStore.load(this);
        HistoryStore.H ready=map.get(key);
        if(DataQuality.visible(ready)){detail(key);return;}
        HotDealStore.Hot hot=null;
        for(HotDealStore.Hot h:HotDealStore.all(this))if(key.equals(h.key)){hot=h;break;}
        if(hot!=null&&DataQuality.acceptable(hot.key,hot.source,hot.url,hot.title,hot.current,hot.dropPct)){
            long now=System.currentTimeMillis();HistoryStore.H h=new HistoryStore.H();
            h.key=hot.key;h.title=hot.title;h.url=hot.url;h.source=hot.source;h.imageUrl=hot.imageUrl;
            h.last=hot.current;h.low=hot.current;h.reference=hot.reference;h.declaredDiscountPct=hot.dropPct;
            h.firstSeen=hot.foundAt>0?hot.foundAt:now;h.lastSeen=hot.lastVerifiedAt>0?hot.lastVerifiedAt:now;h.trusted=true;
            DealScore.observe(h,h.last,h.lastSeen);map.put(key,h);HistoryStore.save(this,map);latestHistory=map;detail(key);return;
        }
        final String source=intent.getStringExtra("source"),url=intent.getStringExtra("url");
        if(true){
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

    private void detail(String key){
        Map<String,HistoryStore.H> all=latestHistory.isEmpty()?HistoryStore.load(this):new HashMap<>(latestHistory);HistoryStore.H x=all.get(key);
        if(!DataQuality.visible(x)){Toast.makeText(this,"재확인이 필요한 상품입니다",Toast.LENGTH_SHORT).show();return;}
        latestHistory=all;reset("상세");LinearLayout c=scrollBody();
        LinearLayout top=row();TextView back=actionChip("←  목록");top.addView(back);top.addView(space(1,1),new LinearLayout.LayoutParams(0,1,1));TextView fav=text(favorite(key)?"♥":"♡",27,false,favorite(key)?accent:muted);fav.setGravity(Gravity.CENTER);top.addView(fav,new LinearLayout.LayoutParams(dp(48),dp(42)));c.addView(top);back.setOnClickListener(v->showList("급락"));fav.setOnClickListener(v->{boolean next=!favorite(key);getSharedPreferences("favorites",0).edit().putBoolean(key,next).apply();fav.setText(next?"♥":"♡");fav.setTextColor(next?accent:muted);});
        ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.FIT_CENTER);im.setBackground(stroked(Color.WHITE,22,border,1));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(275));ip.setMargins(0,dp(10),0,dp(12));c.addView(im,ip);ProductImages.load(this,im,x.imageUrl,x.key);
        c.addView(text(categoryOf(x)+"  ·  "+sourceLabel(x)+(x.rocket?"  ·  로켓 계열":""),12,false,muted));if(x.declaredDiscountPct>=60){TextView bh=badge("🔥 BIG HOT DEAL · 60%+",Color.WHITE,accent);LinearLayout.LayoutParams bhp=new LinearLayout.LayoutParams(-2,-2);bhp.setMargins(0,dp(5),0,dp(4));c.addView(bh,bhp);}TextView title=text(ProductUX.clean(x.title),22,true,ink);title.setLineSpacing(dp(3),1);c.addView(title);String detailSignal=signalText(x);if(!detailSignal.isEmpty()){TextView ds=badge(detailSignal,amber,amberSoft);LinearLayout.LayoutParams dsp=new LinearLayout.LayoutParams(-2,-2);dsp.setMargins(0,dp(6),0,0);c.addView(ds,dsp);}TextView price=text(money(x.last),36,true,accent);price.setPadding(0,dp(5),0,dp(3));c.addView(price);
        DealScore.Evidence e=evaluate(x);int u=DealUrgency.score(x,e,System.currentTimeMillis());LinearLayout badges=row();badges.addView(badge("▼ "+x.declaredDiscountPct+"%",accent,accentSoft));c.addView(badges);
        TextView quickBuy=primaryButton("⚡ 바로 가격 확인");LinearLayout.LayoutParams qbp=new LinearLayout.LayoutParams(-1,dp(58));qbp.setMargins(0,dp(12),0,dp(3));c.addView(quickBuy,qbp);quickBuy.setOnClickListener(v->AppLauncher.openProduct(this,x.source,x.url));
        c.addView(text("발견 "+ago(x.firstSeen)+" · 마지막 확인 "+ago(x.lastSeen),11,false,muted));

        LinearLayout judge=panel();judge.addView(text("딜 요약",13,true,muted));String summary=(signalCount(x)>1?"폴센트 신호 "+signalCount(x)+"개 동시포착":"폴센트 급락 신호 포착")+" · "+ago(x.lastSeen)+" 확인";judge.addView(text(summary,18,true,ink));judge.addView(text("표시 할인율과 현재 가격을 먼저 보여주고, 과거 가격은 아래에서 참고용으로 확인할 수 있어요.",13,false,muted));c.addView(judge);

        long now=System.currentTimeMillis();
        LinearLayout proof=panel();proof.addView(text("가격 이력 · 참고",18,true,ink));proof.addView(text("최근 7일 비교 관측 "+e.count+"회\n관측 평균가  "+money(e.average)+"\n관측 중앙값  "+money(e.reference)+"\n이전 관측 최저가  "+money(e.low)+"\n관측 중앙값 대비 하락  "+Math.round(e.drop)+"%\n관측 기준 절약  "+(e.count>=3?money(e.saving):"데이터 부족"),14,false,muted));
        proof.addView(text("30일 최저  "+money(DealScore.low(x.observations,now,30))+"\n90일 최저  "+money(DealScore.low(x.observations,now,90))+"\n180일 최저  "+money(DealScore.low(x.observations,now,180)),14,true,ink));
        proof.addView(text("표기 할인율은 폴센트/판매 페이지 신호이고, 위 과거 가격은 DealFlash가 실제로 관측한 값만 참고용으로 보여줍니다.",12,false,muted));c.addView(proof);
        LinearLayout graph=panel();graph.addView(text("실제 가격 이력",18,true,ink));LinearLayout periods=row();TextView p30=actionChip("30일"),p90=actionChip("90일"),p180=actionChip("180일");periods.addView(p30,new LinearLayout.LayoutParams(0,dp(38),1));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,dp(38),1);pp.setMargins(dp(7),0,0,0);periods.addView(p90,pp);LinearLayout.LayoutParams pp2=new LinearLayout.LayoutParams(0,dp(38),1);pp2.setMargins(dp(7),0,0,0);periods.addView(p180,pp2);graph.addView(periods);
        PriceHistoryView chart=new PriceHistoryView(this);graph.addView(chart,new LinearLayout.LayoutParams(-1,dp(215)));chart.setPoints(DealScore.historyPoints(x.observations,now,30),"30일 가격 이력");
        View.OnClickListener periodClick=v->{int days=v==p90?90:v==p180?180:30;chart.setPoints(DealScore.historyPoints(x.observations,System.currentTimeMillis(),days),days+"일 기기 이력");if(ServerState.active(this))ServerSync.fetchHistory(this,key,days,chart);};p30.setOnClickListener(periodClick);p90.setOnClickListener(periodClick);p180.setOnClickListener(periodClick);if(ServerState.active(this))ServerSync.fetchHistory(this,key,30,chart);c.addView(graph);
        c.addView(text("최초 발견  "+date(x.firstSeen)+"\n마지막 확인  "+date(x.lastSeen)+"\n출처  "+sourceLabel(x),12,false,muted));
        TextView categoryEdit=actionChip("카테고리 수정");c.addView(categoryEdit);categoryEdit.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("이 상품의 카테고리").setItems(java.util.Arrays.copyOfRange(CATS,1,CATS.length),(d,which)->{getSharedPreferences("category_overrides",0).edit().putString(key,CATS[which+1]).apply();detail(key);}).show());
        TextView buy=actionChip("판매 페이지 다시 열기");LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(58));bp.setMargins(0,dp(7),0,dp(8));body.addView(buy,bp);buy.setOnClickListener(v->AppLauncher.openProduct(this,x.source,x.url));
    }

    private List<MarketComparison.Offer> mergeOffers(List<MarketComparison.Offer> a,List<MarketComparison.Offer> b){
        LinkedHashMap<String,MarketComparison.Offer> map=new LinkedHashMap<>();for(MarketComparison.Offer o:a)map.put((o.source==null?"":o.source)+"|"+(o.url==null?"":o.url),o);for(MarketComparison.Offer o:b)map.put((o.source==null?"":o.source)+"|"+(o.url==null?"":o.url),o);ArrayList<MarketComparison.Offer> out=new ArrayList<>(map.values());out.sort((x,y)->Double.compare(x.price,y.price));return out;
    }
    private void renderMarketRows(LinearLayout market,List<MarketComparison.Offer> offers,boolean serverTried){
        market.removeAllViews();market.addView(text("배송비 포함 판매처 비교",18,true,ink));
        if(offers==null||offers.isEmpty()){market.addView(text(serverTried?"동일 상품으로 안전하게 매칭된 타사 가격이 아직 없어요.":"타사 가격을 확인하는 중…",13,false,muted));market.addView(text("모델명·규격·수량이 확실히 맞는 상품만 합칩니다. 애매하면 비교에서 제외합니다.",12,false,muted));return;}
        for(int i=0;i<offers.size();i++){MarketComparison.Offer o=offers.get(i);LinearLayout line=row();String extra=o.rocket?" · 로켓":o.shipping>0?" · 배송 "+money(o.shipping):" · 배송비 포함";TextView name=text((i==0?"최저  ":"")+o.label+extra,13,i==0,i==0?green:ink);line.addView(name,new LinearLayout.LayoutParams(0,-2,1));TextView p=text(money(o.price),15,true,i==0?green:ink);line.addView(p);market.addView(line);if(o.confidence>0)market.addView(text("매칭 신뢰도 "+o.confidence+"% · "+(o.checkedAt>0?ago(o.checkedAt)+" 확인":"확인 중"),11,false,muted));if(i<offers.size()-1)market.addView(divider());}
        market.addView(text("배송비를 포함한 기본 실구매가 기준입니다. 카드·멤버십·개인 쿠폰은 별도로 달라질 수 있어요.",12,false,muted));
    }

    private void settings(){
        reset("설정");LinearLayout c=scrollBody();c.addView(text("가격 감시",23,true,ink));
        c.addView(text("v2.8도 수집 범위와 내 알림 기준을 분리합니다. 서버는 폴센트 4개 신호를 계속 수집하고, 60% 이상은 BIG HOT DEAL로 강조합니다. 아래 값은 알림 기준만 바꿉니다.",13,false,muted));
        LinearLayout server=panel();server.addView(text("24시간 서버 감시",17,true,ink));TextView serverText=text(ServerState.label(this),14,true,ServerState.active(this)?green:amber);server.addView(serverText);server.addView(text(ServerState.active(this)?"v2.8 서버는 5분 주기로 폴센트 신호를 수집합니다. 푸시 상태: "+(ServerState.pushReady(this)?"FCM 즉시 알림 준비됨":"기기 등록 확인 중")+". 15분 폴링은 누락 복구용입니다.":"현재 백엔드의 /api/status가 확인되지 않아 기기 보조 감시로 동작합니다.",12,false,muted));TextView refreshServer=actionChip("서버 상태 새로고침");server.addView(refreshServer);refreshServer.setOnClickListener(v->ServerSync.refreshStatus(this,()->{refreshStatus();settings();}));c.addView(server);
        LinearLayout monitor=panel();monitor.addView(text("내 알림 기준",17,true,ink));TextView threshold=text(thresholdLabel(),14,true,ink);monitor.addView(threshold);monitor.addView(text("서버 수집량에는 영향이 없습니다. 60% 이상은 항상 BIG HOT DEAL로 표시하고, 이 값 이상일 때만 알림을 울립니다. 재확인 대기로 알림을 늦추지 않습니다.",12,false,muted));
        LinearLayout presets=row();int[] presetValues={20,30,40,50,60,70};for(int pv:presetValues){TextView pc=chip(pv+"%",ScannerPrefs.threshold(this)==pv);LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(0,dp(36),1);pl.setMargins(0,dp(8),dp(5),dp(2));presets.addView(pc,pl);pc.setOnClickListener(v->{ScannerPrefs.threshold(MainActivity.this,pv);threshold.setText("할인/급락 "+pv+"% 이상이면 알림 · 서버 저장 중…");ServerSync.setAlertThreshold(MainActivity.this,pv,()->{threshold.setText(thresholdLabel());settings();});});}monitor.addView(presets);
        SeekBar seek=new SeekBar(this);seek.setMax(16);seek.setProgress(Math.max(0,Math.min(16,(ScannerPrefs.threshold(this)-10)/5)));monitor.addView(seek);c.addView(monitor);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){int value=ScannerPrefs.threshold(MainActivity.this);public void onProgressChanged(SeekBar b,int n,boolean user){if(user){value=10+n*5;ScannerPrefs.threshold(MainActivity.this,value);threshold.setText("할인/급락 "+value+"% 이상이면 알림 · 저장 전");}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){threshold.setText("할인/급락 "+value+"% 이상이면 알림 · 서버 저장 중…");ServerSync.setAlertThreshold(MainActivity.this,value,()->{threshold.setText(thresholdLabel());reload();});}});
        LinearLayout controls=row();TextView start=primaryButton("기기 보조 감시 시작"),stop=actionChip("보조 감시 중지");controls.addView(start,new LinearLayout.LayoutParams(0,dp(50),1));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(50),1);sp.setMargins(dp(8),0,0,0);controls.addView(stop,sp);c.addView(controls);start.setOnClickListener(v->start(true));stop.setOnClickListener(v->{ScannerPrefs.enabled(this,false);stopService(new Intent(this,MonitorService.class));refreshStatus();});
        TextView test=actionChip("알림 테스트");c.addView(test);test.setOnClickListener(v->NotificationHelper.test(this));
        LinearLayout diagnostic=panel();diagnostic.addView(text("수집 상태",17,true,ink));diagnostic.addView(text((ServerState.active(this)?"서버 모드\n":"기기 보조 모드\n")+ScannerPrefs.current(this)+"\n성공 "+ScannerPrefs.success(this)+" / 요청 "+ScannerPrefs.pages(this)+"\n검증 제외 "+ScannerPrefs.rejected(this)+"\n기기 마지막 시도 "+date(ScannerPrefs.lastCheck(this))+"\n서버 마지막 스캔 "+date(ServerState.lastServerCheck(this)),13,false,muted));c.addView(diagnostic);
        LinearLayout sync=panel();sync.addView(text("피드 동기화 진단",17,true,ink));sync.addView(text(SyncDiag.report(this),11,false,muted));c.addView(sync);
        c.addView(text("폴센트의 실시간 인기 · 오늘 인기 · 지금이 타이밍 · 카테고리 급락을 알림 설정과 무관하게 자동 수집합니다. 같은 상품은 하나로 합치고 신호만 중첩 표시하며, 60% 이상은 BIG HOT DEAL로 강조합니다. 가격 이력은 최대 180일 조회합니다.",12,false,muted));
    }

    private String thresholdLabel(){String state=ServerSync.thresholdSyncState(this);String suffix="saved".equals(state)?"저장 완료":"saving".equals(state)?"서버 저장 중…":"failed".equals(state)?"저장 실패 · 다시 시도":"서버와 동기화";return "할인/급락 "+ScannerPrefs.threshold(this)+"% 이상이면 알림 · "+suffix;}

    private void start(boolean manual){if(ServerState.active(this)){ServerPollJobService.schedule(this);stopService(new Intent(this,MonitorService.class));refreshStatus();if(manual)Toast.makeText(this,"서버가 감시 중이라 기기 상시 감시는 필요 없어요",Toast.LENGTH_SHORT).show();return;}if(manual)FeedCooldownStore.clear(this);ScannerPrefs.enabled(this,true);Intent i=new Intent(this,MonitorService.class);startForegroundService(i);refreshStatus();}
    private void refreshStatus(){if(status==null)return;if(ServerState.active(this)){status.setText("서버가 5분 주기로 감시 · 마지막 서버 스캔 "+ago(ServerState.lastServerCheck(this)));if(statusPill!=null){statusPill.setText("● 서버 감시");statusPill.setTextColor(green);statusPill.setBackground(shape(greenSoft,12));}return;}String state=ScannerPrefs.current(this);boolean on=ScannerPrefs.enabled(this);String msg=!on?"서버 미연결 · 기기 감시 중지":ScannerPrefs.lastCheck(this)==0?"기기에서 첫 가격 정보를 확인하고 있어요":state.startsWith("정상")?"기기 마지막 확인 "+ago(ScannerPrefs.lastCheck(this)):"기기 수집 지연 · 설정에서 상태를 확인해 주세요";status.setText(msg);if(statusPill!=null){statusPill.setText(on?"● 기기 감시":"● 중지");statusPill.setTextColor(on?green:muted);statusPill.setBackground(shape(on?greenSoft:soft,12));}}
    private void persist(){getSharedPreferences("ui_v25",0).edit().putInt("sort",sort).putInt("drop",minDrop).putInt("saving",minSaving).putString("category",category).putString("signal",signalFilter).apply();}
    private boolean hasSignal(HistoryStore.H x,String signal){if(x==null||x.signals==null||x.signals.isEmpty())return false;for(String s:x.signals.split("\\|"))if(signal.equals(s.trim()))return true;return false;}
    private int signalCount(HistoryStore.H x){if(x==null||x.signals==null||x.signals.trim().isEmpty())return 0;LinkedHashSet<String> set=new LinkedHashSet<>();for(String s:x.signals.split("\\|")){s=s.trim();if(!s.isEmpty()&&!"추천".equals(s))set.add(s);}return set.size();}
    private String signalText(HistoryStore.H x){if(x==null||x.signals==null||x.signals.trim().isEmpty())return "";LinkedHashSet<String> set=new LinkedHashSet<>();for(String s:x.signals.split("\\|")){s=s.trim();if(!s.isEmpty()&&!"추천".equals(s))set.add(s);}if(set.isEmpty())return "";StringBuilder b=new StringBuilder();if(set.size()>1)b.append("⚡ 신호 ").append(set.size()).append("중첩 · ");int i=0;for(String s:set){if(i++>0)b.append(" · ");b.append(s);}return b.toString();}

    private String sourceLabel(HistoryStore.H x){if(x.sources!=null&&x.sources.contains("+"))return "폴센트 + 쿠팡";return MarketComparison.label(x.source);}
    private String categoryOf(HistoryStore.H x){return getSharedPreferences("category_overrides",0).getString(x.key,ProductUX.category(x.title));}
    private boolean favorite(String key){return getSharedPreferences("favorites",0).getBoolean(key,false);}

    private LinearLayout scrollBody(){ScrollView sc=new ScrollView(this);sc.setFillViewport(true);LinearLayout c=column();c.setPadding(0,dp(9),0,dp(24));sc.addView(c);body.addView(sc,new LinearLayout.LayoutParams(-1,0,1));return c;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout panel(){LinearLayout l=column();l.setPadding(dp(16),dp(15),dp(16),dp(15));l.setBackground(stroked(card,20,border,1));l.setElevation(dp(1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(10),0,dp(2));l.setLayoutParams(p);return l;}
    private View divider(){View v=new View(this);v.setBackgroundColor(border);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.setMargins(0,dp(9),0,dp(9));v.setLayoutParams(p);return v;}
    private View space(int w,int h){Space s=new Space(this);s.setLayoutParams(new LinearLayout.LayoutParams(dp(w),dp(h)));return s;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private GradientDrawable stroked(int color,int radius,int stroke,int width){GradientDrawable d=shape(color,radius);d.setStroke(dp(width),stroke);return d;}
    private TextView text(String s,int size,boolean bold,int color){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);t.setPadding(0,dp(4),0,dp(4));if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private TextView pill(String s,int size,int fg,int fill,boolean stroke){TextView t=text(s,size,true,fg);t.setGravity(Gravity.CENTER);t.setPadding(dp(11),0,dp(11),0);t.setBackground(stroke?stroked(fill,12,border,1):shape(fill,12));return t;}
    private TextView chip(String s,boolean selected){TextView t=pill(s,12,selected?Color.WHITE:ink,selected?accent:card,true);if(selected)t.setBackground(shape(accent,13));return t;}
    private TextView actionChip(String s){TextView t=pill(s,12,ink,card,true);t.setMinHeight(dp(40));return t;}
    private TextView badge(String s,int fg,int fill){TextView t=text(s,11,true,fg);t.setGravity(Gravity.CENTER);t.setPadding(dp(9),dp(5),dp(9),dp(5));t.setBackground(shape(fill,10));return t;}
    private TextView primaryButton(String s){TextView t=text(s,14,true,Color.WHITE);t.setGravity(Gravity.CENTER);t.setBackground(shape(accent,16));t.setPadding(dp(14),0,dp(14),0);return t;}
    private EditText input(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);return e;}
    private int number(EditText e){try{return Math.max(0,Integer.parseInt(e.getText().toString()));}catch(Exception ex){return 0;}}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private String money(double n){return !Double.isFinite(n)||n<=0?"정보 없음":NumberFormat.getIntegerInstance(Locale.KOREA).format(Math.round(n))+"원";}
    private String ago(long t){if(t<=0)return "미확인";long m=Math.max(0,(System.currentTimeMillis()-t)/60000);return m==0?"방금":m<60?m+"분 전":m<1440?m/60+"시간 전":m/1440+"일 전";}
    private String date(long t){return t<=0?"미확인":new SimpleDateFormat("MM.dd HH:mm",Locale.KOREA).format(new Date(t));}
    @Override protected void onDestroy(){handler.removeCallbacks(tick);try{reads.shutdownNow();}catch(Exception ignored){}super.onDestroy();}
    @Override public void onBackPressed(){
        if(screen.equals("상세")){showList("급락");return;}
        if(!screen.equals("급락")){showList("급락");return;}
        long now=System.currentTimeMillis();
        if(now-backPressedAt>2000L){backPressedAt=now;Toast.makeText(this,"한 번 더 누르면 앱이 종료됩니다",Toast.LENGTH_SHORT).show();return;}
        super.onBackPressed();
    }
}
