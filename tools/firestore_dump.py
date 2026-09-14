#!/usr/bin/env python3
"""
Dump every Daybook user's cloud data from Firestore as readable JSON (testing only).

The app stores, per signed-in user:
  users/{uid}                      definitions (gzipped JSON bytes), aiSettings, aiExclusions, ...
  users/{uid}/months/{YYYY-MM}     payload (gzipped JSON bytes) = that month's day logs

The gzipped fields show up as unreadable "bytes" in the Firebase console. This script reads them
with the Admin SDK (project-owner credentials, NOT the user's Google account), gunzips them and
writes one folder per user:

  firestore_dump/<email or uid>/profile.json      parent doc, definitions decoded
  firestore_dump/<email or uid>/months/2026-09.json

Setup (once):
  pip install firebase-admin
  # Either: a service-account key (Console > Project settings > Service accounts > Generate key)
  export GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json
  # Or: gcloud auth application-default login   (with the project-owner Google account)

Run:
  python3 tools/firestore_dump.py [--project daybook-v2-1f578] [--out firestore_dump]

The output contains personal health/food data — keep it out of git (it's in .gitignore).
"""
import argparse
import datetime
import gzip
import json
import os
import re

import firebase_admin
from firebase_admin import auth, credentials, firestore


def decode(value):
    """Make a Firestore value JSON-friendly, gunzipping byte blobs back into JSON."""
    if isinstance(value, (bytes, bytearray)):
        try:
            text = gzip.decompress(bytes(value)).decode("utf-8")
            try:
                return json.loads(text)
            except ValueError:
                return text
        except OSError:
            return f"<{len(value)} bytes, not gzip>"
    if isinstance(value, dict):
        return {k: decode(v) for k, v in value.items()}
    if isinstance(value, list):
        return [decode(v) for v in value]
    if isinstance(value, datetime.datetime):
        return value.isoformat()
    return value


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False, default=str)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--project", default="daybook-v2-1f578")
    ap.add_argument("--out", default="firestore_dump")
    args = ap.parse_args()

    firebase_admin.initialize_app(credentials.ApplicationDefault(), {"projectId": args.project})
    db = firestore.client()

    for user_doc in db.collection("users").list_documents():
        uid = user_doc.id
        try:
            email = auth.get_user(uid).email or uid
        except Exception:  # noqa: BLE001 — auth lookup is best-effort
            email = uid
        folder = os.path.join(args.out, re.sub(r"[^\w@.+-]", "_", email))

        snap = user_doc.get()
        profile = decode(snap.to_dict() or {})
        profile["_uid"] = uid
        write_json(os.path.join(folder, "profile.json"), profile)

        months = 0
        for month in user_doc.collection("months").stream():
            write_json(os.path.join(folder, "months", f"{month.id}.json"), decode(month.to_dict()))
            months += 1
        print(f"{email}: profile + {months} month doc(s) -> {folder}")


if __name__ == "__main__":
    main()
