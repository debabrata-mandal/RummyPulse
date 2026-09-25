"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  generatedProfileName,
  needsProfileNameConfirmation,
  profileNameWithSuffix,
  replaceGameIdentityNames,
  replaceLinkedPlayerNames,
  validateProfileName,
} = require("../lib/profile-name");

test("legacy migrated profiles require one confirmation prompt", () => {
  assert.equal(needsProfileNameConfirmation(undefined, false), true);
  assert.equal(needsProfileNameConfirmation(null, false), true);
  assert.equal(needsProfileNameConfirmation(true, false), true);
  assert.equal(needsProfileNameConfirmation(false, false), false);
  assert.equal(needsProfileNameConfirmation(false, true), true);
});

test("profile names are trimmed and normalized case-insensitively", () => {
  assert.deepEqual(validateProfileName("  Debabrata   M.  "), {
    profileName: "Debabrata M.",
    key: "debabrata m.",
  });
  assert.deepEqual(validateProfileName("Élodie R."), {
    profileName: "Élodie R.",
    key: "élodie r.",
  });
  assert.throws(() => validateProfileName("two/words"));
  assert.throws(() => validateProfileName("ab"));
  assert.deepEqual(validateProfileName(""), {profileName: null, key: null});
});

test("migration names use first name, last initial, and bounded suffixes", () => {
  assert.equal(generatedProfileName("Debabrata Mandal", "uid"), "Debabrata M.");
  assert.equal(generatedProfileName("Prince", "uid"), "Prince");
  assert.equal(generatedProfileName(null, "abcdef12345678"), "Player 12345678");
  assert.equal(profileNameWithSuffix("Debabrata M.", 2), "Debabrata M. 2");
  assert.equal(validateProfileName("Debabrata M. 2").profileName, "Debabrata M. 2");
  assert.ok([...profileNameWithSuffix("ABCDEFGHIJKLMNOPQRSTUVWX", 12)].length <= 24);
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

test("game attribution names are removed without changing approval status", () => {
  const result = replaceGameIdentityNames({
    creatorUserId: "uid-a",
    creatorName: "Google Name",
    lastEditorUserId: "uid-b",
    lastEditorName: "Someone Else",
    pendingViewRequests: {"uid-a": {status: "requested"}},
  }, "uid-a", "CardKing");
  assert.equal(result.data.creatorName, undefined);
  assert.equal(result.data.lastEditorName, undefined);
  assert.equal(result.data.pendingViewRequests["uid-a"].status, "requested");
});
