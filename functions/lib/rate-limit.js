"use strict";

function validCount(value) {
  return Number.isInteger(value) && value >= 0 ? value : 0;
}

function nextRollingCounter(previousStart, previousCount, now, windowSize, maximum) {
  const withinWindow = Number.isFinite(previousStart) &&
    previousStart <= now && now - previousStart < windowSize;
  const windowStartedAt = withinWindow ? previousStart : now;
  const count = withinWindow ? validCount(previousCount) : 0;
  return {
    allowed: count < maximum,
    count: count < maximum ? count + 1 : count,
    windowStartedAt,
  };
}

function nextFixedCounter(previousStart, previousCount, currentWindowStart, maximum) {
  const sameWindow = previousStart === currentWindowStart;
  const count = sameWindow ? validCount(previousCount) : 0;
  return {
    allowed: count < maximum,
    count: count < maximum ? count + 1 : count,
    windowStartedAt: currentWindowStart,
  };
}

function rollingWindowRetryDays(windowStartedAt, now, windowSize) {
  const remaining = windowStartedAt + windowSize - now;
  return Math.max(1, Math.ceil(remaining / (24 * 60 * 60 * 1000)));
}

module.exports = {nextFixedCounter, nextRollingCounter, rollingWindowRetryDays};
