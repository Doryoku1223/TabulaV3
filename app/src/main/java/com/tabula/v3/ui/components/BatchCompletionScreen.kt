package com.tabula.v3.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.R
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.util.HapticFeedback
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.core.models.Size
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * 一组完成总结页面
 *
 * 显示统计信息 + 礼花庆祝效果
 *
 * @param totalReviewed 本次预览的照片数量
 * @param totalMarked 标记删除的数量
 * @param onContinue 再来一组
 * @param onViewMarked 查看标记（回收站）
 */
@Composable
fun BatchCompletionScreen(
    totalReviewed: Int,
    totalMarked: Int,
    onContinue: () -> Unit,
    onViewMarked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showContent by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }
    
    // 序列帧动画资源列表
    val animationFrames = listOf(
        R.drawable.cat_seq_01, R.drawable.cat_seq_02, R.drawable.cat_seq_03,
        R.drawable.cat_seq_04, R.drawable.cat_seq_05, R.drawable.cat_seq_06,
        R.drawable.cat_seq_07, R.drawable.cat_seq_08, R.drawable.cat_seq_09,
        R.drawable.cat_seq_10, R.drawable.cat_seq_11, R.drawable.cat_seq_12,
        R.drawable.cat_seq_13, R.drawable.cat_seq_14, R.drawable.cat_seq_15
    )
    
    // 当前帧索引
    var currentFrameIndex by remember { mutableIntStateOf(0) }
    // 动画播放状态
    var isConfigured by remember { mutableStateOf(false) }

    val isDarkTheme = LocalIsDarkTheme.current

    // 主题色
    val backgroundColor = if (isDarkTheme) Color.Black else Color(0xFFF2F2F7)
    val backgroundGradientEnd = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFE5E5EA)
    val cardColor = if (isDarkTheme) TabulaColors.CatBlackLight else Color.White
    val textColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack
    val secondaryTextColor = if (isDarkTheme) Color(0xFFB0B0B0) else Color(0xFF666666)
    val dividerColor = if (isDarkTheme) Color(0xFF3D3D3D) else Color(0xFFEEEEEE)
    val primaryButtonColor = if (isDarkTheme) TabulaColors.EyeGold else TabulaColors.CatBlack
    val primaryButtonTextColor = if (isDarkTheme) TabulaColors.CatBlack else Color.White
    val secondaryButtonColor = if (isDarkTheme) TabulaColors.CatBlackLight else Color.White
    val secondaryButtonTextColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack

    // 延迟显示内容和礼花
    LaunchedEffect(Unit) {
        delay(200)
        showContent = true
        delay(300)
        showConfetti = true
        isConfigured = true
    }
    
    // 序列帧动画循环
    LaunchedEffect(isConfigured) {
        if (isConfigured) {
            while (true) {
                // 每帧间隔 66ms (约 15fps)
                delay(66)
                currentFrameIndex = (currentFrameIndex + 1) % animationFrames.size
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(backgroundColor, backgroundGradientEnd)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // 礼花效果
        if (showConfetti) {
            KonfettiView(
                modifier = Modifier.fillMaxSize(),
                parties = listOf(
                    // 从左上角
                    Party(
                        speed = 8f,
                        maxSpeed = 16f,
                        damping = 0.92f,
                        angle = 315,
                        spread = 50,
                        colors = listOf(
                            0xFFFFD54F.toInt(),
                            0xFFFF7043.toInt(),
                            0xFF66BB6A.toInt()
                        ),
                        shapes = listOf(Shape.Circle),
                        size = listOf(Size(10), Size(14)),
                        position = Position.Relative(0.0, 0.0),
                        emitter = Emitter(duration = 3, TimeUnit.SECONDS).perSecond(20)
                    ),
                    // 从右上角
                    Party(
                        speed = 8f,
                        maxSpeed = 16f,
                        damping = 0.92f,
                        angle = 225,
                        spread = 50,
                        colors = listOf(
                            0xFFFFD54F.toInt(),
                            0xFFFF8A65.toInt(),
                            0xFF81C784.toInt()
                        ),
                        shapes = listOf(Shape.Circle),
                        size = listOf(Size(10), Size(14)),
                        position = Position.Relative(1.0, 0.0),
                        emitter = Emitter(duration = 3, TimeUnit.SECONDS).perSecond(20)
                    )
                )
            )
        }

        // 主要内容
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(
                initialScale = 0.8f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
            exit = fadeOut() + scaleOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 序列帧动画播放
                Image(
                    painter = painterResource(id = animationFrames[currentFrameIndex]),
                    contentDescription = "Cat Animation",
                    modifier = Modifier
                        .size(160.dp)
                        .padding(bottom = 16.dp)
                    // 注意：不需要 graphicsLayer 的呼吸动画了，因为现在是播放真正的帧动画
                )

                // 完成文字
                Text(
                    text = "干得漂亮! 🎉",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = textColor
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 统计卡片
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardColor)
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "本次整理",
                            style = MaterialTheme.typography.bodyLarge,
                            color = secondaryTextColor
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // 预览数量
                            StatItem(
                                icon = Icons.Default.Check,
                                iconColor = TabulaColors.SuccessGreen,
                                value = totalReviewed,
                                label = "张照片",
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor
                            )

                            // 分隔
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(60.dp)
                                    .background(dividerColor)
                            )

                            // 标记数量
                            StatItem(
                                icon = Icons.Default.Delete,
                                iconColor = TabulaColors.DangerRed,
                                value = totalMarked,
                                label = "张删除",
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 再来一组按钮
                Button(
                    onClick = {
                        HapticFeedback.mediumTap(context)
                        onContinue()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryButtonColor,
                        contentColor = primaryButtonTextColor
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "再来一组",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 查看标记按钮
                Button(
                    onClick = {
                        HapticFeedback.mediumTap(context)
                        onViewMarked()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = secondaryButtonColor,
                        contentColor = secondaryButtonTextColor
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "查看标记",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 统计项
 */
@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    value: Int,
    label: String,
    textColor: Color,
    secondaryTextColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$value",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = textColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = secondaryTextColor
        )
    }
}
