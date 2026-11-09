package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type

/** Oculta el teclat en prémer «Fet», sense tancar el formulari ni perdre les dades. */
@Composable
internal fun familyFormKeyboardActions(): KeyboardActions {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    return KeyboardActions(onDone = {
        focus.clearFocus()
        keyboard?.hide()
    })
}

/**
 * Formulari ample i accessible al marc Android: contingut desplaçable i botons
 * sempre a sota, fora de la zona que ocupa el teclat. No depèn de la densitat
 * ni dels botons diminuts per defecte de Material AlertDialog.
 */
@Composable
internal fun FamilyFormDialog(
    title: String,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().imePadding().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.fillMaxWidth(0.86f).widthIn(max = 820.dp)
                    .heightIn(max = maxHeight)
                    .background(Palette.Card, RoundedCornerShape(30.dp))
                    .padding(horizontal = 30.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(title, style = Type.Large.copy(
                    fontWeight = FontWeight.SemiBold, color = Palette.Ink))
                Column(Modifier.weight(1f, fill = false)
                    .fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(
                        onClick = { focus.clearFocus(); keyboard?.hide(); onDismiss() },
                        modifier = Modifier.weight(1f).heightIn(min = 66.dp)
                    ) {
                        Text("Cancel·lar", fontSize = 22.sp, maxLines = 1)
                    }
                    Button(
                        onClick = { focus.clearFocus(); keyboard?.hide(); onConfirm() },
                        enabled = confirmEnabled,
                        modifier = Modifier.weight(1.3f).heightIn(min = 66.dp)
                    ) {
                        Text(confirmLabel, fontSize = 22.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
