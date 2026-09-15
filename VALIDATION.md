# 构建与验证状态

## v0.3.0

验证日期：2026-09-15。交付源码：[d11972f](https://github.com/callmecc-wsm/aitodonote/commit/d11972f47d92bf6d3de6ccbd139db6a4fa77f9b8)。[最终构建](https://github.com/callmecc-wsm/aitodonote/actions/runs/34955111008)全部通过；后续文档提交不改变这份 APK 的源码。

| 验证 | 结果 |
| --- | --- |
| JavaScript | 8 项通过 |
| JVM 单元测试 | 10 项通过 |
| Android 8.0 / API 26 | 59 项通过，失败、错误、跳过均为 0 |
| Android 10 / API 29 | 59 项通过，失败、错误、跳过均为 0 |
| Android 15 / API 35 | 59 项通过，失败、错误、跳过均为 0 |
| 构建与 Lint | 通过 |
| APK 签名 | v2 开发测试签名校验通过 |

已下载三版设备的 JUnit XML 并核对上述计数。设备测试从同一源码分别构建安装；不将它们描述为与下载附件逐字节相同的 APK。

## 安装包

[下载 Actions 附件](https://github.com/callmecc-wsm/aitodonote/actions/runs/34955111008/artifacts/10389894518)，解压取得 `NoteNote-0.3.0-test.apk`。APK 为 2,478,251 字节，附件保留至 2026-12-14。已核对下载文件、附件内校验文件与构建日志中的 SHA-256 一致：

```text
43375d1b1570f721a844865adf475a0c4187a36533a394e3303562fac6ba4b29
```

## 新增验收

| 场景 | 检查内容 |
| --- | --- |
| 编辑草稿 | 重建后重新打开编辑页，原文在点保存之前不变 |
| 补充草稿 | 两条记录分别恢复，发送后只清除当前记录的草稿 |
| 原生分享 | 原草稿不被覆盖，重建不重复接收，移入输入框前不生成记录 |
| 通知 | 实际 PendingIntent 打开对应补充框，其他记录草稿保留 |
| 锁屏 | 最新摘要出现在进展通知，公开版本不包含原文和摘要 |
| 长回复 | 后台刷新保留阅读位置 |
| 备份 | 草稿不进入导出，空库恢复保持顺序，本机已有记录保留 |
| 异常输入 | 超长草稿不能替换已有草稿，未知字段不存储 |

保留 v0.2.0 的调度、模型协议、HTTPS、来源过滤、数据并发、通知与备份测试。

## 界面走查

截图来自 Android 模拟器中的实际应用窗口，由 UiAutomation 捕获，内容为测试样例。已检查三版安卓首页，以及 Android 15 的下列操作画面；通知流程另外检查了 Android 10 截图。

| 步骤 | 截图文件 | 检查结果 |
| --- | --- | --- |
| 1. 冷启动首页 | `00-home.png` | 三版安卓均有完整页面内容；旧 WebView 的品牌、按钮间距较紧，布局与新版不完全一致 |
| 2. 重建后继续记录 | `01-capture-restored.png` | 原文字、待思考类型及搜索开关保留 |
| 3. 重建后继续编辑 | `02-edit-restored.png` | 显示恢复提示、未提交文字、保存与放弃操作 |
| 4. 回到另一条补充 | `03-reply-restored.png` | 对应问题恢复自己的补充文字 |
| 5. 分享时已有草稿 | `04-share-preserves-draft.png` | 原草稿保留，待接收分享单独展示；接收操作需向下滚动 |
| 6. 通知「接着聊」 | `05-notification-reply.png` | 三版设备的目标记录及焦点断言通过；Android 10 截图显示目标问题。Android 15 此图仍捕获到切换前的画面，排除为视觉通过证据 |

证据附件：[Android 8](https://github.com/callmecc-wsm/aitodonote/actions/runs/34955111008/artifacts/10391077423)、[Android 10](https://github.com/callmecc-wsm/aitodonote/actions/runs/34955111008/artifacts/10390604502)、[Android 15](https://github.com/callmecc-wsm/aitodonote/actions/runs/34955111008/artifacts/10390208924)。每份包含 6 张截图及测试报告。

本次走查确认主要文字、草稿恢复状态和操作入口可见。未做 TalkBack、超大字体、全部键盘和厂商机型的完整验收。绘制回调与非空白像素检查能拦截空白截图，仍不能单凭它们证明截图已经呈现最新页面；通知跳转截图的限制如上。

## 验证方式与边界

模型语义测试使用固定响应；HTTPS 测试连接模拟器本地 TLS 服务。测试验证协议、数据流和应用行为，**不代表真实模型推理或 Tavily 检索效果已验收**。真实服务需要用户自己的有效 API Key。

已验证 Activity 重建和持久化，不将其描述为完整断电恢复测试。尚未验证真实手机厂商的后台省电策略、长期无人值守运行或手机重启后的实际调度时刻；后台回顾不保证准点。

## 本轮修正

- [首次构建](https://github.com/callmecc-wsm/aitodonote/actions/runs/34952661701)：编译、Lint、签名、8 项 JavaScript 和 10 项 JVM 测试通过。API 26、35 各运行 58 项，其中通知测试在清理页面时因 ActivityScenario 无法匹配被更新的 Intent 失败。通知跳转的功能断言已经通过；改为由测试自身清理这个 Activity，保留实际 PendingIntent 和焦点验证。
- API 29 在第 17 项启动测试中发生 WebView 74 GPU 字体渲染崩溃。CI 从已弃用的 `swiftshader_indirect` 改为 `swiftshader`，没有关闭应用硬件加速或删除测试。模拟器渲染选项见 [Android 官方说明](https://developer.android.com/studio/run/emulator-acceleration)。
- UTP 测试结束后会卸载 APK，导致应用目录内截图被清除。截图在用例内复制到模拟器 Download 目录后再提取；启动页也在实际测试中捕获。

- [第二轮](https://github.com/callmecc-wsm/aitodonote/actions/runs/34953462651)：API 26、35 各 59 项通过；API 29 仍在旧 WebView GPU 字体绘制中崩溃。应用为 Chromium 74 及更早版本使用软件绘制层，现代 WebView 保持默认渲染。
- 第二轮截图中存在空白和旧帧，不能作为视觉验收证据。新增 WebView 绘制完成回调、显示帧等待和非空白像素检查；这与只验证 DOM 的用例分别验收。回调依据见 [Android VisualStateCallback](https://developer.android.com/reference/android/webkit/WebView.VisualStateCallback)。
- 将系统栏和键盘留白施加到 WebView 的父容器，使页面可用高度跟随实际窗口变化。

- [画面检查](https://github.com/callmecc-wsm/aitodonote/actions/runs/34954487129)：API 26、29 各 59 项通过；API 35 的 5 个操作流程截图通过，冷启动截图仍为空白，被新增断言拦截。截图采集改为最多等待 5 秒，实际画面未出现则失败。

## 历史构建

| 版本 | 验证 |
| --- | --- |
| [v0.2.0](https://github.com/callmecc-wsm/aitodonote/actions/runs/34857149768) | 8 项 JavaScript、10 项 JVM，API 26、29、35 各 48 项设备测试通过 |
| [v0.1.0](https://github.com/callmecc-wsm/aitodonote/actions/runs/34119925870) | 5 项 JavaScript、8 项 JVM，API 29 共 7 项设备测试通过 |

APK 通过 Actions 附件交付。独立 GitHub Release 因集成权限限制未创建。
