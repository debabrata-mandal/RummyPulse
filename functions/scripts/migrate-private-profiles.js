"use strict";

const {initializeApp} = require("firebase-admin/app");
const {getFirestore, FieldValue} = require("firebase-admin/firestore");
const {
  generatedProfileName,
  profileNameKey,
  profileNameWithSuffix,
  validateProfileName,
} = require("../lib/profile-name");

initializeApp();

const apply = process.argv.includes("--apply");
const database = getFirestore();
const PUBLIC_USERS = "appUser_v2";
const PRIVATE_USERS = "appUserIdentity_v1";
const PROFILE_CLAIMS = "profileNameClaims_v1";
const MAX_BATCH_OPERATIONS = 450;

async function main() {
  const [usersSnapshot, identitiesSnapshot, claimsSnapshot] = await Promise.all([
    database.collection(PUBLIC_USERS).get(),
    database.collection(PRIVATE_USERS).get(),
    database.collection(PROFILE_CLAIMS).get(),
  ]);
  const identities = new Map(identitiesSnapshot.docs.map((snapshot) =>
    [snapshot.id, snapshot.data()]));
  const claims = new Map(claimsSnapshot.docs.map((snapshot) =>
    [snapshot.id, snapshot.data()]));
  const users = [...usersSnapshot.docs].sort((left, right) => left.id.localeCompare(right.id));
  const assignments = assignProfileNames(users, identities);
  const operations = [];
  const counts = {users: users.length, generated: 0, preserved: 0, collisions: 0};

  for (const snapshot of users) {
    const uid = snapshot.id;
    const data = snapshot.data();
    const assignment = assignments.get(uid);
    if (assignment.generated) counts.generated++;
    else counts.preserved++;
    if (assignment.collision) counts.collisions++;

    const identity = identities.get(uid) || {};
    const googleDisplayName = clean(identity.googleDisplayName) ||
      clean(data.googleDisplayName) || clean(data.displayName) || null;
    const email = clean(identity.email) || clean(data.email);
    if (!sameNullable(identity.googleDisplayName, googleDisplayName) ||
        !sameNullable(identity.email, email) || identity.userId !== uid) {
      operations.push((batch) => batch.set(database.collection(PRIVATE_USERS).doc(uid), {
        userId: uid,
        googleDisplayName,
        email,
        migratedAt: FieldValue.serverTimestamp(),
      }, {merge: true}));
    }

    const needsConfirmation = assignment.generated ? true : data.profileNameNeedsConfirmation === true;
    if (data.profileName !== assignment.profileName || data.displayName !== assignment.profileName ||
        data.profileNameNeedsConfirmation !== needsConfirmation || data.email !== undefined ||
        data.googleDisplayName !== undefined) {
      operations.push((batch) => batch.update(snapshot.ref, {
        displayName: assignment.profileName,
        profileName: assignment.profileName,
        profileNameNeedsConfirmation: needsConfirmation,
        email: FieldValue.delete(),
        googleDisplayName: FieldValue.delete(),
        profileVersion: Date.now(),
      }));
    }

    const claim = claims.get(assignment.key);
    if (!claim || claim.userId !== uid || claim.profileName !== assignment.profileName) {
      operations.push((batch) => batch.set(database.collection(PROFILE_CLAIMS).doc(assignment.key), {
        userId: uid,
        profileName: assignment.profileName,
      }));
    }
  }

  if (apply) await commitOperations(operations);
  process.stdout.write(`${apply ? "Applied" : "Dry run"}: ${JSON.stringify({...counts, writes: operations.length})}\n`);
}

function assignProfileNames(users, identities) {
  const assignments = new Map();
  const usedKeys = new Set();
  for (const snapshot of users) {
    const existing = validExistingName(snapshot.get("profileName"));
    if (!existing || usedKeys.has(existing.key)) continue;
    usedKeys.add(existing.key);
    assignments.set(snapshot.id, {...existing, generated: false, collision: false});
  }
  for (const snapshot of users) {
    if (assignments.has(snapshot.id)) continue;
    const data = snapshot.data();
    const identity = identities.get(snapshot.id) || {};
    const fullName = clean(identity.googleDisplayName) ||
      clean(data.googleDisplayName) || clean(data.displayName);
    const baseName = generatedProfileName(fullName, snapshot.id);
    let sequence = 1;
    let profileName = profileNameWithSuffix(baseName, sequence);
    let key = profileNameKey(profileName);
    while (usedKeys.has(key)) {
      sequence++;
      profileName = profileNameWithSuffix(baseName, sequence);
      key = profileNameKey(profileName);
    }
    usedKeys.add(key);
    assignments.set(snapshot.id, {
      profileName,
      key,
      generated: true,
      collision: sequence > 1,
    });
  }
  return assignments;
}

function validExistingName(value) {
  try {
    const validated = validateProfileName(value);
    return validated.profileName ? validated : null;
  } catch (error) {
    return null;
  }
}

async function commitOperations(operations) {
  for (let offset = 0; offset < operations.length; offset += MAX_BATCH_OPERATIONS) {
    const batch = database.batch();
    for (const operation of operations.slice(offset, offset + MAX_BATCH_OPERATIONS)) operation(batch);
    await batch.commit();
  }
}

function clean(value) {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function sameNullable(left, right) {
  return (left ?? null) === (right ?? null);
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error}\n`);
  process.exitCode = 1;
});
