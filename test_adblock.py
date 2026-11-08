"""Check network/cosmetic blocking on a real TV, then inspect the requested site."""
import http.server
import json
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

html=b'''<!doctype html><title>Adblock fixture</title>
<h1>Keep main content</h1><a href="/allowed" target="_blank">Keep navigation</a>
<div class="ad-banner">Hidden banner</div><a rel="sponsored" href="https://example.com">Hidden sponsor</a>
<img src="https://ads.doubleclick.net/lan-browser-test.png">
<script>setTimeout(()=>{const d=document.createElement('div');d.className='ad-container';d.textContent='Hidden late ad';document.body.append(d)},1800)</script>'''
class Fixture(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200);self.send_header('Content-Type','text/html');self.send_header('Content-Length',str(len(html)));self.end_headers();self.wfile.write(html)
    def log_message(self,*args):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',8879),Fixture)
threading.Thread(target=server.serve_forever,daemon=True).start()
subprocess.run(['adb','-s','192.168.2.34:5555','reverse','tcp:8879','tcp:8879'],check=True,capture_output=True)
try:
    before=call('/state')['adblock']['blockedRequests']
    cmd('open',url='http://127.0.0.1:8879')
    state=wait(lambda s:s['title']=='Adblock fixture' and s['adblock'].get('hidden',0)>=3 and s['adblock'].get('visible',-1)==0 and s['adblock']['blockedRequests']>before)
    print('PASS: ad network request, sponsor/banner hiding and late inserted ad',flush=True)
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
