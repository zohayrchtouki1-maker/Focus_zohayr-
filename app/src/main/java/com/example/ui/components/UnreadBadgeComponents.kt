package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val WhatsAppBadgeGreen = Color(0xFF25D366)

/**
 * Circular WhatsApp-style unread counter badge (`1`, `2`, `3` ... `99+`).
 * Hidden cleanly when [count] <= 0.
 */
@Composable
fun WhatsAppUnreadBadge(
    count: Int,
    modifier: Modifier = Modifier,
    minSize: Dp = 18.dp,
    fontSize: TextUnit = 10.sp,
    containerColor: Color = WhatsAppBadgeGreen,
    borderColor: Color = MaterialTheme.colorScheme.background,
    testTag: String? = null
) {
    if (count <= 0) return

    val label = if (count > 99) "99+" else count.toString()
    val horizontalPad = if (label.length >= 2) 4.dp else 0.dp
    val taggedModifier = if (!testTag.isNullOrBlank()) modifier.testTag(testTag) else modifier

    Box(
        contentAlignment = Alignment.Center,
        modifier = taggedModifier
            .defaultMinSize(minWidth = minSize, minHeight = minSize)
            .clip(CircleShape)
            .background(containerColor)
            .border(1.5.dp, borderColor, CircleShape)
            .padding(horizontal = horizontalPad)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = fontSize,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
