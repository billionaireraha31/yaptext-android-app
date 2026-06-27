# YapText license server (JVZoo → license key → app unlock)

This folder holds the backend that turns a **JVZoo purchase** into a **license
key** that unlocks the YapText apps. It runs on the **same Supabase project the
web app already uses** (`cgogrstoqhmzkuxvcakk`).

```
buyer pays on JVZoo  ─►  JVZoo calls  jvzoo-delivery  ─►  makes a license key, saves it, shows it to buyer
buyer types key in app  ─►  app calls  verify-license  ─►  { valid: true }  ─►  Pro unlocked
```

## What's here
- `supabase/migrations/..._licenses.sql` — the `licenses` table.
- `supabase/functions/jvzoo-delivery/` — JVZoo webhook: verifies the sale, creates + returns a license key, revokes on refund.
- `supabase/functions/verify-license/` — the apps call this to check a key.

## Deploy (one time)

You need the **Supabase CLI** and your **JVZoo secret key**.

```bash
# 1. Log in + link to the existing project
supabase login
supabase link --project-ref cgogrstoqhmzkuxvcakk

# 2. Create the licenses table
#    (or paste the .sql file into Supabase Dashboard → SQL Editor → Run)
supabase db push

# 3. Deploy both functions (public — no JWT)
supabase functions deploy jvzoo-delivery --no-verify-jwt
supabase functions deploy verify-license --no-verify-jwt

# 4. Tell the webhook your JVZoo secret (find it in JVZoo → Account → IPN settings)
supabase secrets set JVZOO_SECRET=your_jvzoo_secret_key
```

No-CLI option: in the Supabase Dashboard you can paste each `index.ts` under
**Edge Functions → New function**, run the `.sql` in **SQL Editor**, and set
`JVZOO_SECRET` under **Edge Functions → Manage secrets**.

## Configure JVZoo (in your product settings)
- **Instant Notification URL (IPN):**
  `https://cgogrstoqhmzkuxvcakk.supabase.co/functions/v1/jvzoo-delivery`
- If you use JVZoo dynamic **license-key delivery**, point its key-generation /
  delivery URL at the same address — the function returns the key as its
  response body so JVZoo shows/emails it to the buyer.

## How the app uses it
The Android app calls `verify-license` (see `Config.LICENSE_VERIFY_URL`).
Until these functions are deployed, the app still unlocks with the offline
master code in `Config.PRO_UNLOCK_CODE_FALLBACK`. Set that to `""` once real
verification is live if you want to disable the master code.

## Note on where this lives
These files ideally belong in the web-app repo (`moshbari/yaptext-webapp`)
under its existing `supabase/` folder. They're included here because that's the
repo we had write access to. You can copy this `supabase/` content into the web
app repo so everything deploys together.
