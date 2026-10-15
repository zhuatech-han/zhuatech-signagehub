#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Verify original assets, bilingual docs, tracked source and private-value exclusion. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2."""
from pathlib import Path
import hashlib,re,json,subprocess
ROOT=Path(__file__).resolve().parents[1]
expected={'logo.jpg':'90bf87ff294f5ee8cdaef4b10155139173d97d93a88d7ea9679b9721ba05c962','wechat-zhuatech.png':'a1205aeec110016ca889693892250a11d449489f64d27c714816b73c3fc645e1','wechat-zhuatech2.png':'98df6f15d17f94b88bc8bc115262b264fab0cfb5e6ca9443aaaf4143c5275215'}
tracked=subprocess.check_output(['git','ls-files','--cached'],cwd=ROOT,text=True).splitlines();assert tracked,'Stage the reviewed source before checking'
for name,digest in expected.items():assert hashlib.sha256((ROOT/'frontend/public/brand'/name).read_bytes()).hexdigest()==digest and 'frontend/public/brand/'+name in tracked,name
cn=(ROOT/'README.md').read_text();en=(ROOT/'README.en.md').read_text();license=(ROOT/'LICENSE').read_text()
assert '上海如静知华信息科技有限公司' in cn and 'Shanghai Rujing Zhihua Information Technology Co., Ltd.' in en
assert 'README.en.md' in cn and 'README.md' in en
assert 'wechat-zhuatech.png' in cn and 'wechat-zhuatech2.png' in cn and 'wechat-' not in en
assert '不得商用' in cn and 'non-commercial' in en.lower() and '未经' in license
for value in ['han@zhuatech.cn','jack@zhuatech.cn','https://wa.me/8617521234993']:assert value in en
for text in [cn,en]:assert 'https://www.zhuatech.cn/' in text
for name in ['login','media','program','screens','publication','overview','roles','player-settings','player','mobile','english']:
 path=ROOT/'docs/screenshots'/f'{name}.jpg';data=path.read_bytes();assert data[:2]==b'\xff\xd8' and len(data)>10000 and str(path.relative_to(ROOT)) in tracked,name
for file in [ROOT/name for name in tracked if name.endswith('.md')]:
 for matches in re.findall(r'\]\(([^)]+)\)|(?:src|href)="([^"]+)"',file.read_text()):
  target=next(x for x in matches if x)
  if not target.startswith(('https://','http://','mailto:','#')):assert (file.parent/target.split('#')[0]).exists(),target
values=[]
if (ROOT/'.env').exists():
 for line in (ROOT/'.env').read_text().splitlines():
  if '=' in line and not line.startswith('#'):
   key,value=line.split('=',1)
   if value and ('PASSWORD' in key or 'KEY' in key or 'SECRET' in key):values.append(value.encode())
if (ROOT/'output/signagehub-quality-state.json').exists():
 state=json.loads((ROOT/'output/signagehub-quality-state.json').read_text());values.append(state['token'].encode());values.extend(c['password'].encode() for c in state['credentials'])
for name in tracked:
 assert not any(x in Path(name).parts for x in ['output','target','node_modules','dist','player-data','__pycache__']) and Path(name).name!='.env',name
 data=(ROOT/name).read_bytes();assert not any(v in data for v in values),name
 assert not re.search(rb'(?:ghp_|github_pat_)[A-Za-z0-9_]{20,}|-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----',data),name
 if name.endswith(('.java','.vue','.js','.py','.css','.sql','.yaml','.yml')):assert b'zhuatech.cn' in data[:600] and b'zhuatech2' in data[:600],name
for name in ['backend/Dockerfile','frontend/Dockerfile']:assert not re.search(r'skipTests|maven.test.skip',(ROOT/name).read_text())
for package in ['frontend','player']:
 json.loads((ROOT/package/'package.json').read_text());json.loads((ROOT/package/'package-lock.json').read_text())
subprocess.run(['git','diff','--check'],cwd=ROOT,check=True);subprocess.run(['git','diff','--cached','--check'],cwd=ROOT,check=True)
print('Original assets, real screenshots, bilingual docs/contacts, licenses, private-value exclusion, source headers and Git whitespace checks passed.')
