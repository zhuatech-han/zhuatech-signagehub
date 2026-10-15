// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { test, afterEach } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, rm, readFile, writeFile, stat } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { createHash } from "node:crypto";
import { CacheEngine } from "../src/cache.js";
import {
  validateManifest,
  chooseRelease,
  validateServer,
  filename,
} from "../src/program.js";
const dirs = [];
afterEach(async () => {
  for (const dir of dirs.splice(0))
    await rm(dir, { recursive: true, force: true });
});
const bytes = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+cL4sAAAAASUVORK5CYII=",
  "base64",
);
const sha = (b) => createHash("sha256").update(b).digest("hex");
const now = Date.parse("2026-10-14T04:00:00Z");
function manifest(id = "11111111-1111-4111-8111-111111111111", media = bytes) {
  return {
    schema: 1,
    screenId: 1,
    screenName: "QA screen",
    revision: sha(Buffer.from(id)),
    serverTime: new Date(now).toISOString(),
    validUntil: new Date(now + 3600000).toISOString(),
    programs: [
      {
        release: {
          id,
          name: "QA",
          nameEn: "QA",
          orientation: "LANDSCAPE",
          fit: "contain",
          background: "#000000",
          items: [
            {
              assetId: 1,
              sha256: sha(media),
              bytes: media.length,
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
  };
}
async function fixture() {
  const root = await mkdtemp(path.join(os.tmpdir(), "signage-cache-test-"));
  dirs.push(root);
  let m = manifest(),
    content = bytes,
    online = true,
    headers = [],
    sent = [];
  const fetcher = async (url, options) => {
    headers.push(options.headers);
    if (!online) throw Error("OFFLINE");
    if (url.endsWith("/manifest")) return new Response(JSON.stringify(m));
    if (url.includes("/media/"))
      return new Response(content, {
        headers: { "Content-Length": String(content.length) },
      });
    sent.push(JSON.parse(options.body));
    return new Response("{}");
  };
  const engine = await new CacheEngine(root, {
    fetcher,
    now: () => now,
  }).init();
  return {
    engine,
    root,
    headers,
    sent,
    fetcher,
    setManifest: (v) => (m = v),
    setBytes: (v) => (content = v),
    offline: () => (online = false),
  };
}
test("complete media cache and private files survive a process restart", async () => {
  const f = await fixture();
  await f.engine.configure("https://cms.example.invalid", "A".repeat(43));
  assert.equal(f.engine.current().id, manifest().programs[0].release.id);
  assert.equal(
    (await stat(path.join(f.root, "config.json"))).mode & 0o777,
    0o600,
  );
  f.offline();
  const next = await new CacheEngine(f.root, {
    fetcher: f.fetcher,
    now: () => now,
  }).init();
  await next.sync();
  assert.equal(next.current().id, f.engine.current().id);
  assert.equal(next.status().error, "CMS_UNAVAILABLE");
  assert.ok(next.mediaFile(filename(manifest().programs[0].release.items[0])));
});
test("bad checksum never replaces the previous active program", async () => {
  const f = await fixture();
  await f.engine.configure("https://cms.example.invalid", "A".repeat(43));
  const original = f.engine.current().id;
  f.setManifest(
    manifest(
      "22222222-2222-4222-8222-222222222222",
      Buffer.from("changed-image"),
    ),
  );
  f.setBytes(Buffer.from("damaged-image"));
  await f.engine.sync();
  assert.equal(f.engine.current().id, original);
  assert.equal(f.engine.status().error, "MEDIA_CHECKSUM_MISMATCH");
  assert.equal(
    JSON.parse(await readFile(path.join(f.root, "state.json"))).manifest
      .programs[0].release.id,
    original,
  );
});
test("short download fails without activating a partial program", async () => {
  const f = await fixture();
  await f.engine.configure("https://cms.example.invalid", "A".repeat(43));
  f.setManifest(
    manifest(
      "33333333-3333-4333-8333-333333333333",
      Buffer.from("longer-new-content"),
    ),
  );
  f.setBytes(Buffer.from("short"));
  await f.engine.sync();
  assert.equal(f.engine.status().error, "MEDIA_SIZE_MISMATCH");
  assert.equal(f.engine.current().id, manifest().programs[0].release.id);
});
test("corrupted disk cache cannot play after a cold restart", async () => {
  const f = await fixture();
  await f.engine.configure("https://cms.example.invalid", "A".repeat(43));
  await writeFile(
    path.join(
      f.root,
      "media",
      filename(manifest().programs[0].release.items[0]),
    ),
    "bad",
  );
  const next = await new CacheEngine(f.root, {
    fetcher: f.fetcher,
    now: () => now,
  }).init();
  assert.equal(next.current(), null);
  assert.equal(next.status().error, "CACHE_INVALID");
});
test("401 removes cached playback and local media authorization", async () => {
  const f = await fixture();
  await f.engine.configure("https://cms.example.invalid", "A".repeat(43));
  f.engine.fetcher = async () => new Response("{}", { status: 401 });
  await f.engine.sync();
  assert.equal(f.engine.current(), null);
  assert.equal(f.engine.status().error, "DEVICE_REVOKED");
  assert.equal(
    f.engine.mediaFile(filename(manifest().programs[0].release.items[0])),
    null,
  );
});
test("lease expiration stops offline playback", async () => {
  const f = await fixture();
  await f.engine.configure("https://cms.example.invalid", "A".repeat(43));
  f.engine.now = () => now + 3600001;
  assert.equal(f.engine.current(), null);
  assert.equal(f.engine.status().error, "OFFLINE_LEASE_EXPIRED");
});
test("clock rollback after a restart fails closed", async () => {
  const f = await fixture();
  await f.engine.configure("https://cms.example.invalid", "A".repeat(43));
  const next = await new CacheEngine(f.root, {
    fetcher: f.fetcher,
    now: () => now - 600001,
  }).init();
  assert.equal(next.current(), null);
  assert.equal(next.status().error, "CLOCK_INVALID");
});
test("heartbeat distinguishes cached content from an actual renderer acknowledgement", async () => {
  const f = await fixture();
  await f.engine.configure("https://cms.example.invalid", "A".repeat(43));
  assert.equal(f.sent.at(-1).state, "IDLE");
  f.engine.ack = { id: f.engine.current().id, at: now };
  await f.engine.heartbeat();
  assert.equal(f.sent.at(-1).state, "PLAYING");
  assert.equal(f.sent.at(-1).appliedRelease, f.engine.current().id);
});
test("status never returns a token or private path", async () => {
  const f = await fixture();
  await f.engine.configure("https://cms.example.invalid", "A".repeat(43));
  const s = JSON.stringify(f.engine.status());
  assert.ok(!s.includes("A".repeat(43)));
  assert.ok(!s.includes(f.root));
});
test("manifest lease cannot silently exceed 72 hours", () => {
  const m = manifest();
  m.validUntil = new Date(now + 73 * 3600000).toISOString();
  assert.throws(() => validateManifest(m), /PROGRAM_INVALID/);
});
test("absolute media URL and unknown schemas are rejected", () => {
  const m = manifest();
  m.programs[0].release.items[0].url = "https://other.example.invalid/pixel";
  assert.throws(() => validateManifest(m), /PROGRAM_INVALID/);
  const n = manifest();
  n.schema = 2;
  assert.throws(() => validateManifest(n), /PROGRAM_INVALID/);
});
test("plain text remains data and does not execute markup", () => {
  const m = manifest();
  m.programs[0].release.items = [
    { text: "<script>alert(1)</script>", seconds: 10, color: "#ffffff" },
  ];
  assert.equal(validateManifest(m).size, 0);
  assert.equal(
    chooseRelease(m, now).items[0].text,
    "<script>alert(1)</script>",
  );
});
test("schedule boundaries select baseline and return to it after expiry", () => {
  const m = manifest(),
    base = m.programs[0].release.id;
  const r = {
    ...m.programs[0].release,
    id: "44444444-4444-4444-8444-444444444444",
  };
  m.programs.push({
    startsAt: new Date(now + 10000).toISOString(),
    endsAt: new Date(now + 20000).toISOString(),
    release: r,
  });
  validateManifest(m);
  assert.equal(chooseRelease(m, now + 9999).id, base);
  assert.equal(chooseRelease(m, now + 10000).id, r.id);
  assert.equal(chooseRelease(m, now + 19999).id, r.id);
  assert.equal(chooseRelease(m, now + 20000).id, base);
});
test("overlapping intervals and duplicate release identities are rejected", () => {
  const m = manifest();
  m.programs.push(
    {
      startsAt: new Date(now).toISOString(),
      endsAt: new Date(now + 20000).toISOString(),
      release: m.programs[0].release,
    },
    {
      startsAt: new Date(now + 10000).toISOString(),
      endsAt: new Date(now + 30000).toISOString(),
      release: m.programs[0].release,
    },
  );
  assert.throws(() => validateManifest(m), /PROGRAM_INVALID/);
});
test("config URLs reject credentials, cleartext external sites and subpaths", () => {
  for (const s of [
    "http://cms.example.invalid",
    "https://user:password@cms.example.invalid",
    "https://cms.example.invalid/api",
    "https://cms.example.invalid/?token=test",
  ])
    assert.throws(() => validateServer(s));
  assert.equal(
    validateServer("https://cms.example.invalid"),
    "https://cms.example.invalid",
  );
  assert.equal(
    validateServer("http://127.0.0.1:8170"),
    "http://127.0.0.1:8170",
  );
});
