create table if not exists public.ai_chat_rate_limits (
  user_id uuid primary key references auth.users(id) on delete cascade,
  window_started_at timestamptz not null default now(),
  request_count integer not null default 0 check (request_count >= 0)
);

alter table public.ai_chat_rate_limits enable row level security;

create or replace function public.consume_ai_chat_quota(p_user_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_count integer;
begin
  if p_user_id is null then return false; end if;
  insert into public.ai_chat_rate_limits (user_id, window_started_at, request_count)
  values (p_user_id, now(), 1)
  on conflict (user_id) do update
    set window_started_at = case
          when public.ai_chat_rate_limits.window_started_at <= now() - interval '1 minute' then now()
          else public.ai_chat_rate_limits.window_started_at end,
        request_count = case
          when public.ai_chat_rate_limits.window_started_at <= now() - interval '1 minute' then 1
          else public.ai_chat_rate_limits.request_count + 1 end
  returning request_count into current_count;
  return current_count <= 20;
end;
$$;

revoke all on function public.consume_ai_chat_quota(uuid) from public, anon, authenticated;
grant execute on function public.consume_ai_chat_quota(uuid) to service_role;
revoke all on table public.ai_chat_rate_limits from public, anon, authenticated;
grant all on table public.ai_chat_rate_limits to service_role;
