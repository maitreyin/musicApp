-- Profiles are created server-side when an auth account is created. This avoids relying on a
-- mobile client to race an auth trigger or to hold broader database permissions.
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text not null default 'MUSED User',
  avatar_url text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.profiles add column if not exists username text;
alter table public.profiles add column if not exists avatar_url text;
alter table public.profiles add column if not exists created_at timestamptz not null default now();
alter table public.profiles add column if not exists updated_at timestamptz not null default now();
alter table public.profiles alter column username set default 'MUSED User';

create or replace function public.create_profile_for_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
  insert into public.profiles (id, username)
  values (
    new.id,
    coalesce(
      nullif(trim(new.raw_user_meta_data ->> 'full_name'), ''),
      nullif(trim(new.raw_user_meta_data ->> 'name'), ''),
      nullif(split_part(coalesce(new.email, ''), '@', 1), ''),
      'MUSED User'
    )
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created_profile on auth.users;
create trigger on_auth_user_created_profile
  after insert on auth.users
  for each row execute procedure public.create_profile_for_new_user();

-- Backfill profiles for accounts that existed before the trigger. The insert is idempotent and
-- does not overwrite a profile a user has already customised.
insert into public.profiles (id, username)
select
  users.id,
  coalesce(
    nullif(trim(users.raw_user_meta_data ->> 'full_name'), ''),
    nullif(trim(users.raw_user_meta_data ->> 'name'), ''),
    nullif(split_part(coalesce(users.email, ''), '@', 1), ''),
    'MUSED User'
  )
from auth.users as users
on conflict (id) do nothing;

alter table public.profiles enable row level security;

drop policy if exists "Profiles are readable by their owner" on public.profiles;
create policy "Profiles are readable by their owner"
  on public.profiles for select to authenticated
  using ((select auth.uid()) = id);

drop policy if exists "Profiles are insertable by their owner" on public.profiles;
create policy "Profiles are insertable by their owner"
  on public.profiles for insert to authenticated
  with check ((select auth.uid()) = id);

drop policy if exists "Profiles are updatable by their owner" on public.profiles;
create policy "Profiles are updatable by their owner"
  on public.profiles for update to authenticated
  using ((select auth.uid()) = id)
  with check ((select auth.uid()) = id);
