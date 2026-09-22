"use strict";

const {initializeApp} = require("firebase-admin/app");
const {getFirestore, FieldValue} = require("firebase-admin/firestore");
const {
  replaceLinkedPlayerNames,
} = require("../lib/profile-name");

initializeApp();

const apply = process.argv.includes("--apply");
const database = getFirestore();

async function main() {
  const users = await database.collection("appUser_v2").get();
  const publicNames = new Map();
  let writes = 0;
  for (const snapshot of users.docs) {
    const data = snapshot.data();
    const uid = snapshot.id;
    const profileName = clean(data.profileName);
    const googleDisplayName = clean(data.googleDisplayName) || clean(data.displayName) || "Player";
    const displayName = profileName || googleDisplayName;
    publicNames.set(uid, displayName);
    writes += 2 + (profileName ? 1 : 0);
    if (!apply) continue;
    await database.collection("appUserIdentity_v1").doc(uid).set({
      userId: uid,
      googleDisplayName,
      email: clean(data.email) || null,
      migratedAt: FieldValue.serverTimestamp(),
    }, {merge: true});
    await snapshot.ref.update({
      displayName,
      profileName: profileName || FieldValue.delete(),
      email: FieldValue.delete(),
      googleDisplayName: FieldValue.delete(),
      profileVersion: Date.now(),
    });
    if (profileName) {
      await database.collection("profileNameClaims_v1")
          .doc(profileName.toLowerCase()).set({userId: uid, profileName});
    }
  }

  writes += await rewriteCollection("gameData_v2", publicNames, replaceLinkedPlayerNames);
  writes += await rewriteCollection("approvedGames_v2", publicNames, replaceLinkedPlayerNames);
  const defaults = await database.collection("gameDefaults_v2").get();
  for (const snapshot of defaults.docs) {
    const uid = snapshot.get("updatedByUserId");
    const displayName = publicNames.get(uid);
    if (displayName && snapshot.get("updatedByUserName") !== displayName) {
      writes++;
      if (apply) await snapshot.ref.update("updatedByUserName", displayName);
    }
  }
  const statsDocuments = await database.collection("playerStats_v2").get();
  for (const stats of statsDocuments.docs) {
    if (stats.exists && stats.get("displayName") !== undefined) {
      writes++;
      if (apply) await stats.ref.update("displayName", FieldValue.delete());
    }
  }

  for (const [uid, displayName] of publicNames) {
    const approvals = await database.collection("gameViewApprovals_v2")
        .where("userId", "==", uid).get();
    for (const approval of approvals.docs) {
      if (approval.get("userDisplayName") !== displayName) {
        writes++;
        if (apply) await approval.ref.update("userDisplayName", displayName);
      }
    }
  }
  process.stdout.write(`${apply ? "Applied" : "Dry run"}: ${users.size} users, ${writes} writes\n`);
}

async function rewriteCollection(collectionName, names, transformer) {
  const snapshots = await database.collection(collectionName).get();
  let changedCount = 0;
  for (const snapshot of snapshots.docs) {
    let current = snapshot.data();
    let changed = false;
    for (const [uid, displayName] of names) {
      const result = transformer(current, uid, displayName);
      current = result.data;
      changed = changed || result.changed;
    }
    if (changed) {
      changedCount++;
      if (apply) await snapshot.ref.set(current);
    }
  }
  return changedCount;
}

function clean(value) {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error}\n`);
  process.exitCode = 1;
});
