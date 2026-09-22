# Profile privacy rollout

Deploy this change in the following order so legacy clients cannot recreate public email fields.

1. Deploy Cloud Functions, including `syncMyIdentity` and `setProfileName`.
2. Release the compatible Android app and raise Remote Config `min_supported_version_code` to that release.
3. From `functions/`, run `npm run migrate:private-profiles` and review the dry-run counts.
4. Run `npm run migrate:private-profiles -- --apply` with production Admin SDK credentials.
5. Verify that `appUser_v2` contains no `email` fields and that `appUserIdentity_v1` is complete.
6. Deploy `firestore.rules`.

The migration is idempotent. It copies Google identity into the private collection, removes public
email fields, creates claims for any existing profile names, and replaces UID-linked legacy name
snapshots with each user's effective public display name. It also removes the legacy `displayName`
field from the preserved `playerStats_v2` collection. Wipe `games_v2` before rollout; no migration
is performed for that collection. Leaderboard and game-attribution names are joined from
`appUser_v2` by `userId`.
