const $ = id => document.getElementById(id);
let feedbackTimer;
function notice(message) { $('feedback').textContent = message; clearTimeout(feedbackTimer); feedbackTimer = setTimeout(() => $('feedback').textContent = '', 5000); }
async function request(path, body) {
  const response = await fetch(path, {method:body ? 'POST' : 'GET', headers:{'Content-Type':'application/json'}, body:body ? JSON.stringify(body) : undefined, signal:AbortSignal.timeout(5000)});
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || 'Không thực hiện được yêu cầu');
  return data;
}
async function command(data) { try { await request('/command', data); } catch (e) { notice(e.message); } }
function normalizeUrl(input) {
  const value = input.trim();
  if (/^https?:\/\//i.test(value)) return new URL(value).href;
  if (/^[a-z][a-z\d+.-]*:/i.test(value) && !/^[\w.-]+:\d+(\/|$)/.test(value)) throw new Error('Chỉ hỗ trợ địa chỉ http hoặc https');
  if (!/\s/.test(value) && (value.includes('.') || value === 'localhost' || value.startsWith('localhost:'))) return new URL('https://' + value).href;
  return 'https://www.google.com/search?q=' + encodeURIComponent(value);
}
$('url-form').addEventListener('submit', e => { e.preventDefault(); try { command({action:'open',url:normalizeUrl($('url').value)}); } catch(error) { notice(error.message); } });
$('text-form').addEventListener('submit', async e => { e.preventDefault(); await command({action:'text',text:$('text').value}); });
document.querySelectorAll('[data-action]').forEach(button => button.addEventListener('click', () => {
  const data = {action:button.dataset.action}; if (button.dataset.key) data.key = button.dataset.key; if (button.dataset.dy) data.dy = Number(button.dataset.dy); if (button.dataset.seconds) data.seconds = Number(button.dataset.seconds); command(data);
}));
const pad = $('touchpad');
let gesture, deltaX=0, deltaY=0, sending=false;
async function flushMove() {
  if (sending || (!deltaX && !deltaY)) return;
  sending = true; const dx=deltaX,dy=deltaY;deltaX=deltaY=0;
  await command({action:'move',dx,dy});sending=false;if(deltaX || deltaY) flushMove();
}
pad.addEventListener('pointerdown', e => { if (gesture) return; pad.setPointerCapture(e.pointerId); gesture={id:e.pointerId,x:e.clientX,y:e.clientY,start:performance.now(),distance:0}; });
pad.addEventListener('pointermove', e => {
  if (!gesture || gesture.id!==e.pointerId) return;
  const dx=e.clientX-gesture.x,dy=e.clientY-gesture.y; gesture.distance+=Math.abs(dx)+Math.abs(dy);gesture.x=e.clientX;gesture.y=e.clientY;
  deltaX+=dx*2.5;deltaY+=dy*2.5;flushMove();
});
pad.addEventListener('pointerup', async e => {
  if (!gesture || gesture.id!==e.pointerId) return;
  if (gesture.distance<8 && performance.now()-gesture.start<500) command({action:'click'});gesture=null;
});
pad.addEventListener('pointercancel', () => { gesture=null; });
pad.addEventListener('keydown', e => {
  const keys={ArrowUp:[0,-30],ArrowDown:[0,30],ArrowLeft:[-30,0],ArrowRight:[30,0]};
  if(keys[e.key]) { e.preventDefault();command({action:'move',dx:keys[e.key][0],dy:keys[e.key][1]}); }
  if(e.key==='Enter'||e.key===' ') { e.preventDefault();command({action:'click'}); }
});
async function state() {
  if (document.hidden) return;
  try {
    const data=await request('/state');$('status').className='connected';$('page-title').textContent=data.title||'LAN Browser';$('page-url').textContent=data.url;
    $('status').textContent=data.loading?'Đang tải trang…':'Đã kết nối'; if(data.error) $('page-url').textContent=data.error;
    const media=data.media||{};
    const clock=seconds=>`${Math.floor(seconds/60)}:${String(Math.floor(seconds%60)).padStart(2,'0')}`;
    $('media-info').textContent=media.error || (media.found ? `${media.playing?'Đang phát':'Đã dừng'} · ${clock(media.time||0)}${media.duration?' / '+clock(media.duration):' · Trực tiếp / chưa có thời lượng'}` : media.embedded?'Video nhúng · Dùng nút trên trình phát nếu phím điện thoại không phản hồi.':'Chưa phát hiện video trên trang.');
    $('play-button').textContent=media.found?(media.playing?'Tạm dừng':'Phát video'):'Phát / Dừng';
    $('fullscreen-button').textContent=data.fullscreen?'Thoát toàn màn hình':'Toàn màn hình';
    $('fullscreen-button').setAttribute('aria-pressed',String(Boolean(data.fullscreen)));
    $('mute-button').textContent=media.muted?'Bật tiếng':'Tắt tiếng';
    $('mute-button').setAttribute('aria-pressed',String(Boolean(media.muted)));

  } catch (e) { $('status').textContent='Mất kết nối · đang thử lại';$('status').className=''; }
}
state();setInterval(state,2000);
