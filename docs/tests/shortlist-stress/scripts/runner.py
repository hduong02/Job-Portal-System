"""Run Windows k6 through pinned SSH transport, with continuous VPS monitoring."""
import datetime as dt,json,os,subprocess,threading,time,urllib.request
from pathlib import Path
ROOT=Path.cwd().resolve()
LOCAL=ROOT/'.loadtest/ramping-vus-clean-mailhog-20261009'
REMOTE='/root/job-portal-shortlist-loadtest-20261007/ramping-vus-clean-mailhog-20261009'
HOST='root@201.18.216.184'
FLAGS=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0
report={'rate':None,'executor':'ramping-vus','vu_steps':[5,10,20,40,80,160,320],'ramp_seconds':10,'hold_seconds':30,'vus':320,'duration_seconds':280,'timeout_seconds':5,'retries':False,'generator':'Windows PC-local k6','topology':'Windows k6 -> 4 pinned SSH connections -> VPS loopback gateway :5000','gateway_memory_mib':512,'gateway_cpu':.5,'application_memory_mib':1024,'application_cpu':1.0,'host_memory_stop_mib':1536,'mailhog_rss_stop_mib':768,'existing_emails_preserved':False,'mailhog_cleared_before_load':True,'remote_directory':REMOTE,'private_jwt_removed':False}
def ssh(args,timeout=60):
 p=subprocess.run(['ssh','-o','BatchMode=yes','-o','ConnectTimeout=10','-o','ServerAliveInterval=15',HOST,args],text=True,capture_output=True,timeout=timeout,creationflags=FLAGS)
 if p.returncode:raise RuntimeError('SSH operation failed: '+p.stderr[:300])
 return p.stdout
def fetch(name):
 p=subprocess.run(['scp','-o','BatchMode=yes',HOST+':'+REMOTE+'/'+name,str(LOCAL/name)],capture_output=True,text=True,timeout=30,creationflags=FLAGS)
 if p.returncode:raise RuntimeError('Artifact download failed: '+name)
def save(): (LOCAL/'run.json').write_text(json.dumps(report,indent=2))
def main():
 assert not (LOCAL/'run.json').exists(),'Preserve existing run'
 if os.name=='nt':
  user=subprocess.check_output(['whoami'],text=True,creationflags=FLAGS).strip()
  subprocess.run(['icacls',str(LOCAL),'/inheritance:r','/grant:r',user+':(OI)(CI)F','SYSTEM:(OI)(CI)F'],check=True,stdout=subprocess.DEVNULL,creationflags=FLAGS)
 transport=monitor=test=None;logs=[];reader=None
 ready=threading.Event();stop=threading.Event();monitor_done=threading.Event()
 stop_reasons=[]
 try:
  print('PREPARE '+ssh('python3 '+REMOTE+'/remoteops.py prepare'),flush=True)
  for name in ['load-config.json','application-ids.json','preparation.json','baseline.json','service-baseline.json','mailhog-cleanup.json']:fetch(name)
  cfg=json.loads((LOCAL/'load-config.json').read_text())
  assert cfg['basePort']==19250 and cfg['tunnelCount']==4
  log=(LOCAL/'tunnel.log').open('w');logs.append(log)
  transport=subprocess.Popen(['node',str(LOCAL/'transport.cjs')],stdout=log,stderr=log,creationflags=FLAGS)
  deadline=time.monotonic()+60
  while 'READY ' not in (LOCAL/'tunnel.log').read_text():
   if transport.poll() is not None:raise RuntimeError('SSH transport failed; inspect local tunnel.log')
   if time.monotonic()>deadline:raise RuntimeError('SSH transport readiness deadline exceeded')
   time.sleep(.5)
  print('TRANSPORT_READY: 4 SSH connections; k6 will run on Windows',flush=True)
  # Verify authenticated routing through each tunnel without writing statuses.
  for port in range(19250,19254):
   r=urllib.request.Request('http://127.0.0.1:'+str(port)+'/api/applications/50001',headers={'Authorization':'Bearer '+cfg['jwt']})
   with urllib.request.urlopen(r,timeout=10) as response:assert response.status==200 and json.load(response)['id']==50001
  del cfg
  errorlog=(LOCAL/'monitor-error.log').open('w');logs.append(errorlog)
  monitor=subprocess.Popen(['ssh','-o','BatchMode=yes','-o','ServerAliveInterval=15',HOST,'python3 -u '+REMOTE+'/guard.py'],stdout=subprocess.PIPE,stderr=errorlog,text=True,creationflags=FLAGS)
  def consume():
   try:
    with (LOCAL/'server-live.jsonl').open('w') as output:
     for line in monitor.stdout:
      output.write(line);output.flush()
      try:s=json.loads(line)
      except ValueError:continue
      if s.get('monitor_ready'):ready.set()
      if s.get('stop_reason'):
       stop_reasons.append(s['stop_reason']);stop.set()
      small={'at_utc':s.get('at_utc'),'available_memory_mb':s.get('available_memory_mb'),'mailhog_rss_mib':s.get('services',{}).get('job-portal-mailhog',{}).get('rss_mib'),'stop_reason':s.get('stop_reason')}
      print('VPS '+json.dumps(small),flush=True)
   finally:monitor_done.set()
  reader=threading.Thread(target=consume,daemon=True);reader.start()
  if not ready.wait(30) or stop.is_set():raise RuntimeError('VPS baseline guard rejected load')
  executable=next((ROOT/'.loadtest/k6').rglob('k6.exe'))
  args=[str(executable),'run','--quiet','--log-format','json','--address','127.0.0.1:6565','--no-connection-reuse=false','--no-vu-connection-reuse=false']
  for key,name in [('LOAD_CONFIG','load-config.json'),('LOAD_IDS','application-ids.json'),('LOAD_SUMMARY','k6-summary.json')]:args+=['-e',key+'='+str(LOCAL/name)]
  args+=['--out','json='+str(LOCAL/'metrics.jsonl'),str(LOCAL/'stress-vus.js')]
  log=(LOCAL/'k6.log').open('w');logs.append(log)
  test=subprocess.Popen(args,stdout=log,stderr=log,creationflags=FLAGS)
  report.update(started_at_utc=dt.datetime.now(dt.timezone.utc).isoformat(),pc_k6_pid=test.pid,pc_k6_executable=str(executable));save()
  print('STAGE_START '+json.dumps({k:report[k] for k in ['rate','vus','duration_seconds','generator','pc_k6_pid','started_at_utc']}),flush=True)
  while test.poll() is None:
   failed=stop.wait(.5) or monitor_done.is_set() or transport.poll() is not None
   if failed:
    if monitor_done.is_set():stop_reasons.append('VPS monitor connection ended during load')
    if transport.poll() is not None:stop_reasons.append('SSH transport ended during load')
    try:
     r=urllib.request.Request('http://127.0.0.1:6565/v1/status',method='PATCH',data=json.dumps({'data':{'type':'status','id':'default','attributes':{'stopped':True}}}).encode(),headers={'Content-Type':'application/json'})
     urllib.request.urlopen(r,timeout=3).close();test.wait(timeout=10)
    except Exception:
     if test.poll() is None:test.terminate()
    break
  report.update(k6_exit_code=test.wait(timeout=15),finished_at_utc=dt.datetime.now(dt.timezone.utc).isoformat(),resource_stop=stop.is_set(),stop_reasons=sorted(set(stop_reasons)));save()
  print('STAGE_END '+json.dumps({k:report[k] for k in ['k6_exit_code','finished_at_utc','resource_stop','stop_reasons']}),flush=True)
  # Unlink the PC token as soon as load ends, before draining.
  (LOCAL/'load-config.json').unlink(missing_ok=True)
  print(ssh('python3 -u '+REMOTE+'/remoteops.py finish',timeout=600),flush=True)
  monitor.wait(timeout=20);reader.join(timeout=5)
  for name in ['final.json','server.jsonl']:fetch(name)
  final=json.loads((LOCAL/'final.json').read_text());report.update(pipeline=final['pipeline'],post_load_health=final['authenticated_loopback_health'],gateway_restarted_for_recovery=final['gateway_restarted_for_recovery'],resource_stop_during_drain=stop.is_set(),guard_reason_during_drain=final.get('guard_reason_during_drain'))
  report['vps_k6_running']=final['vps_k6_running']
  assert not final['vps_k6_running'],'Unexpected VPS k6 process'
  print('RUN_COMPLETE '+json.dumps(report),flush=True)
 except Exception as error:
  report['runner_error_type']=type(error).__name__;report['runner_error']=str(error)
  print('RUN_ERROR '+json.dumps({'type':type(error).__name__,'message':str(error)}),flush=True)
  raise
 finally:
  if test is not None and test.poll() is None:test.terminate();test.wait(timeout=15)
  (LOCAL/'load-config.json').unlink(missing_ok=True)
  try:ssh('python3 '+REMOTE+'/remoteops.py cleanup')
  except Exception:report['remote_cleanup_failed']=True
  if monitor is not None and monitor.poll() is None:monitor.terminate();monitor.wait(timeout=10)
  if transport is not None and transport.poll() is None:transport.terminate();transport.wait(timeout=10)
  for log in logs:log.close()
  report['private_jwt_removed']=not (LOCAL/'load-config.json').exists()
  report['child_processes_stopped']=all(p is None or p.poll() is not None for p in [test,monitor,transport]);save()
if __name__=='__main__':main()
