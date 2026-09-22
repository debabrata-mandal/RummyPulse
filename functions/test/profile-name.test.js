"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  replaceGameIdentityNames,
  replaceLinkedPlayerNames,
  validateProfileName,
} = require("../lib/profile-name");

test("profile names are trimmed and normalized case-insensitively", () => {
  assert.deepEqual(validateProfileName("  Card_King  "), {
    profileName: "Card_King",
    key: "card_king",
  });
  assert.throws(() => validateProfileName("two words"));
  assert.throws(() => validateProfileName("ab"));
  assert.deepEqual(validateProfileName(""), {profileName: null, key: null});
});

test("linked snapshots are renamed without touching manual players", () => {
  const result = replaceLinkedPlayerNames({
    data: {playersById: {
      a: {userId: "uid-a", name: "Google Name"},
      b: {name: "Manual Player"},
    }},
  }, "uid-a", "CardKing");
  assert.equal(result.data.data.playersById.a.name, "CardKing");
  assert.equal(result.data.data.playersById.b.name, "Manual Player");
});

test("game attribution and pending request names are renamed by uid", () => {
  const result = replaceGameIdentityNames({
    creatorUserId: "uid-a",
    creatorName: "Google Name",
    lastEditorUserId: "uid-b",
    lastEditorName: "Someone Else",
    pendingViewRequests: {"uid-a": {userDisplayName: "Google Name"}},
  }, "uid-a", "CardKing");
  assert.equal(result.data.creatorName, "CardKing");
  assert.equal(result.data.lastEditorName, "Someone Else");
  assert.equal(result.data.pendingViewRequests["uid-a"].userDisplayName, "CardKing");
});
