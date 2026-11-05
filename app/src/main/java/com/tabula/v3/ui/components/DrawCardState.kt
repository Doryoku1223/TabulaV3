package com.tabula.v3.ui.components

import androidx.compose.runtime.Stable
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.repository.resolveCoverRow

/**
 * 已发出的卡片记录
 *
 * @param originalIndex 在原始 deck 中的索引位置
 * @param cardId ImageFile.id，稳定标识
 * @param cardVolumeName 发牌时该行所在存储卷（T03 媒体身份）；deck 变化后的
 * 重校验/回牌定位按 (卷,ID) 完整身份解析，不同卷的同数值 ID 行不会互串。
 * 仅内存状态（Compose 状态，无持久化），无需迁移机制。
 */
@Stable
data class DealtCard(
    val originalIndex: Int,
    val cardId: Long,
    val cardVolumeName: String?
)

/**
 * 摸牌样式的状态
 * 
 * 显示映射规则：
 * - 左卡 = deck[currentIndex + 1] (下一张，预览露出，不可滑)
 * - 中卡 = deck[currentIndex] (当前牌，唯一可滑)
 * - 右卡 = deck[currentIndex + 2] (下下张，预览露出，不可滑)
 * 
 * @param currentIndex 当前可操作牌的索引 (i)
 * @param dealtStack 已发出的牌栈 (LIFO)，存储稳定标识
 * @param returningCard 正在从右侧飞回的牌（收牌动画用）
 * @param exitingRightCard 正在消失的右卡 ID（回退时淡出动画用）
 */
@Stable
data class DrawCardState(
    val currentIndex: Int = 0,
    val dealtStack: List<DealtCard> = emptyList(),
    val returningCard: DealtCard? = null,
    val exitingRightCard: Long? = null
) {
    /**
     * 获取左卡（deck[i+1]）
     */
    fun getLeftCard(deck: List<ImageFile>): ImageFile? {
        val index = currentIndex + 1
        return if (index < deck.size) deck[index] else null
    }
    
    /**
     * 获取中卡（deck[i]）- 当前可操作的牌
     */
    fun getCenterCard(deck: List<ImageFile>): ImageFile? {
        return if (currentIndex < deck.size) deck[currentIndex] else null
    }
    
    /**
     * 获取右卡（deck[i+2]）
     */
    fun getRightCard(deck: List<ImageFile>): ImageFile? {
        val index = currentIndex + 2
        return if (index < deck.size) deck[index] else null
    }
    
    /**
     * 是否可以发牌（右滑）
     * 条件：currentIndex < deck.size - 1
     */
    fun canDraw(deckSize: Int): Boolean {
        return currentIndex < deckSize - 1
    }
    
    /**
     * 是否可以收牌（左滑）
     * 条件：dealtStack 不为空且 currentIndex > 0
     */
    fun canRecall(): Boolean {
        return dealtStack.isNotEmpty() && currentIndex > 0
    }
}

/**
 * 摸牌样式的操作
 */
sealed class DrawCardAction {
    /**
     * 发牌 - 中卡向右飞出
     * 无参数，reducer 内从 deck[currentIndex].id 取 cardId
     */
    object Draw : DrawCardAction()
    
    /**
     * 收牌 - 最后发出的牌从右侧飞回
     */
    object Recall : DrawCardAction()
    
    /**
     * 动画完成 - 清除动画状态
     */
    object AnimationComplete : DrawCardAction()
    
    /**
     * 重置状态 - 当 deck 变化时重置
     */
    object Reset : DrawCardAction()
}

/**
 * 摸牌样式的状态机 reducer
 * 
 * @param state 当前状态
 * @param action 操作
 * @param deck 图片列表
 * @return 新状态
 */
fun drawCardReducer(
    state: DrawCardState,
    action: DrawCardAction,
    deck: List<ImageFile>
): DrawCardState {
    return when (action) {
        is DrawCardAction.Draw -> {
            // 边界检查：不能超过 deck.size - 1
            if (!state.canDraw(deck.size)) return state
            
            val currentCard = deck[state.currentIndex]
            val newDealtCard = DealtCard(
                originalIndex = state.currentIndex,
                cardId = currentCard.id,
                cardVolumeName = currentCard.volumeName
            )
            
            state.copy(
                currentIndex = state.currentIndex + 1,
                dealtStack = state.dealtStack + newDealtCard,
                // 清除之前的动画状态
                returningCard = null,
                exitingRightCard = null
            )
        }
        
        is DrawCardAction.Recall -> {
            // 边界检查：栈不能为空且 index > 0
            if (!state.canRecall()) return state
            
            val returning = state.dealtStack.last()
            
            // 只有当前 rightCard 存在时才设置 exitingRightCard
            // i + 2 < deck.size 时 rightCard 存在
            val exitingId = if (state.currentIndex + 2 < deck.size) {
                deck[state.currentIndex + 2].id
            } else {
                null
            }
            
            state.copy(
                currentIndex = state.currentIndex - 1,
                dealtStack = state.dealtStack.dropLast(1),
                returningCard = returning,
                exitingRightCard = exitingId
            )
        }
        
        is DrawCardAction.AnimationComplete -> {
            state.copy(
                returningCard = null,
                exitingRightCard = null
            )
        }
        
        is DrawCardAction.Reset -> {
            DrawCardState()
        }
    }
}

/**
 * 外部 deck 变化（如删除图片导致索引移动）后，对已发牌栈做身份重校验：
 *
 * 每条 DealtCard 按发牌时捕获的完整媒体身份 (cardVolumeName, cardId) 在当前
 * deck 中定位行——复用 resolveCoverRow 三态协议（C1/T03-COVER 同款）：卷已知
 * 精确匹配该卷行；卷未知（T03 前旧口径）同 ID 候选唯一才解析，跨卷歧义不猜
 * 选。定位成功的条目重算 originalIndex（仅保留位于新 currentIndex 之前的条
 * 目）；目标身份已不在当前 deck（删除/暂不可读/跨卷歧义）按缺失处理丢弃该
 * 条，不回退到裸 ID 匹配——多卷 deck 中同数值 ID 会让回牌飞回另一卷的同 ID
 * 照片。
 *
 * 纯内存状态重校验（dealtStack 仅存于 Compose 状态，无持久化记录，无需迁移）。
 */
internal fun revalidateDealtStack(
    dealtStack: List<DealtCard>,
    images: List<ImageFile>,
    currentIndex: Int
): List<DealtCard> = dealtStack.mapNotNull { dealtCard ->
    val resolved = resolveCoverRow(
        dealtCard.cardVolumeName,
        dealtCard.cardId,
        images.filter { it.id == dealtCard.cardId }
    )
    val newIndex = resolved?.let(images::indexOf)
    if (newIndex != null && newIndex < currentIndex) {
        dealtCard.copy(originalIndex = newIndex)
    } else {
        null
    }
}

/**
 * 收牌预览目标（C4/T07 任务 B）：发牌栈栈顶条目在**当前** deck 中的行。
 *
 * 调用方必须以 dealtStack 与 images 两者为 remember 键——快照替换（同身份
 * 行更新为最新 ImageFile，尺寸与顺序不变）不改变 dealtStack，只以栈为键会
 * 持续返回旧对象，预览停留在编辑前版本而实际收牌（recall 时按
 * images[originalIndex] 现取）已指向新版本。身份失效/越界返回 null，不回退
 * 到按裸 ID 收回其他照片。
 */
internal fun previewReturningCardOf(
    dealtStack: List<DealtCard>,
    images: List<ImageFile>
): ImageFile? = dealtStack.lastOrNull()?.let { dealt ->
    images.getOrNull(dealt.originalIndex)
}
