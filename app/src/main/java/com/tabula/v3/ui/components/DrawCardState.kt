package com.tabula.v3.ui.components

import androidx.compose.runtime.Stable
import com.tabula.v3.data.model.ImageFile

/**
 * 已发出的卡片记录
 * 
 * @param originalIndex 在原始 deck 中的索引位置
 * @param cardId ImageFile.id，稳定标识
 */
@Stable
data class DealtCard(
    val originalIndex: Int,
    val cardId: Long
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
                cardId = currentCard.id
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
