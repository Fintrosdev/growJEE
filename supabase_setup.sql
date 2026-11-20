-- JEE Control Centre cloud storage and read-only teacher sharing.
-- Run this in the Supabase SQL Editor for the project used by index.html.
-- This script never creates or uses a service_role key in the browser.

begin;

create table if not exists public.jee_user_data (
  user_id uuid primary key references auth.users(id) on delete cascade,
  payload jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

create table if not exists public.jee_shares (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references auth.users(id) on delete cascade,
  student_email text not null,
  teacher_email text not null,
  progress_snapshot jsonb not null default '{}'::jsonb,
  snapshot_updated_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  constraint jee_shares_student_email_nonempty check (length(trim(student_email)) > 3),
  constraint jee_shares_teacher_email_nonempty check (length(trim(teacher_email)) > 3)
);

create unique index if not exists jee_shares_student_teacher_email_idx
  on public.jee_shares (student_id, lower(teacher_email));
create index if not exists jee_shares_teacher_email_idx
  on public.jee_shares (lower(teacher_email));

alter table public.jee_user_data enable row level security;
alter table public.jee_shares enable row level security;

revoke all on public.jee_user_data from anon, authenticated;
revoke all on public.jee_shares from anon, authenticated;
grant usage on schema public to authenticated;
grant select, insert, update on public.jee_user_data to authenticated;
grant select, insert, update, delete on public.jee_shares to authenticated;

drop policy if exists "students read their own progress" on public.jee_user_data;
drop policy if exists "students and invited teachers read progress" on public.jee_user_data;
create policy "students read their own progress"
  on public.jee_user_data for select to authenticated
  using (user_id = (select auth.uid()));

drop policy if exists "students create their own progress" on public.jee_user_data;
create policy "students create their own progress"
  on public.jee_user_data for insert to authenticated
  with check (user_id = (select auth.uid()));

drop policy if exists "students update their own progress" on public.jee_user_data;
create policy "students update their own progress"
  on public.jee_user_data for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

drop policy if exists "students and invited teachers read shares" on public.jee_shares;
create policy "students and invited teachers read shares"
  on public.jee_shares for select to authenticated
  using (
    student_id = (select auth.uid())
    or lower(teacher_email) = lower((select auth.jwt() ->> 'email'))
  );

drop policy if exists "students invite teachers to their own progress" on public.jee_shares;
create policy "students invite teachers to their own progress"
  on public.jee_shares for insert to authenticated
  with check (
    student_id = (select auth.uid())
    and lower(student_email) = lower((select auth.jwt() ->> 'email'))
  );

drop policy if exists "students update their own shared snapshot" on public.jee_shares;
create policy "students update their own shared snapshot"
  on public.jee_shares for update to authenticated
  using (student_id = (select auth.uid()))
  with check (student_id = (select auth.uid()));

drop policy if exists "students revoke their own teacher invites" on public.jee_shares;
create policy "students revoke their own teacher invites"
  on public.jee_shares for delete to authenticated
  using (student_id = (select auth.uid()));

commit;
