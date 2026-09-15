import json,subprocess,threading,queue,time,base64,http.server,pathlib,os,psutil
root=pathlib.Path.cwd(); cache=root/'desktop-webview-host/build/smoke-profile';cache.mkdir(parents=True,exist_ok=True)
requests=[]
class Broker(http.server.BaseHTTPRequestHandler):
 def do_POST(self):
  request=json.loads(self.rfile.read(int(self.headers['Content-Length'])));requests.append(request)
  body=b'<html><head><title>MihonW Browser Smoke</title></head><body><h1>Real JCEF</h1><script>window.answer=42;document.cookie="webview_test=success;path=/";</script></body></html>'
  output=json.dumps({'statusCode':200,'headers':{'Content-Type':'text/html; charset=utf-8'},'bodyBase64':base64.b64encode(body).decode()}).encode()
  self.send_response(200);self.end_headers();self.wfile.write(output)
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Broker);threading.Thread(target=server.serve_forever,daemon=True).start()
runtime=pathlib.Path(os.environ.get('MIHON_WEBVIEW_TEST_RUNTIME',str(root/'desktop-webview-host/build/browser-runtime')))
command=[str(runtime/'bin/java.exe'),'--add-modules=jcef','--add-exports=java.desktop/sun.awt=ALL-UNNAMED','-cp',str(root/'desktop-webview-host/build/install/desktop-webview-host/lib/*'),'mihon.webview.MainKt']
errors=open(cache/'smoke-stderr.log','w'); started=time.monotonic();process=subprocess.Popen(command,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=errors,text=True,encoding='utf-8',creationflags=subprocess.CREATE_NO_WINDOW)
events=queue.Queue()
def read():
 for line in process.stdout:
  if line.startswith('MIHON_WEBVIEW '):events.put(json.loads(line.removeprefix('MIHON_WEBVIEW ')))
threading.Thread(target=read,daemon=True).start()
def send(value):
 value.update(sessionId='smoke',sourceId=1,token='smoke-capability');process.stdin.write(json.dumps(value)+'\n');process.stdin.flush()
results=[]
try:
 send({'url':'https://smoke.test/','broker':f'http://127.0.0.1:{server.server_port}/request','cache':str(cache),'headers':{'X-Test':'header-value'},'cookies':[]})
 loaded=False
 deadline=time.monotonic()+45
 while time.monotonic()<deadline:
  event=events.get(timeout=max(1,deadline-time.monotonic()));results.append(event);print(json.dumps(event),flush=True)
  if event['type']=='error':raise RuntimeError(event['value'])
  if event['type']=='loaded' and event['value'].startswith('https://smoke.test'):
   loaded=True;break
 assert loaded
 load_ms=round((time.monotonic()-started)*1000)
 send({'type':'evaluate','id':'eval','script':'({answer:window.answer,title:document.title,cookie:document.cookie})'})
 while True:
  event=events.get(timeout=15);results.append(event);print(json.dumps(event),flush=True)
  if event['type']=='result':
   value=json.loads(event['value']);assert value['answer']==42 and 'webview_test=success' in value['cookie'];break
 send({'type':'cookies','id':'cookies'})
 while True:
  event=events.get(timeout=15);results.append(event);print(json.dumps(event),flush=True)
  if event['type']=='cookie':assert 'webview_test' in event['value'];break
 assert any(r['headers'].get('X-Test')=='header-value' for r in requests)
 memory=sum(p.memory_info().rss for p in [psutil.Process(process.pid)]+psutil.Process(process.pid).children(recursive=True))
 send({'type':'close'});process.stdin.close();process.wait(timeout=15)
 print(json.dumps({'success':True,'loadMillis':load_ms,'exitCode':process.returncode,'requests':len(requests),'rssBytes':memory}),flush=True)
 (cache/'smoke-result.json').write_text(json.dumps({'success':True,'loadMillis':load_ms,'exitCode':process.returncode,'events':results,'requests':len(requests),'rssBytes':memory},indent=2),encoding='utf-8')
finally:
 if process.poll() is None:subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],stdout=subprocess.DEVNULL)
 server.shutdown();errors.close()
