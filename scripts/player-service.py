#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Generate an explicit OS startup file; installation remains a local operator action. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2."""
import argparse,plistlib,os,subprocess
from pathlib import Path
from xml.sax.saxutils import escape
p=argparse.ArgumentParser();p.add_argument('--platform',choices=['macos','systemd','windows'],required=True);p.add_argument('--node',required=True);p.add_argument('--player-dir',required=True);p.add_argument('--data-dir',required=True);p.add_argument('--port',type=int,default=8171);p.add_argument('--output',required=True);a=p.parse_args()
assert 1024<=a.port<=65535
for s in [a.node,a.player_dir,a.data_dir,a.output]:assert not any(ord(c)<32 for c in s),'Control characters are not accepted'
node=str(Path(a.node).resolve());player=Path(a.player_dir).resolve();data=Path(a.data_dir).resolve();assert (player/'src/launcher.js').is_file();launch=str(player/'src/launcher.js');out=Path(a.output).resolve();assert not out.exists(),'Output already exists'
if a.platform=='macos':
 body=plistlib.dumps({'Label':'cn.zhuatech.signagehub.player','ProgramArguments':[node,launch],'WorkingDirectory':str(player),'EnvironmentVariables':{'PLAYER_DATA_DIR':str(data),'PLAYER_PORT':str(a.port)},'RunAtLoad':True,'KeepAlive':True,'StandardOutPath':str(data/'runtime.log'),'StandardErrorPath':str(data/'runtime-error.log')})
elif a.platform=='systemd':
 def quoted(s):return '"'+str(s).replace('\\','\\\\').replace('"','\\"').replace('%','%%')+'"'
 body=('[Unit]\nDescription=ZhiHua SignageHub player\nAfter=network-online.target\n\n[Service]\nType=simple\nWorkingDirectory='+quoted(player)+'\nExecStart='+quoted(node)+' '+quoted(launch)+'\nEnvironment='+quoted('PLAYER_DATA_DIR='+str(data))+'\nEnvironment='+quoted('PLAYER_PORT='+str(a.port))+'\nRestart=always\nRestartSec=5\nNoNewPrivileges=true\n\n[Install]\nWantedBy=default.target\n').encode()
else:
 arguments=subprocess.list2cmdline([launch]);body=('<?xml version="1.0" encoding="UTF-8"?><Task version="1.4" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task"><RegistrationInfo><Description>ZhiHua SignageHub player</Description></RegistrationInfo><Triggers><LogonTrigger><Enabled>true</Enabled></LogonTrigger></Triggers><Principals><Principal id="Author"><LogonType>InteractiveToken</LogonType><RunLevel>LeastPrivilege</RunLevel></Principal></Principals><Settings><MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy><ExecutionTimeLimit>PT0S</ExecutionTimeLimit><RestartOnFailure><Interval>PT1M</Interval><Count>3</Count></RestartOnFailure></Settings><Actions Context="Author"><Exec><Command>'+escape(node)+'</Command><Arguments>'+escape(arguments)+'</Arguments><WorkingDirectory>'+escape(str(player))+'</WorkingDirectory></Exec></Actions></Task>').encode()
 # Windows task sets its current directory to the player; private player-data is its default cache.
 assert data==player/'player-data' and a.port==8171,'Windows template uses default player-data and port 8171'
out.parent.mkdir(parents=True,exist_ok=True);fd=os.open(out,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'wb') as f:f.write(body)
print('Startup file generated. Review and install it on the target computer; no OS settings were changed.')
