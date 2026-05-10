# Firestore — `gameDefaults` (system-wide game defaults)

## Collection and document

- **Collection:** `gameDefaults`
- **Document ID:** `config` (singleton row for the whole project)

## Fields

| Field | Type | Description |
|--------|------|-------------|
| `defaultPointValue` | number | Default ₹ per point for new games (app fallbacks: `0.15` if missing/invalid) |
| `defaultGstPercent` | number | Default contribution % (`gstPercent` on `GameData`; 0–100) |
| `defaultMidGameNewPlayerScoreIncrement` | number (int) | Added to max cumulative opponent total when a player is added mid-game (fallback `2`) |
| `updatedAt` | timestamp | Last write time (`serverTimestamp` on save) |
| `updatedByUserId` | string | Firebase Auth uid of saver |
| `updatedByUserName` | string | Display name or email (optional, for UI) |

## “Missing or insufficient permissions”

The Android client reads/writes `gameDefaults/config` **only while the user is signed in**. If your Firestore rules never mention `gameDefaults`, the default is **deny**, and you will see `PERMISSION_DENIED` / “Missing or insufficient permissions” when opening **Game defaults** or tapping **Save**.

**Fix:** in [Firebase Console](https://console.firebase.google.com) → your project → **Firestore Database** → **Rules**, add a `match` for `gameDefaults` inside `match /databases/{database}/documents { ... }`.

Example (merge with your existing rules; do not replace unrelated `match` blocks for `games`, `gameData`, etc.):

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    match /gameDefaults/{docId} {
      allow read, write: if request.auth != null;
    }

    // ... your other matches (games, gameData, appUser, …)
  }
}
```

Publish rules, wait a few seconds, then retry the app.

Tighten `write` to admin/moderator custom claims when you have them.

## Android

- Code: [`GameDefaultsRepository`](app/src/main/java/com/example/rummypulse/data/GameDefaultsRepository.java) (`COLLECTION`, `DOCUMENT_ID`).
- UI: **Game defaults** in the navigation drawer → [`GameDefaultsFragment`](app/src/main/java/com/example/rummypulse/ui/gamedefaults/GameDefaultsFragment.java).

You can also create the first document from the Firebase console using the field names above.
