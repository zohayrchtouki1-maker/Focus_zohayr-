package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.TomatoChatMessage
import com.example.model.AppLanguage
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkIconMuted
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.ForestEmerald

/**
 * Custom SVG-style vector mascot of a smiling red tomato with little legs/feet and leafy green crown.
 */
@Composable
fun SmilingTomatoMascotIcon(
    size: Dp = 38.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // 1. Little legs and feet at the bottom
        val legColor = Color(0xFF4E2A2A)
        val shoeColor = Color(0xFFE05246)
        val legStroke = w * 0.075f

        // Left leg
        drawLine(
            color = legColor,
            start = Offset(w * 0.38f, h * 0.74f),
            end = Offset(w * 0.35f, h * 0.90f),
            strokeWidth = legStroke,
            cap = StrokeCap.Round
        )
        // Left little foot/shoe
        drawRoundRect(
            color = shoeColor,
            topLeft = Offset(w * 0.23f, h * 0.86f),
            size = Size(w * 0.17f, h * 0.10f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
        )

        // Right leg
        drawLine(
            color = legColor,
            start = Offset(w * 0.62f, h * 0.74f),
            end = Offset(w * 0.65f, h * 0.90f),
            strokeWidth = legStroke,
            cap = StrokeCap.Round
        )
        // Right little foot/shoe
        drawRoundRect(
            color = shoeColor,
            topLeft = Offset(w * 0.60f, h * 0.86f),
            size = Size(w * 0.17f, h * 0.10f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
        )

        // 2. Plump Red Tomato Body
        val tomatoRed = Color(0xFFE5483B)
        val tomatoShadow = Color(0xFFC8372B)
        drawOval(
            color = tomatoShadow,
            topLeft = Offset(w * 0.12f, h * 0.21f),
            size = Size(w * 0.76f, h * 0.62f)
        )
        drawOval(
            color = tomatoRed,
            topLeft = Offset(w * 0.12f, h * 0.19f),
            size = Size(w * 0.76f, h * 0.59f)
        )

        // Glossy highlight on upper-left of tomato
        drawOval(
            color = Color.White.copy(alpha = 0.28f),
            topLeft = Offset(w * 0.22f, h * 0.27f),
            size = Size(w * 0.16f, h * 0.10f)
        )

        // 3. Green Leafy Stem Crown at the top
        val leafGreen = Color(0xFF22C55E)
        val stemDarkGreen = Color(0xFF15803D)
        val crownPath = Path().apply {
            moveTo(w * 0.50f, h * 0.07f)
            lineTo(w * 0.54f, h * 0.18f)
            lineTo(w * 0.68f, h * 0.14f)
            lineTo(w * 0.57f, h * 0.24f)
            lineTo(w * 0.64f, h * 0.30f)
            lineTo(w * 0.50f, h * 0.24f)
            lineTo(w * 0.36f, h * 0.30f)
            lineTo(w * 0.43f, h * 0.24f)
            lineTo(w * 0.32f, h * 0.14f)
            lineTo(w * 0.46f, h * 0.18f)
            close()
        }
        drawPath(path = crownPath, color = leafGreen)
        drawLine(
            color = stemDarkGreen,
            start = Offset(w * 0.50f, h * 0.06f),
            end = Offset(w * 0.50f, h * 0.20f),
            strokeWidth = w * 0.055f,
            cap = StrokeCap.Round
        )

        // 4. Cute Eyes
        val eyeColor = Color(0xFF1E1414)
        val eyeRadius = w * 0.048f
        val leftEyeCenter = Offset(w * 0.37f, h * 0.46f)
        val rightEyeCenter = Offset(w * 0.63f, h * 0.46f)
        drawCircle(color = eyeColor, radius = eyeRadius, center = leftEyeCenter)
        drawCircle(color = eyeColor, radius = eyeRadius, center = rightEyeCenter)
        // Tiny white eye sparkles
        drawCircle(
            color = Color.White,
            radius = eyeRadius * 0.38f,
            center = Offset(leftEyeCenter.x - eyeRadius * 0.25f, leftEyeCenter.y - eyeRadius * 0.25f)
        )
        drawCircle(
            color = Color.White,
            radius = eyeRadius * 0.38f,
            center = Offset(rightEyeCenter.x - eyeRadius * 0.25f, rightEyeCenter.y - eyeRadius * 0.25f)
        )

        // 5. Rosy Cheeks
        val cheekColor = Color(0xFFFF9E99).copy(alpha = 0.65f)
        drawOval(
            color = cheekColor,
            topLeft = Offset(w * 0.24f, h * 0.49f),
            size = Size(w * 0.10f, h * 0.06f)
        )
        drawOval(
            color = cheekColor,
            topLeft = Offset(w * 0.66f, h * 0.49f),
            size = Size(w * 0.10f, h * 0.06f)
        )

        // 6. Happy Smile Arc
        drawArc(
            color = eyeColor,
            startAngle = 15f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(w * 0.41f, h * 0.46f),
            size = Size(w * 0.18f, h * 0.13f),
            style = Stroke(width = w * 0.045f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun TomatoAiDialog(
    apiKey: String,
    messages: List<TomatoChatMessage>,
    isLoading: Boolean,
    language: AppLanguage,
    onSaveApiKey: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val isArabic = language == AppLanguage.AR
    var showKeyEditor by remember(apiKey) { mutableStateOf(apiKey.isBlank()) }
    var draftKey by remember(apiKey) { mutableStateOf(apiKey) }
    var showKeyPlaintext by remember { mutableStateOf(false) }
    var inputMessage by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tomato_ai_screen"),
        color = DarkBackground,
        contentColor = DarkTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Top Header with Smiling Red Tomato Mascot + "Tomato AI" Title + API Key Icon + Close Icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    SmilingTomatoMascotIcon(size = 40.dp)
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Tomato AI",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkTextPrimary
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CoralPrimary.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "2.5 Flash",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CoralPrimary
                                )
                            }
                        }
                        Text(
                            text = if (isArabic) {
                                "مساعد ذكي متصل بمهامك ومؤقتك مباشرة"
                            } else {
                                "Autonomous assistant connected to your live tasks & alarms"
                            },
                            fontSize = 11.sp,
                            color = DarkTextSecondary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (messages.isNotEmpty() && !showKeyEditor) {
                        IconButton(
                            onClick = onClearChat,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("tomato_ai_clear_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = if (isArabic) "مسح المحادثة" else "Clear Chat",
                                tint = DarkIconMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            draftKey = apiKey
                            showKeyEditor = !showKeyEditor
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("tomato_ai_key_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = if (isArabic) "إعداد مفتاح Gemini API" else "Gemini API Key",
                            tint = if (apiKey.isBlank() || showKeyEditor) CoralPrimary else DarkIconMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("tomato_ai_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (isArabic) "إغلاق" else "Close",
                            tint = DarkIconMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = DarkOutline)

            // API Key Setup / Update View
            AnimatedVisibility(
                visible = showKeyEditor || apiKey.isBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkOutline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Key,
                                contentDescription = null,
                                tint = CoralPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (isArabic) "إعداد مفتاح Gemini API الشخصي" else "Personal Gemini API Key Setup",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkTextPrimary
                            )
                        }

                        Text(
                            text = if (isArabic) {
                                "أدخل مفتاح Gemini API الخاص بك لتفعيل مساعد Tomato AI (نموذج gemini-2.5-flash). يتم حفظ المفتاح محلياً على جهازك فقط."
                            } else {
                                "Paste your personal Gemini API Key to enable Tomato AI (gemini-2.5-flash). Stored locally on your device."
                            },
                            fontSize = 13.sp,
                            color = DarkTextSecondary,
                            lineHeight = 19.sp
                        )

                        OutlinedTextField(
                            value = draftKey,
                            onValueChange = { draftKey = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tomato_ai_key_input"),
                            placeholder = {
                                Text(
                                    text = "AIzaSy...",
                                    color = DarkTextSecondary.copy(alpha = 0.6f)
                                )
                            },
                            singleLine = true,
                            visualTransformation = if (showKeyPlaintext) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showKeyPlaintext = !showKeyPlaintext }) {
                                    Icon(
                                        imageVector = if (showKeyPlaintext) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = DarkIconMuted
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CoralPrimary,
                                unfocusedBorderColor = DarkOutline,
                                focusedTextColor = DarkTextPrimary,
                                unfocusedTextColor = DarkTextPrimary,
                                cursorColor = CoralPrimary,
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (apiKey.isNotBlank()) {
                                TextButton(
                                    onClick = { showKeyEditor = false }
                                ) {
                                    Text(
                                        text = if (isArabic) "إلغاء" else "Cancel",
                                        color = DarkTextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Button(
                                onClick = {
                                    val clean = draftKey.trim()
                                    if (clean.isNotEmpty()) {
                                        onSaveApiKey(clean)
                                        showKeyEditor = false
                                    }
                                },
                                enabled = draftKey.trim().isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CoralPrimary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("tomato_ai_save_key_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isArabic) "حفظ وبدء المحادثة" else "Save & Open Chat",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Chat Interface (automatically shown once API key is saved)
            if (apiKey.isNotBlank() && !showKeyEditor) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (messages.isEmpty() && !isLoading) {
                        // Clean Empty State with Quick Prompt Suggestions
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            SmilingTomatoMascotIcon(size = 68.dp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = if (isArabic) "مرحباً! أنا Tomato AI 🍅" else "Hi! I'm Tomato AI 🍅",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkTextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isArabic) {
                                    "أنا مطّلع تلقائياً على جميع مهامك، مشاريعك، وإحصائياتك الحالية. اطلب مني تنظيم جدول دراستك أو إضافة وتعديل المهام والتذكيرات وسأنفذها فوراً!"
                                } else {
                                    "I have full live awareness of your tasks, projects, and focus stats. Ask me to build a study schedule, add tasks, or set reminders and I'll execute them automatically!"
                                },
                                fontSize = 13.sp,
                                color = DarkTextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 19.sp,
                                modifier = Modifier.widthIn(max = 420.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                TomatoMessageBubble(
                                    message = msg,
                                    isArabic = isArabic,
                                    onEditApiKey = { showKeyEditor = true }
                                )
                            }

                            if (isLoading) {
                                item(key = "tomato_loading_indicator") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(DarkSurface)
                                            .border(1.dp, DarkOutline, RoundedCornerShape(14.dp))
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = CoralPrimary,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (isArabic) {
                                                "Tomato AI يفكر وينفذ الإجراءات..."
                                            } else {
                                                "Tomato AI is thinking & executing actions..."
                                            },
                                            fontSize = 13.sp,
                                            color = DarkTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = DarkOutline)

                // Bottom Chat Text Input Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputMessage,
                        onValueChange = { inputMessage = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tomato_ai_message_input"),
                        placeholder = {
                            Text(
                                text = if (isArabic) {
                                    "اكتب رسالتك أو جدول دراستك لـ Tomato AI..."
                                } else {
                                    "Ask Tomato AI or give a schedule..."
                                },
                                fontSize = 13.sp,
                                color = DarkTextSecondary.copy(alpha = 0.65f)
                            )
                        },
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                val text = inputMessage.trim()
                                if (text.isNotEmpty() && !isLoading) {
                                    inputMessage = ""
                                    onSendMessage(text)
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CoralPrimary,
                            unfocusedBorderColor = DarkOutline,
                            focusedTextColor = DarkTextPrimary,
                            unfocusedTextColor = DarkTextPrimary,
                            cursorColor = CoralPrimary,
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )

                    val canSend = inputMessage.trim().isNotEmpty() && !isLoading
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (canSend) CoralPrimary else DarkSurfaceVariant)
                            .clickable(enabled = canSend) {
                                val text = inputMessage.trim()
                                if (text.isNotEmpty()) {
                                    inputMessage = ""
                                    onSendMessage(text)
                                }
                            }
                            .testTag("tomato_ai_send_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (isArabic) "إرسال" else "Send",
                            tint = if (canSend) Color.White else DarkIconMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TomatoMessageBubble(
    message: TomatoChatMessage,
    isArabic: Boolean,
    onEditApiKey: () -> Unit
) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val bubbleBg = when {
        message.isUser -> CoralPrimary
        message.isError -> Color(0xFF2B1618)
        else -> DarkSurface
    }
    val borderColor = when {
        message.isUser -> CoralPrimary
        message.isError -> Color(0xFFEF4444).copy(alpha = 0.6f)
        else -> DarkOutline
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (message.isUser) 14.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 14.dp
            ),
            color = bubbleBg,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!message.isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SmilingTomatoMascotIcon(size = 18.dp)
                        Text(
                            text = "Tomato AI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (message.isError) Color(0xFFEF4444) else CoralPrimary
                        )
                    }
                }

                // Autonomous Tool Execution Badges
                if (message.executedActions.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ForestEmerald.copy(alpha = 0.12f))
                            .border(1.dp, ForestEmerald.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        message.executedActions.forEach { actionSummary ->
                            Text(
                                text = actionSummary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = ForestEmerald,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    color = if (message.isUser) Color.White else DarkTextPrimary,
                    lineHeight = 20.sp
                )

                if (message.isError && message.text.contains("API", ignoreCase = true)) {
                    TextButton(
                        onClick = onEditApiKey,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = null,
                            tint = CoralPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isArabic) "تحديث مفتاح API" else "Update API Key",
                            fontSize = 12.sp,
                            color = CoralPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
