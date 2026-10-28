package com.example.ui.screens.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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

val defaultTargetLanguages = listOf(
    LanguageOption(code = "en", name = "English", flagCode = "en"),
    LanguageOption(code = "ru", name = "Russian", flagCode = "ru"),
    LanguageOption(code = "uz", name = "Uzbek", flagCode = "uz")
)

/**
 * Screen 4: Elingo Target Language Screen
 * Displays progress indicator (~66%), mini mascot El, speech bubble "What would you like to learn?",
 * and a list of target language cards (English, Mandarin, Spanish, Hindi, French, etc.).
 */
@Composable
fun ElingoTargetLanguageScreen(
    onBackClick: () -> Unit,
    onContinueClick: (selectedTargetLangCode: String) -> Unit,
    initialTargetLangCode: String = "en",
    nativeLangCode: String = "uz",
    modifier: Modifier = Modifier
) {
    val availableLanguages = defaultTargetLanguages.filter { it.code != nativeLangCode }
    var selectedTargetLangCode by remember(nativeLangCode) {
        mutableStateOf(initialTargetLangCode.takeIf { code -> availableLanguages.any { it.code == code } }
            ?: availableLanguages.first().code)
    }

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
                progress = 0.66f,
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
                    text = "What would you\nlike to learn?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ElingoColors.TextDark,
                    lineHeight = 22.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Scrollable List of Target Languages
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            availableLanguages.forEach { lang ->
                val isSelected = lang.code == selectedTargetLangCode
                ElingoLanguageCard(
                    flagCode = lang.flagCode,
                    name = lang.name,
                    isSelected = isSelected,
                    onClick = { selectedTargetLangCode = lang.code }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Bottom Continue Button
        ElingoButton(
            text = "Continue",
            onClick = { onContinueClick(selectedTargetLangCode) },
            isPrimary = true,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    }
}
