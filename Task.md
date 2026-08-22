# MediaFlow 多来源数据架构改造计划

> 状态：规划中（自 2026-08-22 起）。分阶段执行清单，先定稿后逐步落地。

## 一、目标架构

从单一本地 ContentProvider 文件，演进为多来源统一数据架构：本地（Local）已成型需优化；WebDAV 基于文件结构独立实现并支持懒加载；Jellyfin 预留接口；对外统一契约 `LMedia`，UI 只认接口不关心来源。

### 包结构

```
com.lollipop.mediaflow.data
 ├─ LMedia.kt          # 统一媒体契约接口
 ├─ MediaMetadata.kt   # 元数据模型（字段待复核）
 ├─ MediaSource.kt     # 来源定义（sealed，硬编码字段）
 ├─ common/            # 公共：字幕抽象、目录树工具、排序、大小格式化
 ├─ local/             # 本地实现（LocalMediaLoader 等）
 ├─ webdav/            # WebDAV 实现（独立包，懒加载）
 └─ jellyfin/          # 预留
```

---

## 二、已确认的设计决策

1. **MediaSource 保持硬编码字段形态**：`local / webDAV / jellyfin` 各自作为字段（如 `SnapshotStateList<LMedia>`），不引入运行时 Map 查找，避免一次映射开销；新增来源=加字段，需配套开发，可接受。外部统一通过 `LMedia` 接口访问。
2. **WebDAV 仿 Local 的 SQLite 缓存 + 树/拍平双视图**，但必须支持懒加载（数据量不确定，无法一次性全量扫描）。
3. **数据库使用原生 SQLite，不引入 Room**。读写代码需集中且醒目，以强调减少数据库操作频次。
4. **ArchiveManager 后续抽象为多来源统一接口**：不同来源对回收站的能力与操作方式不互通，需统一接口 + 各自实现。本次仅占位规划。
5. **LMedia 为接口而非数据类**：避免同一份来源数据因展示需要而复制成两份（原始模型 + 转换列表）。各来源内部模型直接实现 `LMedia`，不做额外转换层。
6. **字幕需抽象**：与具体来源解耦，抽到 `common`。
7. **MediaMetadata 字段暂不纠结**：当前从 Local 直接迁移，是否完全契合后续再议。
8. **加载器归属来源专属**：现有 `MediaLoader` 更名为 Local 专属加载器（如 `LocalMediaLoader`）；每个来源自带加载器；`MediaSource` 定义统一加载接口，各加载器实现。
9. **WebDAV 懒加载策略**（见第三节）。

> ## ⚠️ 基本原则（范式转变，贯穿全程，务必遵守）
>
> **本项目正在从「View + Adapter」架构改造为「Compose + State」架构。**
>
> - 老代码大量使用回调（listener / callback / dataChangedListener）是 **View 时代的写法**，
>   面向新 Compose 结构**不合适是正常的**，可为了新结构主动改造，**不要左右脑互搏、也不要因为老代码用了回调就缩手缩脚不去改**。
> - 新结构「面向状态」：数据通过 `State` / `SnapshotStateList` 暴露，UI 观察状态而非注册监听器。
>   控制层（如 `SourceLoader`）负责把缓存/加载结果投影进 `MediaSource` 的展示列表与 `LocalState` 的业务状态，UI 只观察。
> - **`SourceLoader` 接口语义（铁律）**：
>   - `fill`：把**当前已缓存**的数据投影填充进列表，**不做任何加载**（快，纯从缓存读）。
>   - `refresh`：触发**重新加载**（整 visibility 重扫），比较慢，完成后更新列表 + 同步加载/错误状态。
>   - `loadMore`：**仅针对有分页/懒加载分层的来源**（如 WebDAV）。Local 无分页 → 空实现。
> - 实现就近写在密封类内部（如 `SourceLoader.Local`），所有实现一眼可见，不单独甩文件。

---

## 三、WebDAV 懒加载设计

1. **加载器来源**：WebDAV 客户端代码将作为模块从开源项目迁移进来（该项目已一两年未维护，迁移时一并优化以适配当前技术栈）。
2. **加载方式**：默认一次加载「本文件夹 + 下一层」的文件列表，与本地文件读取粒度类似。采用**队列 + 广度优先**循环遍历文件夹。
3. **可中断与续传**：读取达到设定次数或数据量阈值后暂停；队列内容可暂存。需要时从断点继续循环加载更深一层。
4. **产品定位**：本应用为视频/图片浏览器，内容是核心，目标是把分散在各文件夹的资源集中展示，同时保留文件夹模式的浏览视图。因此树状与拍平两种数据管理模式需兼容。

---

## 四、分阶段任务

> 可运行性原则：每个阶段结束时应用必须可编译、可运行，已完成功能不受影响。改动较大的重构采用「先并行新增 / 后原子切换」策略，不在半成品状态停留。

### 阶段 0：公共基础抽取（common）【纯新增，向后兼容】✅ 已完成
- [x] 新建 `data/common/` 包
- [x] 抽取目录树工具：目录遍历、拍平、树节点计数（`DirectoryTreeKit`，无类型依赖，供各来源复用）
- [x] 抽象字幕：`SubtitleInfo` 及解析规则（baseName / language / suffix / mimeType），与 Local 解耦；`local/SubtitleFile` 改为包装 `SubtitleInfo` 并保留来源字段
- [x] 上提排序 `MediaSort`（基于 `LMedia`，与 local 包解耦；7 处引用已切换）
- [x] 抽取文件大小格式化工具（`FileSizeFormatter`；`MediaInfo.sizeFormat` 已切换）
- [x] 明确 `MediaMetadata` 为跨来源共享模型，标注「字段待复核」
- [x] **切换引用（原子）**：`MediaSort`/`SubtitleFile`/`sizeFormat` 引用全部切到 common；删除 `local/MediaSort.kt`
- [x] 出口标准：App 编译无 ERROR（仅 pre-existing WARNING），Local 功能不变；原子切换完成。

### 阶段 1：Local 结构优化（无新功能）【原子重构】✅ 已完成
- [x] `MediaLoader` 更名为 `LocalMediaLoader`，明确为 Local 专属（改名 + 全部引用同步，一步完成）
- [x] 拆分职责为三个文件：
  - `LocalMediaLoader.kt`：目录读取门面（loadTreeSync / loadDirectorySync / getRootFolderName / loadMediaFileSync / expandFolderSync / CursorLine / Column / findMediaType）
  - `LocalMetadataParser.kt`：元数据解析（EXIF / MediaMetadataRetriever + 缓存读写）
  - `LocalSubtitleMatcher.kt`：字幕按 baseName 关联到视频
- [x] 字幕关联改用阶段 0 的 common.SubtitleInfo（Local 专属 SubtitleFile 包装）
- [x] 梳理 `MediaStore` / `LocalMediaProvider` / `MediaDatabase` 三层职责：MediaStore（生命周期/调度）→ LocalMediaProvider（内存缓存）→ MediaDatabase（原生 SQLite 缓存）；DB 读写集中醒目
- [x] 确认 `MediaInfo.File` 直接实现 `LMedia` 契约成立（lastModified 已在阶段 0 补入 LMedia）
- [x] 出口标准：Lint 0 ERROR（仅 1 个 pre-existing WARNING），Local 行为完全不变，仅内部结构优化。

### 阶段 2：SourceLoader 控制层 + SourceState 状态契约【接口定义，不影响运行】✅ 已完成

关键架构认知（经多轮纠偏后的最终结论）：
- **View→Compose 范式转变**：老代码用回调（listener / callback）是 View+Adapter 时代写法，新结构面向状态，可主动改造，不要左右脑互搏（详见文件顶部基本原则框）。
- **`MediaStore` 的重新定位（按用户拆解）**：老结构给 RecyclerView 一次加载一个 List + 缓存一个 List 做筛选。现在「筛选结果交给 `MediaSource.local` (SnapshotStateList) 缓存」这部分工作已上交。因此 `MediaStore` 拆为两部分：
  1. **原数据内存缓存层**：`StoreCache` 按 visibility 一次性缓存全部文件（图片+视频），避免重复 IO / 网络，保留。
  2. **业务筛选上移**：按状态（scope / sort）过滤、投影进 `StateList` 的事，由 `SourceLoader` 业务层做，不再依赖 `Gallery` 的 `dataChangedListener` 命令式广播（该广播在新结构冗余，UI 观察 `MediaSource.local` / `SourceState`）。
- **状态契约 = `SourceState` 接口（非 Params 数据类）**：每次加载请求都传入一个 [SourceState] 实例；不同来源（Local / WebDAV）各自提供实现（如 Local 的 `LocalState`），把「某个模式实例」（PublicVideo 等）放进来。控制层只依赖接口，反解范围 + 填充列表 + 翻转状态，UI 也只观察它。这样所有逻辑聚合在一个状态里，命名也更合理（去掉难听的 Params）。
- **`fill` / `refresh` / `loadMore` 语义铁律**：`fill`=填缓存不加载(快)；`refresh`=重扫加载(慢)；`loadMore`=仅分页来源(WebDAV)实现，Local 空实现。
- **范围筛选（choose）是通用状态**：`Gallery.loadChoose` 的「只展示某文件夹」对应 `rootDirectoryId`，抽成 `SourceState.scopeId` 通用范围筛选；Local 解释为文件夹 id，WebDAV 解释为网盘目录。范围筛选必须在 `fill/refresh` 时通过 `Gallery.setRootDirectory(scopeId)` 接上，否则 choose 不生效。

文件拆分（避免单文件臃肿）：
- `data/SourceState.kt`：接口 [SourceState]（sort / scopeId / isLoading / error 可观察状态 + setter）。
- `data/SourceLoader.kt`：`sealed class SourceLoader`（控制有限实现），`object Local` 嵌套其中（一眼可见），负责编排。
- `data/local/LocalState.kt`：`sealed class LocalState : SourceState`，4 个单例各带 `visibility` / `mediaType` / `source`(对应 MediaSource 列表)，无需再查表反解。
- `data/MediaSource.kt`：只保留展示列表密封类 + `loader = SourceLoader.Local`，不再塞控制层与状态。
- （具体包装业务如投影/加载，可继续在 `data/local/` 包内扩展，SourceLoader.Local 委托调用。）

改动：
- [x] `SourceLoader` 为 `sealed class`，方法签名 `fill(context, state: SourceState)` / `refresh(context, state: SourceState)` / `loadMore(context, state: SourceState)`；`Local` 嵌套实现。
- [x] 新增接口 `SourceState`，`LocalState` 移出 `MediaSource.kt` 到 `data/local/` 并实现它，单例携带 `visibility`/`mediaType`/`source`。
- [x] `SourceLoader.Local.fill/refresh`：把 `state.scopeId` 经 `Gallery.setRootDirectory` 接上范围筛选，再 `loadChoose`(fill)/`refresh`(refresh) 投影进 `state.source.local`；`refresh` 成败都 `setLoading(false)`，失败 `setError`。`loadMore` 空实现。
- [x] 删除多余的 `MediaSource.loader` 转发入口（UI 直接消费 `SourceLoader.Local`），`MediaSource` 只保留展示列表与 `of()` 定位方法；删除旧 `SourceParams` / `MediaSource.of` / `LocalState.of` 仅在本次收敛中调整。
- [x] 回退 7 个 UI 文件里错误改成 `MediaSource.loader` 的调用，恢复 `MediaStore.loadGallery/loadStore`（属于筛选视图获取），本次不动 UI 触发逻辑（待 Compose 迁移）。老代码本阶段不动，新业务可直接调用 / 复制其内容，后续阶段再去重。
- [x] 出口标准：Lint 0 ERROR 0 WARNING（全部 4 文件）。

### 阶段 2.5：MediaStore 架构对齐（破坏性改造，先于新增来源）

> 背景：阶段 2 确立了新架构（控制层 `SourceLoader` + 状态契约 `SourceState` + 展示层 `MediaSource.local` 直接投影）。但 `MediaStore` / `Gallery` 仍是 View+Adapter 时代的回调式结构，与新架构不匹配，必须先对齐再新增 WebDAV 等来源。
>
> 范围与边界（用户明确）：
> - **破坏性修改 `MediaStore`，彻底迁移数据结构**：数据层（`Store` / `Gallery` / 缓存）按新架构重塑，UI 暂不动，由用户（人工）处理页面层接线。
> - **Local 数据结构与设计方向必须和新架构保持一致、逻辑完整**：即「原数据内存缓存层」与「业务筛选投影」职责分清，筛选投影由 `SourceLoader` 驱动，不再依赖命令式广播。
> - **忽略 View 页面列表展示问题**：用户自行调整，本阶段不碰 UI 展示。

具体子项：
- [ ] **拆除命令式广播**：移除 `MediaStore.dataChangedListener` / `requestList` / `notifyDataChanged()` / `register` / `unregister` / `createListener` / `LifecycleDataChangedListener` 这套 listener 链（新架构 UI 观察 `SourceState` / `MediaSource.local`，不需要广播）。
- [ ] **`Gallery` 去 UI 耦合**：`Gallery.sortType` 默认值不再取自 `ui.HomePage.findPage(...).sortType`（数据层反向依赖 UI 枚举），改为由调用方（`SourceLoader.Local`）显式传入 `SourceState.sort`，`Gallery` 不再持有默认 sort 来源。
- [ ] **范围筛选统一走 `SourceState.scopeId`，持久化迁移到 `LocalState` 初始化**：选中文件夹（scopeId）**需要持久化**（避免重启 APP 丢失状态），所以 `Preferences.selectXxxDir` 不能丢，但**不能再由 `MediaStore.loadGallery` 里的 `folderPreferences` 去初始化**（数据层反向依赖 `Preferences`）。应迁移到对应的 `LocalState` 单例初始化阶段：每个单例初始化 `scopeId` 时直接从 `Preferences.selectXxxDir`（按 visibility+mediaType 维度）读取初始值，使状态从一开始就带着持久化值。`MediaStore` 彻底摆脱对 `Preferences` 的依赖。
- [ ] **scopeId 变更写回持久化**：`SourceLoader.Local` 在用户改选范围（`setScopeId`）时，同步把新值写回 `Preferences.selectXxxDir`，保证持久化闭环（读在初始化、写在变更）。
- [ ] **`Gallery` 改造为纯投影工具**：保留「按 mediaType + scopeId + sort 从 `StoreCache` 投影进列表」的能力，但去掉 `galleryCallback` 命令式完成通知（或收敛为内部同步投影），由 `SourceLoader.Local` 直接读投影结果填 `MediaSource.local`，消除 `MediaStore.loadGallery` 全局 `galleryCache` 复用带来的隐式状态共享。
- [ ] **`StoreCache` 定位为「原数据内存缓存层」**：按 visibility 一次性缓存全部文件（图片+视频），避免重复 IO；其读写醒目（原生 SQLite 后续阶段落地），本阶段只确保它作为底层缓存被 `SourceLoader` 正确消费、不掺筛选逻辑。
- [ ] **校验 Local 逻辑完整性**：改造后 `SourceLoader.Local.fill/refresh(loadMore 空)` 能完整跑通「读缓存 → 按 scopeId/sort 筛选 → 投影进 `MediaSource.Xxx.local` → 翻转 `SourceState` 状态」，Local 行为与改造前一致（仅机制替换，无功能回归）。
- [ ] **出口标准**：App 编译通过（数据层改动不引入 ERROR）；Local 数据路径逻辑完整，可被 `SourceLoader` 驱动；UI 展示层由用户「人工处理-待定」自行接线，本阶段不保证 UI 可运行展示。

> 人工处理-待定（用户亲自做的部分，不在此阶段自动改动）：
> 1. View 页面层（MainActivity / VideoFlow / PhotoFlow / Archive / DirectoryChooseDialog 等）从老的 `Gallery` / `dataChangedListener` 切换到观察 `MediaSource.local` + `SourceState` 的 Compose 化接线。
> 2. 任何页面列表展示（RecyclerView Adapter）的最终调整。

### 阶段 3：WebDAV 来源实现（独立包）
> 现状：WebDAV 客户端已作为独立 Gradle 模块 `webDAV/`（基于 sardine-android 开源库）迁入，约 90 个 Java 文件，已一两年未维护。数据层目录 `vision/.../data/webdav/` 为空。`MediaSource` 已预留 `webDAV` 字段。
- [ ] 优化 `webDAV/` 客户端模块：对齐当前技术栈（Kotlin 化接口封装、依赖升级、OkHttp 适配、清理废弃 API），使其可维护
- [ ] 在 `data/webdav/` 定义 WebDAV 内部模型（直接实现 `LMedia`）
- [ ] 实现 `WebDAVLoader`（实现 MediaSource 统一接口），封装 `webDAV/` 客户端调用
- [ ] 实现 WebDAV 原生 SQLite 缓存（仿 Local：拍平存储 + 树重建 + 版本化）
- [ ] 实现懒加载：队列 + 广度优先，阈值暂停 / 断点续传（客户端模块需支持按目录增量列举）
- [ ] 复用 common 的字幕抽象、目录树工具、排序
- [ ] `MediaSource.webDAV` 字段接线
- [ ] 兼容树状浏览与拍平集中展示两种模式
- [ ] 出口标准（分两步）：① WebDAV 模块优化 + 数据层实现完成、但 `webDAV` 字段尚未接线时，App 编译运行正常且 Local 完全不受影响；② 接线后，Local 仍正常，WebDAV 来源可独立加载与展示。每步结束均可运行。

### 阶段 4：ArchiveManager 多来源抽象（占位，后置）
- [ ] 定义归档/回收站统一接口（能力声明：是否支持、移动/还原操作）
- [ ] Local 实现（现有 DocumentsContract 逻辑迁移）
- [ ] WebDAV / Jellyfin 各自实现或声明不支持

### 阶段 5：Jellyfin 预留
- [ ] 仅留包结构与接口占位，实际接入另开任务

---

## 五、待讨论项

- [ ] MediaMetadata 字段复核时机
- [ ] WebDAV 懒加载阈值的具体取值（次数 / 数据量）
- [ ] 多来源在 UI 上的切换与聚合展示方式

---

## 六、执行顺序

0 → 1 → 2 → 3 →（4、5 并行/后置）。每完成一阶段在对应 checkbox 打勾。
