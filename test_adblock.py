"""Check network/cosmetic blocking on a real TV, then inspect the requested site."""
import http.server
import json
from pathlib import Path
import sys
import subprocess
import threading
import time
import urllib.request

base='http://192.168.2.34:8765'
def call(path,body=None):
    req=urllib.request.Request(base+path,data=json.dumps(body).encode() if body else None,headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req,timeout=6) as response:return json.load(response)
def cmd(action,**kw):return call('/command',{'action':action,**kw})
def wait(test,seconds=35):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        state=call('/state')
        if test(state):return state
        time.sleep(.3)
    raise AssertionError(state)

html='''<!doctype html><meta charset="utf-8"><title>Adblock fixture</title>
<h1 id="main-content">Keep main content</h1><a id="main-link" href="/allowed" target="_blank">Keep navigation</a>
<div class="ad-banner">Hidden banner</div><a rel="sponsored" href="https://example.com">Hidden sponsor</a>
<div id="fixed-player" style="position:fixed;bottom:0;right:0;width:200px;height:120px"><video id="main-video" style="width:160px;height:80px"></video><span aria-label="Advertisement">Hidden in-player ad</span></div>
<nav id="main-nav" style="position:fixed;top:0;right:0"><a href="/normal">Navigation</a><a rel="sponsored" href="/sponsor">Hidden sponsor in nav</a></nav>
<aside id="backdrop" style="position:fixed;inset:0;background:#0008"><section aria-label="Quảng cáo">Hidden popup</section></aside>
<img src="https://ads.doubleclick.net/lan-browser-test.png"><img src="/ads/fixture.png">
<div id="late-marker">Will become an ad</div><div class="shadow" id="download">Keep these unrelated names</div>
<iframe src="/frame" id="content-frame" style="width:200px;height:100px"></iframe>
<script>
setTimeout(()=>{const d=document.createElement('div');d.className='ad-container';d.textContent='Hidden late ad';document.body.append(d);document.getElementById('late-marker').setAttribute('aria-label','Advertisement');},1800);
const check=setInterval(()=>{
 const visible=id=>document.getElementById(id).getClientRects().length>0;
 if(window.__lanBrowserAdblock && document.getElementById('late-marker').hasAttribute('data-lan-ad-hidden')){
  document.title=['main-content','main-link','main-video','fixed-player','main-nav','download','content-frame'].every(visible)&&!visible('backdrop')?'Adblock fixture OK':'Adblock fixture BROKE CONTENT';
  clearInterval(check);
 }
},250);
</script>'''.encode('utf-8')
frame=b'''<!doctype html><h2>Keep frame content</h2><div class="ad-unit">Hidden frame ad</div>'''

class Fixture(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        script=self.path=='/adblock.js'
        body=Path('assets/adblock.js').read_bytes() if script else frame if self.path=='/frame' else html
        self.send_response(200);self.send_header('Content-Type','text/javascript' if script else 'text/html');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
    def log_message(self,*args):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',8879),Fixture)
if '--preview' in sys.argv:
    html+=b'<script src="/adblock.js"></script>'
    print('Adblock browser check: http://127.0.0.1:8879',flush=True)
    server.serve_forever()
    sys.exit()
threading.Thread(target=server.serve_forever,daemon=True).start()
subprocess.run(['adb','-s','192.168.2.34:5555','reverse','tcp:8879','tcp:8879'],check=True,capture_output=True)
try:
    for host in ['127.0.0.1','localhost']:
        before=call('/state')['adblock']['blockedRequests']
        cmd('open',url=f'http://{host}:8879')
        state=wait(lambda s:s['title']=='Adblock fixture OK' and s['adblock'].get('hidden',0)>=9 and s['adblock'].get('visible',-1)==0 and s['adblock']['blockedRequests']>=before+2)
        print('PASS:',host,'network/path ads, dynamic ads, popup backdrop, iframe; video/navigation preserved',flush=True)

finally:
    server.shutdown()
    subprocess.run(['adb','-s','192.168.2.34:5555','reverse','--remove','tcp:8879'],capture_output=True)

for page in ['https://chophim.cam/','https://chophim.cam/xem/loi-noi-doi-hoan-hao']:
    cmd('open',url=page)
    state=wait(lambda s:s['url'].startswith(page) and not s['loading'] and s['adblock'].get('hidden',0)>5 and s['adblock'].get('visible',-1)==0)
    print('PASS:',page,state['adblock'],flush=True)
    if '/xem/' in page:
        state=wait(lambda s:s.get('media',{}).get('found'),25)
        print('PASS: main video remains present; readiness =',state['media']['ready'],flush=True)
        if state['media'].get('playing'):cmd('media')
cmd('open',url='https://chophim.cam/')
