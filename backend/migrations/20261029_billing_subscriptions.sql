create table if not exists public.billing_subscriptions (
  user_id uuid primary key references auth.users(id) on delete cascade,
  paypal_subscription_id text unique not null,
  paypal_plan_id text not null,
  status text not null default 'APPROVAL_PENDING',
  updated_at timestamptz not null default now()
);

alter table public.billing_subscriptions enable row level security;

drop policy if exists "Users can read their own billing subscription" on public.billing_subscriptions;
create policy "Users can read their own billing subscription"
on public.billing_subscriptions
for select
using (auth.uid() = user_id);

create index if not exists billing_subscriptions_paypal_id_idx
on public.billing_subscriptions(paypal_subscription_id);
