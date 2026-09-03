import http from 'k6/http';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { Counter, Rate, Trend } from 'k6/metrics';
const config=new SharedArray('private configuration',()=>[JSON.parse(open(__ENV.LOAD_CONFIG))])[0];
const ids=new SharedArray('pending application IDs',()=>JSON.parse(open(__ENV.LOAD_IDS)));
const steps=[5,10,20,40,80,160,320];
const accepted=new Counter('shortlist_accepted');
const failed=new Counter('shortlist_failed');
const networkFailed=new Counter('shortlist_network_failed');
const serverFailed=new Counter('shortlist_server_failed');
const success=new Rate('shortlist_success');
const successfulLatency=new Trend('shortlist_success_duration',true);
const stageRequests=new Counter('stage_requests');
const stageSuccess=new Rate('stage_success');
const stageLatency=new Trend('stage_latency',true);
const timeouts=new Counter('request_timeouts');
const thresholds={};
for(const vus of steps) {
 thresholds['stage_requests{target_vus:'+vus+',phase:hold}']=['count>0'];
 thresholds['stage_success{target_vus:'+vus+',phase:hold}']=['rate>0.999'];
 thresholds['stage_latency{target_vus:'+vus+',phase:hold}']=['p(95)<500','p(99)<1000'];
}
export const options={
 discardResponseBodies:true,noConnectionReuse:false,noVUConnectionReuse:false,
 systemTags:['status','method','name','scenario','error','error_code','expected_response'],
 summaryTrendStats:['avg','min','med','max','p(95)','p(99)'],
 scenarios:{target:{executor:'ramping-vus',startVUs:0,
  stages:steps.flatMap(target=>[{duration:'10s',target},{duration:'30s',target}]),
  gracefulRampDown:'5s',gracefulStop:'5s'}},
 thresholds:{...thresholds,shortlist_success:['rate>0.999'],
 http_req_duration:['p(95)<500','p(99)<1000'],
 shortlist_success_duration:['p(95)<500','p(99)<1000'],
 shortlist_accepted:['count>=0'],shortlist_failed:['count>=0'],
 shortlist_network_failed:['count>=0'],shortlist_server_failed:['count>=0']}
};
export default function(){
 const elapsed=(Date.now()-exec.scenario.startTime)/1000;
 const index=Math.min(steps.length-1,Math.floor(elapsed/40));
 const tags={target_vus:String(steps[index]),phase:elapsed%40<10?'ramp':'hold'};
 const id=ids[exec.scenario.iterationInTest];
 if(id===undefined)exec.test.abort('Pending fixture dataset exhausted');
 const port=config.basePort+((exec.vu.idInInstance-1)%config.tunnelCount);
 const response=http.patch('http://127.0.0.1:'+port+'/api/applications/'+id+'/status',
  JSON.stringify({status:'SHORTLISTED'}),{
   headers:{Authorization:'Bearer '+config.jwt,'Content-Type':'application/json'},
   timeout:'5s',redirects:0,tags:{...tags,name:'PATCH /api/applications/:id/status'}});
 const ok=response.status===200;
 stageRequests.add(1,tags);stageSuccess.add(ok,tags);stageLatency.add(response.timings.duration,tags);
 success.add(ok);if(ok){accepted.add(1);successfulLatency.add(response.timings.duration);}else failed.add(1);
 if(response.status===0)networkFailed.add(1);
 if(response.status>=500)serverFailed.add(1);
 if(response.status===0&&response.error_code===1050)timeouts.add(1);
}
export function handleSummary(data){return {[__ENV.LOAD_SUMMARY]:JSON.stringify(data,null,2)};}
