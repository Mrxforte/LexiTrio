package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.Language
import com.example.ui.theme.EnBadgeColor
import com.example.ui.theme.RuBadgeColor
import com.example.ui.theme.UzBadgeColor

@Composable
fun DictionarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "So‘z yoki tarjimani qidiring...",
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {}
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search icon",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.testTag("clear_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("dictionary_search_input")
    )
}

@Composable
fun LanguageDirectionSelector(
    selectedSource: Language?,
    selectedTarget: Language?,
    onSourceSelect: (Language?) -> Unit,
    onTargetSelect: (Language?) -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Source Language Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        val next = when (selectedSource) {
                            Language.ENGLISH -> Language.RUSSIAN
                            Language.RUSSIAN -> Language.UZBEK_LATIN
                            Language.UZBEK_LATIN -> Language.UZBEK_CYRILLIC
                            else -> Language.ENGLISH
                        }
                        onSourceSelect(next)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedSource?.flagEmoji ?: "🌐", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedSource?.displayNameUzLatn ?: "Barcha tillar",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Swap Languages Button
            IconButton(
                onClick = onSwap,
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .testTag("swap_languages_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Swap source and target languages",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Target Language Indicator
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        val next = when (selectedTarget) {
                            Language.UZBEK_LATIN -> Language.RUSSIAN
                            Language.RUSSIAN -> Language.ENGLISH
                            Language.ENGLISH -> Language.UZBEK_CYRILLIC
                            else -> Language.UZBEK_LATIN
                        }
                        onTargetSelect(next)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedTarget?.flagEmoji ?: "🇺🇿", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedTarget?.displayNameUzLatn ?: "O‘zbekcha",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun WordEntryCard(
    entry: DictionaryEntryEntity,
    onClick: () -> Unit,
    onAudioClick: (rateMultiplier: Float) -> Unit,
    isFavorite: Boolean = false,
    onFavoriteToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("word_entry_card_${entry.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = entry.headword,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    PartOfSpeechChip(partOfSpeech = entry.partOfSpeech)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onAudioClick(1.0f) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), CircleShape)
                            .testTag("audio_button_${entry.id}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Pronounce word",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("favorite_button_${entry.id}")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (entry.phoneticIpa.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.phoneticIpa,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Primary translations preview
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (entry.primaryMeaningUz.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LanguageBadge(lang = "uz", text = "UZ")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = entry.primaryMeaningUz,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (entry.primaryMeaningRu.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LanguageBadge(lang = "ru", text = "RU")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = entry.primaryMeaningRu,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (entry.language != "en" && entry.primaryMeaningEn.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LanguageBadge(lang = "en", text = "EN")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = entry.primaryMeaningEn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PartOfSpeechChip(partOfSpeech: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (partOfSpeech.lowercase()) {
        "verb" -> Color(0xFFEFF6FF) to Color(0xFF1976D2)
        "noun" -> Color(0xFFF0FDF4) to Color(0xFF059669)
        "adjective" -> Color(0xFFFFFBEB) to Color(0xFFD97706)
        "adverb" -> Color(0xFFFAF5FF) to Color(0xFF7E22CE)
        "idiom" -> Color(0xFFFFF1F2) to Color(0xFFE11D48)
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = partOfSpeech.lowercase(),
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun LanguageBadge(lang: String, text: String, modifier: Modifier = Modifier) {
    val color = when (lang.lowercase()) {
        "en" -> EnBadgeColor
        "ru" -> RuBadgeColor
        "uz", "uz-cyrl" -> UzBadgeColor
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
