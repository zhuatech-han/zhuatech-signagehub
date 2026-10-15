// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** Validate all untrusted CMS content before exposing a cached program. ZhiHua: https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
export function validateManifest(m) {
  const fail = () => {
    throw new Error("PROGRAM_INVALID");
  };
  if (
    !m ||
    m.schema !== 1 ||
    !Number.isSafeInteger(m.screenId) ||
    m.screenId < 1 ||
    typeof m.screenName !== "string" ||
    m.screenName.length > 120 ||
    !/^[a-f0-9]{64}$/.test(m.revision) ||
    !Array.isArray(m.programs) ||
    m.programs.length > 101
  )
    fail();
  const server = Date.parse(m.serverTime),
    until = Date.parse(m.validUntil);
  if (
    !Number.isFinite(server) ||
    !Number.isFinite(until) ||
    until <= server ||
    until - server > 72 * 3600000 + 1000
  )
    fail();
  const assets = new Map();
  let baseline = 0;
  const windows = [];
  const releases = new Map();
  for (const p of m.programs) {
    if (p.startsAt === undefined && p.endsAt === undefined) {
      if (++baseline > 1) fail();
    } else {
      const start = Date.parse(p.startsAt),
        end = Date.parse(p.endsAt);
      if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start)
        fail();
      windows.push([start, end]);
    }
    const r = p.release;
    if (
      !r ||
      !/^[a-f0-9-]{36}$/.test(r.id) ||
      !Array.isArray(r.items) ||
      !r.items.length ||
      r.items.length > 100 ||
      !["LANDSCAPE", "PORTRAIT"].includes(r.orientation) ||
      !["contain", "cover"].includes(r.fit) ||
      !/^#[a-f0-9]{6}$/i.test(r.background)
    )
      fail();
    if (releases.has(r.id) && releases.get(r.id) !== JSON.stringify(r)) fail();
    releases.set(r.id, JSON.stringify(r));
    for (const item of r.items) {
      if (
        !Number.isInteger(item.seconds) ||
        item.seconds < 2 ||
        item.seconds > 600 ||
        !/^#[a-f0-9]{6}$/i.test(item.color)
      )
        fail();
      if (item.assetId === undefined) {
        if (
          typeof item.text !== "string" ||
          !item.text.trim() ||
          item.text.length > 4000
        )
          fail();
      } else {
        if (
          !Number.isSafeInteger(item.assetId) ||
          item.assetId < 1 ||
          !/^[a-f0-9]{64}$/.test(item.sha256) ||
          !Number.isSafeInteger(item.bytes) ||
          item.bytes < 1 ||
          item.bytes > 64 * 1024 * 1024 ||
          !["IMAGE", "VIDEO"].includes(item.kind) ||
          item.mime !== (item.kind === "IMAGE" ? "image/png" : "video/mp4") ||
          item.url !== `/api/player/media/${item.assetId}`
        )
          fail();
        if (
          assets.has(item.sha256) &&
          assets.get(item.sha256).bytes !== item.bytes
        )
          fail();
        assets.set(item.sha256, item);
      }
    }
  }
  windows.sort((a, b) => a[0] - b[0]);
  for (let i = 1; i < windows.length; i++)
    if (windows[i][0] < windows[i - 1][1]) fail();
  if ([...assets.values()].reduce((n, a) => n + a.bytes, 0) > 256 * 1024 * 1024)
    throw new Error("PROGRAM_TOO_LARGE");
  return assets;
}
/** Scheduled content wins only within its exact interval; otherwise return the baseline. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
export function chooseRelease(m, now) {
  if (!m || now >= Date.parse(m.validUntil)) return null;
  const scheduled = m.programs.find(
    (p) =>
      p.startsAt !== undefined &&
      Date.parse(p.startsAt) <= now &&
      now < Date.parse(p.endsAt),
  );
  return (
    (scheduled || m.programs.find((p) => p.startsAt === undefined))?.release ||
    null
  );
}
/** Only explicit HTTPS origins or localhost development are allowed; never follow media URLs. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
export function validateServer(value) {
  const u = new URL(value);
  if (
    u.username ||
    u.password ||
    u.search ||
    u.hash ||
    u.pathname !== "/" ||
    !(
      u.protocol === "https:" ||
      (u.protocol === "http:" &&
        ["127.0.0.1", "localhost", "[::1]"].includes(u.hostname))
    )
  )
    throw new Error("SERVER_INVALID");
  return u.origin;
}
/** Content paths are derived from a checked digest, never a supplied pathname. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
export function filename(a) {
  return a.sha256 + (a.kind === "IMAGE" ? ".png" : ".mp4");
}
