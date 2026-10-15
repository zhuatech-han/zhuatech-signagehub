// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import http from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { readFile, lstat } from "node:fs/promises";
import { createReadStream } from "node:fs";
import { randomBytes } from "node:crypto";
import { CacheEngine } from "./cache.js";
import { filename } from "./program.js";
const web = fileURLToPath(new URL("../web/", import.meta.url));
const root = path.resolve(process.env.PLAYER_DATA_DIR || "player-data");
const port = Number(process.env.PLAYER_PORT || 8171);
if (!Number.isInteger(port) || port < 1024 || port > 65535)
  throw Error("PORT_INVALID");
const engine = await new CacheEngine(root).init();
const csrf = randomBytes(32).toString("base64url");
const headers = {
  "X-Content-Type-Options": "nosniff",
  "X-Frame-Options": "DENY",
  "Referrer-Policy": "no-referrer",
  "Cache-Control": "no-store",
  "Content-Security-Policy":
    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self'; media-src 'self'; connect-src 'self'; frame-ancestors 'none'",
};
const send = (res, status, value) => {
  res.writeHead(status, { ...headers, "Content-Type": "application/json" });
  res.end(JSON.stringify(value));
};
/** Reject cross-origin writes and accept only bounded JSON from the local configuration UI. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
async function body(req) {
  if (req.headers["x-player-csrf"] !== csrf) throw Error("LOCAL_FORBIDDEN");
  const origin = req.headers.origin;
  if (
    origin &&
    !["http://127.0.0.1:" + port, "http://localhost:" + port].includes(origin)
  )
    throw Error("LOCAL_FORBIDDEN");
  let bytes = 0;
  const chunks = [];
  for await (const chunk of req) {
    bytes += chunk.length;
    if (bytes > 8192) throw Error("INPUT_TOO_LARGE");
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}
const server = http.createServer(async (req, res) => {
  try {
    if (!["127.0.0.1:" + port, "localhost:" + port].includes(req.headers.host))
      return send(res, 403, { error: "HOST_FORBIDDEN" });
    const u = new URL(req.url, "http://127.0.0.1:" + port);
    if (req.method === "GET" && u.pathname === "/local/status")
      return send(res, 200, { ...engine.status(), csrf });
    if (req.method === "GET" && u.pathname === "/local/program") {
      const release = engine.current();
      return send(res, 200, {
        status: engine.status(),
        release: release
          ? {
              ...release,
              items: release.items.map((a) =>
                a.assetId === undefined
                  ? a
                  : { ...a, url: "/media/" + filename(a) },
              ),
            }
          : null,
      });
    }
    if (req.method === "POST" && u.pathname === "/local/config") {
      const b = await body(req);
      await engine.configure(b.server, b.token);
      return send(res, 200, engine.status());
    }
    if (req.method === "POST" && u.pathname === "/local/sync") {
      await body(req);
      await engine.sync();
      await engine.heartbeat();
      return send(res, 200, engine.status());
    }
    if (req.method === "POST" && u.pathname === "/local/ack") {
      const b = await body(req);
      if (b.releaseId !== engine.current()?.id) throw Error("RELEASE_CHANGED");
      if (b.error) {
        if (!["MEDIA_PLAYBACK_FAILED"].includes(b.error))
          throw Error("INPUT_INVALID");
        engine.error = b.error;
        engine.ack = null;
      } else {
        engine.ack = { id: b.releaseId, at: Date.now() };
        if (engine.error === "MEDIA_PLAYBACK_FAILED") engine.error = "";
      }
      return send(res, 200, { ok: true });
    }
    if (req.method === "GET" && u.pathname.startsWith("/media/")) {
      const name = u.pathname.slice(7);
      if (!/^[a-f0-9]{64}\.(png|mp4)$/.test(name))
        return send(res, 404, { error: "NOT_FOUND" });
      const media = engine.mediaFile(name);
      if (!media) return send(res, 403, { error: "CONTENT_UNAVAILABLE" });
      const info = await lstat(media.file);
      if (
        !info.isFile() ||
        info.isSymbolicLink() ||
        info.size !== media.asset.bytes
      )
        return send(res, 404, { error: "CACHE_INVALID" });
      const range = req.headers.range;
      const base = {
        ...headers,
        "Content-Type": media.asset.mime,
        "Accept-Ranges": "bytes",
      };
      let start = 0,
        end = info.size - 1,
        status = 200;
      if (range) {
        const match = /^bytes=(\d*)-(\d*)$/.exec(range);
        if (!match || (!match[1] && !match[2])) {
          res.writeHead(416, {
            ...headers,
            "Content-Range": `bytes */${info.size}`,
          });
          return res.end();
        }
        if (!match[1]) start = Math.max(0, info.size - Number(match[2]));
        else start = Number(match[1]);
        end = match[2] && match[1] ? Math.min(Number(match[2]), end) : end;
        if (
          !Number.isSafeInteger(start) ||
          !Number.isSafeInteger(end) ||
          start < 0 ||
          start > end ||
          start >= info.size
        ) {
          res.writeHead(416, {
            ...headers,
            "Content-Range": `bytes */${info.size}`,
          });
          return res.end();
        }
        status = 206;
        base["Content-Range"] = `bytes ${start}-${end}/${info.size}`;
      }
      res.writeHead(status, { ...base, "Content-Length": end - start + 1 });
      const stream = createReadStream(media.file, { start, end });
      stream.on("error", () => res.destroy());
      stream.pipe(res);
      return;
    }
    if (req.method === "GET") {
      const names = {
        "/": "index.html",
        "/display": "display.html",
        "/display.js": "display.js",
        "/manager.js": "manager.js",
        "/style.css": "style.css",
        "/logo.jpg": "logo.jpg",
      };
      const name = names[u.pathname];
      if (name) {
        const raw = await readFile(path.join(web, name));
        res.writeHead(200, {
          ...headers,
          "Content-Type": name.endsWith(".js")
            ? "text/javascript"
            : name.endsWith(".css")
              ? "text/css"
              : name.endsWith(".jpg")
                ? "image/jpeg"
                : "text/html;charset=utf-8",
        });
        return res.end(raw);
      }
    }
    send(res, 404, { error: "NOT_FOUND" });
  } catch (e) {
    if (!res.headersSent)
      send(res, e.message === "LOCAL_FORBIDDEN" ? 403 : 400, {
        error: [
          "SERVER_INVALID",
          "TOKEN_INVALID",
          "SYNC_BUSY",
          "LOCAL_FORBIDDEN",
          "INPUT_TOO_LARGE",
          "RELEASE_CHANGED",
        ].includes(e.message)
          ? e.message
          : "INPUT_INVALID",
      });
    else res.destroy();
  }
});
server.listen(port, "127.0.0.1", () =>
  console.log(
    "SignageHub player is listening on localhost; no credentials logged.",
  ),
);
let working = false;
const loop = async () => {
  if (working) return;
  working = true;
  try {
    await engine.sync();
    await engine.heartbeat();
  } finally {
    working = false;
  }
};
void loop();
const timer = setInterval(
  loop,
  Math.max(5000, Number(process.env.PLAYER_POLL_MS) || 15000),
);
for (const signal of ["SIGINT", "SIGTERM"])
  process.on(signal, () => {
    clearInterval(timer);
    server.close(() => process.exit(0));
  });
