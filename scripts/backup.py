#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Consistent private SQL and media backup while uploads are paused. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2."""
import argparse,subprocess,zipfile,json,hashlib,os,re
from pathlib import Path
from http_client import ROOT
parser=argparse.ArgumentParser();parser.add_argument('--project',required=True);parser.add_argument('--env-file',default=str(ROOT/'.env'));parser.add_argument('--output',required=True);a=parser.parse_args();assert re.fullmatch(r'[a-z][a-z0-9-]{2,60}',a.project)
cmd=['docker','compose','--env-file',a.env_file,'-p',a.project]
def run(args,capture=False):return subprocess.run(cmd+args,cwd=ROOT,check=True,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE if capture else None).stdout
destination=Path(a.output).resolve();assert not destination.exists(),'Backup destination already exists';destination.parent.mkdir(parents=True,exist_ok=True)
try:
 run(['stop','backend'])
 sql=run(['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF -u"$MYSQL_USER" "$MYSQL_DATABASE"'],True)
 media=run(['run','--no-deps','--rm','-T','--entrypoint','tar','backend','-C','/app/media','--exclude=upload-*','--exclude=*.tmp','-cf','-','.'],True)
 assert len(sql)<=1024**3 and len(media)<=1024**3,'Backup exceeds the supported one-gigabyte member limit'
 data={'database.sql':sql,'media.tar':media};manifest={'format':'SIGNAGEHUB_BACKUP_1','sha256':{n:hashlib.sha256(b).hexdigest() for n,b in data.items()}}
 fd=os.open(destination,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
 with os.fdopen(fd,'wb') as raw:
  with zipfile.ZipFile(raw,'w',zipfile.ZIP_DEFLATED) as archive:
   for name,body in data.items():archive.writestr(name,body)
   archive.writestr('manifest.json',json.dumps(manifest))
 print('Consistent private database and media backup saved (0600); no credentials printed.')
finally:
 run(['up','-d','--wait','--wait-timeout','1800','backend'])
