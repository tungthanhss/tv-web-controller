(() => {
  const key='__lanBrowserAdblock';
  if (window[key]) return window[key].state();
  const selectors=[
    'a[rel~="sponsored"]','.adsbygoogle','[data-ad-slot]','[data-ad-client]','[data-ad-unit]','[data-ad-placement]',
    '.ad','.ads','.advert','.advertisement','.advertising','.ad-banner','.banner-ad','.ad-container','.ad-wrapper',
    '.ad-slot','.ad-unit','.ad-box','.ad-placement','.ad-overlay','.ads-overlay','.ad-popup','.popup-ad','.popunder',
    '[id^="ad-"]','[id^="ad_"]','[id^="ads-"]','[id^="ads_"]','[id^="div-gpt-ad"]','[id^="google_ads_"]',
    'iframe[src*="doubleclick.net/"]','iframe[src*="googlesyndication.com/"]','img[src*="/ads/"]'
  ].join(',');
  let hidden=0;
  const docs=new Set();
  function hide(el) { if(!el.hasAttribute('data-lan-ad-hidden')) {el.setAttribute('data-lan-ad-hidden','');hidden++;} }
  function candidates(doc) {
    return [...new Set([
      ...doc.querySelectorAll(selectors),
      ...[...doc.querySelectorAll('[aria-label]')].filter(el=>/quang cao|quảng cáo|\badvertisement\b|\bsponsored\b/i.test(el.getAttribute('aria-label')||''))
    ])];
  }
  function hasContent(el) {
    if(el.matches('video,audio,iframe,main,article,nav,header,form,[role="navigation"]'))return true;
    return [...el.querySelectorAll('video,audio,iframe,main,article,nav,header,form,[role="navigation"]')]
      .some(child=>!child.closest('[data-lan-ad-hidden]'));
  }
  function clean(doc) {
    if(!doc.documentElement)return;
    if(!doc.getElementById('lan-adblock-style')) {
      const style=doc.createElement('style');style.id='lan-adblock-style';
      style.textContent='[data-lan-ad-hidden],'+selectors+'{display:none!important;visibility:hidden!important;pointer-events:none!important}';
      doc.documentElement.appendChild(style);
    }
    let overlayHidden=false;
    for(const el of candidates(doc)) {
      hide(el);
      // Remove an ad backdrop, but preserve fixed navigation and video players containing an ad.
      for(let parent=el.parentElement,depth=0;parent && parent!==doc.body && depth<5;parent=parent.parentElement,depth++) {
        if(doc.defaultView.getComputedStyle(parent).position==='fixed') {
          if(!hasContent(parent)){hide(parent);overlayHidden=true;}
          break;
        }
      }
      const section=el.closest('section');
      if(section && section.children.length && [...section.children].every(child=>child.hasAttribute('data-lan-ad-hidden')))hide(section);
    }
    if(overlayHidden && doc.body && doc.body.style.overflow==='hidden') {
      const dialog=[...doc.querySelectorAll('[role="dialog"],dialog[open],[aria-modal="true"]')].some(el=>el.getClientRects().length);
      if(!dialog)doc.body.style.removeProperty('overflow');
    }
    for(const frame of doc.querySelectorAll('iframe')) {try{if(frame.contentDocument)attach(frame.contentDocument);}catch(_){} }
  }
  function attach(doc) {
    clean(doc);
    if(docs.has(doc))return;
    docs.add(doc);
    let pending=false;
    new MutationObserver(()=>{if(!pending){pending=true;setTimeout(()=>{pending=false;clean(doc);},80);}})
      .observe(doc,{childList:true,subtree:true,attributes:true,attributeFilter:['class','id','aria-label','src','rel']});
    doc.addEventListener('load',event=>{if(event.target.tagName==='IFRAME')clean(doc);},true);
    doc.addEventListener('click',event=>{
      const link=event.target.closest && event.target.closest('a');
      if(!link)return;
      if(link.matches(selectors)||link.closest('[data-lan-ad-hidden]')){event.preventDefault();event.stopImmediatePropagation();return;}
      if(link.target==='_blank' && link.origin===doc.location.origin)link.target='_self';
    },true);
  }
  window.open=()=>null;
  attach(document);
  window[key]={state:()=>{
    clean(document);
    let visible=0;
    for(const doc of docs) {
      try {
        if(doc!==document && !doc.defaultView.frameElement?.isConnected){docs.delete(doc);continue;}
        visible+=candidates(doc).filter(el=>el.getClientRects().length && doc.defaultView.getComputedStyle(el).visibility!=='hidden').length;
      }catch(_){docs.delete(doc);}
    }
    return {hidden,visible};
  }};
  return window[key].state();
})()
