package app.fouru.data

import app.fouru.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/** Cliente único de Supabase. La sesión se persiste automáticamente. */
object Supa {
    val client: SupabaseClient by lazy {
        check(BuildConfig.SUPABASE_URL.isNotEmpty()) { "Falta SUPABASE_URL en android/local.properties" }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }
}
