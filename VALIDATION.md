# 构建与验证状态

## v0.2.0：全部自动化验收通过

验证日期：2026-09-14。源码提交：`54f08f4ecc4c04114559dd9430dc2e616d29a393`。

[完整构建与设备测试](https://github.com/callmecc-wsm/aitodonote/actions/runs/34857149768)

| 检查 | 结果 |
| --- | --- |
| APK 编译、Android Lint | 通过 |
| APK v2 签名校验 | 通过 |
| JavaScript 逻辑测试 | 8 项通过 |
| Android JVM 单元测试 | 10 项通过 |
| Android 8 / API 26 | 48 项设备测试通过，0 失败、0 跳过 |
| Android 10 / API 29 | 48 项设备测试通过，0 失败、0 跳过 |
| Android 15 / API 35 | 48 项设备测试通过，0 失败、0 跳过 |

## 安装包

[下载 NoteNote-Android-APK](https://github.com/callmecc-wsm/aitodonote/actions/runs/34857149768/artifacts/10352809162)。登录 GitHub 后下载并解压，内含 `NoteNote-0.2.0-test.apk` 和校验文件。附件约 2.4 MB，保留至 2026-12-13。

APK SHA-256：

```text
667a660401e55dbda7186182a82b0947b6ff85c20856a3206a7303b4e2add379
```

安装包与设备测试来自同一源码提交。文档更新不会重新编译或更换签名。CI 临时开发签名可能与旧版不同；升级冲突时，先导出备份，再卸载旧版、安装新版并导入，密钥重新填写。

## 设备测试覆盖

- SQLite 原文与事件保留、备份合并、导入事务回滚、更新顺序与草稿存储。
- 连续研究间隔和 3 轮上限；等用户回复；筛除现实行动和仅记录；失败停止；编辑或取消时丢弃旧结果；每轮最多 3 次尝试。
- 真实 Android 通知、独立快捷操作、重复完成、旧通知失效、关闭或安静时段后的补发、未读并发保护。
- 模型请求结构、单条搜索开关、历史上下文、来源过滤、错误格式处理、接口与密钥快照。
- 真实本地 HTTPS：中文 JSON、Authorization、拒绝重定向、401/403/404/429/500、超大响应限制。
- 底层传输参数异常即使包含请求头，也不会把其中的测试密钥写进错误记录或备份。
- WorkManager 实际 Worker：无模型也能提醒、关闭自动回顾、错误暂停、按记录排队、启动失败重试上限。
- 实际 WebView：首页、设置、原生记录写入、草稿在 Activity 重建后恢复、未读状态、不执行 HTML、详情弹层在视口内正确铺开。

设备镜像使用的 WebView：API 26 为 Chrome 69，API 29 为 WebView 74，API 35 为 WebView 124。API 26 使用官方 Google APIs 镜像以提供系统 WebView；应用本身不调用 Google Play 服务。

CI 附件保留测试报告、JUnit XML、系统 WebView 信息、运行错误日志和模拟器截图。

## 验证方式与边界

模型语义测试采用注入的固定响应；HTTPS 测试连接模拟器本地 TLS 服务。它们验证协议、数据流和应用行为，**不代表真实模型推理或 Tavily 检索效果已验收**。真实服务需要用户自己的有效 API Key。

尚未验证真实手机厂商的后台省电策略、长时间无人值守运行、手机重启后的实际调度时刻；后台回顾不保证准点。已测试 Activity 重建与持久化，不将其描述为完整的断电恢复测试。

当前环境无法查看下载出的模拟器截图。界面验收依据是实际 WebView 文本、行为和几何断言，没有声称完成截图视觉验收。

## 本轮修正记录

- [首轮](https://github.com/callmecc-wsm/aitodonote/actions/runs/34854041569)：API 29 有 1 项失败，API 35 有 2 项失败；修正通知异步可见性的等待与 WebView 字符串断言。
- [第二轮](https://github.com/callmecc-wsm/aitodonote/actions/runs/34854929380)：API 29、35 各 47 项通过；已加入队列保留、旧页面不能标记新结果已读、有界启动重试。
- [最低版本检查](https://github.com/callmecc-wsm/aitodonote/actions/runs/34855505373)：发现 API 26 默认镜像没有安装 WebView，导致测试进程崩溃；更换成含 WebView 的官方镜像，保留全部测试。
- [兼容性复测](https://github.com/callmecc-wsm/aitodonote/actions/runs/34856332187)：API 26、29、35 各 47 项通过，包含旧 WebView 弹层布局检查。
- 最终构建进一步加入传输错误不泄露请求头的回归测试，三版安卓各 48 项通过。

## 历史 v0.1.0

[2026-09-07 构建](https://github.com/callmecc-wsm/aitodonote/actions/runs/34119925870)：5 项 UI 逻辑、8 项 JVM、7 项 API 29 设备测试通过。

交付使用 Actions 附件。先前 Release 发布受到 GitHub 集成权限限制，未创建 Release。
