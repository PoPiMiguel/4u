package app.fouru.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.fouru.BuildConfig
import app.fouru.data.MediaSaver
import app.fouru.data.ProfileName
import app.fouru.data.Repo
import app.fouru.data.Updates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Mi perfil (foto y nombre), versión y actualizaciones, cerrar sesión. */
@Composable
fun AccountSection(busy: Boolean, launchSafe: (String?, suspend () -> Unit) -> Unit) {
    val ctx = LocalContext.current
    var profile by remember { mutableStateOf<ProfileName?>(null) }
    var name by remember { mutableStateOf("") }
    var update by remember { mutableStateOf<Updates.Available?>(null) }
    var checked by remember { mutableStateOf(false) }
    var batteryFree by remember { mutableStateOf(isBatteryFree(ctx)) }

    suspend fun reload() {
        Repo.profile().also {
            profile = it
            name = it.displayName
        }
    }

    LaunchedEffect(Unit) {
        runCatching { reload() }
        runCatching { Updates.check() }.onSuccess { update = it }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) launchSafe("Foto cambiada") {
            Repo.setAvatar(avatarJpeg(ctx, uri), profile?.avatarPath)
            reload()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(profile?.avatarPath, profile?.displayName ?: "?", 72.dp)
            OutlinedButton(enabled = !busy, onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Cambiar foto") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Tu nombre") }, modifier = Modifier.weight(1f))
            Button(
                enabled = !busy && name.isNotBlank() && name.trim() != profile?.displayName,
                onClick = {
                    launchSafe("Nombre cambiado") {
                        Repo.setDisplayName(name.trim())
                        reload()
                    }
                },
            ) { Text("Guardar") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Versión ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
            val u = update
            if (u != null) {
                Button(onClick = { open(ctx, u.url) }) { Text("Descargar la ${u.version}") }
            } else {
                TextButton(enabled = !busy, onClick = {
                    launchSafe(null) {
                        update = Updates.check()
                        checked = true
                    }
                }) { Text("Buscar actualizaciones") }
                if (checked) Text("Estás al día", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!batteryFree) {
            Text(
                "Para que los avisos lleguen con la app cerrada, deja que 4u funcione en segundo plano.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = {
                requestBatteryFree(ctx)
                batteryFree = isBatteryFree(ctx)
            }) { Text("Permitir en segundo plano") }
        }
        TextButton(onClick = { launchSafe(null) { Repo.signOut(ctx) } }) { Text("Cerrar sesión") }
    }
}

private fun isBatteryFree(ctx: Context): Boolean =
    ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

@android.annotation.SuppressLint("BatteryLife")
private fun requestBatteryFree(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
    }
}

private fun open(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/** Recorte cuadrado centrado de 256 px. */
private suspend fun avatarJpeg(ctx: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("No se pudo leer la imagen")
    val bmp = MediaSaver.decodeScaled(MediaSaver.prepareJpeg(bytes, 1024), 1024) ?: error("Imagen no válida")
    val side = minOf(bmp.width, bmp.height)
    val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
    MediaSaver.jpeg(Bitmap.createScaledBitmap(square, 256, 256, true), 85)
}
