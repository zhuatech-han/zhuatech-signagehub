#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Create a new private restore environment on a distinct localhost port. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2."""
import argparse,os
from pathlib import Path
from http_client import env
p=argparse.ArgumentParser();p.add_argument('--source',required=True);p.add_argument('--output',required=True);p.add_argument('--port',type=int,default=8172);a=p.parse_args();v=env(a.source);assert 1024<=a.port<=65535 and str(a.port)!=v.get('WEB_PORT','8170');v.update({'BIND_ADDRESS':'127.0.0.1','WEB_PORT':str(a.port),'COOKIE_SECURE':'false'});out=Path(a.output);out.parent.mkdir(parents=True,exist_ok=True);fd=os.open(out,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as f:f.write(''.join(k+'='+value+'\n' for k,value in v.items()))
print('Private isolated restore environment created (0600), without changing the source file.')
