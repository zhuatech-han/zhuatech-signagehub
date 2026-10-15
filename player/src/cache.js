// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import {
  mkdir,
  readFile,
  writeFile,
  open,
  rename,
  unlink,
  readdir,
  lstat,
  chmod,
} from "node:fs/promises";
import path from "node:path";
import { createHash, randomUUID } from "node:crypto";
import {
  validateManifest,
  validateServer,
  chooseRelease,
  filename,
} from "./program.js";
const digest = (b) => createHash("sha256").update(b).digest("hex");
/** Write complete private state then atomically replace the active pointer. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
export async function atomicJSON(file, value) {
  const temp = file + "." + randomUUID() + ".tmp";
  try {
    const handle = await open(temp, "wx", 0o600);
    try {
      await handle.writeFile(JSON.stringify(value));
      await handle.sync();
    } finally {
      await handle.close();
    }
    await rename(temp, file);
  } finally {
    await unlink(temp).catch(() => {});
  }
}
/** Disk-backed player engine; failed downloads never switch active content. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
export class CacheEngine {
  constructor(root, { fetcher = fetch, now = Date.now } = {}) {
    this.root = path.resolve(root);
    this.media = path.join(this.root, "media");
    this.fetcher = fetcher;
    this.now = now;
    this.config = null;
    this.state = null;
    this.error = "";
    this.online = false;
    this.busy = false;
    this.ack = null;
    this.baseMono = performance.now();
    this.monotonicFloor = 0;
    this.clockInvalid = false;
  }
  /** Recover private credentials and validate every cached file after process restart. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
  async init() {
    await mkdir(this.root, { recursive: true, mode: 0o700 });
    if ((await lstat(this.root)).isSymbolicLink())
      throw new Error("CACHE_PATH_INVALID");
    await chmod(this.root, 0o700);
    await mkdir(this.media, { recursive: true, mode: 0o700 });
    if ((await lstat(this.media)).isSymbolicLink())
      throw new Error("CACHE_PATH_INVALID");
    try {
      this.config = JSON.parse(
        await readFile(path.join(this.root, "config.json"), "utf8"),
      );
      this.config.server = validateServer(this.config.server);
      if (!/^[A-Za-z0-9_-]{43}$/.test(this.config.token)) throw Error();
    } catch (e) {
      if (e.code !== "ENOENT") this.error = "CONFIG_INVALID";
      this.config = null;
    }
    try {
      const s = JSON.parse(
        await readFile(path.join(this.root, "state.json"), "utf8"),
      );
      const assets = validateManifest(s.manifest);
      if (
        !this.config ||
        s.configKey !==
          digest(Buffer.from(this.config.server + "\n" + this.config.token))
      )
        throw Error();
      if (
        !Number.isFinite(s.offset) ||
        !Number.isFinite(s.receivedLocal) ||
        !Number.isFinite(s.lastTrusted)
      )
        throw Error();
      for (const a of assets.values())
        if (!(await this.validFile(a))) throw Error();
      this.state = s;
      this.monotonicFloor = s.lastTrusted;
      this.clockInvalid = this.now() < s.receivedLocal - 300000;
    } catch (e) {
      if (e.code !== "ENOENT") this.error = "CACHE_INVALID";
      this.state = null;
    }
    return this;
  }
  /** Bind one explicit CMS and device; old content is cleared before changing identity. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
  async configure(server, token) {
    server = validateServer(server);
    if (!/^[A-Za-z0-9_-]{43}$/.test(token)) throw new Error("TOKEN_INVALID");
    if (this.busy) throw new Error("SYNC_BUSY");
    await this.revoke();
    this.config = { server, token };
    await atomicJSON(path.join(this.root, "config.json"), this.config);
    this.error = "";
    await this.sync();
  }
  async validFile(a) {
    try {
      const file = path.join(this.media, filename(a));
      const info = await lstat(file);
      return (
        info.isFile() &&
        !info.isSymbolicLink() &&
        info.size === a.bytes &&
        digest(await readFile(file)) === a.sha256
      );
    } catch {
      return false;
    }
  }
  /** Clock rollback stops unattended playback until the CMS supplies fresh trusted time. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
  effectiveNow() {
    if (!this.state) return this.now();
    if (this.now() < this.state.receivedLocal - 300000)
      this.clockInvalid = true;
    const elapsed = performance.now() - this.baseMono;
    return Math.max(
      this.now() + this.state.offset,
      this.monotonicFloor + elapsed,
    );
  }
  /** Networking errors preserve a valid cache; revocation clears its playback pointer. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
  async sync() {
    if (this.busy || !this.config) return;
    this.busy = true;
    try {
      const response = await this.fetcher(
        this.config.server + "/api/player/manifest",
        {
          headers: { Authorization: "Bearer " + this.config.token },
          redirect: "error",
          signal: AbortSignal.timeout(10000),
        },
      );
      if (response.status === 401 || response.status === 403) {
        await this.revoke();
        this.online = true;
        this.error = "DEVICE_REVOKED";
        return;
      }
      if (!response.ok) throw new Error("CMS_UNAVAILABLE");
      const raw = await response.text();
      if (raw.length > 2 * 1024 * 1024) throw new Error("PROGRAM_INVALID");
      const m = JSON.parse(raw);
      const assets = validateManifest(m);
      this.online = true;
      if (!this.state || this.state.manifest.revision !== m.revision) {
        for (const a of assets.values()) {
          if (await this.validFile(a)) continue;
          const r = await this.fetcher(this.config.server + a.url, {
            headers: { Authorization: "Bearer " + this.config.token },
            redirect: "error",
            signal: AbortSignal.timeout(60000),
          });
          if (r.status === 401 || r.status === 403) {
            await this.revoke();
            throw new Error("DEVICE_REVOKED");
          }
          if (!r.ok) throw new Error("MEDIA_DOWNLOAD_FAILED");
          const contentLength = Number(r.headers.get("content-length"));
          if (contentLength && contentLength !== a.bytes)
            throw new Error("MEDIA_SIZE_MISMATCH");
          const chunks = [];
          let size = 0;
          for await (const chunk of r.body) {
            size += chunk.byteLength;
            if (size > a.bytes) throw new Error("MEDIA_SIZE_MISMATCH");
            chunks.push(chunk);
          }
          const bytes = Buffer.concat(chunks);
          if (size !== a.bytes || digest(bytes) !== a.sha256)
            throw new Error("MEDIA_CHECKSUM_MISMATCH");
          const dest = path.join(this.media, filename(a)),
            temp = dest + ".part";
          try {
            await writeFile(temp, bytes, { mode: 0o600 });
            await rename(temp, dest);
          } finally {
            await unlink(temp).catch(() => {});
          }
        }
      }
      const playbackError =
        this.error === "MEDIA_PLAYBACK_FAILED" &&
        this.current()?.id === chooseRelease(m, Date.parse(m.serverTime))?.id;
      const local = this.now();
      const next = {
        configKey: digest(
          Buffer.from(this.config.server + "\n" + this.config.token),
        ),
        manifest: m,
        offset: Date.parse(m.serverTime) - local,
        receivedLocal: local,
        lastTrusted: Date.parse(m.serverTime),
      };
      await atomicJSON(path.join(this.root, "state.json"), next);
      this.state = next;
      this.monotonicFloor = next.lastTrusted;
      this.baseMono = performance.now();
      this.clockInvalid = false;
      this.error = playbackError ? "MEDIA_PLAYBACK_FAILED" : "";
      await this.collect(assets);
      await this.heartbeat();
    } catch (e) {
      this.online = false;
      this.error = [
        "PROGRAM_INVALID",
        "PROGRAM_TOO_LARGE",
        "MEDIA_SIZE_MISMATCH",
        "MEDIA_CHECKSUM_MISMATCH",
        "MEDIA_DOWNLOAD_FAILED",
        "DEVICE_REVOKED",
      ].includes(e.message)
        ? e.message
        : "CMS_UNAVAILABLE";
    } finally {
      this.busy = false;
    }
  }
  async collect(assets) {
    const keep = new Set([...assets.values()].map(filename));
    for (const name of await readdir(this.media)) {
      if (/^[a-f0-9]{64}\.(png|mp4)(\.part)?$/.test(name) && !keep.has(name))
        await unlink(path.join(this.media, name)).catch(() => {});
    }
  }
  /** Online revocation removes local access to all cached media and does not silently re-pair. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
  async revoke() {
    this.state = null;
    this.ack = null;
    await unlink(path.join(this.root, "state.json")).catch(() => {});
    for (const name of await readdir(this.media)) {
      if (/^[a-f0-9]{64}\.(png|mp4)(\.part)?$/.test(name))
        await unlink(path.join(this.media, name)).catch(() => {});
    }
  }
  /** Report PLAYING only after a local renderer acknowledged the current release. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
  async heartbeat() {
    if (!this.config) return;
    const active = this.current();
    const playing =
      this.ack &&
      this.ack.id === active?.id &&
      this.now() - this.ack.at < 30000;
    const state =
      this.clockInvalid || this.expired()
        ? "EXPIRED"
        : this.error
          ? "ERROR"
          : playing
            ? "PLAYING"
            : "IDLE";
    try {
      const r = await this.fetcher(
        this.config.server + "/api/player/heartbeat",
        {
          method: "POST",
          headers: {
            Authorization: "Bearer " + this.config.token,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            state,
            appliedRelease: playing ? active.id : null,
            error: this.error || "",
          }),
          redirect: "error",
          signal: AbortSignal.timeout(10000),
        },
      );
      if (r.status === 401 || r.status === 403) {
        await this.revoke();
        this.error = "DEVICE_REVOKED";
      }
      if (this.state) {
        this.state.lastTrusted = this.effectiveNow();
        await atomicJSON(path.join(this.root, "state.json"), this.state);
      }
    } catch {
      this.online = false;
    }
  }
  expired() {
    return (
      !!this.state &&
      this.effectiveNow() >= Date.parse(this.state.manifest.validUntil)
    );
  }
  current() {
    return this.clockInvalid
      ? null
      : chooseRelease(this.state?.manifest, this.effectiveNow());
  }
  /** Public local status excludes credentials, cache paths and customer file contents. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
  status() {
    return {
      configured: !!this.config,
      server: this.config?.server || "",
      screenName: this.state?.manifest.screenName || "",
      online: this.online,
      error: this.clockInvalid
        ? "CLOCK_INVALID"
        : this.expired()
          ? "OFFLINE_LEASE_EXPIRED"
          : this.error,
      revision: this.state?.manifest.revision || "",
      validUntil: this.state?.manifest.validUntil || "",
      releaseId: this.current()?.id || "",
      ready: !!this.current(),
    };
  }
  /** Rendered media are limited to the current authorized and unexpired program. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
  mediaFile(name) {
    const release = this.current();
    if (!release) return null;
    const asset = release.items.find(
      (a) => a.assetId !== undefined && filename(a) === name,
    );
    return asset ? { file: path.join(this.media, name), asset } : null;
  }
}
