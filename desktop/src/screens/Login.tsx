import { useState, type FormEvent } from "react";
import { supabase } from "../lib/supabase";

export default function Login() {
  const [mode, setMode] = useState<"login" | "signup">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const { error } =
      mode === "login"
        ? await supabase.auth.signInWithPassword({ email, password })
        : await supabase.auth.signUp({ email, password, options: { data: { display_name: name } } });
    if (error) setError(error.message);
    setBusy(false);
  }

  return (
    <form className="card login" onSubmit={submit}>
      <h1>4u 💌</h1>
      {mode === "signup" && (
        <input placeholder="Tu nombre" value={name} onChange={(e) => setName(e.target.value)} required maxLength={40} />
      )}
      <input type="email" placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} required />
      <input
        type="password"
        placeholder="Contraseña"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        required
        minLength={6}
      />
      <button disabled={busy}>{mode === "login" ? "Entrar" : "Crear cuenta"}</button>
      <button type="button" className="link" onClick={() => setMode(mode === "login" ? "signup" : "login")}>
        {mode === "login" ? "¿No tienes cuenta? Regístrate" : "Ya tengo cuenta"}
      </button>
      {error && <p className="error">{error}</p>}
    </form>
  );
}
