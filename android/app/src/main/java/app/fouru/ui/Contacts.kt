package app.fouru.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fouru.data.ContactRow
import app.fouru.data.Repo
import coil3.compose.AsyncImage
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Foto de perfil redonda; sin foto, la inicial. */
@Composable
fun Avatar(path: String?, name: String, size: Dp = 36.dp) {
    val bytes by produceState<ByteArray?>(null, path) {
        value = path?.let { runCatching { Repo.avatar(it) }.getOrNull() }
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(0xFF34343D)),
        contentAlignment = Alignment.Center,
    ) {
        if (bytes != null) {
            AsyncImage(model = bytes, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(name.trim().take(1).uppercase().ifEmpty { "?" }, fontWeight = FontWeight.Bold)
        }
    }
}

/** Generar código (con QR), escribirlo o escanear el QR de otro. */
@Composable
fun InviteBlock(busy: Boolean, launchSafe: (String?, suspend () -> Unit) -> Unit, onAdded: suspend () -> Unit) {
    val ctx = LocalContext.current
    var invite by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    val qr = remember(invite) { invite?.let { qrBitmap(it, 480) } }

    fun accept(value: String) = launchSafe("Contacto añadido") {
        Repo.acceptInvite(value)
        code = ""
        onAdded()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(FourUColors.Surface)
                .border(1.dp, FourUColors.Line, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            if (invite == null) {
                Button(enabled = !busy, onClick = { launchSafe(null) { invite = Repo.createInvite() } }) {
                    Text("Generar mi código")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    qr?.let {
                        Image(it.asImageBitmap(), contentDescription = "QR del código", modifier = Modifier.size(120.dp).clip(RoundedCornerShape(10.dp)))
                    }
                    Column {
                        Text(invite.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                        Text("Que lo escanee o lo escriba en su 4u. Vale 24 h y un solo uso.", style = MaterialTheme.typography.bodySmall, color = FourUColors.Muted)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(code, { code = it.take(6).uppercase() }, label = { Text("Código de la otra persona") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(enabled = !busy && code.length == 6, onClick = { accept(code) }) { Text("Añadir") }
        }
        OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            scanQr(ctx, onCode = { accept(it) }, onError = { launchSafe(null) { error(it) } })
        }) { Text("Escanear un QR") }
    }
}

/** Fila de un contacto con su menú (renombrar, bloquear, eliminar). */
@Composable
fun ContactItem(
    c: ContactRow,
    busy: Boolean,
    onPing: () -> Unit,
    launchSafe: (String?, suspend () -> Unit) -> Unit,
    onChanged: suspend () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(c.avatarPath, c.name, 44.dp)
        Column(Modifier.weight(1f)) {
            Text(c.name, fontWeight = FontWeight.SemiBold)
            val extra = listOfNotNull(c.displayName.takeIf { c.nickname != null }, "Bloqueado".takeIf { c.blocked })
            if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = FourUColors.Muted)
        }
        IconButton(enabled = !busy && !c.blocked, onClick = onPing) {
            Icon(Icons.Outlined.FavoriteBorder, contentDescription = "Enviar un toque", tint = FourUColors.Accent)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Opciones", tint = FourUColors.Muted)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menu = false; renaming = true })
                DropdownMenuItem(
                    text = { Text(if (c.blocked) "Desbloquear" else "Bloquear") },
                    onClick = {
                        menu = false
                        launchSafe(if (c.blocked) "Desbloqueado" else "Bloqueado") {
                            Repo.setBlocked(c.contactId, !c.blocked)
                            onChanged()
                        }
                    },
                )
                DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menu = false; removing = true })
            }
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(c.nickname ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Renombrar") },
            text = {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Apodo (vacío = ${c.displayName})") }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    renaming = false
                    launchSafe("Nombre cambiado") {
                        Repo.renameContact(c.contactId, name.trim().ifEmpty { null })
                        onChanged()
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancelar") } },
        )
    }

    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text("¿Eliminar a ${c.name}?") },
            text = { Text("Se borra para los dos. Para volver a hablar haría falta otro código.") },
            confirmButton = {
                TextButton(onClick = {
                    removing = false
                    launchSafe("Contacto eliminado") {
                        Repo.removeContact(c.contactId)
                        onChanged()
                    }
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { removing = false }) { Text("No") } },
        )
    }
}

/** Escáner de Google Play Services: no necesita permiso de cámara. */
private fun scanQr(ctx: Context, onCode: (String) -> Unit, onError: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(ctx, options).startScan()
        .addOnSuccessListener { barcode ->
            val value = barcode.rawValue?.trim()?.uppercase().orEmpty()
            if (Regex("^[A-Z0-9]{6}$").matches(value)) onCode(value) else onError("Ese QR no es un código de 4u")
        }
        .addOnFailureListener { onError(it.message ?: "No se pudo escanear") }
}

private fun qrBitmap(text: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) AColor.BLACK else AColor.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
