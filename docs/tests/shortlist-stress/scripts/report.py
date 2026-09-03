from pathlib import Path
import collections,datetime as dt,json
NAME='ramping-vus-clean-mailhog-20261009'
B=Path('.loadtest')/NAME
def live():
 stages=collections.defaultdict(lambda:{'requests':0,'success':0,'latencies':[]})
 with (B/'metrics.jsonl').open() as f:
  for line in f:
   try:x=json.loads(line)
   except ValueError:continue
   if x.get('type')!='Point' or x.get('metric') not in ['stage_requests','stage_success','stage_latency']:continue
   d=x['data'];t=d.get('tags',{})
   if t.get('phase')!='hold':continue
   s=stages[int(t['target_vus'])]
   if x['metric']=='stage_requests':s['requests']+=d['value']
   if x['metric']=='stage_success':s['success']+=d['value']
   if x['metric']=='stage_latency':s['latencies'].append(d['value'])
 return stages
def percentile(a,p):
 if not a:return None
 a=sorted(a);pos=(len(a)-1)*p/100;i=int(pos);j=min(i+1,len(a)-1)
 return a[i]+(a[j]-a[i])*(pos-i)
def rows(stages):
 result=[]
 for vus,d in sorted(stages.items()):
  n=d['requests'];lat=d['latencies']
  result.append({'vus':vus,'hold_seconds':30,'requests':n,'http_200':d['success'],
   'requests_per_second':n/30,'successful_requests_per_second':d['success']/30,
   'success_rate':d['success']/n if n else 0,
   'p95_ms':percentile(lat,95),'p99_ms':percentile(lat,99),
   'average_ms':sum(lat)/len(lat) if lat else None})
 return result
if __name__=='__main__':
 import sys
 rs=rows(live())
 if '--live' in sys.argv:
  for row in rs:print(json.dumps(row))
  raise SystemExit
 run=json.loads((B/'run.json').read_text());final=json.loads((B/'final.json').read_text())
 summary=json.loads((B/'k6-summary.json').read_text())
 prep=json.loads((B/'preparation.json').read_text());cleanup=json.loads((B/'mailhog-cleanup.json').read_text())
 metrics=summary['metrics']
 def value(n):return metrics.get(n,{}).get('values',{})
 def count(n):return value(n).get('count',0)
 samples=[json.loads(x) for x in (B/'server.jsonl').read_text().splitlines()]
 elapsed=(dt.datetime.fromisoformat(run['finished_at_utc'])-dt.datetime.fromisoformat(run['started_at_utc'])).total_seconds()
 full=not run.get('runner_error') and not run['resource_stop'] and run['k6_exit_code'] in [0,99] and elapsed>=280 and len(rs)==7
 for row in rs:
  key='stage_requests{target_vus:'+str(row['vus'])+',phase:hold}'
  assert row['requests']==metrics[key]['values']['count'], 'Raw/summary hold count mismatch'
  row['full_hold_completed']=full
  row['passes_latency_and_success_thresholds']=full and row['success_rate']>.999 and row['p95_ms']<500 and row['p99_ms']<1000
 passing=[r for r in rs if r['passes_latency_and_success_thresholds']]
 best=max(passing,key=lambda r:r['successful_requests_per_second']) if passing else None
 peak=max(rs,key=lambda r:r['successful_requests_per_second']) if rs else None
 report={'run':run,'full_workload_completed':full,'mailhog_cleanup':cleanup,'preparation':prep,
  'hold_measurements':rs,'highest_passing_hold':best,'highest_observed_hold':peak,
  'requests':count('http_reqs'),'http_200':count('shortlist_accepted'),
  'http_failures':count('shortlist_failed'),'network_failures':count('shortlist_network_failed'),
  'server_5xx':count('shortlist_server_failed'),'request_timeouts':count('request_timeouts'),
  'aggregate_rps_including_ramps_and_completion_tail':value('http_reqs').get('rate'),
  'aggregate_latency_ms':value('http_req_duration'),'final':final,
  'minimum_host_available_memory_mib':min(s['available_memory_mb'] for s in samples),
  'minimum_host_available_memory_during_load_mib':min(s['available_memory_mb'] for s in samples if run['started_at_utc']<=s['at_utc']<=run['finished_at_utc']),
  'peak_mailhog_rss_mib':max(s['services']['job-portal-mailhog']['rss_mib'] for s in samples),
  'resource_peaks_mib':{n:max(s.get('services',{}).get(n,{}).get('memory_mib',0) for s in samples) for n in prep['baseline']['services']},
  'measurement_method':'Requests tagged by iteration start in each 30-second constant-VU hold, including their response completion tail, divided by 30. Ramp requests excluded. No think time or retries.',
  'thresholds':{n:d['thresholds'] for n,d in metrics.items() if d.get('thresholds')}}
 assert run['private_jwt_removed'] and final['private_jwt_removed'] and run['child_processes_stopped']
 assert final['authenticated_loopback_health']=='passed'
 out=Path('docs/tests/shortlist-stress')
 (out/(NAME+'.json')).write_text(json.dumps(report,indent=2))
 start=dt.datetime.fromisoformat(run['started_at_utc']).astimezone(dt.timezone(dt.timedelta(hours=7))).strftime('%H:%M:%S')
 md='# Ramping VUs with empty MailHog - 9 October 2026\n\n'
 if best:
  md+=f"**Highest measured throughput meeting the existing latency and success thresholds: {best['successful_requests_per_second']:.2f} successful RPS at {best['vus']} VUs** during a 30-second hold. "
 if peak:
  md+=f"Peak observed successful throughput: **{peak['successful_requests_per_second']:.2f} RPS at {peak['vus']} VUs**, p95 **{peak['p95_ms']:.2f} ms**, p99 **{peak['p99_ms']:.2f} ms**. "
 md+='These short holds measure capacity for this shortlist workload; they do not establish long-duration system-wide capacity.\n\n'
 md+=f"Start: {start} Asia/Bangkok. Full 280-second profile completed: **{full}**. k6 exit: **{run['k6_exit_code']}** (99 indicates threshold failures). Resource stop during load: **{run['resource_stop']}**.\n\n"
 md+="The user requested SSH access to root@201.18.216.184, MailHog cleanup, and a rerun with ramping VUs and recorded RPS. Uses [Grafana k6's ramping-vus executor](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/ramping-vus/), with active VUs increasing through **5, 10, 20, 40, 80, 160, 320**. Each level has a 10-second linear ramp and 30-second hold. No think time; no retries; five-second HTTP timeout and graceful stop; connection reuse enabled.\n\n"
 md+="Windows k6 -> four pinned SSH connections -> VPS loopback gateway :5000. Each iteration PATCHes a distinct existing PENDING test-company application to SHORTLISTED. No application status resets. Current deployment is tested: gateway local/job-portal-gateway:pool10-total50-20261009 (10 backend connections per route / 50 total), 512 MiB / 0.5 CPU; application 1 GiB / 1 CPU. Exact images, lifecycle baseline, and resource settings are retained in JSON. Gateway and application configuration were not changed.\n\n"
 md+=f"MailHog: **{cleanup['messages_before']:,} messages deleted**; DELETE HTTP **{cleanup['delete_http_status']}**; **{cleanup['messages_remaining']} remaining** before load. MailHog restart needed: **{cleanup['mailhog_restarted_to_release_memory']}**.\n\n"
 md+='| Active VUs | Successful RPS | Total RPS | Success | p95 ms | p99 ms | Thresholds pass |\n| ---: | ---: | ---: | ---: | ---: | ---: | :---: |\n'
 for r in rs:
  md+=f"| {r['vus']} | {r['successful_requests_per_second']:.2f} | {r['requests_per_second']:.2f} | {r['success_rate']*100:.3f}% | {r['p95_ms']:.2f} | {r['p99_ms']:.2f} | {'Yes' if r['passes_latency_and_success_thresholds'] else 'No'} |\n"
 md+='\nPassing means p95 <500 ms, p99 <1000 ms, and success >99.9%. Each row counts completed requests whose iterations started in that 30-second hold, including their completion tail, divided by 30 seconds. Ramps are excluded. Raw timestamped metrics are retained. This closed workload naturally reduces RPS when response latency rises; no offered RPS target or scheduled-arrival drop metric applies. SSH/network overhead is included.\n\n'
 md+=f"Across ramps and holds: **{report['requests']:,} requests**, **{report['http_200']:,} HTTP 200**, **{report['http_failures']:,} failures**, **{report['request_timeouts']:,} timeouts**, **{report['server_5xx']:,} server 5xx**. Whole-run average **{report['aggregate_rps_including_ramps_and_completion_tail']:.2f} RPS**, including ramps and completion tail; this average is not the capacity estimate.\n\n"
 p=final['pipeline']
 md+=f"After drain: **{p['new_shortlisted_rows']:,} database updates**, **{p['new_kafka_records']:,} Kafka events**, **{p['new_mailhog_messages']:,} captured messages**, final Kafka lag **{p['final_kafka_lag']}**. Aggregate count reconciliation passed: **{p['count_reconciliation_passed']}**. This does not verify per-identity delivery or external inbox delivery. New captured messages are retained.\n\n"
 md+=f"Authenticated health: **{final['authenticated_loopback_health']}**. Gateway recovery restart: **{final['gateway_restarted_for_recovery']}**. Minimum host available memory **{report['minimum_host_available_memory_mib']:.1f} MiB**. Guard during notification drain: **{final.get('guard_reason_during_drain')}**. Guards: host available memory >=1536 MiB (preflight >=1920), MailHog RSS <=768 MiB, bounded service memory <97% of limit, unchanged monitored service lifecycle. No VPS k6; owned PC test/transport/monitor processes stopped; private JWT files removed from both hosts.\n\n"
 md+=f"The full load ran within its resource guards. During notification drain, MailHog RSS peaked at **{report['peak_mailhog_rss_mib']:.1f} MiB**, crossing 768 MiB, and host available memory fell below its 1536 MiB guard to **{report['minimum_host_available_memory_mib']:.1f} MiB**. Minimum host available memory during load was **{report['minimum_host_available_memory_during_load_mib']:.1f} MiB**. No further requests were generated after load ended. Captured test emails remain; another run with these guards requires mailbox memory to be addressed.\n\n"
 md+=f"[Sanitized measurements]({NAME}.json). Scripts and raw metrics: .loadtest/{NAME}/. Remote artifacts: {run['remote_directory']}.\n"
 (out/(NAME+'.md')).write_text(md,encoding='utf-8')
 print(json.dumps({k:report[k] for k in ['full_workload_completed','highest_passing_hold','highest_observed_hold','requests','http_200','http_failures','request_timeouts','server_5xx','minimum_host_available_memory_mib']},indent=2))
