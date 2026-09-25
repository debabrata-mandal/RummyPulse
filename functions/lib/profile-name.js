"use strict";

const PROFILE_NAME_MIN_LENGTH = 3;
const PROFILE_NAME_MAX_LENGTH = 24;
const PROFILE_NAME_PATTERN = /^[\p{L}\p{N}_ ]+\.?(?: [0-9]+)?$/u;

function normalizeProfileName(value) {
  return typeof value === "string" ? value.normalize("NFKC").trim().replace(/\s+/gu, " ") : "";
}

function profileNameKey(value) {
  return normalizeProfileName(value).toLocaleLowerCase("en-US");
}

function truncateCodePoints(value, maxLength) {
  return [...value].slice(0, maxLength).join("").trimEnd();
}

function generatedProfileName(displayName, uid = "") {
  const normalized = normalizeProfileName(displayName);
  const tokens = normalized.split(" ").map((token) =>
    [...token].filter((character) => /[\p{L}\p{N}_]/u.test(character)).join(""),
  ).filter(Boolean);
  let candidate = "";
  if (tokens.length === 1) {
    candidate = tokens[0];
  } else if (tokens.length > 1) {
    candidate = `${tokens[0]} ${[...tokens[tokens.length - 1]][0].toLocaleUpperCase("en-US")}.`;
  }
  if ([...candidate].length < PROFILE_NAME_MIN_LENGTH) {
    const suffix = String(uid).replace(/[^A-Za-z0-9]/g, "").slice(-8) || "User";
    candidate = `Player ${suffix}`;
  }
  return truncateCodePoints(candidate, PROFILE_NAME_MAX_LENGTH);
}

function profileNameWithSuffix(baseName, sequence) {
  if (!Number.isInteger(sequence) || sequence <= 1) return truncateCodePoints(baseName, PROFILE_NAME_MAX_LENGTH);
  const suffix = ` ${sequence}`;
  return `${truncateCodePoints(baseName, PROFILE_NAME_MAX_LENGTH - suffix.length)}${suffix}`;
}

function needsProfileNameConfirmation(existingValue, generated) {
  if (generated) return true;
  // Legacy appUser_v2 documents have no confirmation field. They still need
  // one prompt even when their existing name can be preserved as the default.
  return existingValue !== false;
}

function cloneValue(value) {
  if (Array.isArray(value)) {
    return value.map(cloneValue);
  }
  if (value && Object.getPrototypeOf(value) === Object.prototype) {
    const copy = {};
    for (const [key, child] of Object.entries(value)) {
      copy[key] = cloneValue(child);
    }
    return copy;
  }
  return value;
}

function validateProfileName(value) {
  if (value === null || value === undefined || value === "") {
    return {profileName: null, key: null};
  }
  if (typeof value !== "string") {
    throw new Error("Profile name must be text.");
  }
  const profileName = normalizeProfileName(value);
  const length = [...profileName].length;
  if (length < PROFILE_NAME_MIN_LENGTH || length > PROFILE_NAME_MAX_LENGTH ||
      !PROFILE_NAME_PATTERN.test(profileName) || profileName.includes("  ")) {
    throw new Error("Use 3–24 letters, numbers, spaces, underscores, or a period.");
  }
  return {profileName, key: profileNameKey(profileName)};
}

function replaceLinkedPlayerNames(source, uid, displayName) {
  const document = cloneValue(source || {});
  const gameData = document.data || document;
  let changed = false;
  const replace = (player) => {
    if (player && player.userId === uid && player.name !== displayName) {
      player.name = displayName;
      changed = true;
    }
  };
  if (gameData.playersById && typeof gameData.playersById === "object") {
    Object.values(gameData.playersById).forEach(replace);
  }
  if (Array.isArray(gameData.players)) {
    gameData.players.forEach(replace);
  }
  return {changed, data: document};
}

function replaceGameIdentityNames(source, uid, displayName) {
  const data = cloneValue(source || {});
  let changed = false;
  for (const nameField of ["creatorName", "activeEditorName", "lastEditorName"]) {
    if (Object.prototype.hasOwnProperty.call(data, nameField)) {
      delete data[nameField];
      changed = true;
    }
  }
  return {changed, data};
}

module.exports = {
  generatedProfileName,
  normalizeProfileName,
  needsProfileNameConfirmation,
  PROFILE_NAME_MAX_LENGTH,
  PROFILE_NAME_MIN_LENGTH,
  PROFILE_NAME_PATTERN,
  profileNameKey,
  profileNameWithSuffix,
  replaceGameIdentityNames,
  replaceLinkedPlayerNames,
  validateProfileName,
};
