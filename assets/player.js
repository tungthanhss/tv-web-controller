const video=document.getElementById('video'),message=document.getElementById('message');
const source=new URLSearchParams(location.search).get('src')||'';
function fail(text) { message.textContent=text; document.documentElement.dataset.lanPlayerError=text; }
function clearMessage() { message.textContent='';delete document.documentElement.dataset.lanPlayerError; }
try {
  const url=new URL(source);
  if (!['http:','https:'].includes(url.protocol)) throw new Error('Chỉ hỗ trợ video HTTP/HTTPS.');
  const ext=url.pathname.toLowerCase();
  if (ext.endsWith('.m3u8') && Hls.isSupported()) {
    const hls=new Hls({enableWorker:false});hls.loadSource(url.href);hls.attachMedia(video);
    hls.on(Hls.Events.ERROR,(_,data)=>{if(data.fatal)fail('Không tải được luồng HLS. Kiểm tra đường dẫn, quyền truy cập hoặc CORS của nguồn.');});
  } else if (ext.endsWith('.mpd')) {
    const dash=dashjs.MediaPlayer().create();dash.initialize(video,url.href,false);
    dash.on(dashjs.MediaPlayer.events.ERROR,()=>fail('Không tải được luồng DASH. Kiểm tra nguồn, CORS hoặc DRM.'));
  } else video.src=url.href;
  video.addEventListener('loadeddata',clearMessage);
  video.addEventListener('playing',clearMessage);
  video.addEventListener('error',()=>fail('Không phát được video. Kiểm tra định dạng, bộ giải mã hoặc quyền truy cập nguồn.'));
  video.addEventListener('waiting',()=>{message.textContent='Đang tải video…';});
  video.addEventListener('canplay',clearMessage);
  video.play().catch(()=>{message.textContent='Nhấn Phát trên điện thoại hoặc tivi để bắt đầu.';});
} catch (e) { fail(e.message); }
