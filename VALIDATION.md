# 构建与验证状态

**已通过的版本**：`c2b92d1c20be05cbddd006a107ab803685523dc9`。验证日期：2026-09-07。

[完整构建与设备测试记录](https://github.com/callmecc-wsm/aitodonote/actions/runs/34119925870)

| 检查 | 结果 |
| --- | --- |
| APK 编译 | 通过，生成 Android 测试安装包 |
| 签名校验 | 通过 |
| Android Lint | 通过，已修复 Android 8.0 导航栏兼容性问题 |
| UI 纯逻辑测试 | 5 项通过 |
| Android 单元测试 | 8 项通过 |
| Android 模拟器测试 | 7 项通过，Android 10 / API 29 |

**设备测试覆盖**：首页实际渲染、从界面创建记录写入 SQLite、设置页进入、原文保留、旧 AI 结果不覆盖新编辑、删除后不被结果重新创建、补充进入下一轮、导入保留本机新内容、Keystore 密钥加密。

**尚未验证**：真实模型与 Tavily 请求需使用有效 API Key 在用户设备连接；各手机厂商的后台省电策略需真机观察。当前云端环境无法读取下载出的模拟器截图，因此没有声称完成截图视觉验收；首页渲染通过实际 WebView 文本断言验证。

构建产物来自上述通过测试的提交。后续只改文档和下载流程时，直接使用同一份已验证 APK，不重新编译或更换签名。

**交付入口**：上述成功构建中的 `NoteNote-Android-APK` 附件。曾尝试创建 Release，GitHub 返回 `Resource not accessible by integration`，因此没有创建 Release；失败的发布流程已移除。
