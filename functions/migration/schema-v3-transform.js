"use strict";

const SCHEMA_VERSION = 3;
const DEPRECATED_KEYS = new Set([
  "pointValue",
  "gstPercent",
  "gstAmount",
  "grossAmount",
  "netAmount",
  "gstPaid",
  "contributionPaid",
  "totalContribution",
  "totalGstCollected",
  "defaultPointValue",
  "defaultGstPercent",
  "dashboardPointValue",
  "dashboardGstPercent",
  "displayIntermediateCalculation",
  "showDashboardLeaderboardAmounts",
  "pointValueReports",
  "statsApplied",
]);

function requireFiniteNumber(value, field) {
  const number = typeof value === "string" && value.trim() !== ""
    ? Number(value)
    : value;
  if (typeof number !== "number" || !Number.isFinite(number)) {
    throw new Error(`${field} must be a finite number`);
  }
  return number;
}

function javaRound(value) {
  return Math.floor(value + 0.5);
}

function calculateGamePoints(scores, gamePointFactor, boardAdjustmentPercent, numPlayers) {
  const playerCount = requireFiniteNumber(numPlayers, "numPlayers");
  if (!Number.isInteger(playerCount) || playerCount <= 0 || scores.length !== playerCount) {
    throw new Error("numPlayers must equal the number of player rows");
  }
  const factor = requireFiniteNumber(gamePointFactor, "gamePointFactor");
  const adjustmentPercent = requireFiniteNumber(
      boardAdjustmentPercent, "boardAdjustmentPercent");
  if (factor <= 0 || adjustmentPercent < 0 || adjustmentPercent > 100) {
    throw new Error("Game Point factor or Board Adjustment percent is outside its valid range");
  }
  const normalizedScores = scores.map((score, index) =>
    requireFiniteNumber(score, `players[${index}].score`));
  const totalScore = normalizedScores.reduce((sum, score) => sum + score, 0);
  const players = normalizedScores.map((score) => {
    const baseGamePoints = javaRound(
        (totalScore - score * playerCount) * factor);
    const boardAdjustmentPoints = baseGamePoints > 0
      ? javaRound(baseGamePoints * adjustmentPercent / 100)
      : 0;
    return {
      baseGamePoints,
      boardAdjustmentPoints,
      finalGamePoints: baseGamePoints - boardAdjustmentPoints,
    };
  });
  const boardPoints = players.reduce(
      (sum, player) => sum + player.boardAdjustmentPoints, 0);
  const balance = players.reduce(
      (sum, player) => sum + player.finalGamePoints, 0) + boardPoints;
  if (balance !== 0) {
    throw new Error(`Game Point invariant failed with balance ${balance}`);
  }
  return {players, boardPoints};
}

function approvedPlayers(data) {
  if (Array.isArray(data.players) && data.players.length > 0) {
    return data.players.map((player, index) => {
      if (!player || typeof player !== "object" || Array.isArray(player)) {
        throw new Error(`players[${index}] must be an object`);
      }
      const name = typeof player.name === "string" ? player.name : "";
      return {
        name,
        userId: typeof player.userId === "string" && player.userId.trim() !== ""
          ? player.userId
          : null,
        score: requireFiniteNumber(player.score, `players[${index}].score`),
      };
    });
  }
  if (data.playerScores && typeof data.playerScores === "object" &&
      !Array.isArray(data.playerScores)) {
    return Object.entries(data.playerScores).map(([name, score]) => ({
      name,
      userId: null,
      score: requireFiniteNumber(score, `playerScores.${name}`),
    }));
  }
  throw new Error("approved game has neither players nor legacy playerScores");
}

function cloneWithoutDeprecated(value) {
  if (Array.isArray(value)) {
    return value.map(cloneWithoutDeprecated);
  }
  if (!value || typeof value !== "object" || value instanceof Date ||
      typeof value.toDate === "function") {
    return value;
  }
  const result = {};
  for (const [key, child] of Object.entries(value)) {
    if (!DEPRECATED_KEYS.has(key) && key !== "playerScores") {
      result[key] = cloneWithoutDeprecated(child);
    }
  }
  return result;
}

function readCanonical(data, canonical, legacy) {
  if (data[canonical] !== undefined) return data[canonical];
  return data[legacy];
}

function transformApprovedGame(data) {
  const players = approvedPlayers(data);
  const gamePointFactor = requireFiniteNumber(
      readCanonical(data, "gamePointFactor", "pointValue"), "gamePointFactor");
  const boardAdjustmentPercent = requireFiniteNumber(
      readCanonical(data, "boardAdjustmentPercent", "gstPercent"),
      "boardAdjustmentPercent");
  const numPlayers = requireFiniteNumber(data.numPlayers, "numPlayers");
  const calculated = calculateGamePoints(
      players.map((player) => player.score),
      gamePointFactor,
      boardAdjustmentPercent,
      numPlayers);
  const storedBoardPoints = data.boardPoints !== undefined
    ? requireFiniteNumber(data.boardPoints, "boardPoints")
    : data.gstAmount !== undefined
      ? requireFiniteNumber(data.gstAmount, "gstAmount")
      : calculated.boardPoints;
  if (storedBoardPoints !== calculated.boardPoints) {
    throw new Error(
        `stored Board Points ${storedBoardPoints} do not match calculated ` +
        `${calculated.boardPoints}`);
  }
  return {
    ...cloneWithoutDeprecated(data),
    schemaVersion: SCHEMA_VERSION,
    numPlayers,
    gamePointFactor,
    boardAdjustmentPercent,
    boardPoints: calculated.boardPoints,
    players,
  };
}

function transformDefaults(data) {
  return {
    ...cloneWithoutDeprecated(data || {}),
    defaultGamePointFactor: requireFiniteNumber(
        readCanonical(data || {}, "defaultGamePointFactor", "defaultPointValue"),
        "defaultGamePointFactor"),
    defaultBoardAdjustmentPercent: requireFiniteNumber(
        readCanonical(data || {}, "defaultBoardAdjustmentPercent", "defaultGstPercent"),
        "defaultBoardAdjustmentPercent"),
    showLiveGamePoints: readCanonical(
        data || {}, "showLiveGamePoints", "displayIntermediateCalculation") !== false,
    showDashboardLeaderboardGamePoints: readCanonical(
        data || {}, "showDashboardLeaderboardGamePoints",
        "showDashboardLeaderboardAmounts") !== false,
  };
}

function dateOf(value) {
  if (value && typeof value.toDate === "function") return value.toDate();
  if (value instanceof Date) return value;
  if (typeof value === "string" && value.trim() !== "") {
    const parsed = new Date(value.includes("T") ? value : value.replace(" ", "T") + "+05:30");
    if (!Number.isNaN(parsed.getTime())) return parsed;
  }
  return null;
}

function monthKey(game) {
  const date = dateOf(game.approvedAt) || dateOf(game.creationDateTime);
  if (!date) throw new Error("approved game has no usable approval/creation timestamp");
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Kolkata", year: "numeric", month: "2-digit",
  }).formatToParts(date);
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}`;
}

function isoWeekKey(game) {
  const instant = dateOf(game.approvedAt) || dateOf(game.creationDateTime);
  if (!instant) throw new Error("approved game has no usable approval/creation timestamp");
  const local = new Date(instant.toLocaleString("en-US", {timeZone: "Asia/Kolkata"}));
  local.setHours(0, 0, 0, 0);
  local.setDate(local.getDate() + 3 - ((local.getDay() + 6) % 7));
  const weekYear = local.getFullYear();
  const weekOne = new Date(weekYear, 0, 4);
  const week = 1 + Math.round(((local - weekOne) / 86400000 - 3 +
    ((weekOne.getDay() + 6) % 7)) / 7);
  return `${weekYear}-W${String(week).padStart(2, "0")}`;
}

function emptyBucket() {
  return {games: 0, wins: 0, finalGamePoints: 0,
    baseGamePoints: 0, boardAdjustmentPoints: 0};
}

function addBucket(bucket, points) {
  bucket.games += 1;
  bucket.wins += points.finalGamePoints > 0 ? 1 : 0;
  bucket.finalGamePoints += points.finalGamePoints;
  bucket.baseGamePoints += points.baseGamePoints;
  bucket.boardAdjustmentPoints += points.boardAdjustmentPoints;
}

function rebuildPlayerStats(games) {
  const stats = new Map();
  for (const game of games) {
    const calculated = calculateGamePoints(
        game.players.map((player) => player.score), game.gamePointFactor,
        game.boardAdjustmentPercent, game.numPlayers);
    const month = monthKey(game);
    const week = isoWeekKey(game);
    const seenUserIds = new Set();
    game.players.forEach((player, index) => {
      if (!player.userId) return;
      if (seenUserIds.has(player.userId)) return;
      seenUserIds.add(player.userId);
      if (!stats.has(player.userId)) {
        stats.set(player.userId, {
          schemaVersion: SCHEMA_VERSION,
          userId: player.userId,
          displayName: player.name,
          allTime: emptyBucket(), months: {}, weeks: {},
        });
      }
      const target = stats.get(player.userId);
      if (player.name) target.displayName = player.name;
      target.months[month] ||= emptyBucket();
      target.weeks[week] ||= emptyBucket();
      addBucket(target.allTime, calculated.players[index]);
      addBucket(target.months[month], calculated.players[index]);
      addBucket(target.weeks[week], calculated.players[index]);
    });
  }
  for (const value of stats.values()) {
    value.months = newestEntries(value.months, 24);
    value.weeks = newestEntries(value.weeks, 12);
  }
  return stats;
}

function newestEntries(source, limit) {
  return Object.fromEntries(Object.entries(source).sort(([a], [b]) =>
    a.localeCompare(b)).slice(-limit));
}

function rebuildReports(games) {
  const months = new Map();
  for (const game of games) {
    const month = monthKey(game);
    if (!months.has(month)) months.set(month, new Map());
    const factors = months.get(month);
    const factorKey = String(game.gamePointFactor);
    if (!factors.has(factorKey)) {
      factors.set(factorKey, {
        gamePointFactor: game.gamePointFactor,
        totalGames: 0,
        totalBoardPoints: 0,
        totalPlayers: 0,
      });
    }
    const row = factors.get(factorKey);
    row.totalGames += 1;
    row.totalBoardPoints += game.boardPoints;
    row.totalPlayers += game.numPlayers;
  }
  return new Map([...months].map(([month, factors]) => [month, {
    schemaVersion: SCHEMA_VERSION,
    monthYear: new Intl.DateTimeFormat("en-US", {
      timeZone: "Asia/Kolkata", year: "numeric", month: "long",
    }).format(new Date(`${month}-01T00:00:00+05:30`)),
    gamePointFactorReports: [...factors.values()].sort(
        (left, right) => left.gamePointFactor - right.gamePointFactor),
  }]));
}

function findDeprecatedPaths(value, prefix = "") {
  const found = [];
  if (Array.isArray(value)) {
    value.forEach((child, index) => found.push(
        ...findDeprecatedPaths(child, `${prefix}[${index}]`)));
  } else if (value && typeof value === "object" && !(value instanceof Date) &&
      typeof value.toDate !== "function") {
    for (const [key, child] of Object.entries(value)) {
      const path = prefix ? `${prefix}.${key}` : key;
      if (DEPRECATED_KEYS.has(key) || key === "playerScores") found.push(path);
      found.push(...findDeprecatedPaths(child, path));
    }
  }
  return found;
}

module.exports = {
  DEPRECATED_KEYS,
  SCHEMA_VERSION,
  calculateGamePoints,
  findDeprecatedPaths,
  rebuildPlayerStats,
  rebuildReports,
  transformApprovedGame,
  transformDefaults,
};
