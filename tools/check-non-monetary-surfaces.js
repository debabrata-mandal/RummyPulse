#!/usr/bin/env node
"use strict";

const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve(__dirname, "..");
const resourceRoot = path.join(root, "app", "src", "main", "res");
const stringsPath = path.join(resourceRoot, "values", "strings.xml");
const explicitlyProhibitiveResources = new Set(["activity_safe_play_policy.xml"]);
const explicitlyProhibitiveDocs = new Set([
  "game-points-policy.html",
  "privacy-policy.html",
]);
const prohibited = [
  {label: "currency symbol", expression: /₹/iu},
  {label: "currency abbreviation", expression: /\b(?:INR|Rs\.)\b/iu},
  {label: "tax terminology", expression: /\bGST\b/iu},
  {label: "winnings", expression: /\bwinnings?\b/iu},
  {label: "settlement", expression: /\bsettlements?\b/iu},
  {label: "dues", expression: /\bdues?\b/iu},
  {label: "payout", expression: /\bpayouts?\b/iu},
  {label: "cash", expression: /\bcash\b/iu},
  {label: "payment", expression: /\bpayments?\b/iu},
  {label: "stakes", expression: /\bstakes?\b/iu},
  {label: "wagering", expression: /\bwager(?:ing)?\b/iu},
  {label: "prize", expression: /\bprizes?\b/iu},
  {label: "payment routing", expression: /who\s+pays\s+whom/iu},
];

function filesBelow(directory) {
  return fs.readdirSync(directory, {withFileTypes: true}).flatMap((entry) => {
    const target = path.join(directory, entry.name);
    return entry.isDirectory() ? filesBelow(target) : [target];
  });
}

function violationsIn(text, relativePath) {
  return prohibited
      .filter(({expression}) => expression.test(text))
      .map(({label}) => `${relativePath}: ${label}`);
}

const violations = [];
const strings = fs.readFileSync(stringsPath, "utf8");
const stringPattern = /<string\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/gu;
for (const match of strings.matchAll(stringPattern)) {
  if (!match[1].startsWith("safe_play_")) {
    violations.push(...violationsIn(match[2], `strings.xml#${match[1]}`));
  }
}

for (const file of filesBelow(resourceRoot)) {
  if (!file.endsWith(".xml") || file === stringsPath ||
      explicitlyProhibitiveResources.has(path.basename(file))) continue;
  violations.push(...violationsIn(
      fs.readFileSync(file, "utf8"), path.relative(root, file)));
}

const docsRoot = path.join(root, "docs");
for (const file of [...filesBelow(docsRoot), path.join(root, "README.md")]) {
  if (explicitlyProhibitiveDocs.has(path.basename(file))) continue;
  violations.push(...violationsIn(
      fs.readFileSync(file, "utf8"), path.relative(root, file)));
}

if (violations.length > 0) {
  console.error("Non-monetary surface check failed:");
  violations.forEach((violation) => console.error(`- ${violation}`));
  process.exitCode = 1;
} else {
  console.log("Non-monetary surface check passed");
}
