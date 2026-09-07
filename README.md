# Note Note and ToDo

随手记下来。AI 定期推进值得思考的问题，到时间提醒需要自己行动的事项。

Android 个人测试版。原文保存在本机，支持配置自己的模型接口，可选联网检索。

## 下载与安装

1. 打开本仓库 **Releases**，选择最新的测试版本。
2. 在 Assets 中直接下载 `NoteNote-0.1.0-test.apk`。
3. 将 APK 放到 Android 8.0 及以上设备打开，按系统提示允许当前来源安装。测试版无需上架商店。
4. 首次打开可直接记录。在「设置」连接自己的模型服务；需要推送时允许通知。

当前由 CI 生成临时开发签名。不同构建的签名可能不同；如覆盖安装提示签名冲突，先在旧版导出记录，再卸载旧版、安装新版并导入。私钥不保存在仓库内。

## 文档

- [Note Note and ToDo 产品 Wiki](wiki/Note-Note-and-ToDo.md)
- [构建与验证状态](VALIDATION.md)
- [开发测试签名说明](ci/README.md)

`wiki/` 保存可版本管理的 Wiki 源文档。GitHub 独立 Wiki 需要先在网页创建首页；未初始化时仍可直接阅读上面的产品文档。

## 构建

使用 JDK 17、Gradle 8.11.1、Android SDK 35 与 Build Tools 35.0.0。在 Android Studio 打开根目录，或运行：

```sh
gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。CI 会完成编译、单元测试、Lint、签名验证，并在 Android 模拟器测试持久化和启动。

界面资源：`app/src/main/assets/`。浏览器可预览本地交互；不会模拟 AI 成果，也不会发通知。

## 当前边界

- 真实 AI 需要用户自己的 HTTPS Chat Completions 兼容接口与 API Key。
- 联网检索需要另配 Tavily Key；没有配置时结果明确标注未联网。
- 回顾由手机 WorkManager 执行，系统省电和强制停止会影响执行时间；当前没有云端执行服务。
- AI 只追加进展，完成状态由本人确认；不自动发消息、购买或执行现实事务。
