"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {initializeApp, getApps} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {applyMigration, planMigration, validateSchemaV3} =
  require("../migration/schema-v3-runner");

const emulatorAvailable = Boolean(process.env.FIRESTORE_EMULATOR_HOST);

test("schema-v3 migration completes against Firestore Emulator", {
  skip: emulatorAvailable ? false : "FIRESTORE_EMULATOR_HOST is not set",
}, async () => {
  if (getApps().length === 0) initializeApp({projectId: "demo-rummypulse-schema-v3"});
  const db = getFirestore();
  for (const name of ["games_v2", "gameData_v2", "approvedGames_v2",
    "playerStats_v2", "approvedGamesReport_v2", "gameDefaults_v2"]) {
    await db.recursiveDelete(db.collection(name));
  }
  await db.collection("gameDefaults_v2").doc("config").set({
    defaultPointValue: 0.15,
    defaultGstPercent: 25,
    displayIntermediateCalculation: true,
    showDashboardLeaderboardAmounts: true,
  });
  await db.collection("approvedGames_v2").doc("fixture").set({
    gameId: "fixture",
    numPlayers: 3,
    pointValue: 2,
    gstPercent: 25,
    gstAmount: "15",
    players: [
      {name: "A", userId: "uid-a", score: 10},
      {name: "B", userId: "uid-b", score: 20},
      {name: "C", userId: "uid-c", score: 30},
    ],
    creationDateTime: "2026-09-10 17:30:00",
  });
  await db.collection("playerStats_v2").doc("ghost").set({
    allTime: {games: 99, netAmount: 999},
  });
  await db.collection("playerStats_v2").doc("uid-a").set({
    allTime: {games: 99, netAmount: 999},
  });

  const plan = await planMigration(db);
  assert.equal(plan.review.length, 0);
  const result = await applyMigration(db, plan);
  assert.equal(result.alreadyComplete, false);
  assert.deepEqual(await validateSchemaV3(db), []);
  assert.equal((await db.collection("playerStats_v2").doc("ghost").get()).exists, false);
  assert.equal((await db.collection("playerStats_v2").doc("uid-a").get())
      .get("allTime.finalGamePoints"), 45);
  assert.equal((await db.collection("gameDefaults_v2").doc("config").get())
      .get("schemaVersion"), 3);

  const rerun = await applyMigration(db, await planMigration(db));
  assert.deepEqual(rerun, {alreadyComplete: true, writes: 0});

  await db.collection("games_v2").doc("active").set({status: "R1"});
  await assert.rejects(() => planMigration(db), /games_v2 still contains active games/);
  await db.collection("games_v2").doc("active").delete();
});
