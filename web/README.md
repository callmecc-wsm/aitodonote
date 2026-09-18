# Note Note H5

移动端优先的记录、待办与 AI 思考应用。由 Android v0.5 界面迁移，沿用 Semi 图标，使用独立网页数据层；原 Android 代码不变。

## 使用

通过私有站点入口登录。在记录页写下内容，选择待思考、待行动或仅记录；设置中填写 HTTPS Chat Completions 服务、模型和自己的 API Key。可选 Tavily 搜索。支持手动推进、补充对话、未读进展、分类搜索、完成/恢复、明天再看、到期提醒、安静时段、定期回顾和最近回顾记录。

记录与设置保存在账号隔离的 D1 数据库。密钥使用服务器 NOTE_KEY 的 AES-GCM 加密，不返回浏览器，不进入备份。新记录、原文编辑、补充草稿和待接收分享保存在当前浏览器；提交失败时保留。更换模型服务地址需要重新填写密钥。

安卓导出的 `notenote-backup` v1 JSON 可直接导入；只补入不存在的记录，整批校验，不覆盖已有记录。可导出相同格式。备份不含密钥和草稿。

## 网页运行边界

- 自动回顾和提醒由打开的页面触发；每 30 秒检查一次，重新打开时补检查。浏览器关闭、休眠或系统挂起后不保证执行；没有云端定时器或 Web Push。
- 一轮最多尝试 3 条；同一问题最多连续研究 3 轮，后续研究至少间隔一天。等待补充时暂停。模型异常暂停自动回顾。
- 记录需联网读取和提交；已经打开的页面断网时可继续写本地草稿。此版本不是离线完整应用。
- 通过浏览器菜单添加到主屏幕；通知和分享目标取决于浏览器支持。iOS 添加主屏幕和通知仍需真机验收。
- 单账号记录序列化后限制 1.5 MB；接近容量需导出并整理。尚未做大型资料库分页。
- AI 生成期间编辑、完成、删除记录或更换配置，旧结果不写回。数据库乐观锁保护多页面并发。

## 开发

Node 24，使用项目指定的 pnpm 版本和锁文件。

```sh
pnpm install --frozen-lockfile
pnpm test
pnpm build
pnpm exec playwright install --with-deps chromium webkit
node tests/browser.mjs
```

`tests/ui-server.mjs` 是独立测试服务，使用内存 SQLite 和固定模型响应，绝不导入生产应用。浏览器测试不等于真实服务、真实 iPhone 或线上身份链路验收。

生产入口为 `app/page.tsx`，账号校验位于 `app/api/note/route.ts`；`public/note/` 保留应用交互，`lib/` 是数据和 AI 推进规则。发布由 Sites 处理 `.openai/hosting.json` 的 DB 绑定和 Drizzle 迁移。`NOTE_KEY` 必须通过站点环境变量设置为 32 字节随机值的 Base64，并标记为 secret；不可随意轮换，否则已有密钥需要重填。

图标来自 `@douyinfe/semi-icons@2.103.0`，授权见 `public/note/LICENSE-SEMI.txt`。原始 SVG 和生成器见主仓库 `design-system/` 和 `ci/generate_icons.py`。

## 验证记录

2026-09-18：16 项服务测试通过；Chromium、WebKit 在 390 px 下完成记录、草稿恢复、编辑、模型配置、AI 进展与回复、搜索、完成、备份导入导出流程，并检查 320 px 无横向溢出及桌面布局。证据见 [GitHub Actions](https://github.com/callmecc-wsm/aitodonote/actions/runs/35358409563)。模型与身份为测试夹具；生产登录、真实模型和 iOS 真机仍需实际使用验收。
