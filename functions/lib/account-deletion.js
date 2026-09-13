"use strict";

const DELETED_PLAYER_NAME = "Deleted player";
const DELETED_USER_NAME = "Deleted user";
const RECENT_AUTH_MAX_AGE_SECONDS = 5 * 60;
const ACCOUNT_DELETION_CALLABLE_OPTIONS = Object.freeze({
  region: "asia-south1",
  enforceAppCheck: true,
  timeoutSeconds: 540,
  memory: "256MiB",
});
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

function deleteMatchingIdentity(target, uid, idField, nameField) {
  if (!target || target[idField] !== uid) {
    return false;
  }
  delete target[idField];
  if (nameField) {
    target[nameField] = DELETED_USER_NAME;
  }
  return true;
}

function anonymizePlayer(player, uid) {
  if (!player || player.userId !== uid) {
    return false;
  }
  delete player.userId;
  player.name = DELETED_PLAYER_NAME;
  return true;
}

function anonymizeGameAuth(source, uid) {
  const data = cloneValue(source || {});
  let changed = false;

  changed = deleteMatchingIdentity(
      data, uid, "creatorUserId", "creatorName") || changed;
  changed = deleteMatchingIdentity(
      data, uid, "activeEditorUserId", "activeEditorName") || changed;
  changed = deleteMatchingIdentity(
      data, uid, "lastEditorUserId", "lastEditorName") || changed;

  if (Array.isArray(data.memberUserIds) && data.memberUserIds.includes(uid)) {
    data.memberUserIds = data.memberUserIds.filter((memberUid) => memberUid !== uid);
    changed = true;
  }
  if (data.pendingViewRequests &&
      Object.prototype.hasOwnProperty.call(data.pendingViewRequests, uid)) {
    delete data.pendingViewRequests[uid];
    changed = true;
  }

  return {changed, data};
}

function anonymizeGameDataDocument(source, uid) {
  const document = cloneValue(source || {});
  const gameData = document.data;
  let changed = false;

  if (gameData && gameData.playersById &&
      typeof gameData.playersById === "object") {
    for (const player of Object.values(gameData.playersById)) {
      changed = anonymizePlayer(player, uid) || changed;
    }
  }
  if (gameData && Array.isArray(gameData.players)) {
    for (const player of gameData.players) {
      changed = anonymizePlayer(player, uid) || changed;
    }
  }
  if (document.statsApplied && document.statsApplied.byUser &&
      Object.prototype.hasOwnProperty.call(document.statsApplied.byUser, uid)) {
    delete document.statsApplied.byUser[uid];
    changed = true;
  }

  return {changed, data: document};
}

function anonymizeApprovedGame(source, uid) {
  const data = cloneValue(source || {});
  let changed = false;
  if (Array.isArray(data.players)) {
    for (const player of data.players) {
      changed = anonymizePlayer(player, uid) || changed;
    }
  }
  return {changed, data};
}

function isRecentAuthentication(token, nowSeconds = Math.floor(Date.now() / 1000)) {
  const authTime = Number(token && token.auth_time);
  return Number.isFinite(authTime) && authTime > 0 &&
    nowSeconds - authTime >= 0 &&
    nowSeconds - authTime <= RECENT_AUTH_MAX_AGE_SECONDS;
}

function isRecentGoogleAuthentication(
    token,
    nowSeconds = Math.floor(Date.now() / 1000),
) {
  return isRecentAuthentication(token, nowSeconds) &&
    token.firebase && token.firebase.sign_in_provider === "google.com";
}

function isValidUid(uid) {
  return typeof uid === "string" && uid.length > 0 && uid.length <= 128;
}

function isAdministratorProfile(profile) {
  return Boolean(profile) && profile.role === "admin_user";
}

module.exports = {
  ACCOUNT_DELETION_CALLABLE_OPTIONS,
  DELETED_PLAYER_NAME,
  DELETED_USER_NAME,
  RECENT_AUTH_MAX_AGE_SECONDS,
  anonymizeApprovedGame,
  anonymizeGameAuth,
  anonymizeGameDataDocument,
  isRecentAuthentication,
  isRecentGoogleAuthentication,
  isAdministratorProfile,
  isValidUid,
};
