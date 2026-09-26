-- These are legacy duplicates of the newer owner-only profile policies.
-- No table data, RLS settings, triggers, or newer policies are changed.
drop policy if exists "Users can select their own profile" on public.profiles;
drop policy if exists "Users can insert their own profile" on public.profiles;
drop policy if exists "Users can update their own profile" on public.profiles;
