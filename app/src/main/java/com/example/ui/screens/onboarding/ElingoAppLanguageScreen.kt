package com.example.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class LanguageOption(
    val code: String,
    val name: String,
    val flagCode: String
)

val defaultNativeLanguages = listOf(
    LanguageOption(code = "uz", name = "O‘zbekcha", flagCode = "uz"),
    LanguageOption(code = "ru", name = "Русский", flagCode = "ru"),
    LanguageOption(code = "en", name = "English", flagCode = "en")
)

val defaultAppLanguages = listOf(
    LanguageOption(code = "en", name = "English", flagCode = "en"),
    LanguageOption(code = "uz", name = "O‘zbekcha", flagCode = "uz"),
    LanguageOption(code = "ru", name = "Русский", flagCode = "ru")
)

/**
 * Screen 3: Elingo App & Native Language Screen
 * Displays progress indicator, mini mascot El, speech bubble "What language do you want to use for Elingo?",
 * "Your Native Language" card with "Change", and selectable "App Language" options.
 */
@Composable
fun ElingoAppLanguageScreen(
    onBackClick: () -> Unit,
    onContinueClick: (selectedAppLangCode: String, selectedNativeLangCode: String) -> Unit,
    initialNativeLangCode: String = "uz",
    initialAppLangCode: String = "en",
    modifier: Modifier = Modifier
) {
    var nativeLangCode by remember { mutableStateOf(initialNativeLangCode) }
    var selectedAppLangCode by remember { mutableStateOf(initialAppLangCode) }
    var showNativePicker by remember { mutableStateOf(false) }

    val currentNative = defaultNativeLanguages.find { it.code == nativeLangCode }
        ?: defaultNativeLanguages.first()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ElingoColors.ScreenBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        // Top Bar: Back Arrow + Centered Progress Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = ElingoColors.TextDark
                )
            }

            ElingoProgressBar(
                progress = 0.33f,
                modifier = Modifier
                    .width(170.dp)
                    .height(8.dp)
            )

            // Balancer spacer matching IconButton width
            Spacer(modifier = Modifier.size(40.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mascot El and Speech Bubble Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            ElingoMiniMascot(
                modifier = Modifier.size(width = 68.dp, height = 76.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            SpeechBubble(
                tail = SpeechBubbleTail.Left,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "What language do\nyou want to use for\nElingo?",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ElingoColors.TextDark,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Scrollable content area
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Section 1: "Your Native Language"
            Text(
                text = "Your Native Language",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ElingoColors.TextDark
            )

            Spacer(modifier = Modifier.height(12.dp))

            ElingoNativeLanguageCard(
                flagCode = currentNative.flagCode,
                name = currentNative.name,
                onChangeClick = { showNativePicker = true }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: "App Language"
            Text(
                text = "App Language",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ElingoColors.TextDark
            )

            Spacer(modifier = Modifier.height(12.dp))

            defaultAppLanguages.forEach { lang ->
                val isSelected = lang.code == selectedAppLangCode
                ElingoLanguageCard(
                    flagCode = lang.flagCode,
                    name = lang.name,
                    isSelected = isSelected,
                    onClick = { selectedAppLangCode = lang.code }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Bottom Continue Button
        ElingoButton(
            text = "Continue",
            onClick = { onContinueClick(selectedAppLangCode, nativeLangCode) },
            isPrimary = true,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    }

    // Change Native Language Dialog
    if (showNativePicker) {
        AlertDialog(
            onDismissRequest = { showNativePicker = false },
            title = {
                Text(
                    text = "Select Native Language",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = ElingoColors.TextDark
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    defaultNativeLanguages.forEach { lang ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    nativeLangCode = lang.code
                                    showNativePicker = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            FlagBadge(code = lang.flagCode)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = lang.name,
                                fontSize = 16.sp,
                                fontWeight = if (lang.code == nativeLangCode) FontWeight.Bold else FontWeight.Normal,
                                color = if (lang.code == nativeLangCode) ElingoColors.Purple else ElingoColors.TextDark
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNativePicker = false }) {
                    Text("Close", color = ElingoColors.Purple)
                }
            },
            containerColor = ElingoColors.CardBackground,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
