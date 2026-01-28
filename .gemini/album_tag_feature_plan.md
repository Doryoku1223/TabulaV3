# 相册整理/标签归类功能 - 实现计划

> ✅ **状态**: Phase 1-7 全部完成！（2026-01-27）
>
> 编译通过，核心功能和系统同步功能已实现。

## 📋 需求概述

在 Tabula 现有"滑卡清理"体验基础上，新增照片标签归类能力：
- ✅ 保持左/右/上手势不变
- ✅ 支持用户自定义相册
- ✅ 相册与图片关系持久化
- ✅ 同步到系统相册

---

## ✅ 已完成功能

### Phase 1: 数据层 ✅
- `data/model/Album.kt` - 相册数据模型（含 isSyncEnabled 字段）
- `data/model/AlbumMapping.kt` - 图片-相册映射模型
- `data/repository/AlbumManager.kt` - 相册管理器

### Phase 2: UI 组件 ✅
- `ui/components/AlbumChips.kt` - 卡片底部相册选择器
- `ui/components/UndoSnackbar.kt` - 撤销操作提示
- `ui/components/AlbumDialogs.kt` - 新建/编辑相册对话框

### Phase 3: DeckScreen 集成 ✅
- 底部相册 Chips 行
- 点击相册自动归类+下一张
- 新建相册对话框

### Phase 4: 相册视图屏幕 ✅
- `ui/screens/AlbumViewScreen.kt` - 相册列表和内容查看

### Phase 5: 路由集成 ✅
- AppScreen 枚举新增 ALBUM_VIEW
- MainActivity 路由配置完成

### Phase 6: 回收站协同 ✅
- 标签关系保留（照片进回收站不删除映射）
- AlbumManager.cleanupMappingsForDeletedImages() 可用

### Phase 7: 系统相册同步 ✅
- `data/repository/SystemAlbumSyncManager.kt` - 系统相册操作
- AlbumManager 集成同步方法
- AlbumViewScreen 添加同步开关菜单
- 同步状态指示图标

---

## 🏗️ 架构设计

### 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                             │
├─────────────────────────────────────────────────────────────┤
│  DeckScreen     TagViewScreen     TagManageScreen           │
│  (新增 TagChips) (按标签浏览)      (标签管理)                │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────┴──────────────────────────────────┐
│                    Domain/Manager Layer                      │
├─────────────────────────────────────────────────────────────┤
│  TagManager         SystemAlbumSyncManager                   │
│  (标签CRUD+归类)    (同步到系统相册)                          │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────┴──────────────────────────────────┐
│                      Data Layer                              │
├─────────────────────────────────────────────────────────────┤
│  tags.json          tag_mappings.json       MediaStore API  │
│  (标签定义)         (图片→标签映射)          (系统相册操作)   │
└─────────────────────────────────────────────────────────────┘
```

### 数据模型设计

#### 1. Tag（标签定义）
```kotlin
data class Tag(
    val id: String,           // UUID
    val name: String,         // 用户可见名称
    val color: Long?,         // 可选颜色值
    val icon: String?,        // 可选图标标识符
    val order: Int,           // 排序权重（越小越靠前）
    val createdAt: Long,      // 创建时间戳
    val systemAlbumId: String? // 对应的系统相册ID（如已同步）
)
```

#### 2. TagMapping（图片→标签映射）
```kotlin
data class TagMapping(
    val imageId: Long,        // MediaStore 图片 ID
    val imageUri: String,     // 图片 URI（备份用）
    val tagIds: List<String>, // 关联的标签 ID 列表
    val taggedAt: Long        // 最后归类时间
)
```

#### 3. PendingTagAction（撤销队列项）
```kotlin
data class PendingTagAction(
    val id: String,
    val type: ActionType,     // ADD, REMOVE, BULK_ADD, BULK_REMOVE
    val imageId: Long,
    val tagId: String,
    val timestamp: Long
)

enum class ActionType {
    ADD, REMOVE, BULK_ADD, BULK_REMOVE
}
```

---

## 📁 新增文件清单

### Data Layer
| 文件路径 | 描述 |
|---------|------|
| `data/model/Tag.kt` | 标签数据模型 |
| `data/model/TagMapping.kt` | 图片-标签映射模型 |
| `data/model/PendingTagAction.kt` | 撤销操作队列项模型 |
| `data/repository/TagManager.kt` | 标签 CRUD + 归类管理器 |
| `data/repository/SystemAlbumSyncManager.kt` | 系统相册同步管理器 |

### UI Components
| 文件路径 | 描述 |
|---------|------|
| `ui/components/TagChips.kt` | 卡片底部标签 Chip 行 |
| `ui/components/TagManageDialog.kt` | 标签新建/编辑对话框 |
| `ui/components/UndoSnackbar.kt` | 撤销操作 Snackbar |

### UI Screens
| 文件路径 | 描述 |
|---------|------|
| `ui/screens/TagViewScreen.kt` | 按标签浏览照片集合 |
| `ui/screens/TagManageScreen.kt` | 标签管理（列表+编辑+排序） |

### Navigation
| 文件路径 | 修改 |
|---------|------|
| `ui/navigation/AppScreen.kt` | 新增 `TAG_VIEW`, `TAG_MANAGE` 枚举值 |

---

## 🔧 现有文件修改清单

| 文件 | 修改内容 |
|------|---------|
| `MainActivity.kt` | 新增路由状态、TagManager 初始化、新屏幕内容 |
| `DeckScreen.kt` | 1) 接收标签列表参数 2) 底部添加 TagChips 组件 3) 新增 onTagSelect 回调 |
| `ui/components/CardStack.kt` | 可能需要调整布局以容纳底部 TagChips |
| `data/repository/RecycleBinManager.kt` | 回收站恢复时保留标签关系（无需大改，只需 TagManager 配合） |
| `ui/screens/SettingsScreen.kt` | 新增"标签管理"入口 |
| `ui/components/TopBar.kt` | 新增"标签视图"入口按钮（可选） |

---

## 📦 实现步骤（分阶段）

### Phase 1: 数据层基础（约 2 小时）

#### Step 1.1: 创建数据模型
- [ ] 创建 `data/model/Tag.kt`
- [ ] 创建 `data/model/TagMapping.kt`
- [ ] 创建 `data/model/PendingTagAction.kt`

#### Step 1.2: 创建 TagManager
- [ ] 创建 `data/repository/TagManager.kt`
- [ ] 实现 JSON 持久化（参考 `RecycleBinManager`）
- [ ] 实现以下方法：
  ```kotlin
  // 标签 CRUD
  suspend fun createTag(name: String, color: Long? = null): Tag
  suspend fun updateTag(tag: Tag)
  suspend fun deleteTag(tagId: String)
  suspend fun getAllTags(): List<Tag>
  suspend fun reorderTags(tagIds: List<String>)  // 批量更新顺序
  
  // 归类操作
  suspend fun addImageToTag(imageId: Long, imageUri: String, tagId: String)
  suspend fun removeImageFromTag(imageId: Long, tagId: String)
  suspend fun getTagsForImage(imageId: Long): List<Tag>
  suspend fun getImagesForTag(tagId: String): List<Long>
  
  // 撤销支持
  suspend fun recordAction(action: PendingTagAction)
  suspend fun undoLastAction(): Boolean
  suspend fun getLastAction(): PendingTagAction?
  suspend fun clearActionHistory()
  ```

### Phase 2: UI 组件（约 3 小时）

#### Step 2.1: TagChips 组件
- [ ] 创建 `ui/components/TagChips.kt`
- [ ] 实现横向滚动的 Chip 行
- [ ] 支持选中态动画
- [ ] 点击 Chip 触发回调

```kotlin
@Composable
fun TagChips(
    tags: List<Tag>,
    selectedTagIds: Set<String>,  // 当前图片已有的标签
    onTagClick: (Tag) -> Unit,
    onAddTagClick: () -> Unit,
    modifier: Modifier = Modifier
)
```

#### Step 2.2: UndoSnackbar 组件
- [ ] 创建 `ui/components/UndoSnackbar.kt`
- [ ] 实现底部浮动提示 + 撤销按钮
- [ ] 支持自动消失（3 秒）

#### Step 2.3: TagManageDialog 组件
- [ ] 创建 `ui/components/TagManageDialog.kt`
- [ ] 实现标签新建/编辑对话框
- [ ] 支持颜色选择器

### Phase 3: DeckScreen 集成（约 2 小时）

#### Step 3.1: 修改 DeckScreen
- [ ] 新增参数：
  ```kotlin
  tags: List<Tag>,
  onTagSelect: (imageId: Long, tagId: String) -> Unit,
  onAddTagClick: () -> Unit
  ```
- [ ] 在底部区域添加 TagChips 组件
- [ ] 实现点击标签后自动进入下一张的逻辑

#### Step 3.2: 修改 CardStack（可选）
- [ ] 调整布局以适应底部 TagChips
- [ ] 确保手势不冲突

### Phase 4: 标签视图屏幕（约 2 小时）

#### Step 4.1: 创建 TagViewScreen
- [ ] 创建 `ui/screens/TagViewScreen.kt`
- [ ] 实现标签列表 + 照片网格
- [ ] 支持多选模式（批量改标签）
- [ ] 实现照片点击查看器

#### Step 4.2: 创建 TagManageScreen
- [ ] 创建 `ui/screens/TagManageScreen.kt`
- [ ] 实现标签列表（支持拖拽排序）
- [ ] 实现标签编辑/删除

### Phase 5: 路由集成（约 1 小时）

#### Step 5.1: 更新 AppScreen 枚举
```kotlin
enum class AppScreen {
    DECK,
    RECYCLE_BIN,
    SETTINGS,
    ABOUT,
    STATISTICS,
    TAG_VIEW,      // 新增
    TAG_MANAGE     // 新增
}
```

#### Step 5.2: 更新 MainActivity
- [ ] 初始化 TagManager
- [ ] 新增路由内容
- [ ] 处理预测性返回层级

### Phase 6: 回收站协同（约 1 小时）

#### Step 6.1: 标签关系保留
- [ ] 照片进入回收站时，**不删除** TagMapping
- [ ] 照片从回收站恢复时，关系自动恢复
- [ ] 照片永久删除时，清理对应 TagMapping

#### Step 6.2: TagManager 与 RecycleBinManager 协同
```kotlin
// 在永久删除时调用
suspend fun cleanupMappingsForDeletedImages(imageIds: List<Long>)
```

### Phase 7: 系统相册同步（约 3 小时）【高优先扩展】

#### Step 7.1: 创建 SystemAlbumSyncManager
- [ ] 创建 `data/repository/SystemAlbumSyncManager.kt`
- [ ] 实现 MediaStore 相册操作：
  ```kotlin
  // 在 Android 10+ 系统上，"相册"实际是目录概念
  suspend fun createSystemAlbum(name: String): Result<Uri>
  suspend fun addImageToAlbum(imageUri: Uri, albumUri: Uri): Result<Unit>
  suspend fun removeImageFromAlbum(imageUri: Uri, albumUri: Uri): Result<Unit>
  suspend fun deleteSystemAlbum(albumUri: Uri): Result<Unit>
  ```

#### Step 7.2: 同步策略
- [ ] 创建标签时，自动创建对应系统相册
- [ ] 归类时，同时操作系统相册
- [ ] 处理权限请求弹窗
- [ ] 实现失败回滚机制

#### Step 7.3: 权限处理
```kotlin
sealed class SyncResult {
    data object Success : SyncResult()
    data class NeedsPermission(val intentSender: IntentSender) : SyncResult()
    data class Error(val message: String) : SyncResult()
}
```

---

## 🎨 UI/UX 设计要点

### TagChips 设计
```
┌─────────────────────────────────────────┐
│                 [Photo]                  │
├─────────────────────────────────────────┤
│  🏷️ 风景  🏷️ 家人  🏷️ 美食  ➕          │
│  ←─────── 可横向滚动 ─────────→         │
└─────────────────────────────────────────┘
```

### 交互流程
1. 用户在 DeckScreen 看到底部 TagChips
2. 点击某个标签 → 图片归类到该标签 → 自动翻到下一张
3. 点击 ➕ → 弹出新建标签对话框
4. 可在 TopBar 或 Settings 进入标签视图/管理

### 撤销机制
- 归类后底部显示 Snackbar：`"已归类到「风景」" [撤销]`
- 3 秒后自动消失
- 撤销后恢复之前状态

---

## ⚠️ 注意事项

### 1. 数据一致性
- 系统相册同步失败时，本地状态也要回滚
- 批量操作使用事务模式

### 2. 性能优化
- TagManager 加载时缓存到内存
- 使用 Flow 实现实时更新
- 大列表使用 LazyColumn/LazyGrid

### 3. 边界情况
- 标签被删除时，相关映射也要清理
- 图片被永久删除时，映射也要清理
- 处理同名标签冲突

### 4. 系统相册同步兼容性
- Android 10+ 使用 Scoped Storage
- 可能需要 `MediaStore.createWriteRequest()`
- 不同厂商 ROM 可能行为不一致

---

## 📊 工作量估算

| Phase | 预计时长 | 优先级 |
|-------|---------|-------|
| Phase 1: 数据层 | 2h | P0 |
| Phase 2: UI 组件 | 3h | P0 |
| Phase 3: DeckScreen 集成 | 2h | P0 |
| Phase 4: 标签视图屏幕 | 2h | P0 |
| Phase 5: 路由集成 | 1h | P0 |
| Phase 6: 回收站协同 | 1h | P0 |
| Phase 7: 系统相册同步 | 3h | P1 |
| **总计** | **~14h** | - |

---

## 🚀 开始实现

准备好后，我们可以按照 Phase 顺序逐步实现。建议先完成 Phase 1-6（核心功能），确保"标签归类"的基础能力可用，再实现 Phase 7（系统相册同步）。

**下一步**：开始 Phase 1，创建数据模型和 TagManager。

---

*此计划由 Opus 于 2026-01-27 生成*
