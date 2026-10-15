#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Create private local credentials without overwriting an existing deployment. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2."""
from pathlib import Path
import secrets,os
root=Path(__file__).resolve().parents[1]
values={k:'Aa9'+secrets.token_urlsafe(30) for k in ['DATABASE_PASSWORD','MYSQL_ROOT_PASSWORD','ADMIN_PASSWORD','TEST_DATABASE_PASSWORD','TEST_ROOT_PASSWORD']}
values.update({'ADMIN_USERNAME':'admin','BIND_ADDRESS':'127.0.0.1','WEB_PORT':'8170','COOKIE_SECURE':'false'})
fd=os.open(root/'.env',os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as f:f.write(''.join(k+'='+v+'\n' for k,v in values.items()))
print('Private .env created (0600); no credentials printed. Read ADMIN_PASSWORD locally to sign in.')
