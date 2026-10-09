"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {nextRollingCounter, rollingWindowRetryDays} = require("../lib/rate-limit");

const DAY = 24 * 60 * 60 * 1000;
const WEEK = 7 * DAY;

test("profile name changes allow two per rolling week", () => {
  const start = Date.UTC(2026, 9, 1);
  const first = nextRollingCounter(null, undefined, start, WEEK, 2);
  assert.deepEqual(first, {allowed: true, count: 1, windowStartedAt: start});

  const second = nextRollingCounter(first.windowStartedAt, first.count, start + DAY, WEEK, 2);
  assert.deepEqual(second, {allowed: true, count: 2, windowStartedAt: start});

  const third = nextRollingCounter(second.windowStartedAt, second.count, start + 2 * DAY, WEEK, 2);
  assert.equal(third.allowed, false);
  assert.equal(third.count, 2);
  assert.equal(third.windowStartedAt, start);
});

test("profile name changes are allowed again once the week ends", () => {
  const start = Date.UTC(2026, 9, 1);
  const afterWeek = nextRollingCounter(start, 2, start + WEEK, WEEK, 2);
  assert.deepEqual(afterWeek, {allowed: true, count: 1, windowStartedAt: start + WEEK});
});

test("retry days round up and never drop below one", () => {
  const start = Date.UTC(2026, 9, 1);
  assert.equal(rollingWindowRetryDays(start, start + 2 * DAY, WEEK), 5);
  assert.equal(rollingWindowRetryDays(start, start + 2 * DAY + 1, WEEK), 5);
  assert.equal(rollingWindowRetryDays(start, start + 2 * DAY - 1, WEEK), 6);
  assert.equal(rollingWindowRetryDays(start, start + WEEK - 1, WEEK), 1);
  assert.equal(rollingWindowRetryDays(start, start + WEEK, WEEK), 1);
});
