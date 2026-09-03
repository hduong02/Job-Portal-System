import json,time
from remoteops import RUN,guard
sequence=0
while not (RUN/'monitor.stop').exists():
 try:s=guard()
 except Exception as error:s={'stop_reason':'VPS resource monitoring failed: '+type(error).__name__}
 with (RUN/'server.jsonl').open('a') as f:f.write(json.dumps(s)+'\n')
 if sequence==0 or sequence%10==0 or s.get('stop_reason'):
  s['monitor_ready']=sequence==0;print(json.dumps(s),flush=True)
 sequence+=1
 time.sleep(1)
