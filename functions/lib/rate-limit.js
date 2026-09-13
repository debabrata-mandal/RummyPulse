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

module.exports = {nextFixedCounter, nextRollingCounter};
