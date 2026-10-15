// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { test, afterEach } from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import net from "node:net";
import { spawn } from "node:child_process";
import { mkdtemp, writeFile, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createHash } from "node:crypto";
const resources = [];
const bytes = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+cL4sAAAAASUVORK5CYII=",
  "base64",
);
const hash = (b) => createHash("sha256").update(b).digest("hex");
const token = "A".repeat(43);
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function port() {
  const s = net.createServer();
  await new Promise((resolve) => s.listen(0, "127.0.0.1", resolve));
  const p = s.address().port;
  await new Promise((resolve) => s.close(resolve));
  return p;
}
async function stop(child) {
  if (child.exitCode !== null) return;
  child.kill("SIGTERM");
  await Promise.race([
    new Promise((resolve) => child.once("exit", resolve)),
    sleep(3000),
  ]);
  if (child.exitCode === null && child.signalCode === null)
    child.kill("SIGKILL");
}
afterEach(async () => {
  for (const r of resources.splice(0)) {
    await stop(r.child);
    await new Promise((resolve) => r.cms.close(resolve));
    await rm(r.root, { recursive: true, force: true });
  }
});
async function launch(root, p, supervised = false) {
  const child = spawn(
    process.execPath,
    [
      fileURLToPath(
        new URL(
          supervised ? "../src/launcher.js" : "../src/server.js",
          import.meta.url,
        ),
      ),
    ],
    {
      env: {
        PATH: process.env.PATH,
        PLAYER_DATA_DIR: root,
        PLAYER_PORT: String(p),
        PLAYER_POLL_MS: "5000",
      },
      stdio: ["ignore", "ignore", "ignore"],
    },
  );
  const url = "http://127.0.0.1:" + p;
  for (let n = 0; n < 100; n++) {
    try {
      const r = await fetch(url + "/local/status", {
        signal: AbortSignal.timeout(500),
      });
      if (r.ok) return { child, url };
    } catch {
      /* Wait for the actual local server, not a fixed startup duration. */
    }
    await sleep(100);
  }
  await stop(child);
  throw Error("PLAYER_START_FAILED");
}
async function fixture(supervised = false) {
  const root = await mkdtemp(path.join(os.tmpdir(), "signage-server-test-"));
  let revoked = false;
  const releaseId = "55555555-5555-4555-8555-555555555555";
  const cms = http.createServer((req, res) => {
    if (revoked || req.headers.authorization !== "Bearer " + token) {
      res.writeHead(403);
      return res.end("{}");
    }
    if (req.url === "/api/player/manifest") {
      res.setHeader("Content-Type", "application/json");
      return res.end(
        JSON.stringify({
          schema: 1,
          screenId: 1,
          screenName: "QA network player",
          revision: hash(Buffer.from("revision")),
          serverTime: new Date().toISOString(),
          validUntil: new Date(Date.now() + 3600000).toISOString(),
          programs: [
            {
              release: {
                id: releaseId,
                name: "QA",
                nameEn: "QA",
                orientation: "LANDSCAPE",
                fit: "contain",
                background: "#000000",
                items: [
                  {
                    assetId: 1,
                    sha256: hash(bytes),
                    bytes: bytes.length,
                    mime: "image/png",
                    kind: "IMAGE",
                    url: "/api/player/media/1",
                    seconds: 10,
                    color: "#ffffff",
                  },
                ],
              },
            },
          ],
        }),
      );
    }
    if (req.url === "/api/player/media/1") {
      res.setHeader("Content-Length", bytes.length);
      return res.end(bytes);
    }
    res.end("{}");
  });
  await new Promise((resolve) => cms.listen(0, "127.0.0.1", resolve));
  await writeFile(
    path.join(root, "config.json"),
    JSON.stringify({ server: "http://127.0.0.1:" + cms.address().port, token }),
    { mode: 0o600 },
  );
  const p = await port();
  const started = await launch(root, p, supervised);
  const resource = { root, cms, p, ...started, revoke: () => (revoked = true) };
  resources.push(resource);
  for (let n = 0; n < 100; n++) {
    const s = await (await fetch(resource.url + "/local/status")).json();
    if (s.ready) return resource;
    await sleep(100);
  }
  throw Error("SYNC_FAILED");
}
test("real local server caches a program and serves verified bytes with HTTP ranges", async () => {
  const f = await fixture();
  const s = await (await fetch(f.url + "/local/status")).json();
  assert.equal(s.ready, true);
  const media = f.url + "/media/" + hash(bytes) + ".png";
  const r = await fetch(media, { headers: { Range: "bytes=2-7" } });
  assert.equal(r.status, 206);
  assert.equal(r.headers.get("Content-Range"), `bytes 2-7/${bytes.length}`);
  assert.deepEqual(Buffer.from(await r.arrayBuffer()), bytes.subarray(2, 8));
});
test("local writes reject missing CSRF and a foreign Origin", async () => {
  const f = await fixture();
  const status = await (await fetch(f.url + "/local/status")).json();
  const missing = await fetch(f.url + "/local/sync", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: "{}",
  });
  assert.equal(missing.status, 403);
  const foreign = await fetch(f.url + "/local/sync", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Player-CSRF": status.csrf,
      Origin: "https://evil.example.invalid",
    },
    body: "{}",
  });
  assert.equal(foreign.status, 403);
});
test("host-header rebinding and unauthorized file paths are rejected", async () => {
  const f = await fixture();
  const code = await new Promise((resolve, reject) => {
    const req = http.get(
      f.url + "/local/status",
      { headers: { Host: "evil.example.invalid" } },
      (res) => {
        res.resume();
        resolve(res.statusCode);
      },
    );
    req.on("error", reject);
  });
  assert.equal(code, 403);
  const file = await fetch(
    f.url + "/media/" + hash(Buffer.from("other")) + ".png",
  );
  assert.equal(file.status, 403);
  const unknown = await fetch(f.url + "/config.json");
  assert.equal(unknown.status, 404);
});
test("API never exposes the pairing token", async () => {
  const f = await fixture();
  for (const route of ["/local/status", "/local/program", "/"]) {
    const text = await (await fetch(f.url + route)).text();
    assert.ok(!text.includes(token));
    assert.ok(!text.includes(f.root));
  }
});
test("video-style invalid or out-of-bounds ranges fail safely", async () => {
  const f = await fixture();
  for (const range of ["bytes=999999-", "bytes=20-5", "bytes=0-2,4-6"]) {
    const r = await fetch(f.url + "/media/" + hash(bytes) + ".png", {
      headers: { Range: range },
    });
    assert.equal(r.status, 416);
  }
});
test("actual process restart while CMS is offline retains the disk cache", async () => {
  const f = await fixture();
  await new Promise((resolve) => f.cms.close(resolve));
  await stop(f.child);
  const second = await launch(f.root, f.p);
  f.child = second.child;
  const s = await (await fetch(f.url + "/local/status")).json();
  assert.equal(s.ready, true);
  const r = await fetch(f.url + "/media/" + hash(bytes) + ".png");
  assert.equal(r.status, 200);
  assert.deepEqual(Buffer.from(await r.arrayBuffer()), bytes);
});
test("online revocation stops local playback and media reads", async () => {
  const f = await fixture();
  f.revoke();
  const status = await (await fetch(f.url + "/local/status")).json();
  await fetch(f.url + "/local/sync", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Player-CSRF": status.csrf,
    },
    body: "{}",
  });
  const s = await (await fetch(f.url + "/local/status")).json();
  assert.equal(s.ready, false);
  assert.equal(s.error, "DEVICE_REVOKED");
  assert.equal(
    (await fetch(f.url + "/media/" + hash(bytes) + ".png")).status,
    403,
  );
});

test("supervisor restarts only its owned player after a forced process exit", async () => {
  const f = await fixture(true);
  const runtime = path.join(f.root, "runtime.json");
  let before;
  for (let i = 0; i < 20; i++) {
    try {
      before = JSON.parse(await readFile(runtime, "utf8"));
      break;
    } catch {
      await sleep(100);
    }
  }
  assert.equal(before.supervisorPid, f.child.pid);
  assert.ok(before.playerPid > 0);
  process.kill(before.playerPid, "SIGKILL");
  let recovered = false;
  for (let i = 0; i < 80; i++) {
    try {
      const info = JSON.parse(await readFile(runtime, "utf8"));
      const status = await (await fetch(f.url + "/local/status")).json();
      if (info.playerPid !== before.playerPid && status.ready) {
        recovered = true;
        break;
      }
    } catch {
      /* Wait for the replacement player to recover its disk cache. */
    }
    await sleep(100);
  }
  assert.equal(recovered, true);
});

test("successful CMS sync cannot erase a renderer playback failure", async () => {
  const f = await fixture();
  const status = await (await fetch(f.url + "/local/status")).json();
  const options = {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Player-CSRF": status.csrf,
    },
  };
  await fetch(f.url + "/local/ack", {
    ...options,
    body: JSON.stringify({
      releaseId: status.releaseId,
      error: "MEDIA_PLAYBACK_FAILED",
    }),
  });
  await fetch(f.url + "/local/sync", { ...options, body: "{}" });
  assert.equal(
    (await (await fetch(f.url + "/local/status")).json()).error,
    "MEDIA_PLAYBACK_FAILED",
  );
  await fetch(f.url + "/local/ack", {
    ...options,
    body: JSON.stringify({ releaseId: status.releaseId }),
  });
  assert.equal((await (await fetch(f.url + "/local/status")).json()).error, "");
});
