package com.fuelx.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fuelx.ui.theme.*

data class MusicLanguage(
    val id: String,
    val name: String,
    val nativeScript: String
)

val AVAILABLE_LANGUAGES = listOf(
    MusicLanguage("English", "English", "Global Hits"),
    MusicLanguage("Tamil", "Tamil", "தமிழ்"),
    MusicLanguage("Hindi", "Hindi", "हिंदी"),
    MusicLanguage("Malayalam", "Malayalam", "മലയാളം")
)

@Composable
fun LanguageSelectionDialog(
    initialSelected: Set<String>,
    canDismiss: Boolean = false,
    onLanguagesSelected: (Set<String>) -> Unit,
    onDismissRequest: () -> Unit = {}
) {
    var selected by remember { mutableStateOf(initialSelected) }

    Dialog(
        onDismissRequest = {
            if (canDismiss) onDismissRequest()
        },
        properties = DialogProperties(
            dismissOnBackPress = canDismiss,
            dismissOnClickOutside = canDismiss,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(AccentPrimary.copy(alpha = 0.3f), AccentSecondary.copy(alpha = 0.3f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = "Language",
                        tint = AccentPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Select Music Languages",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Choose your preferred languages. Top tracks will be curated based on your selection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Language options grid / list
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AVAILABLE_LANGUAGES.forEach { lang ->
                        val isChecked = selected.contains(lang.id)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    selected = if (isChecked) {
                                        selected - lang.id
                                    } else {
                                        selected + lang.id
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isChecked) AccentPrimary.copy(alpha = 0.15f) else DarkSurfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = lang.name,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isChecked) AccentPrimary else TextPrimary
                                    )
                                    Text(
                                        text = lang.nativeScript,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextMuted
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (isChecked) AccentPrimary else Color.Transparent)
                                        .border(
                                            BorderStroke(
                                                1.5.dp,
                                                if (isChecked) AccentPrimary else TextSecondary
                                            ),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isChecked) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected",
                                            tint = DarkBackground,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Confirm button
                Button(
                    onClick = {
                        if (selected.isNotEmpty()) {
                            onLanguagesSelected(selected)
                        }
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPrimary,
                        disabledContainerColor = DarkSurfaceVariant
                    )
                ) {
                    Text(
                        text = if (selected.isEmpty()) "Select at least 1 language" else "Save & Continue (${selected.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (selected.isEmpty()) TextMuted else DarkBackground
                    )
                }
            }
        }
    }
}
