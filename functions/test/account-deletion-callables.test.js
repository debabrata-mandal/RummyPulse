"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  adminDeleteAccount,
  deleteMyAccount,
} = require("../index");

test("self deletion rejects missing and non-Google authentication", async () => {
  await assert.rejects(
      deleteMyAccount.run({auth: null}),
      (error) => error.code === "unauthenticated",
  );
  await assert.rejects(
      deleteMyAccount.run({
        auth: {
          uid: "user-a",
          token: {
            auth_time: Math.floor(Date.now() / 1000),
            firebase: {sign_in_provider: "password"},
          },
        },
      }),
      (error) => error.code === "failed-precondition",
  );
});

test("administrator deletion rejects missing auth and self-deletion", async () => {
  await assert.rejects(
      adminDeleteAccount.run({auth: null, data: {userId: "user-b"}}),
      (error) => error.code === "unauthenticated",
  );
  await assert.rejects(
      adminDeleteAccount.run({
        auth: {uid: "user-a", token: {}},
        data: {userId: "user-a"},
      }),
      (error) => error.code === "failed-precondition",
  );
});
