# Cross-Device Sync with Google Sign-In — Design Spec

**Date:** 2026-07-22
**Status:** Approved — implementing

## Overview

Add Google Sign-In authentication and periodic cloud sync so a user's phone and tablet share the same time bank, settings, and controlled apps via Supabase.

## Architecture

```
Device A (Phone)          Supabase                Device B (Tablet)
─────────────            ┌──────────┐             ─────────────
Room DB ───┐             │ Auth     │             ┌── Room DB
           │  every 30s  │ (Google) │  every 30s  │
SyncEngine ── POST/GET ──│ PostgREST│── GET/POST ── SyncEngine
           │   pending   │  ──────  │   pending   │
           │   records   │ user_*   │   records   │
           └─────────────│  tables  │─────────────┘
                        └──────────┘
```

## Anti-Exploit Strategy

**Sync raw immutable records, not computed balances.** The time bank balance is always derived by summing all records across devices. No device can overwrite another's data. Records are additive facts.

### Concurrency protection per scenario:

| Scenario | Protection |
|----------|-----------|
| Both betting same time | Each round is a separate record. Daily bonus cap (75%) enforced server-side on settlement. |
| Both using apps same time | Each device generates its own usage sessions with device_id. Sum is total. |
| Both changing settings | Last-write-wins by updated_at timestamp. |
| Offline play → reconnect | Pushes pending records on reconnect. Server-side cap check. |
| 2s session minimum | Already exists in ForegroundUsageMonitor. |

## Database Changes

### New Supabase tables:
- `user_time_banks` — one row per user per date, synced from local
- `user_settings` — one row per user
- `user_controlled_apps` — per-user app list
- `user_casino_rounds` — immutable round records
- `user_usage_sessions` — immutable usage records

### Local Room DB changes:
- Add `sync_status` (pending/synced), `device_id`, `server_id` to all entities
- Add `user_profile` table for auth state

## Auth Flow

1. User taps "Sign in with Google"
2. Google One-Tap sign-in → get id_token
3. Send id_token to Supabase Auth → get session (access_token + user_id)
4. Store session locally (DataStore)
5. All SyncEngine requests use the access_token (RLS scopes data to user_id)

## Data Flow

### Push (every 30s):
1. Query local DB for records with sync_status = 'pending'
2. POST/PUT to Supabase via PostgREST
3. On success: update sync_status to 'synced', store server_id

### Pull (every 30s):
1. GET records with updated_at > last_sync_time
2. Upsert into local Room DB
3. Recompute time bank balance

### Balance Recompute:
```
balance = base_allowance
        - SUM(usage_sessions.duration_seconds WHERE date = today)
        + SUM(casino_rounds.profit_seconds WHERE date = today)
        - SUM(casino_rounds.loss_seconds WHERE date = today)
        + SUM(sports_preds.settlement_profit WHERE date = today)
```
