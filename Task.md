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

### 阶段 1：Local 结构优化（无新功能）【原子重构】
- [ ] `MediaLoader` 更名为 `LocalMediaLoader`，明确为 Local 专属（改名 + 全部引用同步，一步完成，不在中途停留）
- [ ] 拆分 `LocalMediaLoader` 职责：目录读取 / 元数据解析（EXIF、MediaMetadataRetriever）/ 字幕关联（改用 common 抽象）
- [ ] 梳理 `MediaStore`、`LocalMediaProvider`、`MediaDatabase` 三层职责，确保内存缓存 ↔ DB 缓存 ↔ ContentProvider 实时数据分层清晰，DB 读写集中醒目
- [ ] 确认 `MediaInfo.File` 直接实现 `LMedia` 契约成立
- [ ] 出口标准：App 编译运行正常，Local 行为完全不变（仅内部结构优化）。

### 阶段 2：MediaSource 统一加载接口【接口定义，不影响运行】
- [ ] 在 `MediaSource` 定义统一加载接口（load / refresh / loadMetadata 等）
- [ ] `LocalMediaLoader` 实现该接口（实现后 Local 内部调用路径不变，行为无差）
- [ ] UI 层（HomePage / MainMediaSubPage / BasicMediaGridPage 等）改为调用统一接口，不再直连 local 内部
- [ ] 出口标准：App 编译运行正常，Local 行为不变，仅调用路径统一。此阶段不触碰 webDAV，`webDAV` 字段仍为空、不被消费。

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
