# Profile privacy rollout

Deploy this change in the following order so legacy clients cannot recreate public email fields.

1. Deploy Cloud Functions, including `syncMyIdentity` and `setProfileName`.
2. From `functions/`, run `npm run migrate:private-profiles` and review the dry-run counts.
3. Run `npm run migrate:private-profiles -- --apply` with production Admin SDK credentials.
4. Verify that every `appUser_v2` document has a unique profile name and confirmation flag, contains
   no email or Google real name, and has matching identity and claim documents.
5. Deploy `firestore.rules` and release the compatible Android app.
6. Raise Remote Config `min_supported_version_code` to that release.

The migration is idempotent and uses `appUser_v2` as its only source user collection. It copies
Google identity into `appUserIdentity_v1`, removes private fields from the public document, creates
case-insensitive claims, and generates friendly names such as `Debabrata M.` when needed.

The administrator will separately purge `_functionRateLimits`, `approvedGamesReport_v2`,
`approvedGames_v2`, `gameData_v2`, `gameScoreHistory_v2`, `gameViewApprovals_v2`, `games_v2`, and
`playerStats_v2`. The migration does not read, update, or delete any of those collections.
