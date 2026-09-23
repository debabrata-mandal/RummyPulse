"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  adminCreateManagedProfile,
  adminUpdateGamePlayerMapping,
  adminUpdateManagedProfile,
} = require("../index");

test("managed-profile and review mapping endpoints require authentication", async () => {
  await assert.rejects(
      adminCreateManagedProfile.run({auth: null, data: {}}),
      (error) => error.code === "unauthenticated",
  );
  await assert.rejects(
      adminUpdateManagedProfile.run({auth: null, data: {}}),
      (error) => error.code === "unauthenticated",
  );
  await assert.rejects(
      adminUpdateGamePlayerMapping.run({auth: null, data: {}}),
      (error) => error.code === "unauthenticated",
  );
});
