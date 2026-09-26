import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function jsonResponse(status: number, body: Record<string, string>) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (request.method !== "POST") {
    return jsonResponse(405, { error: "Method not allowed" });
  }

  const authorization = request.headers.get("Authorization");
  const token = authorization?.startsWith("Bearer ") ? authorization.slice(7).trim() : null;
  if (!token) return jsonResponse(401, { error: "Unauthorized" });

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !anonKey || !serviceRoleKey) {
    console.error("Account deletion function is missing Supabase configuration");
    return jsonResponse(500, { error: "Account deletion is unavailable" });
  }

  const callerClient = createClient(supabaseUrl, anonKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
  const { data: caller, error: authError } = await callerClient.auth.getUser(token);
  if (authError || !caller.user) return jsonResponse(401, { error: "Unauthorized" });

  const adminClient = createClient(supabaseUrl, serviceRoleKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
  const avatarPaths: string[] = [];
  let offset = 0;
  while (true) {
    const { data: objects, error: listError } = await adminClient.storage
      .from("avatars")
      .list(caller.user.id, { limit: 100, offset });
    if (listError) {
      console.error("Could not list account avatar objects", listError.message);
      return jsonResponse(500, { error: "Account deletion failed" });
    }
    avatarPaths.push(...objects.map((object) => `${caller.user.id}/${object.name}`));
    if (objects.length < 100) break;
    offset += objects.length;
  }

  const { error: deleteError } = await adminClient.auth.admin.deleteUser(caller.user.id);
  if (deleteError) {
    console.error("Could not delete authenticated account", deleteError.message);
    return jsonResponse(500, { error: "Account deletion failed" });
  }

  for (let index = 0; index < avatarPaths.length; index += 100) {
    const { error: removeError } = await adminClient.storage.from("avatars").remove(avatarPaths.slice(index, index + 100));
    if (removeError) {
      console.error("Could not remove deleted account avatar objects", removeError.message);
    }
  }

  return jsonResponse(200, { status: "deleted" });
});