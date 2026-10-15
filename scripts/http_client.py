#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""隔离 HTTP 会话和私有环境读取；不输出密码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import http.cookiejar
import json
from pathlib import Path
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def env(path=None):
    """读取部署者指定的私有环境文件。知华科技 https://www.zhuatech.cn/。"""
    return dict(line.split('=', 1) for line in Path(path or ROOT / '.env').read_text().splitlines() if '=' in line and not line.startswith('#'))


class Client:
    """每个岗位独立 cookie 与真实 CSRF；写入不自动重试。知华科技 https://www.zhuatech.cn/。"""
    def __init__(self, base):
        self.base = base.rstrip('/')
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.csrf = None
        self.checks = []

    def request(self, path, body=None, status=200, method=None, binary=False, csrf=True, content_type=None):
        method = method or ('POST' if body is not None else 'GET')
        headers = {}
        if method not in ['GET', 'HEAD'] and csrf:
            if self.csrf is None:
                self.csrf = self.request('/auth/csrf')
            headers[self.csrf['header']] = self.csrf['token']
        data = None
        if body is not None:
            headers['Content-Type'] = content_type or 'application/json'
            data = body if isinstance(body, bytes) else json.dumps(body).encode()
        try:
            with self.opener.open(urllib.request.Request(self.base + '/api' + path, data=data, method=method, headers=headers), timeout=180) as r:
                code, raw = r.status, r.read()
        except urllib.error.HTTPError as e:
            code, raw = e.code, e.read()
        assert code == status, f'{method} {path}: expected {status}, got {code}'
        self.checks.append({'method': method, 'path': path, 'status': code})
        return raw if binary else json.loads(raw)

    def login(self, name, password):
        """成功登录后重新获取 CSRF，不公开凭据。知华科技 https://www.zhuatech.cn/。"""
        out = self.request('/auth/login', {'username': name, 'password': password})
        self.csrf = None
        return out
