-- NeverDrop — Supabase schema for cloud task sync.
-- Run this in the Supabase SQL editor (hosted) or against your self-hosted Postgres.
-- Works identically for hosted and self-hosted; the app only needs the project URL + anon key.

create table if not exists public.tasks (
    id                 uuid primary key,
    user_id            uuid not null default auth.uid() references auth.users (id) on delete cascade,
    title              text not null,
    description        text not null default '',
    commitment_type    text not null,
    priority           text not null,
    status             text not null,
    related_person     text,
    deadline_ms        bigint,
    created_at_ms      bigint not null,
    completed_at_ms    bigint,
    snooze_count       integer not null default 0,
    last_snoozed_at_ms bigint,
    updated_at_ms      bigint not null,
    deleted            boolean not null default false
);

-- Incremental pulls filter on updated_at_ms.
create index if not exists tasks_user_updated_idx
    on public.tasks (user_id, updated_at_ms);

-- Each user can only see and write their own rows.
alter table public.tasks enable row level security;

drop policy if exists "own tasks" on public.tasks;
create policy "own tasks" on public.tasks
    for all
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- Auth: enable the Google provider so the app can exchange its Google ID token.
--   Hosted:        Dashboard -> Authentication -> Providers -> Google
--   Self-hosted:   GOTRUE_EXTERNAL_GOOGLE_ENABLED=true plus client id/secret in your stack .env
-- Use the same Google web client ID the Android app already uses
-- (GoogleAuthManager.WEB_CLIENT_ID) as an authorized client.
