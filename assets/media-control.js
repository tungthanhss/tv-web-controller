function (action, value) {
  const videos = [], frames = [];
  function scan(doc, outer) {
    for (const el of doc.querySelectorAll('video,audio')) {
      const r = el.getBoundingClientRect();
      videos.push({el, outer, score:(!el.paused ? 1e9 : 0) + r.width * r.height});
    }
    for (const frame of doc.querySelectorAll('iframe')) {
      const r = frame.getBoundingClientRect();
      if (r.width > 100 && r.height > 80) frames.push({el:outer || frame, score:r.width*r.height});
      try { if (frame.contentDocument) scan(frame.contentDocument, outer || frame); } catch (_) {}
    }
  }
  scan(document, null);
  videos.sort((a,b)=>b.score-a.score); frames.sort((a,b)=>b.score-a.score);
  const choice = videos[0], video = choice && choice.el;
  let problem = '';
  const stageKey = '__lanBrowserStage';
  function restore() {
    const stage = window[stageKey];
    if (stage) { for (const [el,style] of stage) { if (style === null) el.removeAttribute('style'); else el.setAttribute('style',style); } delete window[stageKey]; }
  }
  if (action === 'exit') restore();
  if (action === 'fullscreen') {
    restore();
    const target = choice ? (choice.outer || video) : frames[0] && frames[0].el;
    const saved = [], seen = new Set();
    function set(el, properties) {
      if (!seen.has(el)) { saved.push([el,el.getAttribute('style')]);seen.add(el); }
      for (const [key,val] of Object.entries(properties)) el.style.setProperty(key,val,'important');
    }
    if (target) {
      for (let el=target.parentElement;el;el=el.parentElement) set(el,{transform:'none',filter:'none',perspective:'none',contain:'none',isolation:'auto','z-index':'2147483647'});
      set(target,{position:'fixed',inset:'0',width:'100vw',height:'100vh','max-width':'none','max-height':'none',margin:'0',padding:'0',border:'0','z-index':'2147483647',background:'#000','object-fit':'contain'});
      set(document.documentElement,{overflow:'hidden',background:'#000'});
      if (choice && choice.outer) set(video,{position:'fixed',inset:'0',width:'100vw',height:'100vh',margin:'0','z-index':'2147483647',background:'#000','object-fit':'contain'});
    }
    window[stageKey] = saved;
  }
  if (action === 'toggle' && video) {
    if (video.paused) { video.play().catch(e=>{ window.__lanBrowserMediaError=e.message; }); }
    else video.pause();
  }
  if (action === 'seek' && video && (video.seekable.length || Number.isFinite(video.duration))) {
    const start=video.seekable.length?video.seekable.start(0):0,end=video.seekable.length?video.seekable.end(video.seekable.length-1):video.duration;
    video.currentTime = Math.max(start, Math.min(end,video.currentTime+Number(value || 0)));
  }
  if (action === 'mute' && video) video.muted=!video.muted;
  if (video && video.error) problem = 'Không phát được video (mã ' + video.error.code + '). Nguồn, định dạng hoặc codec có thể không được hỗ trợ.';
  return {found:Boolean(video),embedded:frames.length>0,playing:Boolean(video && !video.paused && !video.ended),time:video && Number.isFinite(video.currentTime)?video.currentTime:0,duration:video && Number.isFinite(video.duration)?video.duration:0,ready:video?video.readyState:0,muted:Boolean(video && video.muted),fullscreen:Boolean(window[stageKey]),error:problem || window.__lanBrowserMediaError || document.documentElement.dataset.lanPlayerError || ''};
}
