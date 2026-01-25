package com.tabula.v3.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.R
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors

/**
 * 关于 Tabula 页面
 */
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val cardColor = if (isDarkTheme) TabulaColors.CatBlackLight.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.9f)
    val textColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack
    val secondaryTextColor = if (isDarkTheme) Color(0xFFB0B0B0) else Color(0xFF666666)
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 背景图片
        Image(
            painter = painterResource(id = R.drawable.about_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // 半透明遮罩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isDarkTheme) Color.Black.copy(alpha = 0.5f)
                    else Color.White.copy(alpha = 0.3f)
                )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 顶部导航栏
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = textColor
                    )
                }
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = textColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
            // 内容
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                
                // Logo
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Tabula Logo",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 应用名称
                Text(
                    text = "Tabula",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = textColor
                )
                
                // 版本号
                Text(
                    text = "版本 3.0.1",
                    style = MaterialTheme.typography.bodyLarge,
                    color = secondaryTextColor
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // 介绍卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "简介",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = textColor
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Tabula 是一款优雅的本地照片整理工具。通过卡片式交互，让您轻松快速地清理手机相册中的冗余照片。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor,
                            lineHeight = 22.sp
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "✨ 设计理念",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = textColor
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "• 简洁高效的卡片式交互\n• 优雅流畅的动画体验\n• 安全可靠的本地存储\n• 保护隐私，数据不上传",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor,
                            lineHeight = 24.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 隐私声明卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "🔒 隐私保护",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = textColor
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Tabula 完全在本地运行，不会收集、上传或共享您的任何照片和数据。您的隐私是我们的首要关注。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor,
                            lineHeight = 22.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // 底部版权
                Text(
                    text = "Made with ❤️",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "© 2026 Tabula",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
