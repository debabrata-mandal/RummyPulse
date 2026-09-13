"use strict";

const {initializeApp} = require("firebase-admin/app");
const {getAuth} = require("firebase-admin/auth");
const {getFirestore, FieldPath, FieldValue, Timestamp} =
  require("firebase-admin/firestore");
const {createHash} = require("node:crypto");
const {defineSecret} = require("firebase-functions/params");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {logger} = require("firebase-functions");
const {extractGroqName} = require("./lib/game-name");
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

const groqApiKey = defineSecret("GROQ_API_KEY");
const GROQ_MODEL = "openai/gpt-oss-20b";
const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
const RATE_LIMIT_COLLECTION = "_functionRateLimits";
const RATE_LIMIT_WINDOW_MS = 60 * 60 * 1000;
const GLOBAL_RATE_LIMIT_WINDOW_MS = 24 * 60 * 60 * 1000;
const MAX_REQUESTS_PER_WINDOW = 10;
const MAX_GLOBAL_REQUESTS_PER_DAY = 200;
const DELETE_PAGE_SIZE = 200;
const PROMPT =
  "Generate one short, catchy English name for a rummy or card game app. " +
  "Use one or two words in title case, with no numbers or punctuation. " +
  "Reply with only the name.";

exports.suggestGameName = onCall({
  region: "asia-south1",
  secrets: [groqApiKey],
  enforceAppCheck: true,
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
  const counts = await cleanupAccountData(uid);
  await deleteAuthenticationUser(uid);
  await getFirestore().collection("appUser_v2").doc(uid).delete();
  return counts;
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
    database.collection(RATE_LIMIT_COLLECTION).doc("groqGameNames")
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
  const namespaceRef = database.collection(RATE_LIMIT_COLLECTION).doc("groqGameNames");
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
