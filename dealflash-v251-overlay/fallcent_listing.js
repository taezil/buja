(function(){
  /* Fallcent recommendation collector v2.5.1
   * Keep one product = one isolated card. Do not guess across card boundaries. */
  var RESERVED={recommend:1,ranking:1,list:1,search:1,category:1,home:1};
  var ROCKET=/(로켓배송|로켓프레시|로켓직구|판매자로켓)/;
  var PAIR=/([0-9][0-9,]{2,})\s*원\s*(?:▼|↓)\s*([0-9]{1,3})\s*%/g;

  function tx(e){return ((e&&(e.innerText||e.textContent))||'').replace(/\s+/g,' ').trim();}
  function pairs(t){var r=[],seen={},m;PAIR.lastIndex=0;
    while((m=PAIR.exec(t||''))!==null){var p=parseInt(m[1].replace(/,/g,''),10)||0,c=parseInt(m[2],10)||0;
      if(p>=100&&c>0&&c<=99&&!seen[p+'|'+c]){seen[p+'|'+c]=1;r.push({price:p,pct:c});}}
    return r;
  }
  function info(h){
    try{
      var u=new URL(h||'',location.href),host=u.hostname.toLowerCase().replace(/^www\./,'');
      if(host!=='fallcent.com')return null;
      var m=u.pathname.match(/^\/product\/(.+?)\/?$/i);if(!m)return null;
      var rest=m[1].replace(/^\/+|\/+$/g,'');if(!rest)return null;
      var first=rest.split('/')[0].toLowerCase();if(RESERVED[first])return null;
      return {id:rest,href:'https://fallcent.com/product/'+rest};
    }catch(e){return null;}
  }
  function path(e){var s=[];for(var i=0;i<5&&e;i++,e=e.parentElement){s.push(e.tagName+(e.className&&String(e.className).trim()?'.'+String(e.className).trim().split(/\s+/)[0]:''));}return s.join('<');}
  function productIds(el){var set={},arr=el.querySelectorAll?el.querySelectorAll('a[href]'):[];
    for(var i=0;i<arr.length;i++){var z=info(arr[i].href||arr[i].getAttribute('href')||'');if(z)set[z.id]=1;}
    return Object.keys(set);
  }
  function cleanTitle(t){
    t=(t||'').replace(/\s+/g,' ').trim();
    t=t.replace(/^(?:역대급\s*)?(?:타이밍\s*)?(?:(?:로켓배송|로켓프레시|로켓직구|판매자로켓)\s*)+/,'');
    t=t.replace(/\s+[0-9][0-9,]{2,}\s*원\s*(?:▼|↓)\s*[0-9]{1,3}\s*%.*$/,'').trim();
    t=t.replace(/^(?:역대급|타이밍)\s+/g,'').trim();
    return t;
  }
  function imageOf(card){var ims=card.querySelectorAll?card.querySelectorAll('img'):[],z='';
    for(var i=0;i<ims.length;i++){var im=ims[i];z=im.currentSrc||im.getAttribute('src')||im.getAttribute('data-src')||im.getAttribute('data-original')||im.getAttribute('data-lazy-src')||'';
      if(!z){var ss=im.getAttribute('srcset')||im.getAttribute('data-srcset')||'';if(ss)z=ss.split(',')[0].trim().split(' ')[0];}
      if(z&&z.indexOf('data:image')!==0)break;z='';}
    if(z.indexOf('//')===0)z='https:'+z;return z;
  }
  function coupangOf(card){var links=card.querySelectorAll?card.querySelectorAll('a[href]'):[],found={};
    for(var i=0;i<links.length;i++){try{var u=new URL(links[i].href,location.href),m=u.pathname.match(/^\/(?:vp|np|vm)\/products\/([0-9]+)\/?$/),v=u.searchParams.get('vendorItemId');
      if(/^(?:www\.|m\.)?coupang\.com$/.test(u.hostname)&&m&&/^[0-9]+$/.test(v||''))found[m[1]+':'+v]=u.href;}catch(e){}}
    var ks=Object.keys(found);return ks.length===1?found[ks[0]]:'';
  }

  var body=document.body?document.body.innerText:'';
  var blocked=/access denied|captcha|비정상적인 접근|자동입력|로봇이 아닙니다/i.test(body);
  var anchors=[].slice.call(document.querySelectorAll('a[href]'));
  var groups={},order=[],productAnchorCount=0;
  for(var i=0;i<anchors.length;i++){
    var a=anchors[i],z=info(a.href||a.getAttribute('href')||'');if(!z)continue;productAnchorCount++;
    if(!groups[z.id]){groups[z.id]={id:z.id,href:z.href,anchors:[]};order.push(z.id);}groups[z.id].anchors.push(a);
  }

  var out=[],ambiguous=0,sample='';
  for(var oi=0;oi<order.length&&out.length<240;oi++){
    var g=groups[order[oi]],best=null,bestText='';
    for(var ai=0;ai<g.anchors.length;ai++){
      var el=g.anchors[ai];
      for(var lv=0;lv<10&&el;lv++,el=el.parentElement){
        var t=tx(el);if(t.length<8)continue;if(t.length>1400)break;
        if(!ROCKET.test(t))continue;
        var ps=pairs(t);if(ps.length!==1)continue;
        var ids=productIds(el);if(ids.length!==1||ids[0]!==g.id)continue;
        if(!best||t.length<bestText.length){best=el;bestText=t;}
        break;
      }
    }
    if(!best){ambiguous++;if(!sample&&g.anchors.length)sample=path(g.anchors[0]);continue;}
    var ps=pairs(bestText);if(ps.length!==1){ambiguous++;continue;}var pair=ps[0];
    var title='';
    var cand=best.querySelectorAll('h1,h2,h3,h4,strong,[class*=title],[class*=name]');
    for(var q=0;q<cand.length;q++){var ct=cleanTitle(tx(cand[q]));if(ct.length>=4&&ct.length<=220&&!/[0-9][0-9,]{2,}\s*원/.test(ct)){title=ct;break;}}
    if(!title)title=cleanTitle(bestText);
    if(title.length<4||title.length>240){ambiguous++;continue;}
    out.push({url:g.href,id:g.id,title:title,price:pair.price,base:0,discount:pair.pct,image:imageOf(best),rocket:true,coupangUrl:coupangOf(best),embPrice:pair.price,embPct:pair.pct,weak:false});
  }

  return JSON.stringify({url:location.href,title:document.title||'',blocked:blocked,blockReason:blocked?'접근 제한':'',links:productAnchorCount,allLinks:anchors.length,items:out,ambiguous:ambiguous,path:sample,bodyHasPrices:pairs(body).length>0});
})()
