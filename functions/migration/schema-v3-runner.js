"use strict";

const {FieldPath, FieldValue} = require("firebase-admin/firestore");
const {
  SCHEMA_VERSION,
  findDeprecatedPaths,
  rebuildPlayerStats,
  rebuildReports,
  transformApprovedGame,
  transformDefaults,
} = require("./schema-v3-transform");

const PAGE_SIZE = 200;
const WRITE_BATCH_SIZE = 200;

async function readCollection(db, name) {
  const documents = [];
  let cursor;
  do {
    let query = db.collection(name).orderBy(FieldPath.documentId()).limit(PAGE_SIZE);
    if (cursor) query = query.startAfter(cursor);
    const snapshot = await query.get();
    documents.push(...snapshot.docs.map((doc) => ({id: doc.id, data: doc.data()})));
    cursor = snapshot.docs.at(-1);
  } while (cursor && documents.length % PAGE_SIZE === 0);
  return documents;
}

async function assertNoActiveGames(db) {
  for (const collection of ["games_v2", "gameData_v2"]) {
    const snapshot = await db.collection(collection).limit(1).get();
    if (!snapshot.empty) {
      throw new Error(`Migration blocked: ${collection} still contains active games`);
    }
  }
}

async function commitOperations(db, operations) {
  for (let offset = 0; offset < operations.length; offset += WRITE_BATCH_SIZE) {
    const batch = db.batch();
    for (const operation of operations.slice(offset, offset + WRITE_BATCH_SIZE)) {
      if (operation.type === "delete") batch.delete(operation.ref);
      else batch.set(operation.ref, operation.data);
    }
    await batch.commit();
  }
}

function anomaliesFor(collection, documents) {
  const anomalies = [];
  for (const document of documents) {
    const paths = findDeprecatedPaths(document.data);
    if (paths.length > 0 || document.data.schemaVersion !== SCHEMA_VERSION) {
      anomalies.push({collection, documentId: document.id, deprecatedPaths: paths,
        schemaVersion: document.data.schemaVersion ?? null});
    }
  }
  return anomalies;
}

async function validateSchemaV3(db) {
  const collections = ["approvedGames_v2", "playerStats_v2", "approvedGamesReport_v2"];
  const anomalies = [];
  for (const collection of collections) {
    anomalies.push(...anomaliesFor(collection, await readCollection(db, collection)));
  }
  const defaults = await db.collection("gameDefaults_v2").doc("config").get();
  if (!defaults.exists) {
    anomalies.push({collection: "gameDefaults_v2", documentId: "config",
      reason: "missing"});
  } else {
    anomalies.push(...anomaliesFor("gameDefaults_v2", [
      {id: defaults.id, data: defaults.data()},
    ]));
  }
  return anomalies;
}

async function planMigration(db) {
  await assertNoActiveGames(db);
  const sourceApproved = await readCollection(db, "approvedGames_v2");
  const review = [];
  const approved = [];
  for (const document of sourceApproved) {
    try {
      approved.push({id: document.id, data: transformApprovedGame(document.data)});
    } catch (error) {
      review.push({collection: "approvedGames_v2", documentId: document.id,
        reason: error.message});
    }
  }
  const defaultsSnapshot = await db.collection("gameDefaults_v2").doc("config").get();
  let defaults;
  try {
    if (!defaultsSnapshot.exists) throw new Error("defaults document is missing");
    defaults = transformDefaults(defaultsSnapshot.data());
  } catch (error) {
    review.push({collection: "gameDefaults_v2", documentId: "config",
      reason: error.message});
  }
  if (review.length > 0) return {review, approved, defaults};
  return {
    review,
    approved,
    defaults,
    playerStats: rebuildPlayerStats(approved.map((entry) => entry.data)),
    reports: rebuildReports(approved.map((entry) => entry.data)),
  };
}

async function applyMigration(db, plan) {
  if (plan.review.length > 0) {
    throw new Error("Migration has manual-review records and cannot be applied");
  }
  await assertNoActiveGames(db);
  const currentAnomalies = await validateSchemaV3(db);
  if (currentAnomalies.length === 0) {
    return {alreadyComplete: true, writes: 0};
  }
  const approvedWrites = plan.approved.map((entry) => ({
    type: "set", ref: db.collection("approvedGames_v2").doc(entry.id), data: entry.data,
  }));
  const oldStats = await readCollection(db, "playerStats_v2");
  const statsDeletes = oldStats.map((entry) => ({type: "delete",
    ref: db.collection("playerStats_v2").doc(entry.id)}));
  const statsWrites = [];
  for (const [uid, data] of plan.playerStats) {
    statsWrites.push({type: "set", ref: db.collection("playerStats_v2").doc(uid),
      data: {...data, updatedAt: FieldValue.serverTimestamp()}});
  }
  const oldReports = await readCollection(db, "approvedGamesReport_v2");
  const reportDeletes = oldReports.map((entry) => ({type: "delete",
    ref: db.collection("approvedGamesReport_v2").doc(entry.id)}));
  const reportWrites = [];
  for (const [month, data] of plan.reports) {
    reportWrites.push({type: "set", ref: db.collection("approvedGamesReport_v2").doc(month),
      data: {...data, lastBuiltAt: FieldValue.serverTimestamp()}});
  }
  const defaultsWrite = [{type: "set",
    ref: db.collection("gameDefaults_v2").doc("config"), data: plan.defaults}];
  // Keep destructive replacement phases separate so a document is never deleted and set in the
  // same Firestore batch. Every phase is retry-safe if the process is interrupted.
  await commitOperations(db, approvedWrites);
  await commitOperations(db, statsDeletes);
  await commitOperations(db, statsWrites);
  await commitOperations(db, reportDeletes);
  await commitOperations(db, reportWrites);
  await commitOperations(db, defaultsWrite);
  await db.collection("gameDefaults_v2").doc("config").set({
    schemaVersion: SCHEMA_VERSION,
    schemaMigratedAt: FieldValue.serverTimestamp(),
  }, {merge: true});
  const remaining = await validateSchemaV3(db);
  if (remaining.length > 0) {
    throw new Error(`Post-migration validation found ${remaining.length} invalid documents`);
  }
  return {alreadyComplete: false, writes: approvedWrites.length + statsDeletes.length +
    statsWrites.length + reportDeletes.length + reportWrites.length + 2};
}

module.exports = {applyMigration, assertNoActiveGames, planMigration,
  readCollection, validateSchemaV3};
