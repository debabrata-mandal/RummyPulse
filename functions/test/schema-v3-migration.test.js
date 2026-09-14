"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  SCHEMA_VERSION,
  calculateGamePoints,
  findDeprecatedPaths,
  rebuildPlayerStats,
  rebuildReports,
  transformApprovedGame,
  transformDefaults,
} = require("../migration/schema-v3-transform");

function legacyGame(overrides = {}) {
  return {
    gameId: "fixture-game",
    numPlayers: 3,
    pointValue: 2,
    gstPercent: 25,
    gstAmount: "25",
    players: [
      {name: "A", userId: "uid-a", score: 10},
      {name: "B", userId: "uid-b", score: 20},
      {name: "C", userId: "uid-c", score: 30},
    ],
    approvedAt: new Date("2026-09-10T12:00:00Z"),
    version: "1.0.0",
    gameStatus: "Approved",
    ...overrides,
  };
}

test("calculation preserves the Java Game Points formula and balance", () => {
  const result = calculateGamePoints([10, 20, 30], 2, 25, 3);
  assert.deepEqual(result.players, [
    {baseGamePoints: 60, boardAdjustmentPoints: 15, finalGamePoints: 45},
    {baseGamePoints: 0, boardAdjustmentPoints: 0, finalGamePoints: 0},
    {baseGamePoints: -60, boardAdjustmentPoints: 0, finalGamePoints: -60},
  ]);
  assert.equal(result.boardPoints, 15);
  assert.equal(result.players.reduce((sum, row) => sum + row.finalGamePoints, 0) +
    result.boardPoints, 0);
});

test("approved game migration removes legacy keys and preserves identity and scores", () => {
  const migrated = transformApprovedGame(legacyGame({gstAmount: "15"}));
  assert.equal(migrated.schemaVersion, SCHEMA_VERSION);
  assert.equal(migrated.gamePointFactor, 2);
  assert.equal(migrated.boardAdjustmentPercent, 25);
  assert.equal(migrated.boardPoints, 15);
  assert.deepEqual(migrated.players.map(({name, userId, score}) =>
    ({name, userId, score})), legacyGame().players);
  assert.deepEqual(findDeprecatedPaths(migrated), []);
});

test("legacy playerScores are converted without inventing account identities", () => {
  const migrated = transformApprovedGame(legacyGame({
    gstAmount: "15", players: undefined, playerScores: {A: 10, B: 20, C: 30},
  }));
  assert.deepEqual(migrated.players, [
    {name: "A", userId: null, score: 10},
    {name: "B", userId: null, score: 20},
    {name: "C", userId: null, score: 30},
  ]);
  assert.equal(Object.hasOwn(migrated, "playerScores"), false);
});

test("mismatched stored Board Points require manual review", () => {
  assert.throws(() => transformApprovedGame(legacyGame()),
      /do not match calculated/);
});

test("schema-v3 approved games are safe to transform again", () => {
  const once = transformApprovedGame(legacyGame({gstAmount: "15"}));
  const twice = transformApprovedGame(once);
  assert.deepEqual(twice, once);
});

test("defaults migration replaces every legacy setting", () => {
  const migrated = transformDefaults({
    defaultPointValue: 0.25,
    defaultGstPercent: 20,
    displayIntermediateCalculation: false,
    showDashboardLeaderboardAmounts: true,
    defaultMidGameNewPlayerScoreIncrement: 2,
  });
  assert.deepEqual(migrated, {
    defaultMidGameNewPlayerScoreIncrement: 2,
    defaultGamePointFactor: 0.25,
    defaultBoardAdjustmentPercent: 20,
    showLiveGamePoints: false,
    showDashboardLeaderboardGamePoints: true,
  });
  assert.deepEqual(findDeprecatedPaths(migrated), []);
});

test("statistics are rebuilt only for linked players in approved games", () => {
  const game = transformApprovedGame(legacyGame({gstAmount: "15"}));
  const stats = rebuildPlayerStats([game]);
  assert.equal(stats.size, 3);
  assert.deepEqual(stats.get("uid-a").allTime, {
    games: 1, wins: 1, finalGamePoints: 45,
    baseGamePoints: 60, boardAdjustmentPoints: 15,
  });
  assert.deepEqual(findDeprecatedPaths(stats.get("uid-a")), []);
});

test("a duplicated UID is counted only once per approved game", () => {
  const game = transformApprovedGame(legacyGame({
    gstAmount: "15",
    players: [
      {name: "A", userId: "uid-a", score: 10},
      {name: "A duplicate", userId: "uid-a", score: 20},
      {name: "C", userId: "uid-c", score: 30},
    ],
  }));
  const stats = rebuildPlayerStats([game]);
  assert.equal(stats.get("uid-a").allTime.games, 1);
  assert.equal(stats.get("uid-a").allTime.finalGamePoints, 45);
});

test("statistics use ISO week-year boundaries in the reporting timezone", () => {
  const first = transformApprovedGame(legacyGame({
    gameId: "year-boundary",
    gstAmount: "15",
    approvedAt: new Date("2021-01-01T12:00:00Z"),
  }));
  const second = transformApprovedGame(legacyGame({
    gameId: "first-week",
    gstAmount: "15",
    approvedAt: new Date("2021-01-04T12:00:00Z"),
  }));
  const weeks = rebuildPlayerStats([first, second]).get("uid-a").weeks;
  assert.equal(weeks["2020-W53"].games, 1);
  assert.equal(weeks["2021-W01"].games, 1);
});

test("derived reports use schema-v3 field names", () => {
  const game = transformApprovedGame(legacyGame({gstAmount: "15"}));
  const reports = rebuildReports([game]);
  const report = reports.get("2026-09");
  assert.equal(report.schemaVersion, SCHEMA_VERSION);
  assert.equal(report.monthYear, "September 2026");
  assert.deepEqual(report.gamePointFactorReports, [{
    gamePointFactor: 2, totalGames: 1, totalBoardPoints: 15, totalPlayers: 3,
  }]);
  assert.deepEqual(findDeprecatedPaths(report), []);
});

test("recursive verifier finds deprecated keys at any depth", () => {
  assert.deepEqual(findDeprecatedPaths({allTime: {netAmount: 4}, pointValue: 2}),
      ["allTime.netAmount", "pointValue"]);
});
