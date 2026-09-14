# Viewing Daybook user data in Firebase (testing only)

Project: **daybook-v2-1f578** · Firestore database: `(default)` (asia-south1)

## Where the data lives

| Path | What | Readable in the console? |
|---|---|---|
| `users/{uid}` | One document per signed-in account | Partly |
| `users/{uid}` → `aiSettings` | Summary/Chat instructions, AI categories, chat date range | Yes |
| `users/{uid}` → `aiExclusions`, `healthHiddenCards` | Hidden-from-AI items, hidden Health cards | Yes |
| `users/{uid}` → `appVersion`, `updatedAt`, `revision`, `deviceId`, `monthHashes` | Sync metadata | Yes |
| `users/{uid}` → `definitions` | Habits, intake reminders, routines, custom exercises | **No**: gzip-compressed JSON bytes |
| `users/{uid}/months/{YYYY-MM}` → `payload` | That month's day logs (habits, intake, workouts, health, hydration, AI summaries) | **No**: gzip-compressed JSON bytes |

## Looking in the Firebase console

1. Firebase console → project **daybook-v2-1f578** → **Firestore Database → Data**.
2. Open the `users` collection. Each document ID is a user's UID.
3. To find which UID belongs to whom: **Authentication → Users**, then match the **User UID** column.

## Reading everything without logging in as the tester

`tools/firestore_dump.py` uses **project-owner** access (Firebase Admin SDK). That access can read
every user's data without their Google account. The script decompresses the gzipped fields into
normal JSON files.

1. Install the SDK:
   ```bash
   pip install firebase-admin
   ```
2. Give it owner access. Either way works:
   - Firebase console → **Project settings → Service accounts → Generate new private key**, then
     ```bash
     export GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json
     ```
   - or, with the project-owner Google account (abhiramys.learn@gmail.com):
     ```bash
     gcloud auth application-default login
     ```
3. Run it from the repo root:
   ```bash
   python3 tools/firestore_dump.py
   # options: --project daybook-v2-1f578 --out firestore_dump
   ```

Output:

```
firestore_dump/<tester email or uid>/profile.json          # parent doc, definitions decoded
firestore_dump/<tester email or uid>/months/2026-09.json   # one file per synced month
```

## Keep it private

- The output holds testers' personal health, food and habit data. `firestore_dump/` is in
  `.gitignore`. Never commit it or share it.
- A service-account key can read (and write) **all** project data. Keep it outside the repo, and
  delete it in the console (**Service accounts → Manage keys**) once testing is done.
- Let testers know their synced data is visible to you as the project owner.

## Firestore security rules

The repo's `firestore.rules` now lists the AI-settings fields in its allowed-fields check. Before
that fix, deploying the file would have rejected every sync upload. The rules currently **live** on
Firebase are an older version: owner-only read/write, with no field list. Sync works with them
today, so deploying is optional. To deploy the stricter repo version:

```bash
firebase deploy --only firestore:rules
```
