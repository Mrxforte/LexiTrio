package com.example.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Screen 2: Elingo Welcome / Mascot Intro Screen
 * Features speech bubble "Hi there! I'm El!", waving mascot El, title, subtitle,
 * "GET STARTED" and "I ALREADY HAVE AN ACCOUNT" buttons.
 */
@Composable
fun ElingoWelcomeScreen(
    onGetStartedClick: () -> Unit,
    onAlreadyHaveAccountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .background(ElingoColors.ScreenBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(36.dp))

        // Speech Bubble: "Hi there! I'm El!"
        SpeechBubble(tail = SpeechBubbleTail.Bottom) {
            Text(
                text = "Hi there! I'm El!",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = ElingoColors.TextDark
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Full-size Mascot El waving happily
        ElingoCharacterMascot(
            modifier = Modifier.size(width = 160.dp, height = 186.dp),
            isWaving = true
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Title: Elingo
        Text(
            text = "Elingo",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ElingoColors.TextDark,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Subtitle
        Text(
            text = "Learn languages whenever and\nwherever you want. It's free and forever.",
            fontSize = 14.sp,
            color = ElingoColors.TextGray,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        // Primary: "GET STARTED"
        ElingoButton(
            text = "GET STARTED",
            onClick = onGetStartedClick,
            isPrimary = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Secondary: "I ALREADY HAVE AN ACCOUNT"
        ElingoButton(
            text = "I ALREADY HAVE AN ACCOUNT",
            onClick = onAlreadyHaveAccountClick,
            isPrimary = false
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
