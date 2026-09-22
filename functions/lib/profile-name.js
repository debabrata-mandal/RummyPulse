"use strict";

const PROFILE_NAME_PATTERN = /^[A-Za-z0-9_]{3,16}$/;

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
  const profileName = value.trim();
  if (!PROFILE_NAME_PATTERN.test(profileName)) {
    throw new Error("Use 3–16 English letters, numbers, or underscores.");
  }
  return {profileName, key: profileName.toLowerCase()};
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
  if (data.pendingViewRequests && data.pendingViewRequests[uid] &&
      data.pendingViewRequests[uid].userDisplayName !== displayName) {
    data.pendingViewRequests[uid].userDisplayName = displayName;
    changed = true;
  }
  return {changed, data};
}

module.exports = {
  PROFILE_NAME_PATTERN,
  replaceGameIdentityNames,
  replaceLinkedPlayerNames,
  validateProfileName,
};
