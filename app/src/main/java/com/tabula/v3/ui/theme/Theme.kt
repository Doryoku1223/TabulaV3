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
import com.tabula.v3.ui.components.LocalLiquidGlassEnabled

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
 * 液态玻璃主题 - 半透明玻璃效果
 * 
 * 设计理念：
 * - 背景使用渐变色彩，让玻璃折射效果更明显
 * - 表面颜色使用半透明，配合液态玻璃组件
 * - 保持金色强调色，延续品牌风格
 */
private val LiquidGlassColorScheme = lightColorScheme(
    primary = EyeGold,
    onPrimary = CatBlack,
    primaryContainer = EyeGoldDark,
    onPrimaryContainer = CatBlack,

    secondary = CatBlack,
    onSecondary = Color.White,
    secondaryContainer = Color(0x30000000),  // 半透明黑
    onSecondaryContainer = Color.White,

    tertiary = SuccessGreen,
    onTertiary = Color.White,

    // 液态玻璃背景 - 使用渐变蓝紫色增强视觉效果
    background = Color(0xFF8B9DC3),  // 柔和蓝灰色背景
    onBackground = Color.White,

    // 表面使用半透明
    surface = Color(0x30FFFFFF),        // 半透明白
    onSurface = CatBlack,
    surfaceVariant = Color(0x20FFFFFF), // 更透明的白
    onSurfaceVariant = Color(0xFF4A4A4A),

    outline = Color(0x40FFFFFF),
    outlineVariant = Color(0x30FFFFFF),

    error = DangerRed,
    onError = Color.White
)

/**
 * 用于在组件中获取当前是否为深色模式
 * 这会响应用户设置而非系统设置
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * Tabula 主题
 * 
 * @param darkTheme 是否为深色模式
 * @param liquidGlassEnabled 是否启用液态玻璃主题
 * @param dynamicColor 是否使用动态取色（默认关闭）
 * @param content 内容
 */
@Composable
fun TabulaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    liquidGlassEnabled: Boolean = false,
    dynamicColor: Boolean = false,  // 默认关闭动态取色，保持品牌一致性
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // 液态玻璃主题优先
        liquidGlassEnabled -> LiquidGlassColorScheme
        // 动态取色
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // 深色主题
        darkTheme -> DarkColorScheme
        // 浅色主题
        else -> LightColorScheme
    }

    // 提供深色模式状态和液态玻璃状态给子组件
    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalLiquidGlassEnabled provides liquidGlassEnabled
    ) {
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
    
    // 液态玻璃主题色彩
    object LiquidGlass {
        // 背景渐变色
        val BackgroundStart = Color(0xFF667EEA)    // 靛蓝紫
        val BackgroundMiddle = Color(0xFF8B9DC3)   // 柔和蓝灰
        val BackgroundEnd = Color(0xFFA8C0FF)      // 淡蓝
        
        // 玻璃表面颜色（半透明）
        val GlassSurface = Color(0x30FFFFFF)
        val GlassSurfaceLight = Color(0x40FFFFFF)
        val GlassSurfaceDark = Color(0x20000000)
        
        // 卡片颜色
        val CardBackground = Color(0x25FFFFFF)
        val CardBorder = Color(0x40FFFFFF)
        
        // 文字颜色
        val TextPrimary = Color(0xFF1A1A1A)
        val TextSecondary = Color(0xFF4A4A4A)
        val TextOnGlass = Color.White
    }
}
