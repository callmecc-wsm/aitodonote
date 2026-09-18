# v0.5：采用 Semi 图标与 Universe 主题基础值

本轮解决图标装饰过多、选型不一致的问题。使用有来源、可升级的图标与主题文件，减少重复装饰，把阅读空间留给记录内容。

## 资源选择

| 资源 | 核实结果 | 本次使用 |
| --- | --- | --- |
| [Semi Design](https://semi.design/zh-CN/start/introduction) | 由抖音前端与 MED 设计团队维护；公开组件主要面向 Web 中后台，官方介绍列有飞书示例主题 | 沿用其设计资产，不声称接入飞书原生移动端组件 |
| [Semi Icons](https://semi.design/zh-CN/basic/icon) | 官方提供线性、面性和 AI 图标，支持继承颜色 | 固定 `@douyinfe/semi-icons@2.103.0`，31 个语义映射，实际使用线性/面性图标 |
| [Universe Design 主题包](https://www.npmjs.com/package/@semi-bot/semi-theme-universedesign) | Semi DSM 发布的公开主题，当前 npm 版本为 `1.0.13`，MIT | 提取蓝色、中性色和 4/6/8px 圆角；不将旧主题包当作飞书 2026 原生界面的完整规范 |
| [移动端讨论](https://github.com/DouyinFE/semi-design/discussions/287) | 维护者在 2023 年说明，内部移动端库与 App 宿主耦合，不适合直接开放复用 | 仅作为历史背景。当前公开介绍仍以 Web 为主；没有把旧答复当作 2026 年的新承诺 |

Universe Design 主题与 Semi 组件库不是同一个概念。这里交付的是「Semi 官方图标 + 公开 Universe 主题基础值 + 本应用移动端布局」，不是整套 Semi React 组件，也不是飞书原生客户端的复制品。

通过浏览器检查了 Semi 官方图标列表和线性图标画面；同时读取 npm 发布包中的原始 SVG、主题 SCSS、版本及授权信息。

## 具体变化

- **导航**：记录使用书本，AI 进展使用对话，设置使用齿轮；未选中为线性，选中为同套库的面性版本。
- **内容**：移除卡片类型、最新摘要和 AI 事件标题旁的重复图标。AI 不再由星星、闪光装饰反复标识。
- **动作**：搜索、提醒、发送、返回、删除、备份、模型服务统一使用 Semi 图标。桌面入口与通知也使用同源书本形状。
- **样式**：采用主题蓝 `#3370FF`、正文 `#1F2329`、次级文字 `#646A73`、背景 `#F5F6F7`；卡片 8px 圆角，常规按钮 6px。主要操作维持 44px 点击尺寸，未照搬桌面组件的小控件高度。
- **绘制**：保留官方 SVG 路径和 viewBox，仅将黑色改为继承文字颜色。取消旧的全局描边，避免给本身已转换为轮廓的图标再次加粗。

## 后续维护

| 文件 | 用途 |
| --- | --- |
| `design-system/icons.json` | 语义名、官方 SVG 名称和固定版本 |
| `design-system/semi-icons/` | 未修改的官方 SVG 原件 |
| `ci/generate_icons.py` | 将原件生成离线图标资源；`--check` 检查同步状态 |
| `app/src/main/assets/icons.js` | APK 内置图标，无 CDN 或网络依赖 |
| `app/src/main/assets/design-tokens.css` | 主题基础值与本应用语义变量 |
| `LICENSE-SEMI` | 完整上游授权；APK 中也打包授权文本 |

此前的 `LICENSE-LUCIDE` 保留用于历史版本归属；v0.5 运行时不再使用 Lucide。

## 视觉验收

以真实 Android 模拟器 APK 截图验收：首页、进展、详情、设置和系统键盘场景。自动检查图标资源来源、选中态和无额外描边；肉眼检查清晰度、数量、颜色、留白及操作位置。具体结果见 [VALIDATION.md](VALIDATION.md)。

功能和数据格式保持 v0.4 的行为。本轮没有增加深色主题，也未完成所有厂商手机、超大字体和第三方输入法的完整验收。

此前 flomo、滴答清单参考和 v0.4 布局说明保存在 [v0.4 设计文档](https://github.com/callmecc-wsm/aitodonote/blob/e743dc6c8a78f3002ce640db344295afd960b065/DESIGN.md)。
