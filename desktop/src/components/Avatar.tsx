/** Foto de perfil redonda; sin foto, la inicial del nombre. */
export default function Avatar({ url, name, size = 32 }: { url: string | null; name: string; size?: number }) {
  return (
    <span className="avatar" style={{ width: size, height: size, fontSize: size * 0.45 }}>
      {url ? <img src={url} alt="" /> : (name.trim()[0] ?? "?").toUpperCase()}
    </span>
  );
}
