-- =====================================================================
-- Sincronización entre los dispositivos de una misma cuenta
-- =====================================================================
-- Antes, la entrega era por persona (message_recipients.downloaded_at):
-- el primer dispositivo que descargaba un mensaje lo "gastaba" y los demás
-- (el PC, un móvil nuevo, volver a entrar) ya no lo veían. Ahora cada
-- dispositivo lleva su propia cuenta de lo que ha descargado, y el servidor
-- guarda los archivos por tiempo (30 días el original, 180 la miniatura) en
-- vez de borrarlos en cuanto "todos" los han descargado.
-- message_recipients sigue siendo por persona: entregado / visto / reacción.

create table public.message_deliveries (
  message_id   uuid not null references public.messages (id) on delete cascade,
  device_id    uuid not null references public.devices (id) on delete cascade,
  delivered_at timestamptz not null default now(),
  primary key (message_id, device_id)
);
alter table public.message_deliveries enable row level security;   -- sin políticas: solo vía RPC

-- Lo que le falta a este dispositivo: recibido por mí, ya enviado, con algo
-- que descargar (o un toque) y de los últimos 180 días. Del más antiguo al
-- más nuevo, para que el historial se rellene en orden.
create function public.device_inbox(p_device uuid, p_limit int default 300)
returns table (
  id           uuid,
  type         public.message_type,
  storage_path text,
  thumb_path   text,
  caption      text,
  sender_id    uuid,
  sender_name  text,
  sent_at      timestamptz,
  seen         boolean,
  reaction     text
)
language plpgsql stable security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  if not exists (select 1 from public.devices d where d.id = p_device and d.user_id = auth.uid()) then
    raise exception 'unknown_device';
  end if;

  return query
  select
    m.id, m.type, m.storage_path, m.thumb_path, m.caption, m.sender_id,
    coalesce(c.nickname, p.display_name),
    coalesce(m.sent_at, m.created_at),
    r.seen_at is not null,
    r.reaction
  from public.message_recipients r
  join public.messages m on m.id = r.message_id
  join public.profiles p on p.id = m.sender_id
  left join public.contacts c on c.user_id = auth.uid() and c.contact_id = m.sender_id
  where r.recipient_id = auth.uid()
    and m.status <> 'scheduled'
    and coalesce(m.sent_at, m.created_at) > now() - interval '180 days'
    and (m.type = 'ping' or m.storage_path is not null or m.thumb_path is not null)
    and not exists (
      select 1 from public.message_deliveries d
      where d.message_id = m.id and d.device_id = p_device
    )
  order by coalesce(m.sent_at, m.created_at)
  limit least(greatest(p_limit, 1), 500);
end;
$$;

-- Este dispositivo ya lo tiene. La primera vez para la persona, además,
-- cuenta como entregado/descargado (lo que ve el remitente).
create function public.mark_device_delivered(p_device uuid, p_message uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  if not exists (select 1 from public.devices d where d.id = p_device and d.user_id = auth.uid()) then
    raise exception 'unknown_device';
  end if;
  if not exists (
    select 1 from public.message_recipients r
    where r.message_id = p_message and r.recipient_id = auth.uid()
  ) then
    raise exception 'not_a_recipient';
  end if;

  insert into public.message_deliveries (message_id, device_id)
  values (p_message, p_device)
  on conflict do nothing;

  update public.message_recipients
  set delivered_at  = coalesce(delivered_at, now()),
      downloaded_at = coalesce(downloaded_at, now())
  where message_id = p_message and recipient_id = auth.uid();
end;
$$;

revoke execute on function public.device_inbox(uuid, int) from public, anon;
revoke execute on function public.mark_device_delivered(uuid, uuid) from public, anon;
grant execute on function public.device_inbox(uuid, int) to authenticated;
grant execute on function public.mark_device_delivered(uuid, uuid) to authenticated;

-- ---------------------------------------------------------------------
-- Limpieza por tiempo: original a los 30 días, miniatura a los 180.
-- ---------------------------------------------------------------------
drop function public.purge_candidates(int);

-- Originales con más de 30 días (la miniatura se queda).
create function public.purge_candidates(p_limit int default 200)
returns table (id uuid, storage_path text, thumb_path text)
language sql stable security definer set search_path = '' as $$
  select m.id, m.storage_path, null::text
  from public.messages m
  where m.status = 'sent'
    and m.storage_path is not null
    and coalesce(m.sent_at, m.created_at) < now() - interval '30 days'
  limit p_limit;
$$;

-- Miniaturas con más de 180 días.
create function public.purge_thumb_candidates(p_limit int default 200)
returns table (id uuid, thumb_path text)
language sql stable security definer set search_path = '' as $$
  select m.id, m.thumb_path
  from public.messages m
  where m.thumb_path is not null
    and m.status <> 'scheduled'
    and coalesce(m.sent_at, m.created_at) < now() - interval '180 days'
  limit p_limit;
$$;

revoke execute on function public.purge_candidates(int) from public, anon, authenticated;
revoke execute on function public.purge_thumb_candidates(int) from public, anon, authenticated;
grant execute on function public.purge_candidates(int) to service_role;
grant execute on function public.purge_thumb_candidates(int) to service_role;
