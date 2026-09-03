import json,os,re,subprocess,sys,time,datetime as dt,urllib.request
from pathlib import Path
os.umask(0o077)
RUN=Path('/root/job-portal-shortlist-loadtest-20261007/ramping-vus-clean-mailhog-20261009')
ROOT=RUN.parent
NAMES=['job-portal-gateway','job-portal-application-service','job-portal-notification-service','job-portal-mailhog','job-portal-user-service','job-portal-applicationdb','job-portal-userdb']
def command(a,timeout=45):
 p=subprocess.run(a,text=True,capture_output=True,timeout=timeout)
 if p.returncode:raise RuntimeError(a[0]+' failed; raw output withheld')
 return p.stdout
def inspect():return json.loads(command(['docker','inspect']+NAMES))
def safe(i):return {'id':i['Id'],'image':i['Config']['Image'],'image_id':i['Image'],'running':i['State']['Running'],'oom_killed':i['State']['OOMKilled'],'restart_count':i['RestartCount'],'started_at':i['State']['StartedAt'],'memory_limit_bytes':i['HostConfig']['Memory'],'memory_reservation_bytes':i['HostConfig']['MemoryReservation'],'cpu_nanocpus':i['HostConfig']['NanoCpus']}
def auth():
 c=json.loads((ROOT/'employer-credentials.json').read_text())
 r=urllib.request.Request('http://127.0.0.1:5000/auth/login',data=json.dumps(c).encode(),headers={'Content-Type':'application/json'})
 with urllib.request.urlopen(r,timeout=10) as response:jwt=json.load(response)['jwt']
 r=urllib.request.Request('http://127.0.0.1:5000/api/applications/50001',headers={'Authorization':'Bearer '+jwt})
 with urllib.request.urlopen(r,timeout=10) as response:assert response.status==200 and json.load(response)['id']==50001
 return jwt
SQL=['docker','exec','job-portal-applicationdb','psql','-X','-v','ON_ERROR_STOP=1','-U','postgres','-d','job_portal_application','-At','-c']
def counts():return {s:int(n) for s,n in [line.split('|') for line in command(SQL+['SELECT status,count(*) FROM applications WHERE company_id=100001 GROUP BY status']).splitlines()]}
def guard():
 sample={'at_utc':dt.datetime.now(dt.timezone.utc).isoformat(),'available_memory_mb':round(next(int(s.split()[1])/1024 for s in Path('/proc/meminfo').read_text().splitlines() if s.startswith('MemAvailable:')),1),'services':{}}
 if sample['available_memory_mb']<1536:sample['stop_reason']='VPS available memory below 1536 MiB'
 baseline=json.loads((RUN/'service-baseline.json').read_text()) if (RUN/'service-baseline.json').exists() else None
 for i in inspect():
  name=i['Name'].lstrip('/');proc=Path('/proc')/str(i['State']['Pid'])
  if not i['State']['Running'] or i['State']['OOMKilled']:
   sample['stop_reason']='Service stopped or Docker OOM: '+name;continue
  if baseline and (i['Id']!=baseline[name]['id'] or i['State']['StartedAt']!=baseline[name]['started_at'] or i['RestartCount']!=baseline[name]['restart_count']):sample['stop_reason']='Service lifecycle changed: '+name
  cg=Path('/sys/fs/cgroup'+(proc/'cgroup').read_text().split('0::',1)[1].strip())
  memory=int((cg/'memory.current').read_text());limit=i['HostConfig']['Memory']
  rss=next(int(s.split()[1])/1024 for s in (proc/'status').read_text().splitlines() if s.startswith('VmRSS:'))
  sample['services'][name]={'memory_mib':round(memory/2**20,1),'rss_mib':round(rss,1),'cpu_stat':{s.split()[0]:int(s.split()[1]) for s in (cg/'cpu.stat').read_text().splitlines()},'running':i['State']['Running'],'oom_killed':i['State']['OOMKilled'],'cpu_max':(cg/'cpu.max').read_text().strip()}
  if limit and memory>limit*.97:sample['stop_reason']='Service memory above 97 percent of limit: '+name
  if name=='job-portal-mailhog' and rss>768:sample['stop_reason']='MailHog RSS above 768 MiB'
 return sample

def snapshot():
 s=guard()
 with urllib.request.urlopen('http://127.0.0.1:8025/api/v2/messages?start=0&limit=1',timeout=10) as response:s['mailhog_total']=json.load(response)['total']
 group=command(['docker','exec','job-portal-kafka-dev','env','KAFKA_HEAP_OPTS=-Xmx128m','/opt/kafka/bin/kafka-consumer-groups.sh','--bootstrap-server','kafka:9092','--group','notification-service','--describe'])
 rows=[]
 for line in group.splitlines():
  f=line.split()
  if len(f)>=6 and f[0]=='notification-service' and f[1]=='application.status.changed':
   assert f[3].isdigit() and f[4].isdigit() and f[5].isdigit(),'Kafka offsets unavailable'
   rows.append({'partition':int(f[2]),'committed':int(f[3]),'end':int(f[4]),'lag':int(f[5])})
 assert rows,'Kafka notification group absent'
 s.update(kafka=rows,published_total=sum(r['end'] for r in rows),lag=sum(r['lag'] for r in rows),database_status_counts=counts())
 s['vps_k6_running']=subprocess.run(['pgrep','-x','k6'],stdout=subprocess.DEVNULL).returncode==0
 return s

def save(name,data): (RUN/name).write_text(json.dumps(data,indent=2))
if __name__=='__main__':
 action=sys.argv[1]
 if action=='prepare':
  assert not (RUN/'preparation.json').exists(),'Preserve previous run'
  assert subprocess.run(['pgrep','-x','k6'],stdout=subprocess.DEVNULL).returncode!=0
  items=inspect();g,a=items[:2]
  assert g['HostConfig']['Memory']==512*2**20 and g['HostConfig']['NanoCpus']==500000000
  assert g['Config']['Image']=='local/job-portal-gateway:pool10-total50-20261009'
  assert a['HostConfig']['Memory']==1024*2**20 and a['HostConfig']['NanoCpus']==1000000000
  assert all(i['State']['Running'] and not i['State']['OOMKilled'] for i in items)
  save('service-baseline.json',{i['Name'].lstrip('/'):safe(i) for i in items})
  before=snapshot();assert before['lag']==0,'Previous notification backlog remains'
  with urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:8025/api/v1/messages',method='DELETE'),timeout=30) as response:delete_status=response.status
  with urllib.request.urlopen('http://127.0.0.1:8025/api/v2/messages?start=0&limit=1',timeout=10) as response:remaining=json.load(response)['total']
  assert remaining==0,'Mailbox cleanup failed'
  time.sleep(3)
  after=guard();restarted=False
  if after['services']['job-portal-mailhog']['rss_mib']>768:
   command(['docker','restart','job-portal-mailhog'],timeout=60);restarted=True;time.sleep(5)
   save('service-baseline.json',{i['Name'].lstrip('/'):safe(i) for i in inspect()})
  save('mailhog-cleanup.json',{'messages_before':before['mailhog_total'],'delete_http_status':delete_status,'messages_remaining':remaining,'mailhog_restarted_to_release_memory':restarted,'rss_before_mib':before['services']['job-portal-mailhog']['rss_mib'],'rss_after_mib':guard()['services']['job-portal-mailhog']['rss_mib']})
  baseline=snapshot();assert baseline['mailhog_total']==0,'Mailbox not empty at baseline';assert not baseline.get('stop_reason') and baseline['available_memory_mb']>=1920,'Insufficient baseline headroom'
  assert baseline['lag']==0,'Previous notification backlog remains'
  jwt=auth()
  ids=json.loads(command(SQL+["SELECT json_agg(id) FROM (SELECT id FROM applications WHERE company_id=100001 AND status='PENDING' ORDER BY md5(id::text || 'ramping-vus-clean-mailhog-20261009') LIMIT 90000) t"]))
  assert ids and len(ids)==90000,'Insufficient pending test fixtures'
  save('application-ids.json',ids);save('load-config.json',{'jwt':jwt,'basePort':19250,'tunnelCount':4})
  save('baseline.json',baseline);save('preparation.json',{'executor':'ramping-vus','vu_steps':[5,10,20,40,80,160,320],'ramp_seconds':10,'hold_seconds':30,'duration_seconds':280,'vus':320,'timeout_seconds':5,'generator':'Windows PC-local k6','status_resets':False,'selected_pending_fixtures':len(ids),'resource_guards':{'host_available_memory_min_mib':1536,'preflight_host_memory_min_mib':1920,'mailhog_rss_max_mib':768,'bounded_service_memory_fraction':.97},'baseline':baseline})
  print(json.dumps({'prepared':True,'selected_pending_fixtures':len(ids),'available_memory_mb':baseline['available_memory_mb'],'mailhog_rss_mib':baseline['services']['job-portal-mailhog']['rss_mib'],'vps_k6_running':False}))
 elif action=='finish':
  (RUN/'load-config.json').unlink(missing_ok=True)
  baseline=json.loads((RUN/'baseline.json').read_text());deadline=time.monotonic()+300;previous=None
  while True:
   current=snapshot();signature=(current['published_total'],current['mailhog_total'],current['lag'])
   rows=current['database_status_counts'].get('SHORTLISTED',0)-baseline['database_status_counts'].get('SHORTLISTED',0)
   events=current['published_total']-baseline['published_total'];mails=current['mailhog_total']-baseline['mailhog_total']
   passed=rows==events==mails and current['lag']==0
   if passed and signature==previous or time.monotonic()>deadline:break
   print('DRAIN '+json.dumps({'rows':rows,'kafka_events':events,'captured_mails':mails,'lag':current['lag']}),flush=True)
   previous=signature;time.sleep(10)
  health='passed';restart=False
  try:auth()
  except Exception:
   command(['docker','restart','job-portal-gateway'],timeout=60);restart=True;end=time.monotonic()+240
   while True:
    try:auth();break
    except Exception:
     if time.monotonic()>end:raise RuntimeError('Gateway recovery failed')
     time.sleep(3)
  items=inspect();services={i['Name'].lstrip('/'):safe(i) for i in items};g,a=items[:2]
  assert g['Config']['Image']==json.loads((RUN/'service-baseline.json').read_text())['job-portal-gateway']['image']
  assert g['HostConfig']['Memory']==512*2**20 and g['HostConfig']['NanoCpus']==500000000
  assert a['HostConfig']['Memory']==1024*2**20 and a['HostConfig']['NanoCpus']==1000000000
  report={'snapshot':current,'pipeline':{'new_shortlisted_rows':rows,'new_kafka_records':events,'new_mailhog_messages':mails,'final_kafka_lag':current['lag'],'count_reconciliation_passed':passed,'identity_level_audit_performed':False},'authenticated_loopback_health':health,'gateway_restarted_for_recovery':restart,'services':services,'private_jwt_removed':not (RUN/'load-config.json').exists(),'vps_k6_running':current['vps_k6_running'],'guard_reason_during_drain':current.get('stop_reason')}
  assert all(i['running'] and not i['oom_killed'] for i in services.values())
  save('final.json',report);(RUN/'monitor.stop').touch()
  print('FINISH '+json.dumps({k:v for k,v in report.items() if k not in ['snapshot','services']}),flush=True)
 elif action=='cleanup':
  (RUN/'load-config.json').unlink(missing_ok=True);(RUN/'monitor.stop').touch();print('Private JWT removed; monitor stop marked')

