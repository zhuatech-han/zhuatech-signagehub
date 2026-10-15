#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Restore a trusted private backup only into a new isolated local project. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2."""
import argparse,subprocess,zipfile,json,hashlib,re,tarfile,io,time
from http_client import ROOT,env
from readiness import wait_for_ready
parser=argparse.ArgumentParser();parser.add_argument('backup');parser.add_argument('--project',required=True);parser.add_argument('--env-file',required=True);a=parser.parse_args();assert re.fullmatch(r'signagehub-[a-z0-9-]{2,50}',a.project)
values=env(a.env_file);assert values.get('BIND_ADDRESS') in ['127.0.0.1','localhost'];assert values['WEB_PORT']!=env()['WEB_PORT'],'Use a separate restore port'
assert not subprocess.check_output(['docker','ps','-a','--filter','label=com.docker.compose.project='+a.project,'--format','{{.ID}}']).strip(),'Target project already has containers'
for v in ['mysql-data','media-data']:
 assert subprocess.run(['docker','volume','inspect',a.project+'_'+v],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode!=0,'Target volume already exists'
with zipfile.ZipFile(a.backup) as archive:
 assert set(archive.namelist())=={'database.sql','media.tar','manifest.json'} and len(archive.namelist())==3,'Unexpected archive members'
 assert all(x.file_size<=1024**3 for x in archive.infolist()),'Archive too large'
 manifest=json.loads(archive.read('manifest.json'));assert manifest['format']=='SIGNAGEHUB_BACKUP_1'
 data={n:archive.read(n) for n in ['database.sql','media.tar']}
 assert all(hashlib.sha256(b).hexdigest()==manifest['sha256'][n] for n,b in data.items()),'Backup checksum mismatch'
with tarfile.open(fileobj=io.BytesIO(data['media.tar'])) as tar:
 total=0;names=set()
 for entry in tar:
  name=entry.name.removeprefix('./');assert not name.startswith('/') and '..' not in name.split('/') and name not in names,'Invalid media member';names.add(name)
  assert entry.isfile() or entry.isdir(),'Links and special files are not accepted';total+=entry.size;assert total<=1024**3
  assert name in ['.',''] or re.fullmatch(r'[a-f0-9-]{36}\.(png|mp4)',name),'Unexpected media filename'
cmd=['docker','compose','--env-file',a.env_file,'-p',a.project]
def run(args,data=None):return subprocess.run(cmd+args,cwd=ROOT,check=True,**({'input':data} if data is not None else {'stdin':subprocess.DEVNULL}))
run(['config','--quiet']);run(['build','backend','frontend']);run(['up','-d','--wait','--wait-timeout','3600','mysql'])
deadline=time.monotonic()+240
while True:
 result=subprocess.run(cmd+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --connect-timeout=3 -h127.0.0.1 -u"$MYSQL_USER" -N -e "SELECT 1" "$MYSQL_DATABASE"'],cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 if result.returncode==0:break
 assert time.monotonic()<deadline,'Database did not become ready';time.sleep(2)
run(['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE"'],data['database.sql'])
run(['run','--no-deps','--rm','-T','--user','root','--entrypoint','sh','backend','-c','tar --no-same-owner -C /app/media -xf - && chown -R signagehub:signagehub /app/media'],data['media.tar'])
run(['up','-d','--wait','--wait-timeout','3600']);wait_for_ready('http://127.0.0.1:'+values['WEB_PORT']);print('Trusted database and media restored into a new isolated local project; no credentials printed.')
