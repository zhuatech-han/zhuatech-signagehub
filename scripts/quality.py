#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Run an isolated real HTTP business workflow with clearly named QA data. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2."""
import argparse,os,json,secrets,uuid,struct,zlib,urllib.request,urllib.error,hashlib,subprocess
from datetime import datetime,timedelta,timezone
from pathlib import Path
from http_client import ROOT,env,Client
parser=argparse.ArgumentParser();parser.add_argument('--base',default='http://127.0.0.1:8170');parser.add_argument('--project',default='signagehub-acceptance-20261014');parser.add_argument('--env-file');parser.add_argument('--video',action='store_true');args=parser.parse_args()
assert urllib.parse.urlparse(args.base).hostname in ['127.0.0.1','localhost'], 'QA accepts only an explicit local deployment'
values=env(args.env_file);admin=Client(args.base);anonymous=Client(args.base);suffix=uuid.uuid4().hex[:8]
anonymous.request('/catalog',status=401);anonymous.request('/screens',{'name':'QA blocked'},status=403,csrf=False)
profile=admin.login(values['ADMIN_USERNAME'],values['ADMIN_PASSWORD']);assert {m['code'] for m in profile['menus']}=={'media','programs','publish','screens','reports','audit','users','roles','settings'},'Fresh administrator navigation is incomplete';users=admin.request('/admin/users');assert all('passwordHash' not in x for x in users)
roles=admin.request('/admin/roles');department=admin.request('/admin/departments',{'name':'QA isolated '+suffix,'zone':'UTC','enabled':True})
credentials=[]
for name,role_name,dept in [('editor','内容编辑',1),('publisher','门店发布',1),('other','门店发布',department['id'])]:
 role=next(r for r in roles if r['name']==role_name);password='Aa9'+secrets.token_urlsafe(18);username='qa-'+name+'-'+suffix
 admin.request('/admin/users',{'username':username,'displayName':'QA '+name,'password':password,'roleId':role['id'],'departmentId':dept,'enabled':True})
 c=Client(args.base);profile=c.login(username,password);assert {m['code'] for m in profile['menus']}==({'media','programs'} if name=='editor' else {'media','programs','publish','screens','reports','audit'}),'Staff navigation does not match permissions';credentials.append({'username':username,'password':password,'client':c})
editor,publisher,other=[c['client'] for c in credentials]
editor.request('/admin/users',status=403);editor.request('/screens',{'departmentId':1,'name':'QA denied','enabled':True},status=403)
group=publisher.request('/groups',{'departmentId':1,'name':'QA reception group '+suffix,'enabled':True})
screen=publisher.request('/screens',{'departmentId':1,'name':'QA reception '+suffix,'groupId':group['id'],'enabled':True});screen_id=screen['screen']['id'];token=screen['token']
secondary=publisher.request('/screens',{'departmentId':1,'name':'QA side screen '+suffix,'groupId':group['id'],'enabled':True})
def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
width,height=1280,720
colors=[(45,67,110),(54,90,129),(93,137,152),(178,204,199),(230,218,180)]
row=b'\x00'+b''.join(bytes(colors[min(4,x*5//width)]) for x in range(width))
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',width,height,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(row*height))+chunk(b'IEND',b'')
def upload(name,data,filename,mime,status=200):
 boundary='qa-'+uuid.uuid4().hex;parts=[]
 for k,v in [('departmentId','1'),('name',name)]:parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
 parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: {mime}\r\n\r\n'.encode()+data+b'\r\n');parts.append(f'--{boundary}--\r\n'.encode())
 return publisher.request('/media',b''.join(parts),content_type='multipart/form-data; boundary='+boundary,status=status)
asset=upload('QA color pattern '+suffix,png,'qa.png','image/png');assert asset['width']==width and asset['height']==height
upload('QA invalid html',b'<script>alert(1)</script>','wrong.png','image/png',status=409)
items=[{'assetId':asset['id'],'seconds':10,'color':'#ffffff'},{'text':'QA · SCREEN PLAYBACK\nLocal deployment verification','seconds':7,'color':'#ffffff'}]
video=None
if args.video:
 assert args.project.startswith('signagehub-')
 video_bytes=subprocess.check_output(['docker','compose','-p',args.project,'exec','-T','backend','ffmpeg','-nostdin','-v','error','-f','lavfi','-i','testsrc2=size=640x360:rate=12','-t','4','-c:v','libx264','-pix_fmt','yuv420p','-movflags','frag_keyframe+empty_moov','-f','mp4','pipe:1'],cwd=ROOT,stdin=subprocess.DEVNULL,timeout=180)
 video=upload('QA H264 video '+suffix,video_bytes,'qa.mp4','video/mp4');assert video['kind']=='VIDEO' and 3.5<video['duration']<4.5
 items.append({'assetId':video['id'],'seconds':5,'color':'#ffffff'})
body={'departmentId':1,'name':'QA reception loop '+suffix,'nameEn':'QA reception loop '+suffix,'categoryId':1,'orientation':'LANDSCAPE','fit':'contain','background':'#172334','items':items}
program=publisher.request('/playlists',body);other.request('/playlists/'+str(program['id']),status=403)
request={'requestId':str(uuid.uuid4()),'playlistVersion':program['version'],'screenIds':[],'groupIds':[group['id']]}
release=publisher.request('/playlists/'+str(program['id'])+'/publish',request);replay=publisher.request('/playlists/'+str(program['id'])+'/publish',request);assert replay['id']==release['id']
request_conflict=dict(request);request_conflict['screenIds']=[screen_id];publisher.request('/playlists/'+str(program['id'])+'/publish',request_conflict,status=409)
editor.request('/playlists/'+str(program['id'])+'/publish',request,status=403)
checks=[]
def device(path,device_token=token,body=None,status=200,binary=False):
 headers={'Authorization':'Bearer '+device_token};data=None
 if body is not None:headers['Content-Type']='application/json';data=json.dumps(body).encode()
 try:
  with urllib.request.urlopen(urllib.request.Request(args.base+'/api/player'+path,headers=headers,data=data),timeout=30) as r:code,raw=r.status,r.read()
 except urllib.error.HTTPError as e:code,raw=e.code,e.read()
 assert code==status,f'Device request {path}: expected {status}, got {code}'
 checks.append({'path':path,'status':code});return raw if binary else json.loads(raw)
manifest=device('/manifest');assert any(p['release']['id']==release['id'] for p in manifest['programs'])
blob=device('/media/'+str(asset['id']),binary=True);assert hashlib.sha256(blob).hexdigest()==asset['sha256']
device('/manifest','A'*43,status=401);device('/heartbeat',body={'state':'PLAYING','appliedRelease':release['id']})
now=datetime.now(timezone.utc);schedule_request={'requestId':str(uuid.uuid4()),'playlistVersion':program['version'],'screenIds':[screen_id],'groupIds':[],'startsAt':(now+timedelta(minutes=15)).isoformat(),'endsAt':(now+timedelta(minutes=20)).isoformat()}
publisher.request('/playlists/'+str(program['id'])+'/publish',schedule_request);overlap=dict(schedule_request);overlap['requestId']=str(uuid.uuid4());publisher.request('/playlists/'+str(program['id'])+'/publish',overlap,status=409)
catalog=publisher.request('/catalog');schedule=next(x for x in catalog['schedules'] if x['releaseId']==schedule_request['requestId']);publisher.request('/schedules/'+str(schedule['id'])+'/cancel',{'version':schedule['version']})
updated=dict(body);updated['version']=program['version'];updated['items']=[{'text':'QA updated draft','seconds':8,'color':'#ffffff'}];new_program=editor.request('/playlists/'+str(program['id']),updated,method='PUT')
frozen=publisher.request('/releases/'+release['id']);assert frozen['items'][0]['assetId']==asset['id']
editor.request('/playlists/'+str(program['id']),updated,method='PUT',status=409)
publisher.request('/media/'+str(asset['id']),method='DELETE',status=409);editor.request('/playlists/'+str(program['id']),method='DELETE',status=409)
second=publisher.request('/playlists/'+str(program['id'])+'/publish',{'requestId':str(uuid.uuid4()),'playlistVersion':new_program['version'],'screenIds':[screen_id],'groupIds':[]})
current=next(x for x in publisher.request('/catalog')['screens'] if x['id']==screen_id);publisher.request('/screens/'+str(screen_id)+'/rollback',{'version':current['version'],'releaseId':release['id']});assert next(x for x in device('/manifest')['programs'] if 'startsAt' not in x)['release']['id']==release['id']
assert not other.request('/catalog')['screens'];other.request('/media/'+str(asset['id'])+'/content',status=403)
rows=publisher.request('/reports');assert any(x['id']==screen_id and x['appliedRelease']==release['id'] for x in rows);publisher.request('/reports.csv',binary=True);publisher.request('/audit')
secondary_row=next(x for x in publisher.request('/catalog')['screens'] if x['id']==secondary['screen']['id']);rotation=publisher.request('/screens/'+str(secondary_row['id'])+'/rotate',{'version':secondary_row['version']});device('/manifest',secondary['token'],status=401)
secondary_row=rotation['screen'];secondary_row['enabled']=False;publisher.request('/screens/'+str(secondary_row['id']),secondary_row,method='PUT');device('/manifest',rotation['token'],status=403)
state={'base':args.base,'screenId':screen_id,'token':token,'programId':program['id'],'releaseId':release['id'],'assetId':asset['id'],'assetSha':asset['sha256'],'videoId':video['id'] if video else None,'credentials':[{k:v for k,v in c.items() if k!='client'} for c in credentials]}
out=ROOT/'output';out.mkdir(exist_ok=True);file=out/'signagehub-quality-state.json';fd=os.open(file,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
with os.fdopen(fd,'w') as f:json.dump(state,f)
count=sum(len(c.checks) for c in [admin,anonymous,editor,publisher,other])+len(checks)
print(f'Real HTTP media, group publication, immutable snapshot, schedule conflict, rollback, device revocation and scope workflow passed: {count} requests. Private QA state stored without printing credentials.')
