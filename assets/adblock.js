(() => {
  const key='__lanBrowserAdblock';
  if (window[key]) return window[key].state();
  const selectors='a[rel~="sponsored"],.adsbygoogle,[data-ad-slot],[data-ad-client],.ad-banner,.ad-container,.advertisement,iframe[id^="google_ads_"]';
  const site=/(^|\.)chophim\.(cam|lat)$/i.test(location.hostname);
  let hidden=0;
  const docs=new WeakSet();
  function hide(el) { if(!el.hasAttribute('data-lan-ad-hidden')) {el.setAttribute('data-lan-ad-hidden','');hidden++;} }
  function candidates(doc) {
    const found=[...doc.querySelectorAll(selectors)];
    if(site) {
      found.push(...[...doc.querySelectorAll('[aria-label]')].filter(el=>/quang cao|quảng cáo/i.test(el.getAttribute('aria-label')||'')));
      found.push(...doc.querySelectorAll('img[src^="/ads/"],img[src*="chophim.cam/ads/"]'));
    }
    return found;
  }
  function clean(doc) {
    if(!doc.documentElement) return;
    if(!doc.getElementById('lan-adblock-style')) {
      const style=doc.createElement('style');style.id='lan-adblock-style';
      style.textContent='[data-lan-ad-hidden],'+selectors+'{display:none!important;visibility:hidden!important;pointer-events:none!important}';
      doc.documentElement.appendChild(style);
    }
    for(const el of candidates(doc)) {
      hide(el);
      if(site) {
        // Hide the popup's backdrop as well as its ad content, without touching the player.
        for(let parent=el.parentElement,depth=0;parent && parent!==doc.body && depth<5;parent=parent.parentElement,depth++) {
          if(doc.defaultView.getComputedStyle(parent).position==='fixed') {hide(parent);break;}
        }
        const section=el.closest('section');
        if(section && section.children.length && [...section.children].every(child=>child.hasAttribute('data-lan-ad-hidden'))) hide(section);
      }
    }
    for(const frame of doc.querySelectorAll('iframe')) {try{if(frame.contentDocument)attach(frame.contentDocument);}catch(_){} }
  }
  function attach(doc) {
    clean(doc);
    if(docs.has(doc))return;
    docs.add(doc);
    let pending=false;
    new MutationObserver(()=>{if(!pending){pending=true;setTimeout(()=>{pending=false;clean(doc);},80);}}).observe(doc,{childList:true,subtree:true});
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
    return {hidden,visible:candidates(document).filter(el=>el.getClientRects().length && getComputedStyle(el).visibility!=='hidden').length};
  }};
  return window[key].state();
})()
