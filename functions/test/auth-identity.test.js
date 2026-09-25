"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {authenticationProvider} = require("../lib/auth-identity");

test("authentication provider comes from the verified token", () => {
  const token = {firebase: {sign_in_provider: "google.com"}};
  assert.equal(authenticationProvider(token, [{providerId: "password"}]), "Google");
});

test("authentication provider uses trusted linked-provider data as fallback", () => {
  assert.equal(authenticationProvider({}, [{providerId: "google.com"}]), "Google");
  assert.equal(authenticationProvider({}, [{providerId: "phone"}]), "Phone");
});

test("unknown providers can never become managed profiles", () => {
  assert.equal(authenticationProvider(
      {firebase: {sign_in_provider: "managed"}},
      [{providerId: "managed"}],
  ), "Firebase");
});
