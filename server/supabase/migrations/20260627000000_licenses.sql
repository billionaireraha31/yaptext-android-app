-- YapText Pro license keys (issued on JVZoo purchase, verified by the apps).
--
-- Run this once in your Supabase project (SQL Editor → paste → Run), or via
-- `supabase db push` if you use the CLI. It lives in the same Supabase project
-- the web app already uses (project ref: cgogrstoqhmzkuxvcakk).

CREATE TABLE IF NOT EXISTS public.licenses (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  license_key  TEXT NOT NULL UNIQUE,
  email        TEXT,
  product      TEXT,
  receipt      TEXT,                 -- JVZoo transaction receipt id
  status       TEXT NOT NULL DEFAULT 'active',  -- 'active' | 'revoked'
  source       TEXT NOT NULL DEFAULT 'jvzoo',
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS licenses_key_idx ON public.licenses (license_key);
CREATE INDEX IF NOT EXISTS licenses_receipt_idx ON public.licenses (receipt);

-- Lock the table down: only the edge functions (service role) may read/write.
-- No public policies are added, so anon/auth clients cannot touch it directly.
ALTER TABLE public.licenses ENABLE ROW LEVEL SECURITY;
