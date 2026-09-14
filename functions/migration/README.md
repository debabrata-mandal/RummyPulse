# Firestore schema-v3 migration

This one-time migration replaces deprecated schema-2 field names in historical approved games,
player statistics, monthly reports, and shared defaults with the Game Points schema.
It does not deploy code, change active games, or contact Firebase unless an operator runs the CLI.

## Safety properties

- Dry-run is the default; writes require `--apply`.
- The production project ID must be exactly `rummy-score-master`.
- Production apply requires the exact confirmation phrase and a backup timestamp less than 24 hours old.
- Both `games_v2` and `gameData_v2` must be empty before planning and immediately before writing.
- Every source document is transformed and validated before the first write.
- Ambiguous or inconsistent records stop the run and are written to an ignored local review report.
- Approved games are rewritten in place, preserving document IDs, player order, names, UIDs, scores,
  and timestamps.
- `playerStats_v2` is rebuilt only from approved games; stale statistics are removed.
- `approvedGamesReport_v2` is rebuilt from approved games.
- `gameDefaults_v2/config.schemaVersion` is changed to `3` only after all other writes succeed.
- Bounded, retry-safe batches allow the command to be rerun after an interruption.

The exact schema-2 compatibility keys are intentionally confined to the migration implementation
and its automated tests. They are not part of the Android application after this cutover. Older
name-keyed player rows become ordered schema-3 rows without inventing Firebase identities.

## Test locally

From the repository root:

```powershell
npm --prefix functions test
npm --prefix functions run check
npx --yes firebase-tools emulators:exec --project demo-rummypulse-schema-v3 --only firestore "npm --prefix functions test"
```

The emulator project name begins with `demo-`, so the Firebase CLI cannot contact production
services accidentally.

## Production runbook

Do not distribute the schema-v3 APK until this runbook is complete.

1. Deploy the matching Firestore rules and finish or delete every active/review game. Confirm both
   `games_v2` and `gameData_v2` are empty.
2. Create and verify a Firestore managed export. Record the UTC completion timestamp.
3. Authenticate Application Default Credentials on the operator workstation:

   ```powershell
   gcloud auth application-default login
   ```

4. Run a read-only dry run:

   ```powershell
   npm --prefix functions run migrate:schema-v3 -- --project rummy-score-master
   ```

5. Resolve every file under `functions/migration/reports/`. Do not apply while the dry run reports
   manual-review records. These files are ignored by Git and can contain document identifiers.
6. Apply only after the dry run is clean, replacing the example timestamp with the verified export
   completion time:

   ```powershell
   npm --prefix functions run migrate:schema-v3 -- --project rummy-score-master --apply --confirm MIGRATE-RUMMY-SCORE-MASTER-TO-SCHEMA-3 --backup-confirmed-at 2026-09-14T12:00:00Z
   ```

7. Validate independently:

   ```powershell
   npm --prefix functions run migrate:schema-v3 -- --project rummy-score-master --validate
   ```

8. Inspect a sample of approved games, statistics, reports, and defaults in Firebase Console. Then
   publish the schema-v3 APK and raise `min_supported_version_code` to its version code.

## Recovery

If apply stops before the marker is written, users remain at the maintenance screen. Fix the
reported record or operational problem and rerun the same command; completed phases are safe to
repeat. Do not manually set `schemaVersion` to `3`.

If validation reveals incorrect source data, keep the APK blocked, restore the verified Firestore
export, investigate, and repeat the dry run. The migration intentionally provides no automatic
rollback because a managed export is the authoritative recovery point.
