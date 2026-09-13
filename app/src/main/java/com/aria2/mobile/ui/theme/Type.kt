package com.aria2.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 正文无衬线 + 数字用等宽，让速率/进度对齐更利落。 */
private val Numeric = FontFamily.Monospace

val AppTypography = Typography(
    // 顶部大标题
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp,
    ),
    // 卡片标题
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.1).sp,
    ),
    // 正文
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    // 速度/进度数字
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFamily = Numeric,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontFamily = Numeric,
    ),
)