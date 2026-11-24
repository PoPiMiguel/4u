# 4u 💌

Widget para compartir momentos con tu gente: fotos, vídeos, GIFs, dibujos y animaciones HTML que aparecen en el widget del móvil o del escritorio de la otra persona.

**Plataformas:** Android · Windows · Linux (Arch / Hyprland)
**Coste:** 0 € (Supabase Free + Firebase Spark solo para push)

## Estructura

```
android/     App Android (Kotlin, Compose, widget Glance, FCM)
desktop/     App escritorio Windows/Linux (Tauri 2 + React + TS)
supabase/    Backend: migraciones SQL (RLS, RPCs, cron) y Edge Functions
assets/      Logo
docs/        Documentación
```

## Documentación

| Documento | Para qué |
|---|---|
| [docs/TAREAS.md](docs/TAREAS.md) | **Empieza aquí:** cuentas y claves que hay que crear a mano |
| [docs/SETUP-ARCH.md](docs/SETUP-ARCH.md) | Instalar herramientas, desplegar el backend y arrancar las apps |
| [docs/PLAN.md](docs/PLAN.md) | Visión, funcionalidades y modelo de datos |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Decisiones técnicas y por qué |
| [docs/TODO.md](docs/TODO.md) | Estado por hitos y siguientes pasos |
