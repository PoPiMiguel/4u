-- =====================================================================
-- Envíos en espera: lista y cancelación
-- =====================================================================
-- "En espera" = envíos propios programados, o ya enviados que algún
-- destinatario todavía no ha recibido (delivered_at nulo: móvil apagado,
-- app de escritorio cerrada...).

-- Lista de envíos propios en espera, del más próximo al más lejano.
create function public.my_pending()
returns table (
  id           uuid,
  type         public.message_type,
  caption      text,
  storage_path text,
  thumb_path   text,
  status       public.message_status,
  deliver_at   timestamptz,
  created_at   timestamptz,
  recipients   jsonb
)
language sql stable security definer set search_path = '' as $$
  select
    m.id, m.type, m.caption, m.storage_path, m.thumb_path, m.status, m.deliver_at, m.created_at,
    jsonb_agg(
      jsonb_build_object(
        'id', r.recipient_id,
        'name', coalesce(c.nickname, p.display_name),
        'delivered', r.delivered_at is not null
      )
      order by p.display_name
    )
  from public.messages m
  join public.message_recipients r on r.message_id = m.id
  join public.profiles p on p.id = r.recipient_id
  left join public.contacts c on c.user_id = m.sender_id and c.contact_id = r.recipient_id
  where m.sender_id = auth.uid()
    and (
      m.status = 'scheduled'
      or (
        m.status = 'sent'
        and exists (
          select 1 from public.message_recipients x
          where x.message_id = m.id and x.delivered_at is null
        )
      )
    )
  group by m.id
  order by m.deliver_at;
$$;

-- Cancela un envío propio que aún no ha llegado.
--  - Programado: se borra entero.
--  - Ya enviado: se quita a los destinatarios que aún no lo han recibido;
--    si no queda ninguno, se borra el mensaje.
-- Devuelve true si el mensaje se ha borrado entero: el cliente borra entonces
-- sus archivos de Storage (política "media: delete own").
-- Límite: si el destinatario ya estaba descargándolo justo en ese momento,
-- puede quedárselo (marca delivered_at después de descargar).
create function public.cancel_pending(p_id uuid) returns boolean
language plpgsql security definer set search_path = '' as $$
declare
  v_status public.message_status;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;

  -- Bloquea la fila para no cruzarse con private.dispatch_scheduled().
  select status into v_status
  from public.messages
  where id = p_id and sender_id = auth.uid()
  for update;

  if v_status is null then raise exception 'not_found'; end if;

  if v_status = 'scheduled' then
    delete from public.messages where id = p_id;
    return true;
  end if;

  if v_status <> 'sent' then raise exception 'already_delivered'; end if;

  delete from public.message_recipients
  where message_id = p_id and delivered_at is null;
  if not found then raise exception 'already_delivered'; end if;

  if not exists (select 1 from public.message_recipients where message_id = p_id) then
    delete from public.messages where id = p_id;
    return true;
  end if;
  return false;
end;
$$;

revoke execute on function public.my_pending() from public, anon;
revoke execute on function public.cancel_pending(uuid) from public, anon;
grant execute on function public.my_pending() to authenticated;
grant execute on function public.cancel_pending(uuid) to authenticated;
