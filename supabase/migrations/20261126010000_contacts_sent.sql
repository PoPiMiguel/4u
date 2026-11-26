-- =====================================================================
-- Gestión de contactos, avatares y vista "Enviados"
-- =====================================================================

-- ---------------------------------------------------------------------
-- Contactos
-- ---------------------------------------------------------------------

-- ¿Tiene `a` a `b` en su lista, en cualquier estado? (para ver nombre y
-- avatar también de los bloqueados).
create function private.has_contact_row(a uuid, b uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.contacts where user_id = a and contact_id = b);
$$;

drop policy "profiles: read self and contacts" on public.profiles;
create policy "profiles: read self and contacts" on public.profiles
  for select to authenticated
  using (id = (select auth.uid()) or private.has_contact_row((select auth.uid()), id));

-- Elimina el contacto en los dos sentidos (para volver a hablar hace falta
-- otro código). Bloquear, en cambio, es un cambio de estado en tu fila.
create function public.remove_contact(p_contact uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  delete from public.contacts
  where (user_id = auth.uid() and contact_id = p_contact)
     or (user_id = p_contact and contact_id = auth.uid());
end;
$$;

-- ---------------------------------------------------------------------
-- Avatares: bucket privado `avatars`, ruta {uid}/{nombre}.jpg
-- ---------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit)
values ('avatars', 'avatars', false, 1048576)   -- 1 MB
on conflict (id) do nothing;

create function private.can_read_avatar(p_folder text) returns boolean
language plpgsql stable security definer set search_path = '' as $$
declare
  v_id uuid;
begin
  begin
    v_id := p_folder::uuid;
  exception when invalid_text_representation then
    return false;
  end;
  return v_id = auth.uid() or private.has_contact_row(auth.uid(), v_id);
end;
$$;

create policy "avatars: upload own folder" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy "avatars: read own or contacts" on storage.objects
  for select to authenticated
  using (bucket_id = 'avatars' and private.can_read_avatar((storage.foldername(name))[1]));

create policy "avatars: delete own" on storage.objects
  for delete to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);

-- ---------------------------------------------------------------------
-- Enviados: lo último que he enviado y qué ha pasado con cada destinatario
-- ---------------------------------------------------------------------
create function public.my_sent(p_limit int default 50)
returns table (
  id         uuid,
  type       public.message_type,
  caption    text,
  thumb_path text,
  sent_at    timestamptz,
  recipients jsonb
)
language sql stable security definer set search_path = '' as $$
  select
    m.id, m.type, m.caption, m.thumb_path, m.sent_at,
    jsonb_agg(
      jsonb_build_object(
        'id', r.recipient_id,
        'name', coalesce(c.nickname, p.display_name),
        'delivered', r.delivered_at is not null,
        'seen', r.seen_at is not null,
        'reaction', r.reaction
      )
      order by p.display_name
    )
  from public.messages m
  join public.message_recipients r on r.message_id = m.id
  join public.profiles p on p.id = r.recipient_id
  left join public.contacts c on c.user_id = m.sender_id and c.contact_id = r.recipient_id
  where m.sender_id = auth.uid()
    and m.status in ('sent', 'purged')
  group by m.id
  order by m.sent_at desc
  limit least(greatest(p_limit, 1), 200);
$$;

revoke execute on function public.remove_contact(uuid) from public, anon;
revoke execute on function public.my_sent(int) from public, anon;
grant execute on function public.remove_contact(uuid) to authenticated;
grant execute on function public.my_sent(int) to authenticated;
