package com.tabula.v3.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Tabula 主题色彩 - 黑猫风格
 *
 * 设计理念：
 * - 主色：深邃黑（如黑猫毛色）
 * - 点缀：金色瞳孔光点
 * - 背景：温暖的米白色
 * - 整体：高级感、艺术感、质感
 */

// 主色调
private val CatBlack = Color(0xFF1A1A1A)          // 黑猫主体
private val CatBlackLight = Color(0xFF2D2D2D)     // 浅黑
private val EyeGold = Color(0xFFFFD54F)           // 金色瞳孔
private val EyeGoldDark = Color(0xFFFFB300)       // 深金色

// 背景色
private val WarmWhite = Color(0xFFFAF9F7)         // 温暖白
private val WarmGray = Color(0xFFF5F4F2)          // 温暖灰
private val CoolGray = Color(0xFFE8E7E5)          // 分隔线

// 功能色
private val SuccessGreen = Color(0xFF4CAF50)      // 成功
private val DangerRed = Color(0xFFE53935)         // 删除/警告
private val InfoBlue = Color(0xFF2196F3)          // 信息

// 文字色
private val TextPrimary = Color(0xFF1A1A1A)       // 主文字
private val TextSecondary = Color(0xFF666666)     // 次要文字
private val TextTertiary = Color(0xFF999999)      // 提示文字

/**
 * 浅色主题 - 温暖米白背景 + 黑猫点缀
 */
private val LightColorScheme = lightColorScheme(
    primary = CatBlack,
    onPrimary = Color.White,
    primaryContainer = CatBlackLight,
    onPrimaryContainer = Color.White,

    secondary = EyeGold,
    onSecondary = CatBlack,
    secondaryContainer = EyeGoldDark,
    onSecondaryContainer = CatBlack,

    tertiary = SuccessGreen,
    onTertiary = Color.White,

    background = WarmWhite,
    onBackground = TextPrimary,

    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = WarmGray,
    onSurfaceVariant = TextSecondary,

    outline = CoolGray,
    outlineVariant = Color(0xFFE0E0E0),

    error = DangerRed,
    onError = Color.White
)

/**
 * 深色主题 - 黑猫沉浸
 */
private val DarkColorScheme = darkColorScheme(
    primary = EyeGold,
    onPrimary = CatBlack,
    primaryContainer = EyeGoldDark,
    onPrimaryContainer = CatBlack,

    secondary = Color.White,
    onSecondary = CatBlack,
    secondaryContainer = CatBlackLight,
    onSecondaryContainer = Color.White,

    tertiary = SuccessGreen,
    onTertiary = Color.White,

    background = CatBlack,
    onBackground = Color.White,

    surface = CatBlackLight,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF3D3D3D),
    onSurfaceVariant = Color(0xFFB0B0B0),

    outline = Color(0xFF4D4D4D),
    outlineVariant = Color(0xFF3D3D3D),

    error = Color(0xFFFF6B6B),
    onError = CatBlack
)

/**
 * 用于在组件中获取当前是否为深色模式
 * 这会响应用户设置而非系统设置
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun TabulaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,  // 默认关闭动态取色，保持品牌一致性
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 提供深色模式状态给子组件
    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/**
 * Tabula 自定义颜色扩展
 */
object TabulaColors {
    val CatBlack = Color(0xFF1A1A1A)
    val CatBlackLight = Color(0xFF2D2D2D)
    val EyeGold = Color(0xFFFFD54F)
    val EyeGoldDark = Color(0xFFFFB300)
    val WarmWhite = Color(0xFFFAF9F7)
    val WarmGray = Color(0xFFF5F4F2)
    val SuccessGreen = Color(0xFF4CAF50)
    val DangerRed = Color(0xFFE53935)

    // 卡片渐变
    val CardGradientStart = Color(0xFF2D2D2D)
    val CardGradientEnd = Color(0xFF1A1A1A)
}
