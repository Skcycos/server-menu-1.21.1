# 服务器服务中心（Server Menu）

Minecraft **NeoForge 1.21.1** 服务器端统一 Pad 菜单模组。手持 Pad 物品或执行 `/servermenu`（别名 `/pad`）打开由 ApricityUI 渲染的服务器服务中心界面；建筑商店、股市、今日黄历三个业务应用均可从统一 Pad 启动，并展示服务端权威的首页摘要。

## 功能特性

- **统一 Pad 界面**：国风 AUI 界面，三张应用卡片展示安装/接入状态（未安装 / 待接入 / 已接入）。
- **服务端权威摘要**：卡片下方最多 3 行的业务摘要（如营业状态与余额、总资产与今日盈亏、今日宜忌预览），由服务端 `MenuService` 连同菜单快照一次性下发，客户端不参与摘要生成。
- **返回 Pad 按钮**：在建筑商店 / 股市 / 黄历页面左上角注入「‹ 返回 Pad」按钮，点击后经服务端重新打开 Pad（完整走 `OpenMenuRequestPayload → MenuSnapshotPayload` 往返，不自行构造快照；股市的 closePanel 由其自身 `removed()` 正常发出）。
- **严格页面识别**：只按 AUI `Document.getPath()` 精确路径白名单识别业务页面，不依赖业务页面类名，不对 Pad 自身或其它 AUI 页面注入。
- **视觉**：Pad 外部半透明帷幔背景（世界透出）；内置思源黑体 Bold（`servermenu-ui`）字体，与业务页面字体统一。

## 环境要求

| 项 | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248（loader ≥ [1,)） |
| Java | 21 |
| ApricityUI (neoforge-1.21.1) | **1.2.3.1**（客户端必需；mods.toml 中声明为 CLIENT 依赖） |
| buildshop / stockmarket / chinese_oracle | 可选（≥1.0.0，需含 `openPanel`/`openAlmanac` 启动链路与 `summary` 摘要 API） |

> 三个业务 Mod 均为**可选依赖**：未安装时 Pad 正常打开，对应卡片显示「未安装」；专用服务器无需 ApricityUI（CLIENT 依赖在服务端被忽略）。

## 构建

```bash
# 完整构建（含 153 个单元测试）
./gradlew clean test build

# 专用服务端构建（ApricityUI 降级为 compileOnly，不进运行时）
./gradlew clean build -PserverOnly

# 无业务 Mod 的专用服务端冒烟验证（应到达 DedicatedServer Done，三应用 installed=false connected=false summary=false）
timeout 45s ./gradlew runServer -PserverOnly --no-daemon
```

构建产物：`build/libs/server_menu-1.0.3.jar`。

## 部署

**服务端**：将 `server_menu-1.0.3.jar` 放入 `mods/`；ApricityUI 为 CLIENT 依赖可省略；业务 Mod 可选，装齐后启动日志会打印：

```
[ServerMenu] app build_shop installed=true connected=true summary=true
[ServerMenu] app stock_market installed=true connected=true summary=true
[ServerMenu] app chinese_oracle installed=true connected=true summary=true
```

**客户端**：`server_menu-1.0.3.jar` + `ApricityUI-neoforge-1.21.1-1.2.3.1.jar` + 三个业务 Mod 的 JAR 一并放入 mods。

## 使用

- **打开 Pad**：手持 Pad 物品右键，或指令 `/servermenu`、`/pad`。
- **启动应用**：点击卡片。业务页面打开后左上角出现「返回 Pad」（600ms 本地防抖；1 秒未返回自动恢复可重试）。
- **回 Pad**：点击「‹ 返回 Pad」。
- **AUI 热重载**：客户端按 END 重载全部 AUI 文档，摘要与返回按钮会自动重新注入且只保留一份。

## 网络协议（`ServerMenuNetwork`）

- `MenuSnapshotPayload`（S2C）：应用状态 + 摘要行（每应用最多 3 行、每行 ≤96 字符）。
- `OpenMenuRequestPayload`（C2S）：打开/返回 Pad 请求（无字段）。
- `LaunchAppRequestPayload`（C2S）：点击应用卡片启动请求。
- `MenuFeedbackPayload`（S2C）：Pad 状态栏反馈。

解码侧对非法集合长度（摘要行数、应用数量越界或负数）**立即抛 `DecoderException`**，由连接层丢弃该包，绝不按 0 处理或按任意输入循环；合法报文字段顺序与 packet ID 稳定。

## 兼容性语义

- `connected` 只代表**业务启动链路**（ModList 安装 + 官方公开 API 结构探测）可用。
- 摘要能力使用**独立的**描述符探测与缓存：摘要 API 缺失、探测失败或生成失败，只让卡片区显示「摘要暂不可用」，**绝不**影响 connected、绝不影响应用启动、绝不触发启动兼容性降级。
- 摘要随菜单快照一次下发，无额外网络往返；客户端不能提交摘要内容。

## 代码结构

```
com.tanrunn.servermenu
├── client/                 # 客户端：PadScreen、BusinessScreenNavigator（返回 Pad 注入）
│   └── navigation/         # BusinessPage 路径白名单 + NavigationState 状态机
├── common/
│   ├── menu/MenuApp        # 内置应用白名单枚举
│   └── network/            # 网络 payload 定义与注册
└── server/
    ├── MenuService         # 服务端权威快照 / 冷却（open/launch 双冷却）
    ├── integration/        # 启动链路（AppLauncherRegistry）与启动适配器
    │   └── summary/        # 摘要注册表/描述符/适配器（AppSummaryRegistry 等）
    └── registry/ hook/     # Pad 物品与交互钩子
```

## 已知限制

- 客户端视觉表现（三卡片等高、560px 以下纵向布局、AUI 热重载后的重放）建议真机人工确认；专用服务器验证以启动日志三态为准。
- 摘要与返回按钮的完整端到端行为依赖 AUI 上游客户端 API，请保持 ApricityUI ≥ 1.2.3.1。
