# 构建与验证状态

## v0.5.0

验证日期：2026-09-18。交付源码：[bc7e3bf](https://github.com/callmecc-wsm/aitodonote/commit/bc7e3bfa8b56cb3fba1555d0186fc150e357db76)。[最终构建](https://github.com/callmecc-wsm/aitodonote/actions/runs/35340759271)及三版 Android 设备测试均已通过。

| 验证 | 结果 |
| --- | --- |
| JavaScript | 8 项通过 |
| JVM 单元测试 | 10 项通过 |
| Android 8.0 / API 26 | 62 项通过 |
| Android 10 / API 29 | 62 项通过 |
| Android 15 / API 35 | 62 项通过（同源码重跑） |
| 构建与 Lint | 通过 |
| APK 签名 | v2 开发测试签名校验通过 |

设备测试从同一源码分别构建安装，不将它们描述为与下载附件逐字节相同的 APK。后续文档提交不改变 APK 源码。

## 安装包

[下载 Actions 附件](https://github.com/callmecc-wsm/aitodonote/actions/runs/35340759271/artifacts/10544946945)，解压取得 `NoteNote-0.5.0-test.apk`。APK 为 2,493,375 字节，附件保留至 2026-12-17。下载文件、附件内校验文件、构建日志的 SHA-256 一致：

```text
6680ca4dc7fe7f789142d27a32c1f575c358b6f6c962c517c8231fff893951d5
```

## 界面走查

截图来自 Android 模拟器中的实际应用窗口，由 UiAutomation 捕获。记录和 AI 回复为测试样例，不是线上模型生成结果，也不会预装在正式测试 APK 中。参考应用研究范围见 [DESIGN.md](DESIGN.md)。

| 步骤 | 截图 | 检查内容 |
| --- | --- | --- |
| 1. 冷启动 | `00-home.png` | 空状态、示例入口、底部输入与三项导航 |
| 2. 恢复输入 | `01-capture-restored.png` | 原文、待思考类型、关闭搜索状态保留 |
| 3. 恢复编辑 | `02-edit-restored.png` | 整页编辑、草稿提示、保存与恢复原文 |
| 4. 恢复补充 | `03-reply-restored.png` | 各条记录的补充草稿独立，底部继续写 |
| 5. 接收分享 | `04-share-preserves-draft.png` | 原草稿和待接收内容分开呈现 |
| 6. 通知继续聊 | `05-notification-reply.png` | 跳到对应记录和补充入口；排除旧帧 |
| 7. 日常记录 | `06-inbox-with-records.png` | 三类记录、原文、状态、时间、最新进展层级 |
| 8. AI 进展 | `07-progress.png` | 原问题、摘要、正文、追问、来源状态与查看入口 |
| 9. 阅读详情 | `08-record-detail.png` | 长正文滚动、固定返回栏、底部补充入口 |
| 10. 设置首页 | `09-settings.png` | 分组列表与当前状态；默认收起详细字段 |
| 11. 配置模型 | `10-model-settings.png` | 展开字段、保存与测试连接；页面可滚动 |
| 12. 行动详情 | `11-action-detail.png` | 提醒时间、完成与延后操作，不显示 AI 补充框 |
| 13. 搜索 | `12-search.png` | 结果与即时数量一致，无横向溢出 |
| 14. 键盘输入 | `13-capture-keyboard.png` | 真实触摸唤起系统键盘，输入与发送按钮仍可见 |

设备报告与 14 张流程截图：[API 26](https://github.com/callmecc-wsm/aitodonote/actions/runs/35340759271/artifacts/10544922548)、[API 29](https://github.com/callmecc-wsm/aitodonote/actions/runs/35340759271/artifacts/10544488603)、[API 35 最终重跑](https://github.com/callmecc-wsm/aitodonote/actions/runs/35340759271/artifacts/10545362549)。三份 XML 均为 62 项、0 失败、0 错误、0 跳过。

首次 API 35 运行有 1 项触摸测试失败。日志显示输入被系统桌面 `com.android.launcher3` 的 ANR 弹窗截获，截图中也出现 Quickstep 无响应提示。保留源码和测试不变，仅重跑失败任务后通过；首轮被遮挡的截图不作为视觉验收依据。最终 API 35 截图已确认首页、设置和键盘场景没有该弹窗。

API 29 的键盘截图捕获了已缩小的应用窗口，但键盘按键尚未绘出；键盘外观与遮挡检查以最终 API 35 的完整键盘截图为依据。三版触摸测试均验证视口缩小、输入区展开以及发送按钮位于可见区域。

## 回归范围

保留记录持久化、密钥存储、调度预算、修订并发、模型协议、HTTPS、来源过滤、通知动作与隐私、备份恢复、草稿与分享测试。继续覆盖带内容的页面导航与搜索、展开收起草稿、真实触摸和系统键盘场景。

本轮保留全部 62 项设备测试，在现有页面流程内补充：所有界面图标来自 Semi、导航选中为官方面性图标、SVG 没有额外描边、卡片装饰图标减少。另由构建检查确认 31 个语义映射与已提交的官方 SVG 原件一致。

## 验证边界

- 已验证模拟器上的 Activity 重建与持久化，不等同于断电恢复或所有真实手机通过。
- 模型语义测试使用固定响应；HTTPS 测试使用模拟器内 TLS 服务。真实模型与 Tavily 需要自己的 API Key，本轮未验收真实服务的推理和检索效果。
- 视觉检查覆盖上述主要流程。未完成 TalkBack、超大字体、所有第三方输入法、厂商后台策略、长期无人值守与真实重启调度验收。
- 系统省电、断网、关机或强制停止会影响后台回顾时间，不保证准点。
- 开发测试包使用临时签名。升级可能需要先备份再卸载；草稿和待接收分享需先保存为记录，密钥需重新配置。

## 历史记录

| 版本 | 验证 |
| --- | --- |
| [v0.4.0 验证文档](https://github.com/callmecc-wsm/aitodonote/blob/e743dc6c8a78f3002ce640db344295afd960b065/VALIDATION.md) | 8 项 JavaScript、10 项 JVM，三版 Android 各 62 项设备测试通过 |
| [v0.3.0 验证文档](https://github.com/callmecc-wsm/aitodonote/blob/4279dc41203f3ce3a8ef2c13d26c562b79518c57/VALIDATION.md) | 8 项 JavaScript、10 项 JVM，API 26、29、35 各 59 项设备测试通过；旧通知截图限制保留在历史文档 |
| [v0.2.0](https://github.com/callmecc-wsm/aitodonote/actions/runs/34857149768) | 8 项 JavaScript、10 项 JVM，三版 Android 各 48 项设备测试通过 |
| [v0.1.0](https://github.com/callmecc-wsm/aitodonote/actions/runs/34119925870) | 5 项 JavaScript、8 项 JVM，API 29 共 7 项设备测试通过 |

APK 通过 Actions 附件交付。独立 GitHub Release 因集成权限限制未创建。
