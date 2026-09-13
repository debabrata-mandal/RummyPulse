"use strict";

const GAME_NAME_PATTERN = /^[A-Za-z]+(?: [A-Za-z]+)?$/;

function normalizeGeneratedName(value) {
  if (typeof value !== "string") {
    throw new Error("Generated content was not text");
  }

  let name = value.split(/\r?\n/, 1)[0].trim();
  if ((name.startsWith('"') && name.endsWith('"')) ||
      (name.startsWith("'") && name.endsWith("'"))) {
    name = name.slice(1, -1).trim();
  }

  if (name.length < 3 || name.length > 32 || !GAME_NAME_PATTERN.test(name)) {
    throw new Error("Generated game name did not match the required format");
  }
  return name;
}

function extractGroqName(payload) {
  const content = payload?.choices?.[0]?.message?.content;
  return normalizeGeneratedName(content);
}

module.exports = {extractGroqName, normalizeGeneratedName};
