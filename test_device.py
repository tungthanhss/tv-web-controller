"""End-to-end check on the connected TV: python test_device.py.

Uses a temporary localhost fixture forwarded by ADB; no external website is modified.
"""
import argparse
import http.server
import json
import subprocess
import threading
import time
import urllib.error
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--ip', default='192.168.2.34')
args = parser.parse_args()
base = f'http://{args.ip}:8765'

def request(path, body=None, extra=None, expected=200):
    headers = {'Content-Type': 'application/json'}
    headers.update(extra or {})
    req = urllib.request.Request(base + path, data=None if body is None else json.dumps(body).encode(), headers=headers)
    try:
        response = urllib.request.urlopen(req, timeout=6)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        result = response.read()
        assert response.status == expected, (path, response.status, result)
        return json.loads(result) if 'application/json' in response.headers['Content-Type'] else result

def command(action, **kw):
    return request('/command', {'action': action, **kw})

def wait_for(predicate):
    end = time.monotonic() + 12
    while time.monotonic() < end:
        state = request('/state')
        if predicate(state):
            return state
        time.sleep(.15)
    raise AssertionError(state)

fixture = b'''<!doctype html><meta name="viewport" content="width=device-width"><title>Fixture</title>
<style>body{margin:0;height:4000px;background:#fff}input{position:absolute;top:0;left:0;width:98%;height:40vh;font-size:40px}button{position:absolute;top:45vh;left:0;width:100%;height:40vh;font-size:40px}</style>
<input autofocus aria-label="Test input" oninput="document.title=this.value"><button onclick="document.title='clicked'">Test click</button>
<script>onscroll=()=>{if(scrollY>10)document.title='scrolled'};</script>'''

class Fixture(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.send_header('Content-Length', str(len(fixture)))
        self.end_headers()
        self.wfile.write(fixture)
    def log_message(self, *args):
        pass

assert 'title' in request('/state')
request('/command', {'action': 'home'})
request('/command', {'action': 'home'}, extra={'Origin': 'http://foreign.invalid'}, expected=403)
request('/../AndroidManifest.xml', expected=404)
request('/pair', {'pin': '000000'}, expected=404)
request('/command', {'action': 'open', 'url': 'javascript:alert(1)'}, expected=400)
request('/command', {'action': 'unknown'}, expected=400)
print('PASS: direct access without credentials, origin, path and command validation')
server = http.server.ThreadingHTTPServer(('127.0.0.1', 8877), Fixture)
threading.Thread(target=server.serve_forever, daemon=True).start()
serial = f'{args.ip}:5555'
subprocess.run(['adb', '-s', serial, 'reverse', 'tcp:8877', 'tcp:8877'], check=True, capture_output=True)
try:
    command('open', url='http://127.0.0.1:8877')
    wait_for(lambda s: s['title'] == 'Fixture' and not s['loading'])
    # Reset the pointer to the top-left, then enter the large input field.
    command('move', dx=-2000, dy=-2000)
    time.sleep(.2)
    command('move', dx=150, dy=120)
    time.sleep(.2)
    command('click')
    time.sleep(.3)
    command('text', text='Xin chào Việt Nam — 你好')
    wait_for(lambda s: s['title'] == 'Xin chào Việt Nam — 你好')
    print('PASS: URL navigation, pointer click and Unicode text input')
    # Hide the software keyboard before testing the lower button.
    command('key', key='enter')
    subprocess.run(['adb', '-s', serial, 'shell', 'input', 'keyevent', '111'], check=True, capture_output=True)
    time.sleep(.3)
    # Device is 1080p; button covers 45-85% of WebView height.
    command('move', dx=0, dy=500)
    time.sleep(.2)
    command('click')
    wait_for(lambda s: s['title'] == 'clicked')
    command('scroll', dy=600)
    wait_for(lambda s: s['title'] == 'scrolled')
    print('PASS: pointer movement, button click and scrolling')
    command('open', url='https://example.com')
    wait_for(lambda s: s['title'] == 'Example Domain' and not s['loading'])
    command('back')
    wait_for(lambda s: '127.0.0.1' in s['url'])
    command('forward')
    wait_for(lambda s: s['title'] == 'Example Domain')
    command('reload')
    wait_for(lambda s: s['title'] == 'Example Domain' and not s['loading'])
    print('PASS: public HTTPS website, back, forward and reload')
finally:
    command('home')
    subprocess.run(['adb', '-s', serial, 'reverse', '--remove', 'tcp:8877'], capture_output=True)
    server.shutdown()
