// License verification for the YapText apps (Android / iOS / etc.).
//
// The app POSTs { "license": "<key>" }. Returns { "valid": true, "email": ... }
// when the key exists and is active. Safe to call publicly — it only confirms
// validity, never lists keys.
//
// Deploy:  supabase functions deploy verify-license --no-verify-jwt

import { serve } from "https://deno.land/std@0.190.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: corsHeaders });

  try {
    const { license } = await req.json().catch(() => ({ license: "" }));
    const key = String(license ?? "").trim().toUpperCase();
    if (!key) {
      return new Response(JSON.stringify({ valid: false, error: "missing license" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data, error } = await supabase
      .from("licenses")
      .select("email, status")
      .eq("license_key", key)
      .eq("status", "active")
      .maybeSingle();

    if (error) {
      console.error("verify-license db error:", error);
      return new Response(JSON.stringify({ valid: false, error: "server" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } });
    }

    return new Response(JSON.stringify({ valid: !!data, email: data?.email ?? null }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } });
  } catch (e) {
    console.error("verify-license error:", e);
    return new Response(JSON.stringify({ valid: false, error: "bad request" }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } });
  }
});
