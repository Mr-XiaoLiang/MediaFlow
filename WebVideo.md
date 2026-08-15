# 局域网及自建媒体服务协议接入技术调研与架构评估报告

## 1. 调研背景与评估目标

### 1.1 项目背景

本评估旨在针对现有 Android 本地视频播放器进行网络化与局域网化升级。计划在保留本地播放能力的基础上，扩展对**局域网共享存储**及**自建媒体库服务**的支持，实现视频、音频的流式播放以及图片的异步加载展示。

### 1.2 评估目标

* 评估各主流网络传输协议与自建媒体库 API 在 **Media3 (ExoPlayer)** 内核下的兼容性与集成可行性。
* 评估各协议在播放器底层（`DataSource` / API 交互 / Seek 拖动 / 缓冲区管理）的**代码侵入度与 API 改造深度**。
* 制定适配多协议的底层分层架构规划，并明确协议接入的优先级路线图。

---

## 2. 核心协议支持度与开发改造深度评估

### 2.1 综合评估矩阵

| 协议 / 服务类型 | ExoPlayer 原生支持度 | 核心接入方案 | API 改造层级 | 协议解析与缓冲区改造复杂度 | Seek (拖动) 改造难度 |
| --- | --- | --- | --- | --- | --- |
| **WebDAV** | ⚠️ 间接原生支持 | `DefaultHttpDataSource` + Header 配置 | **L1：配置层** | **无**<br><br>标准 HTTP 协议，无二进制解析。 | **极低**<br><br>依靠 HTTP `Range` 标头，ExoPlayer 原生处理。 |
| **Emby / Jellyfin API** | 🟢 完美原生支持 | REST API + HTTP 流 | **L1：业务 API 层** | **无**<br><br>服务端处理解封，直接返回 HTTP 流。 | **极低**<br><br>根据直连/转码模式利用 HTTP Range 或 HLS/DASH 机制。 |
| **Plex API** | 🟢 完美原生支持 | REST API + Token 鉴权 + HTTP 流 | **L1：业务 API 层** | **无**<br><br>服务端解封，客户端仅需处理 Auth Token。 | **极低**<br><br>由原生 HTTP 机制或服务端转码切片处理。 |
| **Alist Native API** | 🟢 完美原生支持 | REST API 获取网盘直链 (HTTP) | **L1：业务 API 层** | **无**<br><br>透传网盘 HTTP 直链。 | **低**<br><br>依赖上游网盘对 Range 请求的支持。 |
| **SFTP** | ❌ 无内置支持 | 自定义 `SftpDataSource`<br><br>*(依赖 `JSch` / `SSHJ`)* | **L2：流通道层** | **中等**<br><br>需要处理 SSH 握手、加解密与 `InputStream` 映射。 | **中等**<br><br>重置 Channel 文件 Offset，重新打开输入流。 |
| **SMB (SMB2/SMB3)** | ❌ 无内置支持 | 自定义 `SmbDataSource`<br><br>*(依赖 `jcifs-ng` / `libsmb2`)* | **L3：协议深水区** | **极高**<br><br>ExoPlayer 频繁发起小额读取（数 KB），高 RTT 会导致严重卡顿，必须自研**环形预读缓冲区 (Ring Buffer)**。 | **极高**<br><br>Seek 时需瞬间清空旧缓冲区、取消未完成的远端请求并重置 Offset。 |
| **NFS (NFSv3/v4)** | ❌ 无内置支持 | JNI (`libnfs`) + 自定义 `NfsDataSource` | **L3：跨语言/底层** | **极高**<br><br>需编写 Android NDK C/C++ 代码，管理 DirectByteBuffer 内存与指针传递。 | **高**<br><br>调用 C 层 `nfs_lseek64`，需妥善处理 JNI 跨语言调用与缓存失效。 |

---

### 2.2 技术改造层级（L1 - L3）详细评估

#### L1：零改造 / 配置层 (`HttpDataSource`)

* **涉及协议**：WebDAV、Emby、Jellyfin、Plex、Alist。
* **API 改造说明**：
* **完全无需编写自定义 `DataSource**`。
* 播放器核心仅需调用业务接口获取标准 HTTP/HTTPS 视频流地址，塞给 `MediaItem.fromUri()`。
* ExoPlayer 内置的 `DefaultHttpDataSource` 全权处理连接池复用、HTTP 分段读取（Range）、超时重连及 Seek 定位。



#### L2：流式通道封装层 (`InputStream` 映射)

* **涉及协议**：SFTP。
* **API 改造说明**：
* 需要继承 Media3 的 `BaseDataSource` 并实现 `open()`, `read()`, `close()`。
* 将 SFTP 客户端库提供的远端 `InputStream` 封装为 ExoPlayer 的字节读取管道。
* Seek 拖动通过调用 SFTP Channel 的 `skip()` 或在指定的 Offset 重新建立读取流来实现。



#### L3：协议深水区（自定义 Buffer + 内存管理 + JNI）

* **涉及协议**：SMB (SMB2/SMB3)、NFS (NFSv3/v4)。
* **API 改造说明**：
* **网络 RTT 冲突解决**：ExoPlayer 在解封装（如 MP4 Extractor）时会频繁发起小额随机读取。直接映射为 SMB/NFS 接口会导致严重的网络延迟积累，**必须在 `DataSource` 内部实现预读与滑动窗口机制**。
* **缓存与 Seek 协同**：拖动进度条（`DataSpec.position` 发生跳跃）时，必须具备缓存失效抛弃机制，防止主线程阻塞与内存溢出。
* **NFS JNI 胶水层**：由于 Java 生态缺乏高效的纯 Java NFS 客户端，必须利用 NDK 交叉编译 C 语言 `libnfs` 动态库，并通过 JNI 编写内存映射逻辑。



---

## 3. 多媒体类型处理方案规划

### 3.1 音视频处理方案 (Media3 ExoPlayer)

* **视频 (Video)**：统一采用 Media3 ExoPlayer 内核，通过底层 `DataSource` 路由适配各类协议。
* **音频 (Audio)**：使用 Media3 `media3-session` 与 `MediaSessionService` 架构。
* 原生支持后台播放、系统锁屏控制卡片、蓝牙按键响应。
* 专业自建音乐库（如 Subsonic / Navidrome API）直接透传 HTTP 音频流。



### 3.2 图片/相册处理方案 (Coil / Glide)

* **技术决策**：**不使用 Media3 展示图片**。
* **评估原因**：ExoPlayer 的设计目标为连续流媒体解码，加载静态大图（特别是 SMB/WebDAV 上的 RAW/4K 照片）会导致极高的内存开销与生命周期管理成本。
* **替代方案**：采用 **Coil** 或 **Glide** 图片加载框架。通过自定义 `Fetcher` (Coil) 或 `ModelLoader` (Glide)，复用已实现的 SMB/WebDAV 文件传输管道进行缩略图及原图异步加载。

---

## 4. 多协议解耦架构规划

为保障后续新增协议时不破坏播放器主体逻辑，将底层设计划分为**媒体源抽象层**与**数据源路由层**：

```
                    ┌──────────────────────────────────────────────┐
                    │               播放器业务逻辑 UI               │
                    └──────┬───────────────────────────────┬───────┘
                           │                               │
            ┌──────────────┴──────────────┐ ┌──────────────┴──────────────┐
            │    1. 媒体源抽象层 (Repo)   │ │    2. 数据源路由适配层       │
            └──────────────┬──────────────┘ └──────────────┬──────────────┘
                           │                               │
            ┌──────────────┼──────────────┐ ┌──────────────┼──────────────┐
            │              │              │ │              │              │
        Emby/Plex      WebDAV/SMB     本地文件  HttpDS       SmbDS          NfsDS
       (API/元数据)   (文件树目录)   (Local File) (L1 协议)    (L3 协议)      (L3 协议)

```

1. **媒体源抽象层 (`MediaRepository`)**
* **职责**：负责目录树浏览、文件搜索、媒体库元数据抓取（海报、演员信息）。
* **输出**：统一的 `MediaItemModel`（包含标题、封面图、播放地址 `Uri`、协议类型等）。


2. **数据源路由层 (`RoutingDataSourceFactory`)**
* **职责**：实现 Media3 的 `DataSource.Factory` 接口，根据传入 `Uri` 的 Scheme（如 `http://`, `webdav://`, `smb://`, `nfs://`）动态分发给对应的 `DataSource` 实例。



---

## 5. 路线图与实施优先级规划

基于开发成本与场景覆盖率，制定三期演进计划：

```
  ┌───────────────────────────────────────────┐
  │  Phase 1: P0 高性价比方案                  │
  │  - WebDAV 协议支持                        │
  │  - Emby / Jellyfin / Plex API 对接        │
  │  - 目标: 零低成本覆盖私有云与自建媒体库      │
  └─────────────────────┬─────────────────────┘
                        │
                        ▼
  ┌───────────────────────────────────────────┐
  │  Phase 2: P1 局域网核心攻坚                │
  │  - 自研 SmbDataSource (基于 jcifs-ng)      │
  │  - 实现 Ring Buffer 预读机制与 Seek 缓存优化│
  │  - 目标: 打通 Windows/NAS 局域网共享       │
  └─────────────────────┬─────────────────────┘
                        │
                        ▼
  ┌───────────────────────────────────────────┐
  │  Phase 3: P2 扩展与长尾协议               │
  │  - Alist Native API 对接                  │
  │  - NfsDataSource (JNI + libnfs) / SFTP    │
  │  - 目标: 满足极客用户与高性能原盘播放需求    │
  └───────────────────────────────────────────┘

```

### 5.1 第一阶段 (Phase 1 / P0) - 低成本快速上线

* **接入目标**：WebDAV、Emby / Jellyfin API、Plex API。
* **主要工作**：
* 构建 `MediaRepository` 抽象层。
* 接入 REST API 换取 HTTP 流，配置 `DefaultHttpDataSource`。
* 搭建海报墙及播放进度同步逻辑。



### 5.2 第二阶段 (Phase 2 / P1) - 核心局域网攻坚

* **接入目标**：SMB (SMB2/SMB3)。
* **主要工作**：
* 引入 `jcifs-ng` 或 `libsmb2`。
* 实现自定义 `SmbDataSource`。
* **核心攻坚**：编写专用的**滑动窗口预读缓冲区**与 **Seek 清空重置算法**，解决 SMB 高 RTT 卡顿问题。



### 5.3 第三阶段 (Phase 3 / P2) - 极客与长尾扩展

* **接入目标**：Alist Native API、NFS、SFTP。
* **主要工作**：
* 对接 Alist API 换取网盘直链。
* 编译 NDK `libnfs` 编写 JNI 胶水层，提供 `NfsDataSource`。