// JVZoo → YapText license issuance.
//
// JVZoo calls this URL on every transaction (set it as the product's
// "Instant Notification URL" / IPN, and as the "External Key Generation URL"
// if you use JVZoo dynamic license delivery).
//
// On a paid SALE/BILL it generates a unique license key, stores it, and
// returns the key as plain text so JVZoo can show/email it to the buyer.
// On RFND/CGBK/CANCEL-REBILL it revokes the matching license.
//
// Security: verifies JVZoo's `cverify` signature using your JVZoo secret key,
// which you set as the function secret JVZOO_SECRET. If JVZOO_SECRET is unset,
// verification is skipped (TEST MODE ONLY — set it before going live).
//
// Deploy:  supabase functions deploy jvzoo-delivery --no-verify-jwt
// Secret:  supabase secrets set JVZOO_SECRET=your_jvzoo_secret_key

import { serve } from "https://deno.land/std@0.190.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

// JVZoo IPN signature: concat each POSTed value (except cverify) + "|" in the
// order received, append the secret, sha1, uppercase, first 8 chars.
async function jvzooVerify(fields: [string, string][], cverify: string, secret: string) {
  let pop = "";
  for (const [key, value] of fields) {
    if (key === "cverify") continue;
    pop += value + "|";
  }
  pop += secret;
  const digest = await crypto.subtle.digest("SHA-1", new TextEncoder().encode(pop));
  const hex = Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
  return hex.slice(0, 8).toUpperCase() === (cverify || "").toUpperCase();
}

function newLicenseKey(): string {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no confusing 0/O/1/I
  const rnd = crypto.getRandomValues(new Uint8Array(15));
  let body = "";
  for (let i = 0; i < rnd.length; i++) {
    body += alphabet[rnd[i] % alphabet.length];
    if (i % 5 === 4 && i !== rnd.length - 1) body += "-";
  }
  return "YT-" + body; // e.g. YT-AB3CD-EF4GH-JK5MN
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: corsHeaders });

  try {
    const raw = await req.text();
    const params = new URLSearchParams(raw);
    const fields: [string, string][] = [...params.entries()];
    const get = (k: string) => params.get(k) ?? "";

    const secret = Deno.env.get("JVZOO_SECRET") ?? "";
    if (secret) {
      const ok = await jvzooVerify(fields, get("cverify"), secret);
      if (!ok) {
        return new Response("INVALID_SIGNATURE", { status: 401, headers: corsHeaders });
      }
    }

    const txn = (get("ctransaction") || "").toUpperCase();
    const email = get("ccustemail");
    const receipt = get("ctransreceipt");
    const product = get("cprodtitle") || get("cproditem");

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    // Revocations
    if (["RFND", "CGBK", "CANCEL-REBILL"].includes(txn)) {
      if (receipt) {
        await supabase.from("licenses").update({ status: "revoked", updated_at: new Date().toISOString() })
          .eq("receipt", receipt);
      }
      return new Response("OK", { status: 200, headers: corsHeaders });
    }

    // Grants (new purchase or recurring rebill)
    if (["SALE", "BILL"].includes(txn)) {
      // Reuse an existing key for this receipt if JVZoo retries the IPN.
      const existing = receipt
        ? (await supabase.from("licenses").select("license_key").eq("receipt", receipt).maybeSingle()).data
        : null;
      if (existing?.license_key) {
        return new Response(existing.license_key, { status: 200, headers: corsHeaders });
      }

      const key = newLicenseKey();
      const { error } = await supabase.from("licenses").insert({
        license_key: key,
        email,
        product,
        receipt,
        status: "active",
        source: "jvzoo",
      });
      if (error) {
        console.error("insert license failed:", error);
        return new Response("DB_ERROR", { status: 500, headers: corsHeaders });
      }
      // Return the key as the response body so JVZoo can deliver it to the buyer.
      return new Response(key, { status: 200, headers: corsHeaders });
    }

    // Other transaction types we don't act on (e.g. INSF) — acknowledge.
    return new Response("OK", { status: 200, headers: corsHeaders });
  } catch (e) {
    console.error("jvzoo-delivery error:", e);
    return new Response("ERROR", { status: 500, headers: corsHeaders });
  }
});
