"""Real-TV playback check: python test_video.py [--streams].

CC0 MP4/WebM samples from MDN, local fixtures via ADB reverse.
--streams additionally checks public HLS (Mux) and DASH (DASH-IF) samples.
"""
import argparse
import functools
import http.server
import json
import re
from pathlib import Path
import subprocess
import threading
import time
import urllib.request

p=argparse.ArgumentParser()
p.add_argument('--ip',default='192.168.2.34')
p.add_argument('--streams',action='store_true')
a=p.parse_args()
base=f'http://{a.ip}:8765'
serial=f'{a.ip}:5555'

def call(path,body=None):
    request=urllib.request.Request(base+path,data=json.dumps(body).encode() if body else None,headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(request,timeout=6) as response:return json.load(response)

def cmd(action,**kw):return call('/command',{'action':action,**kw})

def wait(test,seconds=35):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        try:state=call('/state')
        except OSError:time.sleep(.3);continue
        if test(state):return state
        time.sleep(.3)
    raise AssertionError(state)

directory=Path('build/test-media').resolve()
directory.mkdir(parents=True,exist_ok=True)
for ext in ['mp4','webm']:
    file=directory/f'flower.{ext}'
    if not file.exists():
        with urllib.request.urlopen(f'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.{ext}',timeout=20) as response:file.write_bytes(response.read())
(directory/'embedded.html').write_text('''<!doctype html><meta name="viewport" content="width=device-width"><title>Embedded video test</title><style>body{margin:0;background:#182131}button{position:absolute;top:0;left:0;width:160px;height:70px;z-index:3}video{width:70vw;height:70vh;margin:10vh 10vw}</style><video id="v" src="flower.mp4" loop controls></video><button onclick="v.requestFullscreen()">Fullscreen</button>''',encoding='utf-8')
(directory/'iframe.html').write_text('<!doctype html><title>Iframe video test</title><iframe src="embedded.html" style="width:80vw;height:80vh;border:0" allowfullscreen></iframe>',encoding='utf-8')

class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self,*args):pass
    def do_GET(self):
        file=Path(self.translate_path(self.path))
        if file.suffix not in ['.mp4','.webm'] or not file.is_file():return super().do_GET()
        size=file.stat().st_size
        match=re.fullmatch(r'bytes=(\d+)-(\d*)',self.headers.get('Range',''))
        start=int(match[1]) if match else 0
        end=min(size-1,int(match[2])) if match and match[2] else size-1
        self.send_response(206 if match else 200)
        self.send_header('Content-Type',self.guess_type(str(file)))
        self.send_header('Accept-Ranges','bytes')
        self.send_header('Content-Length',str(end-start+1))
        if match:self.send_header('Content-Range',f'bytes {start}-{end}/{size}')
        self.end_headers()
        with file.open('rb') as stream:
            stream.seek(start);self.wfile.write(stream.read(end-start+1))

server=http.server.ThreadingHTTPServer(('127.0.0.1',8878),functools.partial(Quiet,directory=str(directory)))
threading.Thread(target=server.serve_forever,daemon=True).start()
subprocess.run(['adb','-s',serial,'reverse','tcp:8878','tcp:8878'],check=True,capture_output=True)
try:
    wait(lambda s:'title' in s)
    for ext in ['mp4','webm']:
        url=f'http://127.0.0.1:8878/flower.{ext}'
        cmd('open',url=url)
        state=wait(lambda s:s['url']==url and not s['loading'] and s.get('media',{}).get('ready',0)>=2)
        if not state['media']['playing']:cmd('media')
        wait(lambda s:s.get('media',{}).get('time',0)>.3)
        cmd('media');wait(lambda s:not s.get('media',{}).get('playing',True))
        cmd('seek',seconds=-3600);wait(lambda s:s.get('media',{}).get('time',99)<.3)
        cmd('seek',seconds=2);wait(lambda s:s.get('media',{}).get('time',0)>1.5)
        cmd('mute');wait(lambda s:s.get('media',{}).get('muted'))
        cmd('fullscreen');wait(lambda s:s['fullscreen'])
        cmd('fullscreen');wait(lambda s:not s['fullscreen'])
        print(f'PASS: {ext.upper()} direct playback, pause, seek, mute, fullscreen and exit',flush=True)
    for page in ['embedded.html','iframe.html']:
        url=f'http://127.0.0.1:8878/{page}'
        cmd('open',url=url)
        wait(lambda s:s['url']==url and not s['loading'] and s.get('media',{}).get('ready',0)>=2)
        cmd('media');wait(lambda s:s.get('media',{}).get('playing') and s['media']['time']>.2)
        cmd('fullscreen');wait(lambda s:s['fullscreen'])
        cmd('back');wait(lambda s:not s['fullscreen'])
        print(f'PASS: {page} playback, fullscreen and Back exit',flush=True)
    cmd('open',url='http://127.0.0.1:8878/embedded.html')
    wait(lambda s:s['url'].endswith('/embedded.html') and not s['loading'] and s.get('media',{}).get('ready',0)>=2)
    cmd('move',dx=-2000,dy=-2000);time.sleep(.2)
    cmd('move',dx=40,dy=40);time.sleep(.2)
    cmd('click');wait(lambda s:s['nativeFullscreen'])
    cmd('fullscreen');wait(lambda s:not s['fullscreen'])
    print('PASS: website native HTML5 fullscreen callback and phone exit',flush=True)
    if a.streams:
        for label,url in [('HLS','https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8'),('DASH','https://dash.akamaized.net/envivio/EnvivioDash3/manifest.mpd')]:
            cmd('open',url=url)
            state=wait(lambda s:s['url']==url and not s['loading'] and s.get('media',{}).get('ready',0)>=2,55)
            if not state['media']['playing']:cmd('media')
            wait(lambda s:s.get('media',{}).get('playing') and s['media']['time']>1,55)
            cmd('fullscreen');wait(lambda s:s['fullscreen'])
            print(f'PASS: {label} streaming playback and fullscreen',flush=True)
finally:
    cmd('home')
    subprocess.run(['adb','-s',serial,'reverse','--remove','tcp:8878'],capture_output=True)
    server.shutdown()
