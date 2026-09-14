#!/usr/bin/env node
"use strict";

const fs = require("node:fs");
const path = require("node:path");
const {applicationDefault, initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {applyMigration, planMigration, validateSchemaV3} =
  require("./schema-v3-runner");

const PRODUCTION_PROJECT = "rummy-score-master";
const CONFIRMATION = "MIGRATE-RUMMY-SCORE-MASTER-TO-SCHEMA-3";
const MAX_BACKUP_AGE_MS = 24 * 60 * 60 * 1000;

function option(name) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : undefined;
}

async function main() {
  const apply = process.argv.includes("--apply");
  const validateOnly = process.argv.includes("--validate");
  const projectId = option("--project") || process.env.GCLOUD_PROJECT ||
    process.env.GOOGLE_CLOUD_PROJECT;
  const emulator = Boolean(process.env.FIRESTORE_EMULATOR_HOST);
  if (!projectId) throw new Error("Pass --project with an explicit Firebase project ID");
  if (apply && !emulator && (projectId !== PRODUCTION_PROJECT ||
      option("--confirm") !== CONFIRMATION)) {
    throw new Error(`Production apply requires --project ${PRODUCTION_PROJECT} ` +
      `--confirm ${CONFIRMATION}`);
  }
  if (apply && !emulator) {
    const backupAt = new Date(option("--backup-confirmed-at") || "");
    const age = Date.now() - backupAt.getTime();
    if (!Number.isFinite(age) || age < 0 || age > MAX_BACKUP_AGE_MS) {
      throw new Error("Production apply requires --backup-confirmed-at with an ISO timestamp " +
        "from the last 24 hours");
    }
  }
  initializeApp({projectId, credential: emulator ? undefined : applicationDefault()});
  const db = getFirestore();
  if (validateOnly) {
    const anomalies = await validateSchemaV3(db);
    console.log(JSON.stringify({mode: "validate", projectId,
      valid: anomalies.length === 0, anomalyCount: anomalies.length}, null, 2));
    if (anomalies.length > 0) process.exitCode = 2;
    return;
  }
  const plan = await planMigration(db);
  const report = {
    generatedAt: new Date().toISOString(), projectId,
    mode: apply ? "apply" : "dry-run",
    approvedGames: plan.approved.length,
    playerStats: plan.playerStats?.size || 0,
    monthlyReports: plan.reports?.size || 0,
    manualReview: plan.review,
  };
  if (plan.review.length > 0) {
    const reportDir = path.join(__dirname, "reports");
    fs.mkdirSync(reportDir, {recursive: true});
    const reportPath = path.join(reportDir, `schema-v3-review-${Date.now()}.json`);
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), {mode: 0o600});
    console.error(`Migration blocked; review ${plan.review.length} record(s) in ${reportPath}`);
    process.exitCode = 2;
    return;
  }
  if (apply) report.result = await applyMigration(db, plan);
  console.log(JSON.stringify({...report, manualReview: []}, null, 2));
}

main().catch((error) => {
  console.error(`Schema-v3 migration failed: ${error.message}`);
  process.exitCode = 1;
});
