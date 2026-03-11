# Frida Manager

一个运行在 Android 手机上的 `frida-server` 管理器。

它负责把这些原本分散的操作放到一个应用里完成：

- 导入或下载多个 `frida-server` 版本
- 根据设备 ABI 过滤可用资源
- 切换当前活动版本
- 启动、停止、重启 `frida-server`
- 查看控制器日志、`stdout`、`stderr`
- 配置默认 host、port、GitHub API 地址

它不负责替代电脑上的 Frida CLI。常见用法是：

1. 手机里用这个应用把 `frida-server` 跑起来
2. 电脑用 `adb` / Frida CLI 连过去

## 项目定位

如果你平时会在 Android 上频繁切换 Frida 版本，这个应用可以替代下面这类手工流程：

- 复制 `frida-server`
- 解压 `.xz`
- `chmod +x`
- `su` 启动
- 杀进程
- 看运行日志

## 运行前提

使用前先确认：

- 设备已 root，且应用能拿到可用 root shell
- Android 版本不低于 8.0（`minSdk = 26`）
- 设备能联网，用于拉取 GitHub Release 或镜像源
- 你准备使用的 `frida-server` 与设备 ABI 匹配

默认配置：

- host: `127.0.0.1`
- port: `27042`

说明：

- 默认监听 `127.0.0.1` 更安全，适合电脑通过 USB + `adb` 连接
- 如果你要走局域网连接，需要改监听地址，这会增加暴露面
- Android 16+ 上存在上游稳定性问题报告；项目会保守拦截 Frida 16.x，并建议优先从 Frida 17+ 开始尝试

## 快速开始

### 手机端首次使用

1. 安装并打开应用。
2. 给应用 root 权限。
3. 进入“版本”页，准备一个 `frida-server`：
   - 点“导入文件”导入本地文件
   - 或点“刷新远程”后从远程列表下载
4. 确认至少有一个版本处于“当前活动”状态。
5. 回到“主页”，点击“启动”。
6. 启动成功后，主页会显示运行状态、PID、host、port、当前版本。

### 电脑通过 USB 连接

```bash
adb devices
adb forward tcp:27042 tcp:27042
frida-ps -H 127.0.0.1:27042
```

后续：

```bash
frida-ps -H 127.0.0.1:27042
frida -H 127.0.0.1:27042 -f com.example.app
frida -H 127.0.0.1:27042 -n com.example.app
```

确认转发状态：

```bash
adb forward --list
```

清理转发：

```bash
adb forward --remove tcp:27042
```

`-U` 可作为快捷方式使用：

```bash
frida-ps -U
frida -U -f com.example.app
```

但在 Android + USB 场景下，`-U` 并不总是比显式 `adb forward` 更稳定或更容易排障。默认监听 `127.0.0.1` 时，优先推荐 `adb forward` + `-H`。

### 电脑通过 Wi-Fi 连接

先在应用“设置”里把默认 host 改成：

- `0.0.0.0`
- 或手机的局域网 IP

然后回到“主页”，重新启动 `frida-server`。



```bash
frida-ps -H 手机IP:27042
```

例如：

```bash
frida-ps -H 192.168.1.23:27042
```

注意：

- 这样会把 Frida 端口暴露到网络
- 只建议在可信局域网里临时使用

### Android 16 兼容性说明

Android 16 上，Frida 运行存在上游稳定性问题报告。可参考：

- https://github.com/frida/frida/issues/3471
- https://github.com/frida/frida/issues/3620

这些 issue 中都提到了 Android 16 设备在运行 `frida-server` 后出现系统异常、重启，甚至重启卡住的问题。

已知信息可以概括为：

- `#3471` 提到 Android 16.0 上运行 Frida 后设备崩溃，并出现 ActivityManager 相关超时
- `#3620` 提到 Pixel 10 / Android 16 上运行 `frida-server` 后重启，且报告中明确提到 Frida 16.x 和部分 17.x 都可能受影响

基于这个上游风险，以及本项目的本地复现结果，当前策略是：

- 在 Android 16+ 上保守拦截 Frida 16.x
- 建议优先从 Frida 17+ 开始尝试
- 但这不代表 Frida 17+ 在所有 Android 16 设备上都绝对稳定

优先避免已知高风险组合导致设备不稳定。


## 本地数据

应用会在私有目录下维护自己的 Frida 文件结构，主要包括：

- 已安装版本
- 运行状态
- 下载缓存
- 日志文件

另外，应用也会尝试把版本备份到外部应用目录，用于后续恢复。

## 构建与安装

### 环境要求

- Android Studio 最新稳定版
- JDK 17
- Android SDK 35

### 本地构建

调试包：

```bash
./gradlew assembleDebug
```

发布包：

```bash
./gradlew assembleRelease
```

## 技术栈

- Kotlin
- Jetpack Compose
- Room
- DataStore
- OkHttp
- kotlinx.serialization
- libsu
