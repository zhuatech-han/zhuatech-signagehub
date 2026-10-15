#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Verify the same immutable publication, device and media after restart or restore. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2."""
import argparse,json,urllib.request,hashlib
from http_client import ROOT,Client
from readiness import wait_for_ready
p=argparse.ArgumentParser();p.add_argument('--base',default='http://127.0.0.1:8170');p.add_argument('--state',default=str(ROOT/'output/signagehub-quality-state.json'));a=p.parse_args();wait_for_ready(a.base);state=json.load(open(a.state));publisher=state['credentials'][1];c=Client(a.base);c.login(publisher['username'],publisher['password'])
r=c.request('/releases/'+state['releaseId']);assert r['items'][0]['assetId']==state['assetId'];assert c.request('/playlists/'+str(state['programId']))['playlist']['name'].startswith('QA')
for suffix in ['/manifest','/media/'+str(state['assetId'])]:
 with urllib.request.urlopen(urllib.request.Request(a.base+'/api/player'+suffix,headers={'Authorization':'Bearer '+state['token']}),timeout=30) as response:raw=response.read()
 if suffix=='/manifest':assert any(x['release']['id']==state['releaseId'] for x in json.loads(raw)['programs'])
 else:assert hashlib.sha256(raw).hexdigest()==state['assetSha']
c.request('/reports');print('Persisted program, publication, device identity and media bytes verified: 7 requests. No credentials printed.')
