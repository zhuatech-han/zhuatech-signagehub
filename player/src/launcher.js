// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { spawn } from "node:child_process";
import { mkdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { atomicJSON } from "./cache.js";
const root = path.resolve(process.env.PLAYER_DATA_DIR || "player-data");
await mkdir(root, { recursive: true, mode: 0o700 });
let child = null,
  stopping = false,
  timer = null,
  failures = 0,
  started = 0;
/** Restart only the owned player process; cached content remains private and persistent. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
function start() {
  if (stopping) return;
  started = Date.now();
  child = spawn(
    process.execPath,
    [fileURLToPath(new URL("./server.js", import.meta.url))],
    { env: process.env, stdio: "inherit" },
  );
  void atomicJSON(path.join(root, "runtime.json"), {
    supervisorPid: process.pid,
    playerPid: child.pid,
  }).catch(() => {});
  child.on("error", () => console.error("PLAYER_START_FAILED"));
  child.on("exit", () => {
    if (stopping) {
      process.exit(0);
      return;
    }
    failures = Date.now() - started > 60000 ? 0 : failures + 1;
    const delay = Math.min(10000, 100 * 2 ** Math.min(7, failures));
    console.error("PLAYER_RESTART_PENDING");
    timer = setTimeout(start, delay);
  });
}
for (const signal of ["SIGTERM", "SIGINT"])
  process.on(signal, () => {
    stopping = true;
    if (timer) clearTimeout(timer);
    if (child?.exitCode === null) {
      child.kill("SIGTERM");
      setTimeout(() => {
        child?.kill("SIGKILL");
        process.exit(0);
      }, 3000).unref();
    } else process.exit(0);
  });
start();
