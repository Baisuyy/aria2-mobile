# Aria2 手机版（Android）

一个简洁流畅的原生安卓客户端，用于**监听标准链接**并推送给你已有的
aria2 服务器下载。支持从浏览器、任意 App「分享/打开链接」唤入，自动把链接加入下载队列。

技术栈：Kotlin · Jetpack Compose（Material 3）· Navigation Compose · OkHttp · DataStore。UI 采用深色石墨表面 + 电光翠绿的下载语义配色，进度条与状态切换带弹性动效。

## 核心能力

- **标准链接监听**
  - `ACTION_VIEW` + `http/https`：在浏览器里用「打开方式」选择本应用即可捕获链接
  - `ACTION_VIEW` + `text/plain`：接收纯文本链接
  - `ACTION_SEND`：「分享」来源的链接/磁力链
  - 自定义 `aria2://<urlencoded链接>` scheme
  - 若 App 已在后台被再次唤入，通过 `singleTask` + `onNewIntent` 正确处理
- **aria2 JSON-RPC 接入**：新增下载、进度轮询、暂停/继续/移除，参数值实时映射到 `aria2c`
- **设置**：RPC 地址、RPC 密钥（`rpc-secret`）、默认保存目录、默认 aria2c 参数、刷新间隔、测试连接、常亮开关
- **本地持久化**：服务器配置、下载偏好、已跟踪任务 gid 均持久化，重启后自动恢复

## 如何配合 aria2 服务器

在服务器上启动 aria2，开放 RPC：

```bash
aria2c --enable-rpc --rpc-listen-all=false --rpc-listen-port=6800 \
       --rpc-secret=你的密钥 --dir=/downloads
```

然后在本 App 的「设置」中填入 `http://主机IP:6800/jsonrpc` 与密钥即可。注意：手机与 aria2 需网络互通，`INTERNET` 权限已声明并从 8.0 开始默认为明文流量需在服务端用 https 或本机访问。

## 目录结构

```
app/src/main/java/com/aria2/mobile/
├── MainActivity.kt            # 入口 + singleTask/onNewIntent + NavHost 路由
├── data/
│   ├── Aria2Client.kt         # JSON-RPC 客户端（addUri/tellStatus/pause/…）
│   ├── LinkParser.kt          # Intent -> 链接解析（VIEW/SEND/aria2://）
│   ├── DownloadItem.kt        # 下载任务模型 + aria2 状态映射
│   ├── Models.kt              # Aria2Server / DownloadPrefs
│   └── SettingsStore.kt       # DataStore 持久化
├── viewmodel/DownloadViewModel.kt  # 状态 + 轮询调度 + 控制
└── ui/                        # theme / components / screens
```

## 构建与安装

以下配置仅在**本沙箱构建环境**需要，普通 Android Studio 打开无需改动设备：
- `gradle.properties` 末尾的 `systemProp.http(s).proxy*` 是沙箱专用代理，实际使用时删除。
- `local.properties` 的 `sdk.dir` 指向本机 SDK。

```bash
# 生成 debug APK
./gradlew :app:assembleDebug
# 产物
app/build/outputs/apk/debug/app-debug.apk
```

直接安装到手机/模拟器：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> 本沙箱缺少 `/dev/kvm`，无法运行硬件加速模拟器，因此采用「完整可编译 + APK 打包」作为验证方式：`assembleDebug` 构建成功，`aapt dump badging` 确认包名、应用名与启动 Activity 均正确。

## 已知说明

- 默认 aria2 接入方式为连接外部 JSON-RPC 服务（不内置 aria2 二进制），这是避免体积膨胀、贴近主流手机版 aria2 客户端的做法。
- 明文 http 仅在部分场景可用，生产建议 RPC 走 https 或内网。