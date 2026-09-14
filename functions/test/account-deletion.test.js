"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  ACCOUNT_DELETION_CALLABLE_OPTIONS,
  anonymizeApprovedGame,
  anonymizeGameAuth,
  anonymizeGameDataDocument,
  isAdministratorProfile,
  isRecentAuthentication,
  isRecentGoogleAuthentication,
  isValidUid,
} = require("../lib/account-deletion");

const UID_A = "user-a";

test("active game anonymization changes only the deleted account", () => {
  const before = {
    creatorUserId: UID_A,
    creatorName: "Alice",
    activeEditorUserId: UID_A,
    activeEditorName: "Alice",
    lastEditorUserId: "user-b",
    lastEditorName: "Bob",
    memberUserIds: [UID_A, "user-b", "user-c"],
    pendingViewRequests: {
      [UID_A]: {status: "approved", userDisplayName: "Alice"},
      "user-b": {status: "approved", userDisplayName: "Bob"},
    },
    pin: "1234",
    pinGeneration: 8,
  };

  const result = anonymizeGameAuth(before, UID_A);

  assert.equal(result.changed, true);
  assert.equal(result.data.creatorUserId, undefined);
  assert.equal(result.data.creatorName, "Deleted user");
  assert.equal(result.data.activeEditorUserId, undefined);
  assert.equal(result.data.activeEditorName, "Deleted user");
  assert.equal(result.data.lastEditorUserId, "user-b");
  assert.deepEqual(result.data.memberUserIds, ["user-b", "user-c"]);
  assert.deepEqual(result.data.pendingViewRequests["user-b"],
      before.pendingViewRequests["user-b"]);
  assert.equal(result.data.pendingViewRequests[UID_A], undefined);
  assert.equal(result.data.pin, "1234");
  assert.equal(result.data.pinGeneration, 8);
  assert.deepEqual(before.memberUserIds, [UID_A, "user-b", "user-c"]);
});

test("game data preserves player slots scores economics and other users", () => {
  const before = {
    revision: 17,
    editGeneration: 4,
    data: {
      schemaVersion: 3,
      numPlayers: 3,
      gamePointFactor: 2,
      boardAdjustmentPercent: 10,
      playerOrder: ["p1", "p2", "p3"],
      playersById: {
        p1: {playerId: "p1", name: "Alice", userId: UID_A, scores: [10, 20]},
        p2: {playerId: "p2", name: "Bob", userId: "user-b", scores: [30, 40]},
        p3: {playerId: "p3", name: "Guest", scores: [5, 15]},
      },
    },
  };

  const result = anonymizeGameDataDocument(before, UID_A);

  assert.equal(result.changed, true);
  assert.equal(result.data.data.numPlayers, before.data.numPlayers);
  assert.equal(result.data.data.gamePointFactor, before.data.gamePointFactor);
  assert.equal(result.data.data.boardAdjustmentPercent, before.data.boardAdjustmentPercent);
  assert.deepEqual(result.data.data.playerOrder, before.data.playerOrder);
  assert.deepEqual(result.data.data.playersById.p1.scores,
      before.data.playersById.p1.scores);
  assert.equal(result.data.data.playersById.p1.playerId, "p1");
  assert.equal(result.data.data.playersById.p1.userId, undefined);
  assert.equal(result.data.data.playersById.p1.name, "Deleted player");
  assert.deepEqual(result.data.data.playersById.p2, before.data.playersById.p2);
  assert.deepEqual(result.data.data.playersById.p3, before.data.playersById.p3);
  assert.equal(result.data.revision, 17);
  assert.equal(result.data.editGeneration, 4);
});

test("legacy player arrays are anonymized without removing a row", () => {
  const before = {
    schemaVersion: 3,
    data: {
      numPlayers: 2,
      players: [
        {name: "Alice", userId: UID_A, scores: [1, 2]},
        {name: "Bob", userId: "user-b", scores: [3, 4]},
      ],
    },
  };
  const result = anonymizeGameDataDocument(before, UID_A);
  assert.equal(result.data.data.players.length, 2);
  assert.equal(result.data.data.players[0].userId, undefined);
  assert.equal(result.data.data.players[0].name, "Deleted player");
  assert.deepEqual(result.data.data.players[0].scores, [1, 2]);
  assert.deepEqual(result.data.data.players[1], before.data.players[1]);
});

test("approved game keeps all calculation inputs and other players", () => {
  const before = {
    gameId: "game-1",
    numPlayers: 3,
    gamePointFactor: 5,
    boardAdjustmentPercent: 8,
    boardPoints: 14,
    players: [
      {name: "Alice", userId: UID_A, score: 20},
      {name: "Bob", userId: "user-b", score: 40},
      {name: "Guest", score: 10},
    ],
  };
  const result = anonymizeApprovedGame(before, UID_A);
  assert.equal(result.data.players.length, 3);
  assert.equal(result.data.players[0].userId, undefined);
  assert.equal(result.data.players[0].name, "Deleted player");
  assert.equal(result.data.players[0].score, 20);
  assert.deepEqual(result.data.players[1], before.players[1]);
  assert.deepEqual(result.data.players[2], before.players[2]);
  assert.equal(result.data.numPlayers, 3);
  assert.equal(result.data.gamePointFactor, 5);
  assert.equal(result.data.boardAdjustmentPercent, 8);
  assert.equal(result.data.boardPoints, 14);
});

test("name-only legacy approved rows are not guessed", () => {
  const before = {playerScores: {Alice: 20, Bob: 40}, numPlayers: 2};
  const result = anonymizeApprovedGame(before, UID_A);
  assert.equal(result.changed, false);
  assert.deepEqual(result.data, before);
});

test("anonymization is idempotent", () => {
  const first = anonymizeGameDataDocument({
    data: {playersById: {p1: {name: "Alice", userId: UID_A, scores: [10]}}},
  }, UID_A);
  const second = anonymizeGameDataDocument(first.data, UID_A);
  assert.equal(first.changed, true);
  assert.equal(second.changed, false);
  assert.deepEqual(second.data, first.data);
});

test("recent authentication and uid validation reject unsafe input", () => {
  assert.equal(isRecentAuthentication({auth_time: 1000}, 1299), true);
  assert.equal(isRecentAuthentication({auth_time: 1000}, 1301), false);
  assert.equal(isRecentAuthentication({}, 1000), false);
  assert.equal(isRecentGoogleAuthentication({
    auth_time: 1000,
    firebase: {sign_in_provider: "google.com"},
  }, 1299), true);
  assert.equal(isRecentGoogleAuthentication({
    auth_time: 1000,
    firebase: {sign_in_provider: "password"},
  }, 1299), false);
  assert.equal(isValidUid("valid-user"), true);
  assert.equal(isValidUid(""), false);
  assert.equal(isValidUid("x".repeat(129)), false);
});

test("deletion endpoints require App Check in the configured region", () => {
  assert.equal(ACCOUNT_DELETION_CALLABLE_OPTIONS.enforceAppCheck, true);
  assert.equal(ACCOUNT_DELETION_CALLABLE_OPTIONS.region, "asia-south1");
  assert.equal(isAdministratorProfile({role: "admin_user"}), true);
  assert.equal(isAdministratorProfile({role: "regular_user"}), false);
  assert.equal(isAdministratorProfile(null), false);
});
