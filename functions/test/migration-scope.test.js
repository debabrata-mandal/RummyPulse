"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {readFileSync} = require("node:fs");
const {join} = require("node:path");

test("profile migration never touches collections that will be manually purged", () => {
  const source = readFileSync(join(__dirname, "../scripts/migrate-private-profiles.js"), "utf8");
  for (const collection of [
    "_functionRateLimits",
    "approvedGamesReport_v2",
    "approvedGames_v2",
    "gameData_v2",
    "gameScoreHistory_v2",
    "gameViewApprovals_v2",
    "games_v2",
    "playerStats_v2",
  ]) {
    assert.equal(source.includes(collection), false, `${collection} must stay outside the migration`);
  }
});
