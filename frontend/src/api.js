// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

let csrf = null;
/** 身份改变清空CSRF；不保存密码或分享凭据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function resetApi() {
  csrf = null;
}
/** 同源写入带CSRF，不自动重试写操作，网络中断明示结果未知。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function api(path, options = {}) {
  try {
    return await request(path, options);
  } catch (e) {
    if (e.status != null) throw e;
    throw Object.assign(
      new Error(
        !["GET", "HEAD"].includes(options.method || "GET")
          ? "RESULT_UNKNOWN"
          : "NETWORK_ERROR",
      ),
      { status: 0 },
    );
  }
}
/** 普通请求30秒、素材上传120秒截止；超时不重放写入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function request(path, options = {}) {
  const method = options.method || "GET";
  const write = !["GET", "HEAD"].includes(method);
  if (write && !csrf) {
    const r = await fetch("/api/auth/csrf", {
      credentials: "same-origin",
      cache: "no-store",
      signal: AbortSignal.timeout(30000),
    });
    if (!r.ok)
      throw Object.assign(new Error("UNAUTHENTICATED"), { status: r.status });
    csrf = await r.json();
  }
  const headers = { ...options.headers };
  if (write) headers[csrf.header] = csrf.token;
  let body = options.body;
  if (body != null && !(body instanceof FormData)) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(body);
  }
  let r;
  try {
    r = await fetch("/api" + path, {
      ...options,
      method,
      headers,
      body,
      credentials: "same-origin",
      cache: "no-store",
      signal: AbortSignal.timeout(body instanceof FormData ? 120000 : 30000),
    });
  } catch {
    throw Object.assign(new Error(write ? "RESULT_UNKNOWN" : "NETWORK_ERROR"), {
      status: 0,
    });
  }
  if (!r.ok) {
    // A stale CSRF token after server restart can return 403 before authentication. Probe only identity, never replay the write.
    if (write && r.status === 403) {
      try {
        const identity = await fetch("/api/auth/me", {
          credentials: "same-origin",
          cache: "no-store",
          signal: AbortSignal.timeout(30000),
        });
        if (identity.status === 401) {
          csrf = null;
          throw Object.assign(new Error("UNAUTHENTICATED"), { status: 401 });
        }
      } catch (e) {
        if (e.status === 401) throw e;
      }
    }
    let b;
    try {
      b = await r.json();
    } catch {
      b = { code: "HTTP_ERROR" };
    }
    throw Object.assign(
      new Error(
        b?.code ||
          (r.status === 403
            ? "FORBIDDEN"
            : r.status === 401
              ? "UNAUTHENTICATED"
              : "HTTP_ERROR"),
      ),
      {
        status: r.status,
        row: b?.row,
      },
    );
  }
  if (r.status === 204) return null;
  return options.binary ? r.blob() : r.json();
}
/** 授权请求后保存附件/CSV，不产生公开的私有文件地址。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function download(path, name, options = {}) {
  const blob = await api(path, { ...options, binary: true });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
