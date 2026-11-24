-- =====================================================================
-- 4u — esquema inicial
-- Tablas, RLS, RPCs, triggers de notificación, cron y Storage.
-- Requiere (manual, una vez) los secretos de Vault `project_url` y
-- `functions_secret`. Ver docs/SETUP-ARCH.md.
-- =====================================================================

create extension if not exists pg_net with schema extensions;
create extension if not exists pg_cron with schema pg_catalog;

-- Esquema privado: helpers usados por RLS y triggers. No expuesto por la API.
create schema if not exists private;
revoke all on schema private from public;
grant usage on schema private to authenticated, service_role;

-- ---------------------------------------------------------------------
-- Tipos
-- ---------------------------------------------------------------------
create type public.message_type   as enum ('photo', 'video', 'gif', 'html', 'drawing', 'ping');
create type public.message_status as enum ('scheduled', 'sent', 'purged');
create type public.contact_status as enum ('accepted', 'blocked');
create type public.platform       as enum ('android', 'windows', 'linux');

-- ---------------------------------------------------------------------
-- Tablas
-- ---------------------------------------------------------------------
create table public.profiles (
  id           uuid primary key references auth.users (id) on delete cascade,
  display_name text not null check (char_length(display_name) between 1 and 40),
  avatar_path  text,
  created_at   timestamptz not null default now()
);

create table public.devices (
  id         uuid primary key,
  user_id    uuid not null references public.profiles (id) on delete cascade,
  platform   public.platform not null,
  name       text,
  fcm_token  text unique,
  last_seen  timestamptz not null default now()
);
create index devices_user_idx on public.devices (user_id);

create table public.contacts (
  user_id    uuid not null references public.profiles (id) on delete cascade,
  contact_id uuid not null references public.profiles (id) on delete cascade,
  status     public.contact_status not null default 'accepted',
  nickname   text check (char_length(nickname) <= 40),
  since      timestamptz not null default now(),
  primary key (user_id, contact_id),
  check (user_id <> contact_id)
);

create table public.invites (
  code       text primary key,
  owner_id   uuid not null references public.profiles (id) on delete cascade,
  expires_at timestamptz not null default now() + interval '24 hours'
);

create table public.messages (
  id           uuid primary key,                 -- lo genera el cliente (también es la carpeta en Storage)
  sender_id    uuid not null references public.profiles (id) on delete cascade,
  type         public.message_type not null,
  storage_path text,
  thumb_path   text,
  mime         text,
  size_bytes   bigint,
  caption      text check (char_length(caption) <= 280),
  created_at   timestamptz not null default now(),
  deliver_at   timestamptz not null default now(),
  status       public.message_status not null default 'sent',
  sent_at      timestamptz
);
create index messages_sender_idx    on public.messages (sender_id, created_at desc);
create index messages_scheduled_idx on public.messages (deliver_at) where status = 'scheduled';

create table public.message_recipients (
  message_id    uuid not null references public.messages (id) on delete cascade,
  recipient_id  uuid not null references public.profiles (id) on delete cascade,
  delivered_at  timestamptz,
  downloaded_at timestamptz,
  seen_at       timestamptz,
  reaction      text check (char_length(reaction) <= 16),
  primary key (message_id, recipient_id)
);
create index message_recipients_recipient_idx on public.message_recipients (recipient_id) where downloaded_at is null;

-- Realtime necesita la fila antigua completa para comparar cambios (reacciones / visto).
alter table public.message_recipients replica identity full;

-- ---------------------------------------------------------------------
-- Helpers (security definer → saltan RLS, evitan recursión entre políticas)
-- ---------------------------------------------------------------------
create function private.is_contact(a uuid, b uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.contacts
    where user_id = a and contact_id = b and status = 'accepted'
  );
$$;

-- Se puede enviar si ambos se tienen como contacto aceptado (bloquear corta en los dos sentidos).
create function private.can_send(a uuid, b uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select private.is_contact(a, b) and private.is_contact(b, a);
$$;

create function private.is_sender(p_message uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.messages
    where id = p_message and sender_id = auth.uid()
  );
$$;

-- El destinatario solo ve el mensaje cuando ya no está programado.
create function private.is_visible_recipient(p_message uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1
    from public.message_recipients r
    join public.messages m on m.id = r.message_id
    where r.message_id = p_message
      and r.recipient_id = auth.uid()
      and m.status <> 'scheduled'
  );
$$;

create function private.can_read_media(p_folder text) returns boolean
language plpgsql stable security definer set search_path = '' as $$
declare
  v_id uuid;
begin
  begin
    v_id := p_folder::uuid;
  exception when invalid_text_representation then
    return false;
  end;
  return private.is_sender(v_id) or private.is_visible_recipient(v_id);
end;
$$;

-- Llama a una Edge Function vía pg_net. Secretos en Vault (no en el repo).
create function private.call_function(p_name text, p_body jsonb) returns void
language plpgsql security definer set search_path = '' as $$
declare
  v_url    text;
  v_secret text;
begin
  select decrypted_secret into v_url    from vault.decrypted_secrets where name = 'project_url';
  select decrypted_secret into v_secret from vault.decrypted_secrets where name = 'functions_secret';
  if v_url is null or v_secret is null then
    raise warning '4u: faltan los secretos de Vault project_url / functions_secret';
    return;
  end if;
  perform net.http_post(
    url                  := v_url || '/functions/v1/' || p_name,
    body                 := p_body,
    headers              := jsonb_build_object('Content-Type', 'application/json', 'x-4u-secret', v_secret),
    timeout_milliseconds := 5000
  );
end;
$$;

-- ---------------------------------------------------------------------
-- Alta automática de perfil
-- ---------------------------------------------------------------------
create function private.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id, display_name)
  values (
    new.id,
    left(coalesce(nullif(trim(new.raw_user_meta_data ->> 'display_name'), ''), split_part(new.email, '@', 1)), 40)
  );
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function private.handle_new_user();

-- ---------------------------------------------------------------------
-- RPCs públicas (las llaman las apps)
-- ---------------------------------------------------------------------

-- Registra / actualiza el dispositivo actual. Un token FCM solo pertenece a un dispositivo.
create function public.register_device(p_id uuid, p_platform public.platform, p_name text, p_fcm_token text default null)
returns void language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  if p_fcm_token is not null then
    delete from public.devices where fcm_token = p_fcm_token and id <> p_id;
  end if;
  delete from public.devices where id = p_id and user_id <> auth.uid();
  insert into public.devices (id, user_id, platform, name, fcm_token, last_seen)
  values (p_id, auth.uid(), p_platform, p_name, p_fcm_token, now())
  on conflict (id) do update
    set platform = excluded.platform,
        name = excluded.name,
        fcm_token = excluded.fcm_token,
        last_seen = now();
end;
$$;

-- Genera un código de invitación de 6 caracteres (válido 24 h). Uno activo por usuario.
create function public.create_invite() returns text
language plpgsql security definer set search_path = '' as $$
declare
  v_alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  v_code text;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  delete from public.invites where owner_id = auth.uid() or expires_at < now();
  loop
    v_code := '';
    for i in 1..6 loop
      v_code := v_code || substr(v_alphabet, 1 + floor(random() * length(v_alphabet))::int, 1);
    end loop;
    begin
      insert into public.invites (code, owner_id) values (v_code, auth.uid());
      exit;
    exception when unique_violation then
      null;  -- colisión: probar otro código
    end;
  end loop;
  return v_code;
end;
$$;

-- Acepta un código: crea el contacto en ambos sentidos y consume el código.
create function public.accept_invite(p_code text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  v_code  text := upper(trim(p_code));
  v_owner uuid;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  select owner_id into v_owner from public.invites where code = v_code and expires_at > now();
  if v_owner is null then raise exception 'invalid_or_expired_code'; end if;
  if v_owner = auth.uid() then raise exception 'own_code'; end if;

  insert into public.contacts (user_id, contact_id)
  values (auth.uid(), v_owner), (v_owner, auth.uid())
  on conflict (user_id, contact_id) do nothing;

  delete from public.invites where code = v_code;
  return v_owner;
end;
$$;

-- Envía (o programa) un mensaje. El archivo ya debe estar subido a media/{uid}/{p_id}/...
create function public.send_message(
  p_id           uuid,
  p_type         public.message_type,
  p_recipients   uuid[],
  p_storage_path text default null,
  p_thumb_path   text default null,
  p_mime         text default null,
  p_size         bigint default null,
  p_caption      text default null,
  p_deliver_at   timestamptz default null
) returns void language plpgsql security definer set search_path = '' as $$
declare
  v_uid       uuid := auth.uid();
  v_prefix    text;
  v_scheduled boolean;
  r           uuid;
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  if p_recipients is null or cardinality(p_recipients) = 0 then raise exception 'no_recipients'; end if;
  if cardinality(p_recipients) > 50 then raise exception 'too_many_recipients'; end if;
  if p_type <> 'ping' and p_storage_path is null then raise exception 'missing_media'; end if;

  v_prefix := v_uid::text || '/' || p_id::text || '/';
  if p_storage_path is not null and left(p_storage_path, length(v_prefix)) <> v_prefix then
    raise exception 'bad_storage_path';
  end if;
  if p_thumb_path is not null and left(p_thumb_path, length(v_prefix)) <> v_prefix then
    raise exception 'bad_thumb_path';
  end if;

  foreach r in array p_recipients loop
    if not private.can_send(v_uid, r) then
      raise exception 'not_a_contact: %', r;
    end if;
  end loop;

  v_scheduled := p_deliver_at is not null and p_deliver_at > now() + interval '30 seconds';

  insert into public.messages (id, sender_id, type, storage_path, thumb_path, mime, size_bytes, caption, deliver_at, status, sent_at)
  values (
    p_id, v_uid, p_type, p_storage_path, p_thumb_path, p_mime, p_size, p_caption,
    coalesce(p_deliver_at, now()),
    case when v_scheduled then 'scheduled'::public.message_status else 'sent'::public.message_status end,
    case when v_scheduled then null else now() end
  );

  insert into public.message_recipients (message_id, recipient_id)
  select p_id, x from (select distinct unnest(p_recipients) as x) d;
end;
$$;

-- Cancela un envío programado propio. Los archivos los borra el cliente (Storage: delete own).
create function public.cancel_scheduled(p_id uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  delete from public.messages
  where id = p_id and sender_id = auth.uid() and status = 'scheduled';
  if not found then raise exception 'not_found_or_already_sent'; end if;
end;
$$;

-- Solo para la Edge Function `purge` (service_role).
create function public.purge_candidates(p_limit int default 200)
returns table (id uuid, storage_path text, thumb_path text)
language sql stable security definer set search_path = '' as $$
  select m.id, m.storage_path, m.thumb_path
  from public.messages m
  where m.status = 'sent'
    and m.storage_path is not null
    and (
      m.sent_at < now() - interval '30 days'
      or not exists (
        select 1 from public.message_recipients r
        where r.message_id = m.id and r.downloaded_at is null
      )
    )
  limit p_limit;
$$;

revoke execute on function public.register_device(uuid, public.platform, text, text) from public, anon;
revoke execute on function public.create_invite() from public, anon;
revoke execute on function public.accept_invite(text) from public, anon;
revoke execute on function public.send_message(uuid, public.message_type, uuid[], text, text, text, bigint, text, timestamptz) from public, anon;
revoke execute on function public.cancel_scheduled(uuid) from public, anon;
revoke execute on function public.purge_candidates(int) from public, anon, authenticated;

grant execute on function public.register_device(uuid, public.platform, text, text) to authenticated;
grant execute on function public.create_invite() to authenticated;
grant execute on function public.accept_invite(text) to authenticated;
grant execute on function public.send_message(uuid, public.message_type, uuid[], text, text, text, bigint, text, timestamptz) to authenticated;
grant execute on function public.cancel_scheduled(uuid) to authenticated;
grant execute on function public.purge_candidates(int) to service_role;

-- ---------------------------------------------------------------------
-- Triggers de notificación
-- ---------------------------------------------------------------------
create function private.on_message_sent() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.status <> 'sent' then
    return null;
  end if;
  if tg_op = 'UPDATE' then
    if old.status <> 'scheduled' then
      return null;
    end if;
  end if;
  perform private.call_function('notify', jsonb_build_object('kind', 'message', 'message_id', new.id));
  return null;
end;
$$;

create trigger messages_notify
  after insert or update of status on public.messages
  for each row execute function private.on_message_sent();

create function private.on_receipt() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if (new.reaction is distinct from old.reaction and new.reaction is not null)
     or (old.seen_at is null and new.seen_at is not null) then
    perform private.call_function('notify', jsonb_build_object(
      'kind', 'receipt',
      'message_id', new.message_id,
      'recipient_id', new.recipient_id
    ));
  end if;
  return null;
end;
$$;

create trigger message_recipients_notify
  after update on public.message_recipients
  for each row execute function private.on_receipt();

-- ---------------------------------------------------------------------
-- Envíos programados y limpieza
-- ---------------------------------------------------------------------
create function private.dispatch_scheduled() returns void
language plpgsql security definer set search_path = '' as $$
begin
  -- El trigger messages_notify dispara la push de cada fila.
  update public.messages
  set status = 'sent', sent_at = now()
  where status = 'scheduled' and deliver_at <= now();
end;
$$;

select cron.schedule('4u-dispatch-scheduled', '* * * * *', $$select private.dispatch_scheduled()$$);
select cron.schedule('4u-purge', '17 3 * * *', $$select private.call_function('purge', '{}'::jsonb)$$);

-- ---------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------
alter table public.profiles           enable row level security;
alter table public.devices            enable row level security;
alter table public.contacts           enable row level security;
alter table public.invites            enable row level security;   -- sin políticas: solo vía RPC
alter table public.messages           enable row level security;
alter table public.message_recipients enable row level security;

-- profiles
create policy "profiles: read self and contacts" on public.profiles
  for select to authenticated
  using (id = (select auth.uid()) or private.is_contact((select auth.uid()), id));
create policy "profiles: update self" on public.profiles
  for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));
revoke update on public.profiles from authenticated, anon;
grant update (display_name, avatar_path) on public.profiles to authenticated;

-- devices (alta vía register_device)
create policy "devices: read own" on public.devices
  for select to authenticated using (user_id = (select auth.uid()));
create policy "devices: delete own" on public.devices
  for delete to authenticated using (user_id = (select auth.uid()));

-- contacts (alta vía accept_invite)
create policy "contacts: read own" on public.contacts
  for select to authenticated using (user_id = (select auth.uid()));
create policy "contacts: update own" on public.contacts
  for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "contacts: delete own" on public.contacts
  for delete to authenticated using (user_id = (select auth.uid()));
revoke update on public.contacts from authenticated, anon;
grant update (nickname, status) on public.contacts to authenticated;

-- messages (alta vía send_message)
create policy "messages: sender or visible recipient" on public.messages
  for select to authenticated
  using (sender_id = (select auth.uid()) or private.is_visible_recipient(id));

-- message_recipients
create policy "recipients: own receipt or my message" on public.message_recipients
  for select to authenticated
  using (
    (recipient_id = (select auth.uid()) and private.is_visible_recipient(message_id))
    or private.is_sender(message_id)
  );
create policy "recipients: update own receipt" on public.message_recipients
  for update to authenticated
  using (recipient_id = (select auth.uid()) and private.is_visible_recipient(message_id))
  with check (recipient_id = (select auth.uid()));
revoke update on public.message_recipients from authenticated, anon;
grant update (delivered_at, downloaded_at, seen_at, reaction) on public.message_recipients to authenticated;

-- ---------------------------------------------------------------------
-- Realtime (escritorio escucha cambios en vez de FCM)
-- ---------------------------------------------------------------------
alter publication supabase_realtime add table public.messages, public.message_recipients;

-- ---------------------------------------------------------------------
-- Storage: bucket privado `media`, ruta {sender_id}/{message_id}/archivo
-- ---------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit)
values ('media', 'media', false, 52428800)   -- 50 MB (límite del plan gratuito)
on conflict (id) do nothing;

create policy "media: upload own folder" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'media' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy "media: read own or received" on storage.objects
  for select to authenticated
  using (
    bucket_id = 'media'
    and (
      (storage.foldername(name))[1] = (select auth.uid())::text
      or private.can_read_media((storage.foldername(name))[2])
    )
  );

create policy "media: delete own" on storage.objects
  for delete to authenticated
  using (bucket_id = 'media' and (storage.foldername(name))[1] = (select auth.uid())::text);
