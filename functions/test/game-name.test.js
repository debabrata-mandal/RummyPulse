"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {extractGroqName, normalizeGeneratedName} = require("../lib/game-name");
const {nextFixedCounter, nextRollingCounter} = require("../lib/rate-limit");

test("normalizes a valid one- or two-word game name", () => {
  assert.equal(normalizeGeneratedName("  Royal Rummy\n"), "Royal Rummy");
  assert.equal(normalizeGeneratedName('"Cardora"'), "Cardora");
});

test("extracts a game name from the Groq response", () => {
  assert.equal(extractGroqName({
    choices: [{message: {content: "Rummy Royale"}}],
  }), "Rummy Royale");
});

test("rejects malformed model output", () => {
  assert.throws(() => normalizeGeneratedName("Rummy! 123"));
  assert.throws(() => extractGroqName({choices: []}));
});

test("rolling quota blocks the eleventh request inside one hour", () => {
  const result = nextRollingCounter(1_000, 10, 2_000, 3_600_000, 10);
  assert.deepEqual(result, {allowed: false, count: 10, windowStartedAt: 1_000});
});

test("rolling quota resets after its window", () => {
  const result = nextRollingCounter(1_000, 10, 3_601_000, 3_600_000, 10);
  assert.deepEqual(result, {allowed: true, count: 1, windowStartedAt: 3_601_000});
});

test("global quota blocks request 201 and resets on the next UTC day", () => {
  const blocked = nextFixedCounter(86_400_000, 200, 86_400_000, 200);
  assert.deepEqual(blocked,
      {allowed: false, count: 200, windowStartedAt: 86_400_000});

  const reset = nextFixedCounter(86_400_000, 200, 172_800_000, 200);
  assert.deepEqual(reset,
      {allowed: true, count: 1, windowStartedAt: 172_800_000});
});
