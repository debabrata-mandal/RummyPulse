"use strict";

const {initializeApp} = require("firebase-admin/app");
const {getAuth} = require("firebase-admin/auth");
const {getFirestore, FieldPath, FieldValue, Timestamp} =
  require("firebase-admin/firestore");
const {createHash, randomUUID} = require("node:crypto");
const {defineSecret} = require("firebase-functions/params");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {logger} = require("firebase-functions");
const {extractGroqName} = require("./lib/game-name");
const {authenticationProvider} = require("./lib/auth-identity");
const {
  generatedProfileName,
  profileNameKey,
  profileNameWithSuffix,
  validateProfileName,
} = require("./lib/profile-name");
const {nextFixedCounter, nextRollingCounter} = require("./lib/rate-limit");
const {
  ACCOUNT_DELETION_CALLABLE_OPTIONS,
  anonymizeApprovedGame,
  anonymizeGameAuth,
  anonymizeGameDataDocument,
  isAdministratorProfile,
  isRecentGoogleAuthentication,
  isValidUid,
} = require("./lib/account-deletion");

initializeApp();

const IS_EMULATOR = process.env.FUNCTIONS_EMULATOR === "true";
const groqApiKey = defineSecret("GROQ_API_KEY");
const GROQ_MODEL = "openai/gpt-oss-20b";
const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
const FUNCTION_QUOTAS_COLLECTION = "functionQuotas_v2";
const RATE_LIMIT_WINDOW_MS = 60 * 60 * 1000;
const GLOBAL_RATE_LIMIT_WINDOW_MS = 24 * 60 * 60 * 1000;
const MAX_REQUESTS_PER_WINDOW = 10;
const MAX_GLOBAL_REQUESTS_PER_DAY = 200;
const DELETE_PAGE_SIZE = 200;
const LAST_LOGIN_UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000;
const PROFILE_CALLABLE_OPTIONS = Object.freeze({
  region: "asia-south1",
  enforceAppCheck: !IS_EMULATOR,
  timeoutSeconds: 540,
  memory: "256MiB",
});
const PRIVATE_USER_COLLECTION = "appUserIdentity_v1";
const PROFILE_NAME_CLAIMS = "profileNameClaims_v1";
const PUBLIC_USER_COLLECTION = "appUser_v2";
const GAME_COLLECTION = "games_v2";
const GAME_DATA_COLLECTION = "gameData_v2";
const VIEW_APPROVAL_COLLECTION = "gameViewApprovals_v2";
const PROMPT =
  "Generate one short, catchy English name for a rummy or card game app. " +
  "Use one or two words in title case, with no numbers or punctuation. " +
  "Reply with only the name.";

exports.suggestGameName = onCall({
  region: "asia-south1",
  secrets: [groqApiKey],
  enforceAppCheck: !IS_EMULATOR,
  maxInstances: 5,
  concurrency: 10,
  timeoutSeconds: 30,
  memory: "256MiB",
}, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign-in is required.");
  }

  await enforceRequestQuotas(request.auth.uid);

  let response;
  try {
    response = await fetch(GROQ_URL, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${groqApiKey.value()}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: GROQ_MODEL,
        messages: [{role: "user", content: PROMPT}],
        temperature: 0.6,
        max_completion_tokens: 128,
        reasoning_effort: "low",
        include_reasoning: false,
      }),
      signal: AbortSignal.timeout(20000),
    });
  } catch (error) {
    logger.error("Groq request failed before receiving a response");
    throw new HttpsError("unavailable", "Name generation is temporarily unavailable.");
  }

  if (!response.ok) {
    logger.error("Groq request returned a non-success status", {status: response.status});
    throw new HttpsError("unavailable", "Name generation is temporarily unavailable.");
  }

  try {
    const payload = await response.json();
    return {name: extractGroqName(payload)};
  } catch (error) {
    logger.error("Groq returned an invalid game-name response");
    throw new HttpsError("internal", "Name generation returned an invalid response.");
  }
});

exports.syncMyIdentity = onCall(PROFILE_CALLABLE_OPTIONS, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign-in is required.");
  }
  const uid = request.auth.uid;
  const authUser = await getAuth().getUser(uid);
  const provider = authenticationProvider(request.auth.token, authUser.providerData);
  const googleDisplayName = safeText(request.auth.token.name, 120) ||
    safeText(authUser.displayName, 120);
  const email = safeText(request.auth.token.email, 320) ||
    safeText(authUser.email, 320);
  const photoUrl = safeText(request.auth.token.picture, 2048) ||
    safeText(authUser.photoURL, 2048);
  const database = getFirestore();
  const publicRef = database.collection("appUser_v2").doc(uid);
  const privateRef = database.collection(PRIVATE_USER_COLLECTION).doc(uid);
  const user = await database.runTransaction(async (transaction) => {
    const [publicSnapshot, privateSnapshot] = await transaction.getAll(publicRef, privateRef);
    const existing = publicSnapshot.data() || {};
    const privateExisting = privateSnapshot.data() || {};
    const now = Date.now();
    const nowTimestamp = Timestamp.fromMillis(now);
    const publicUpdates = {};
    let profileName = safeText(existing.profileName, 24);
    let profileNameNeedsConfirmation = existing.profileNameNeedsConfirmation === true;
    let generatedClaim = null;
    let publicProfileChanged = false;
    if (!profileName) {
      generatedClaim = await availableGeneratedProfileName(
          transaction, database, googleDisplayName, uid);
      profileName = generatedClaim.profileName;
      profileNameNeedsConfirmation = true;
      publicUpdates.profileName = profileName;
      publicUpdates.displayName = profileName;
      publicUpdates.profileNameNeedsConfirmation = true;
      publicProfileChanged = true;
    }
    if (!publicSnapshot.exists) {
      Object.assign(publicUpdates, {
        userId: uid,
        provider,
        role: "regular_user",
        displayName: profileName,
        photoUrl: photoUrl || null,
        hidden: false,
        createdAt: nowTimestamp,
        lastLoginAt: nowTimestamp,
      });
      publicProfileChanged = true;
    } else {
      if (existing.userId !== uid) publicUpdates.userId = uid;
      if (existing.provider !== provider) {
        publicUpdates.provider = provider;
        publicProfileChanged = true;
      }
      if ((existing.photoUrl || null) !== (photoUrl || null)) {
        publicUpdates.photoUrl = photoUrl || null;
        publicProfileChanged = true;
      }
      const lastLoginAt = timestampMillis(existing.lastLoginAt);
      if (!lastLoginAt || now - lastLoginAt >= LAST_LOGIN_UPDATE_INTERVAL_MS) {
        publicUpdates.lastLoginAt = nowTimestamp;
      }
    }
    if (publicProfileChanged || request.data?.forceProfileVersionRefresh === true) {
      publicUpdates.profileVersion = now;
    }
    if (Object.keys(publicUpdates).length > 0) {
      transaction.set(publicRef, publicUpdates, {merge: true});
    }
    if (generatedClaim) {
      transaction.set(generatedClaim.ref, {userId: uid, profileName});
    }
    const privateUpdates = {};
    if (privateExisting.userId !== uid) privateUpdates.userId = uid;
    if ((privateExisting.googleDisplayName || null) !== (googleDisplayName || null)) {
      privateUpdates.googleDisplayName = googleDisplayName || null;
    }
    if ((privateExisting.email || null) !== (email || null)) privateUpdates.email = email || null;
    if (Object.keys(privateUpdates).length > 0) {
      privateUpdates.updatedAt = nowTimestamp;
      transaction.set(privateRef, privateUpdates, {merge: true});
    }
    return publicUserDto({...existing, ...publicUpdates, profileName,
      displayName: profileName, profileNameNeedsConfirmation});
  });
  return {status: "synced", user};
});

exports.setProfileName = onCall(PROFILE_CALLABLE_OPTIONS, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign-in is required.");
  }
  const requested = requireProfileName(request.data?.profileName);
  if (!requested.profileName) {
    throw new HttpsError("invalid-argument", "A profile name is required.");
  }
  const uid = request.auth.uid;
  const database = getFirestore();
  const publicRef = database.collection("appUser_v2").doc(uid);
  const user = await database.runTransaction(async (transaction) => {
    const publicSnapshot = await transaction.get(publicRef);
    if (!publicSnapshot.exists) {
      throw new HttpsError("failed-precondition", "Profile is not ready yet.");
    }
    const oldName = safeText(publicSnapshot.get("profileName"), 24);
    const oldKey = oldName ? profileNameKey(oldName) : null;
    const newClaimRef = requested.key ?
      database.collection(PROFILE_NAME_CLAIMS).doc(requested.key) : null;
    const oldClaimRef = oldKey ?
      database.collection(PROFILE_NAME_CLAIMS).doc(oldKey) : null;
    const newClaim = newClaimRef ? await transaction.get(newClaimRef) : null;
    const oldClaim = oldClaimRef && oldKey !== requested.key ?
      await transaction.get(oldClaimRef) : null;
    if (newClaim?.exists && newClaim.get("userId") !== uid) {
      throw new HttpsError("already-exists", "That profile name is already taken.");
    }
    if (newClaimRef) {
      transaction.set(newClaimRef, {userId: uid, profileName: requested.profileName});
    }
    if (oldClaimRef && oldKey !== requested.key && oldClaim?.get("userId") === uid) {
      transaction.delete(oldClaimRef);
    }
    const profileVersion = Date.now();
    const updates = {
      profileName: requested.profileName,
      displayName: requested.profileName,
      profileNameNeedsConfirmation: false,
      profileVersion,
    };
    transaction.update(publicRef, updates);
    return publicUserDto({...publicSnapshot.data(), ...updates, userId: uid});
  });
  return {profileName: requested.profileName, displayName: requested.profileName, user};
});

exports.adminCreateManagedProfile = onCall(PROFILE_CALLABLE_OPTIONS, async (request) => {
  const database = getFirestore();
  await requireAdministrator(database, request);
  const actualName = requireActualName(request.data?.actualName);
  const requested = requireProfileName(request.data?.profileName);
  const email = optionalEmail(request.data?.email);
  const phoneNumber = optionalPhoneNumber(request.data?.phoneNumber);
  const userRef = database.collection(PUBLIC_USER_COLLECTION).doc();
  const privateRef = database.collection(PRIVATE_USER_COLLECTION).doc(userRef.id);
  const claimRef = database.collection(PROFILE_NAME_CLAIMS).doc(requested.key);
  const now = Date.now();
  const createdAt = Timestamp.fromMillis(now);
  await database.runTransaction(async (transaction) => {
    const claim = await transaction.get(claimRef);
    if (claim.exists) {
      throw new HttpsError("already-exists", "That profile name is already taken.");
    }
    transaction.create(claimRef, {userId: userRef.id, profileName: requested.profileName});
    transaction.create(userRef, {
      userId: userRef.id,
      provider: "managed",
      profileType: "managed",
      role: "regular_user",
      profileName: requested.profileName,
      displayName: requested.profileName,
      profileNameNeedsConfirmation: false,
      photoUrl: null,
      hidden: false,
      profileVersion: now,
      createdAt,
    });
    transaction.create(privateRef, {
      userId: userRef.id,
      actualName,
      email,
      phoneNumber,
      updatedAt: FieldValue.serverTimestamp(),
    });
  });
  const user = publicUserDto({
    userId: userRef.id,
    provider: "managed",
    profileType: "managed",
    role: "regular_user",
    profileName: requested.profileName,
    displayName: requested.profileName,
    profileNameNeedsConfirmation: false,
    photoUrl: null,
    hidden: false,
    profileVersion: now,
    createdAt,
  }, {actualName, email, phoneNumber});
  return {userId: userRef.id, profileName: requested.profileName,
    displayName: requested.profileName, user};
});

exports.adminUpdateManagedProfile = onCall(PROFILE_CALLABLE_OPTIONS, async (request) => {
  const database = getFirestore();
  await requireAdministrator(database, request);
  const userId = requireDocumentId(request.data?.userId, "A valid managed profile is required.");
  const actualName = requireActualName(request.data?.actualName);
  const requested = requireProfileName(request.data?.profileName);
  const email = optionalEmail(request.data?.email);
  const phoneNumber = optionalPhoneNumber(request.data?.phoneNumber);
  const publicRef = database.collection(PUBLIC_USER_COLLECTION).doc(userId);
  const privateRef = database.collection(PRIVATE_USER_COLLECTION).doc(userId);
  const user = await database.runTransaction(async (transaction) => {
    const publicSnapshot = await transaction.get(publicRef);
    if (!publicSnapshot.exists || publicSnapshot.get("profileType") !== "managed") {
      throw new HttpsError("failed-precondition", "Only managed profiles can be edited here.");
    }
    const oldName = safeText(publicSnapshot.get("profileName"), 24);
    const oldKey = oldName ? profileNameKey(oldName) : null;
    const newClaimRef = database.collection(PROFILE_NAME_CLAIMS).doc(requested.key);
    const oldClaimRef = oldKey && oldKey !== requested.key ?
      database.collection(PROFILE_NAME_CLAIMS).doc(oldKey) : null;
    const newClaim = await transaction.get(newClaimRef);
    const oldClaim = oldClaimRef ? await transaction.get(oldClaimRef) : null;
    if (newClaim.exists && newClaim.get("userId") !== userId) {
      throw new HttpsError("already-exists", "That profile name is already taken.");
    }
    transaction.set(newClaimRef, {userId, profileName: requested.profileName});
    if (oldClaimRef && oldClaim?.get("userId") === userId) {
      transaction.delete(oldClaimRef);
    }
    const profileVersion = Date.now();
    const updates = {
      profileName: requested.profileName,
      displayName: requested.profileName,
      profileNameNeedsConfirmation: false,
      profileVersion,
    };
    transaction.update(publicRef, updates);
    transaction.set(privateRef, {
      userId,
      actualName,
      email,
      phoneNumber,
      updatedAt: FieldValue.serverTimestamp(),
    }, {merge: true});
    return publicUserDto({...publicSnapshot.data(), ...updates, userId},
        {actualName, email, phoneNumber});
  });
  return {userId, profileName: requested.profileName,
    displayName: requested.profileName, user};
});

exports.adminUpdateGamePlayerMapping = onCall(PROFILE_CALLABLE_OPTIONS, async (request) => {
  const database = getFirestore();
  await requireAdministrator(database, request);
  const gameId = requireDocumentId(request.data?.gameId, "A valid game is required.");
  const playerId = requireDocumentId(request.data?.playerId, "A valid player is required.");
  const userId = requireDocumentId(request.data?.userId, "A valid profile is required.");
  const gameRef = database.collection(GAME_COLLECTION).doc(gameId);
  const dataRef = database.collection(GAME_DATA_COLLECTION).doc(gameId);
  const userRef = database.collection(PUBLIC_USER_COLLECTION).doc(userId);
  await database.runTransaction(async (transaction) => {
    const gameSnapshot = await transaction.get(gameRef);
    const dataSnapshot = await transaction.get(dataRef);
    const userSnapshot = await transaction.get(userRef);
    if (!gameSnapshot.exists || !dataSnapshot.exists) {
      throw new HttpsError("not-found", "The active game is no longer available.");
    }
    if (!userSnapshot.exists || userSnapshot.get("hidden") === true) {
      throw new HttpsError("failed-precondition", "Select an available player profile.");
    }
    const displayName = safeText(userSnapshot.get("profileName"), 24);
    if (!displayName) {
      throw new HttpsError("failed-precondition", "The selected profile needs a profile name.");
    }
    const managedProfile = userSnapshot.get("profileType") === "managed" ||
      userSnapshot.get("provider") === "managed";
    const wrapper = dataSnapshot.data() || {};
    const gameData = structuredClone(wrapper.data || {});
    const players = gameData.playersById;
    if (!players || typeof players !== "object" || !players[playerId]) {
      throw new HttpsError("not-found", "The selected player no longer exists.");
    }
    for (const [candidateId, candidate] of Object.entries(players)) {
      if (candidateId !== playerId && candidate?.userId === userId) {
        throw new HttpsError("already-exists", "That profile is already mapped in this game.");
      }
    }
    const previousUserId = safeText(players[playerId].userId, 128);
    players[playerId].userId = userId;
    players[playerId].name = displayName;
    const game = gameSnapshot.data() || {};
    const memberUserIds = new Set();
    for (const player of Object.values(players)) {
      const memberId = safeText(player?.userId, 128);
      if (memberId) memberUserIds.add(memberId);
    }
    const creatorId = safeText(game.creatorUserId, 128);
    const editorId = safeText(game.activeEditorUserId, 128);
    if (creatorId) memberUserIds.add(creatorId);
    if (editorId) memberUserIds.add(editorId);
    const revision = Number.isSafeInteger(wrapper.revision) ? wrapper.revision : 0;
    transaction.update(dataRef, {
      data: gameData,
      revision: revision + 1,
      lastOperationId: `admin-map-${randomUUID()}`,
      lastUpdated: FieldValue.serverTimestamp(),
    });
    transaction.update(gameRef, {
      memberUserIds: Array.from(memberUserIds),
      dashboardNumPlayers: Object.keys(players).length,
    });
    if (previousUserId && previousUserId !== userId) {
      transaction.delete(database.collection(VIEW_APPROVAL_COLLECTION)
          .doc(`${gameId}_${previousUserId}`));
      transaction.update(gameRef,
          new FieldPath("pendingViewRequests", previousUserId), FieldValue.delete());
    }
    const approvalRef = database.collection(VIEW_APPROVAL_COLLECTION)
        .doc(`${gameId}_${userId}`);
    if (managedProfile) {
      transaction.delete(approvalRef);
      transaction.update(gameRef,
          new FieldPath("pendingViewRequests", userId), FieldValue.delete());
    } else {
      transaction.set(approvalRef, {
        gameId,
        userId,
        status: "approved",
        requestedAt: FieldValue.serverTimestamp(),
        lastUpdatedAt: FieldValue.serverTimestamp(),
      });
      transaction.update(gameRef, new FieldPath("pendingViewRequests", userId), {
        status: "approved",
        requestedAt: FieldValue.serverTimestamp(),
        lastUpdatedAt: FieldValue.serverTimestamp(),
      });
    }
  });
  return {gameId, playerId, userId};
});

exports.deleteMyAccount = onCall(
    ACCOUNT_DELETION_CALLABLE_OPTIONS,
    async (request) => {
      if (!request.auth) {
        throw new HttpsError("unauthenticated", "Sign-in is required.");
      }
      if (!isRecentGoogleAuthentication(request.auth.token)) {
        throw new HttpsError(
            "failed-precondition",
            "Sign in again before deleting your account.",
        );
      }

      const counts = await completeAccountDeletion(request.auth.uid);
      logger.info("Self-service account deletion completed", counts);
      return {status: "deleted"};
    });

exports.adminDeleteAccount = onCall(
    ACCOUNT_DELETION_CALLABLE_OPTIONS,
    async (request) => {
      if (!request.auth) {
        throw new HttpsError("unauthenticated", "Sign-in is required.");
      }
      const targetUid = request.data && request.data.userId;
      if (!isValidUid(targetUid)) {
        throw new HttpsError("invalid-argument", "A valid user is required.");
      }
      if (targetUid === request.auth.uid) {
        throw new HttpsError(
            "failed-precondition",
            "Use self-service deletion for your own account.",
        );
      }

      const database = getFirestore();
      const adminSnapshot = await database.collection("appUser_v2")
          .doc(request.auth.uid).get();
      if (!adminSnapshot.exists ||
          !isAdministratorProfile(adminSnapshot.data())) {
        throw new HttpsError(
            "permission-denied",
            "Administrator access is required.",
        );
      }

      const counts = await completeAccountDeletion(targetUid);
      logger.info("Administrator account deletion completed", counts);
      return {status: "deleted"};
    });

async function completeAccountDeletion(uid) {
  const database = getFirestore();
  const publicRef = database.collection("appUser_v2").doc(uid);
  const publicSnapshot = await publicRef.get();
  const profileName = publicSnapshot.exists ?
    safeText(publicSnapshot.get("profileName"), 24) : null;
  const counts = await cleanupAccountData(uid);
  await deleteAuthenticationUser(uid);
  const removals = [
    publicRef.delete(),
    database.collection(PRIVATE_USER_COLLECTION).doc(uid).delete(),
  ];
  if (profileName) {
    removals.push(database.collection(PROFILE_NAME_CLAIMS)
        .doc(profileNameKey(profileName)).delete());
  }
  await Promise.all(removals);
  return counts;
}

async function requireAdministrator(database, request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign-in is required.");
  }
  const snapshot = await database.collection(PUBLIC_USER_COLLECTION)
      .doc(request.auth.uid).get();
  if (!snapshot.exists || !isAdministratorProfile(snapshot.data())) {
    throw new HttpsError("permission-denied", "Administrator access is required.");
  }
}

async function availableGeneratedProfileName(transaction, database, displayName, uid) {
  const baseName = generatedProfileName(displayName, uid);
  for (let sequence = 1; sequence <= 20; sequence++) {
    const profileName = profileNameWithSuffix(baseName, sequence);
    const ref = database.collection(PROFILE_NAME_CLAIMS).doc(profileNameKey(profileName));
    const claim = await transaction.get(ref);
    if (!claim.exists || claim.get("userId") === uid) return {profileName, ref};
  }
  const fallback = generatedProfileName(null, uid);
  const profileName = profileNameWithSuffix(fallback, 1);
  const ref = database.collection(PROFILE_NAME_CLAIMS).doc(profileNameKey(profileName));
  const claim = await transaction.get(ref);
  if (!claim.exists || claim.get("userId") === uid) return {profileName, ref};
  throw new HttpsError("resource-exhausted", "Could not allocate a unique profile name.");
}

function timestampMillis(value) {
  if (value instanceof Timestamp) return value.toMillis();
  if (value instanceof Date) return value.getTime();
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function publicUserDto(data, privateData = null) {
  const dto = {
    userId: data.userId || null,
    provider: data.provider || null,
    profileType: data.profileType || null,
    role: data.role || "regular_user",
    profileName: data.profileName || null,
    displayName: data.displayName || data.profileName || null,
    profileNameNeedsConfirmation: data.profileNameNeedsConfirmation === true,
    photoUrl: data.photoUrl || null,
    profileVersion: Number.isFinite(data.profileVersion) ? data.profileVersion : 0,
    createdAtMillis: timestampMillis(data.createdAt),
    lastLoginAtMillis: timestampMillis(data.lastLoginAt),
    safePlayPolicyVersion: Number.isInteger(data.safePlayPolicyVersion) ?
      data.safePlayPolicyVersion : null,
    safePlayAcceptedAtMillis: timestampMillis(data.safePlayAcceptedAt),
    hidden: data.hidden === true,
  };
  if (privateData) {
    dto.googleDisplayName = privateData.googleDisplayName || null;
    dto.actualName = privateData.actualName || null;
    dto.email = privateData.email || null;
    dto.phoneNumber = privateData.phoneNumber || null;
  }
  return dto;
}

function requireProfileName(value) {
  let requested;
  try {
    requested = validateProfileName(value);
  } catch (error) {
    throw new HttpsError("invalid-argument", error.message);
  }
  if (!requested.profileName) {
    throw new HttpsError("invalid-argument", "Profile name is required.");
  }
  return requested;
}

function requireActualName(value) {
  const actualName = safeText(value, 120);
  if (!actualName) {
    throw new HttpsError("invalid-argument", "Actual name is required.");
  }
  return actualName;
}

function optionalEmail(value) {
  if (value === null || value === undefined || value === "") return null;
  const email = safeText(value, 320);
  if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    throw new HttpsError("invalid-argument", "Enter a valid email address.");
  }
  return email.toLowerCase();
}

function optionalPhoneNumber(value) {
  if (value === null || value === undefined || value === "") return null;
  const phoneNumber = safeText(value, 32);
  const digits = phoneNumber ? phoneNumber.replace(/\D/g, "") : "";
  if (!phoneNumber || !/^[+0-9()\-\s]+$/.test(phoneNumber)
      || digits.length < 7 || digits.length > 15) {
    throw new HttpsError("invalid-argument", "Enter a valid phone number.");
  }
  return phoneNumber;
}

function requireDocumentId(value, message) {
  const id = safeText(value, 128);
  if (!id || id.includes("/")) {
    throw new HttpsError("invalid-argument", message);
  }
  return id;
}

function safeText(value, maxLength) {
  if (typeof value !== "string") {
    return null;
  }
  const text = value.trim();
  return text && text.length <= maxLength ? text : null;
}

async function cleanupAccountData(uid) {
  const database = getFirestore();
  const counts = {
    activeGamesUpdated: 0,
    approvedGamesUpdated: 0,
    approvalsDeleted: 0,
    historyEventsUpdated: 0,
  };

  await scanCollection(database.collection("games_v2"), async (gameSnapshot) => {
    const updated = await database.runTransaction(async (transaction) => {
      const gameRef = gameSnapshot.ref;
      const gameDataRef = database.collection("gameData_v2").doc(gameSnapshot.id);
      const game = await transaction.get(gameRef);
      const gameData = await transaction.get(gameDataRef);
      if (!game.exists) {
        return false;
      }

      const authResult = anonymizeGameAuth(game.data(), uid);
      const dataResult = gameData.exists ?
        anonymizeGameDataDocument(gameData.data(), uid) :
        {changed: false};
      if (authResult.changed) {
        transaction.set(gameRef, authResult.data);
      }
      if (gameData.exists && dataResult.changed) {
        transaction.set(gameDataRef, dataResult.data);
      }
      return authResult.changed || dataResult.changed;
    });
    if (updated) {
      counts.activeGamesUpdated++;
    }
  });

  // Repair orphaned/legacy game-data documents that have no corresponding games_v2 row.
  await scanCollection(database.collection("gameData_v2"), async (snapshot) => {
    await database.runTransaction(async (transaction) => {
      const current = await transaction.get(snapshot.ref);
      if (!current.exists) {
        return;
      }
      const result = anonymizeGameDataDocument(current.data(), uid);
      if (result.changed) {
        transaction.set(snapshot.ref, result.data);
      }
    });
  });

  await scanCollection(database.collection("approvedGames_v2"), async (snapshot) => {
    const result = anonymizeApprovedGame(snapshot.data(), uid);
    if (result.changed) {
      await snapshot.ref.set(result.data);
      counts.approvedGamesUpdated++;
    }
  });

  counts.approvalsDeleted = await deleteQueryMatches(
      database.collection("gameViewApprovals_v2").where("userId", "==", uid));
  counts.historyEventsUpdated = await removeHistoryEditorIdentity(database, uid);

  const rateLimitKey = createHash("sha256").update(uid).digest("hex");
  await Promise.all([
    database.collection("playerStats_v2").doc(uid).delete(),
    database.collection(FUNCTION_QUOTAS_COLLECTION).doc("groqGameNames")
        .collection("users").doc(rateLimitKey).delete(),
  ]);
  return counts;
}

async function scanCollection(collection, visitor) {
  let cursor = null;
  do {
    let query = collection.orderBy(FieldPath.documentId()).limit(DELETE_PAGE_SIZE);
    if (cursor) {
      query = query.startAfter(cursor);
    }
    const page = await query.get();
    for (const snapshot of page.docs) {
      await visitor(snapshot);
    }
    cursor = page.empty ? null : page.docs[page.docs.length - 1];
    if (page.size < DELETE_PAGE_SIZE) {
      break;
    }
  } while (cursor);
}

async function deleteQueryMatches(query) {
  let deleted = 0;
  while (true) {
    const page = await query.limit(DELETE_PAGE_SIZE).get();
    if (page.empty) {
      return deleted;
    }
    const batch = getFirestore().batch();
    for (const snapshot of page.docs) {
      batch.delete(snapshot.ref);
    }
    await batch.commit();
    deleted += page.size;
  }
}

async function removeHistoryEditorIdentity(database, uid) {
  let updated = 0;
  const query = database.collectionGroup("events").where("editorUserId", "==", uid);
  while (true) {
    const page = await query.limit(DELETE_PAGE_SIZE).get();
    if (page.empty) {
      return updated;
    }
    const batch = database.batch();
    for (const snapshot of page.docs) {
      batch.update(snapshot.ref, "editorUserId", FieldValue.delete());
    }
    await batch.commit();
    updated += page.size;
  }
}

async function deleteAuthenticationUser(uid) {
  try {
    await getAuth().deleteUser(uid);
  } catch (error) {
    if (!error || error.code !== "auth/user-not-found") {
      throw error;
    }
  }
}

async function enforceRequestQuotas(uid) {
  const database = getFirestore();
  const namespaceRef = database.collection(FUNCTION_QUOTAS_COLLECTION).doc("groqGameNames");
  const userKey = createHash("sha256").update(uid).digest("hex");
  const userRef = namespaceRef.collection("users").doc(userKey);
  const now = Date.now();
  const currentDayStartedAt =
    Math.floor(now / GLOBAL_RATE_LIMIT_WINDOW_MS) * GLOBAL_RATE_LIMIT_WINDOW_MS;

  await database.runTransaction(async (transaction) => {
    const userSnapshot = await transaction.get(userRef);
    const globalSnapshot = await transaction.get(namespaceRef);
    const userData = userSnapshot.data();
    const globalData = globalSnapshot.data();
    const userCounter = nextRollingCounter(
        timestampMillis(userData?.windowStartedAt),
        userData?.count,
        now,
        RATE_LIMIT_WINDOW_MS,
        MAX_REQUESTS_PER_WINDOW,
    );
    const globalCounter = nextFixedCounter(
        timestampMillis(globalData?.windowStartedAt),
        globalData?.count,
        currentDayStartedAt,
        MAX_GLOBAL_REQUESTS_PER_DAY,
    );

    if (!userCounter.allowed) {
      throw new HttpsError(
          "resource-exhausted",
          "Game-name request limit reached. Try again later.",
      );
    }
    if (!globalCounter.allowed) {
      throw new HttpsError(
          "resource-exhausted",
          "The daily game-name limit has been reached. Try again tomorrow.",
      );
    }

    transaction.set(userRef, {
      count: userCounter.count,
      windowStartedAt: Timestamp.fromMillis(userCounter.windowStartedAt),
      updatedAt: Timestamp.fromMillis(now),
    });
    transaction.set(namespaceRef, {
      count: globalCounter.count,
      windowStartedAt: Timestamp.fromMillis(globalCounter.windowStartedAt),
      updatedAt: Timestamp.fromMillis(now),
    });
  });
}

function timestampMillis(value) {
  return value instanceof Timestamp ? value.toMillis() : null;
}
