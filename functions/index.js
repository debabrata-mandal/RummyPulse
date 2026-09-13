"use strict";

const {initializeApp} = require("firebase-admin/app");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");
const {createHash} = require("node:crypto");
const {defineSecret} = require("firebase-functions/params");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {logger} = require("firebase-functions");
const {extractGroqName} = require("./lib/game-name");
const {nextFixedCounter, nextRollingCounter} = require("./lib/rate-limit");

initializeApp();

const groqApiKey = defineSecret("GROQ_API_KEY");
const GROQ_MODEL = "openai/gpt-oss-20b";
const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
const RATE_LIMIT_COLLECTION = "_functionRateLimits";
const RATE_LIMIT_WINDOW_MS = 60 * 60 * 1000;
const GLOBAL_RATE_LIMIT_WINDOW_MS = 24 * 60 * 60 * 1000;
const MAX_REQUESTS_PER_WINDOW = 10;
const MAX_GLOBAL_REQUESTS_PER_DAY = 200;
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
