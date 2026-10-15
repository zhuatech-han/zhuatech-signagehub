// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { createDirectoryModel } from "./directory-model.js";

function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}

test("a slow previous directory cannot replace the newly selected records", async () => {
  const slow = deferred();
  const model = createDirectoryModel((path) =>
    path === "/admin/roles"
      ? slow.promise
      : Promise.resolve([{ code: "NOTICE" }]),
  );
  const old = model.load("roles");
  assert.equal(await model.load("dictionaries"), true);
  slow.resolve([{ name: "Old role" }]);
  assert.equal(await old, false);
  assert.deepEqual(model.rows.value, [{ code: "NOTICE" }]);
  assert.equal(model.ready.value, true);
  assert.equal(model.available.value, true);
});

test("late account metadata cannot leak into another directory", async () => {
  const metadata = deferred();
  let metadataStarted;
  const started = new Promise((resolve) => (metadataStarted = resolve));
  const model = createDirectoryModel(async (path) => {
    if (path === "/admin-options") {
      metadataStarted();
      return metadata.promise;
    }
    return path === "/admin/users" ? [{ username: "QA" }] : [{ code: "media" }];
  });
  const users = model.load("users");
  await started;
  await model.load("menus");
  metadata.resolve({ roles: [{ id: 1 }], departments: [{ id: 1 }] });
  assert.equal(await users, false);
  assert.deepEqual(model.rows.value, [{ code: "media" }]);
  assert.deepEqual(model.options.value, { roles: [], departments: [] });
});

test("an obsolete failure cannot dismiss a newer loading state or show its error", async () => {
  const first = deferred(),
    next = deferred(),
    errors = [];
  const model = createDirectoryModel(
    (path) => (path === "/admin/roles" ? first.promise : next.promise),
    (e) => errors.push(e),
  );
  const old = model.load("roles"),
    current = model.load("menus");
  first.reject(new Error("old failure"));
  await old;
  assert.equal(model.ready.value, false);
  assert.equal(model.available.value, false);
  assert.deepEqual(errors, []);
  next.resolve([]);
  await current;
  assert.equal(model.available.value, true);
});

test("failed account metadata leaves no editable partial records and can recover", async () => {
  let failing = true,
    recovered = 0;
  const errors = [];
  const model = createDirectoryModel(
    async (path) => {
      if (path === "/admin/users") return [{ username: "QA" }];
      if (failing) throw new Error("metadata unavailable");
      return { roles: [{ id: 1 }], departments: [{ id: 2 }] };
    },
    (e) => errors.push(e.message),
    () => recovered++,
  );
  assert.equal(await model.load("users"), false);
  assert.equal(model.ready.value, true);
  assert.equal(model.available.value, false);
  assert.deepEqual(model.rows.value, []);
  assert.equal(recovered, 0);
  assert.deepEqual(errors, ["metadata unavailable"]);
  failing = false;
  assert.equal(await model.load("users"), true);
  assert.equal(model.available.value, true);
  assert.equal(model.options.value.departments[0].id, 2);
  assert.equal(recovered, 1);
});

test("leaving a workspace prevents its pending read from affecting the next page", async () => {
  const pending = deferred();
  let recovered = 0;
  const model = createDirectoryModel(
    () => pending.promise,
    undefined,
    () => recovered++,
  );
  const result = model.load("roles");
  model.dispose();
  pending.resolve([{ name: "QA" }]);
  assert.equal(await result, false);
  assert.equal(await model.load("roles"), false);
  assert.deepEqual(model.rows.value, []);
  assert.equal(model.available.value, false);
  assert.equal(recovered, 0);
});
