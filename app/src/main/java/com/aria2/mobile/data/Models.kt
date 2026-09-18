package com.aria2.mobile.data

// 新的简化策略下：不再需要 aria2 服务器配置与 aria2c 参数。
// 下载全部由本机 DownloadEngine 直接完成，只保留界面层少量偏好（见 SettingsStore）。