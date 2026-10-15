#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Wait for actual frontend-to-database readiness. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2."""
import argparse
import json
import time
import urllib.error
import urllib.parse
import urllib.request


def wait_for_ready(base, timeout=180):
    """Only retry a safe health GET; never replay business writes. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2."""
    parsed = urllib.parse.urlsplit(base)
    assert parsed.scheme in ['http', 'https'] and parsed.hostname
    assert not parsed.username and not parsed.password and not parsed.query and not parsed.fragment
    assert 1 <= timeout <= 600
    url = base.rstrip('/') + '/actuator/health/readiness'
    deadline = time.monotonic() + timeout
    last = 'no response'
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=min(5, max(0.1, deadline-time.monotonic()))) as response:
                data = json.loads(response.read(16384))
                if response.status == 200 and isinstance(data, dict) and data.get('status') == 'UP':
                    return
                last = 'readiness is not UP'
        except urllib.error.HTTPError as error:
            last = 'HTTP ' + str(error.code)
        except (urllib.error.URLError, OSError, ValueError):
            last = 'connection or response unavailable'
        time.sleep(min(1, max(0, deadline-time.monotonic())))
    raise RuntimeError('Frontend gateway/database readiness timeout: ' + last)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', default='http://127.0.0.1:8170')
    parser.add_argument('--timeout', type=int, default=180)
    args = parser.parse_args()
    wait_for_ready(args.base, args.timeout)
    print('Frontend gateway and database readiness confirmed.')
