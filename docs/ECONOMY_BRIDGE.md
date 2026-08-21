# Server Menu 经济桥接（Lightman's Currency 个人 ATM 银行账户）

本文档描述 server-menu 提供的公共经济桥接 API，以及它如何把
**Lightman's Currency（LC）个人 ATM 银行账户**接给 BuildShop 与 StockMarket。

> 本文档已按第二轮审查更新：玩家可见兑换规则为 **1 证券资金 = 1 铜币**；
> 资金操作使用带业务命名空间的内部 opId；转账改为阶段状态机；BuildShop 退款金额强校验。

## 1. 总览

- server-menu 的 `api.economy` 公共 API 不引用任何 LC / 业务 Mod 类；LC 只在
  `server/integration/lc` 的 typed 适配器中被直接引用。
- LC 是**可选依赖**（`neoforge.mods.toml` 中 `type="optional"`、`ordering="AFTER"`、`side="BOTH"`）。
  未安装 LC 时服务器照常运行、桥接保持不可用（fail closed）。
- LC JAR 绝不打包进 server-menu（compileOnly）；构建路径可移植
  （`-PlcJar=` / `-PchineseOracleJar=` / `-PserverModsDir=` property → 工作区相对路径 → 本机服务器目录兜底）。
- 桥接目标是**当前玩家自己的 LC 个人 ATM 银行账户**（`PlayerBankReference.of(player)`），绝不选管理员/团队/其他玩家。

## 2. 账户、货币与兑换规则

- 账户：`PlayerBankReference.of(ServerPlayer).get()` → `IBankAccount`。
- 货币链：`main`；金额 = long 最小单位，1 单位 = 1 LC `main` core value = 1 枚铜币。
- **兑换比例（玩家可见）：1 证券资金 = 1 铜币**。
  - 入金 N 铜币 → 证券显示 +N（内部 +N×100 cents）；
  - 出金请求 R cents → 向上取整到整数铜币 `copper=ceil(R/100)`，证券<b>实际扣</b>
    `copper×100` cents（与到账铜币一致，防止小数出金凭空增发），ATM 到账 `copper` 铜币；
  - 证券内部以分保存（显示 1 = 内部 100 cents）——这是内部存储换算，不是 100:1 经济比例；
  - 换算集中到 `ExchangeRates.copperToSecuritiesCents` / `ExchangeRates.securitiesCentsToCopperCeil`
    （均做 long 溢出检查），禁止在别处散落计算。
- 余额读取：`account.getMoneyStorage().valueOf("lightmanscurrency:coins!main").getCoreValue()`（铜币）。

## 3. 公共 API：`com.tanrunn.servermenu.api.economy`

- `EconomyProvider` / `EconomyBridgeRegistry` / `EconomyBalance` / `EconomyTransactionResult` /
  `EconomyTransactionStatus` / `EconomyInputs`。
- **fail-closed 边界**：注册表对 `isAvailable()/balance()/format()/交易` 全部捕获第三方
  provider 的 `RuntimeException` 与 `LinkageError`（如可选依赖缺失），转换为不可用/provider
  error，绝不让普通业务请求崩溃；但 `VirtualMachineError`（OOM / StackOverflowError）不得吞。
- 安全：只允许服务端玩家与主线程、amount>0、requestId/source/reason 长度上限、溢出安全加减。

## 4. 内部资金操作幂等键（operationId）

`EconomyOperationIds`（Server Menu）/ `OperationIds`（StockMarket）是**唯一**生成资金操作
幂等键的工具（可单测），算法两边一致：

- 指纹 = `SHA-256(domain + provider + source + operationType + 完整原始 requestId + direction)`，
  Base64 URL-safe 无填充（43 字符），前置短业务域前缀，总长 ≤ 64；
- 业务命名空间隔离（同名域或不同 source/操作类型/方向都不会碰撞）：
  - `bs:wd:` BuildShop 扣款；`bs:rf:` BuildShop 退款；
  - `sm:bd:` 银行扣款（入金）；`sm:bc:` 银行入账（出金）；
  - `sm:sd:` 证券扣款（出金）；`sm:sc:` 证券入账（入金）；`sm:rb:` 补偿。
- **原始客户端 requestId 只用于 StockMarket 转账账本查重/审计**，绝不直接作为 LC 或证券
  资金操作幂等键外传。这样 BuildShop 的商店扣款键不可能被 StockMarket 复用而「免费入金」。

## 5. 转账阶段状态机（StockMarket）

`BankTransferRecord` 持久化（玩家附件，上限 256）记录：原始 requestId、方向、phase、status、
三个金额（`requestedSecuritiesCents` / `actualDebitCents` / `copperAmount`）、各内部 opId、
已知银行/证券余额、安全审计消息。

阶段：`PREPARED → SOURCE_DEBITED → DESTINATION_CREDITED → COMPLETED`；失败走
`COMPENSATED / COMPENSATION_FAILED / MANUAL_REVIEW`。服务端先落盘 PREPARED 再动账。

- **不做单边推断**：绝不因为存在证券流水就判定整笔成功；只有持久化阶段 + 账本内 opId 恢复。
- 重放：COMPLETED/DESTINATION_CREDITED 重放成功（不重复动账）；PREPARED 崩溃留档 →
  MANUAL_REVIEW（不动账）；COMPENSATION_FAILED / MANUAL_REVIEW 重放不再动账。
- <b>跨重启恢复（runtimeEpoch，第三轮）</b>：转账记录持久化 providerId /
  operationIdVersion / runtimeEpoch（每进程随机）。SOURCE_DEBITED 恢复按 epoch 分支：
  同 epoch 才用账本内 opId 幂等恢复；<b>跨 epoch 出金绝不自动向 LC deposit</b>（LC 内存
  幂等账本重启清空，无法证明是否已入账）→ MANUAL_REVIEW；跨 epoch 入金仅补证券
  （证券 opId 持久幂等），失败不动银行 → MANUAL_REVIEW。恢复一律使用账本持久化的 opId，
  禁止现场重算替换；provider/version/opId/金额/阶段任一异常 → MANUAL_REVIEW。
- MANUAL_REVIEW / COMPENSATION_FAILED 必须人工审计，服务端 ERROR 日志关键字：
  `[StockMarket] bank transfer COMPENSATION_FAILED|MANUAL_REVIEW` 与
  `[ServerMenu][LC] COMPENSATION_FAILED`。
- LC 不具备跨 Mod 原子事务，崩溃间隙无法完全消除 → 一律 fail closed + 人工审计，
  **不承诺跨硬崩溃 exactly-once**。

## 6. BuildShop 接入（provider id：`server_menu:lc_bank_main`）

- 实现 `LcBuildShopCurrencyProvider`，`BuildingShopApi.registerCurrencyProvider` 注册。
- 本地用<b>完整原始 BuildShop 幂等键</b>（`product|requestId|mode|quantity`，可 >64字符）
  查重；传给 LC 前用 `EconomyOperationIds` 生成 `bs:wd:` / `bs:rf:` opId。
- **退款金额强校验**：必须存在原始成功扣款记录且退款金额与之一致，否则直接失败且<b>不调用 LC</b>；
  重复相同退款幂等；退款后不同金额的请求失败；退款后把「已冲正」墓碑写到扣款 opId 上，
  即使账本被淘汰 LC 侧也拒绝再扣（防免费发货）。
- 管理员把商品 JSON 的 `"currency"` 改为 `"server_menu:lc_bank_main"` 即切到 LC 支付。
  **本轮未切换任何现有商品**（25 个商品仍为 `virtual_coins`，1 个 `items:minecraft:emerald`）。

## 7. 幂等与重启边界（如实声明）

- LC/BuildShop 幂等账本：内存 + 有界（LRU 2048），重启清空；保留期内重放/冲突生效。
- StockMarket 转账账本：持久化（上限 256），保留期内跨重启重放精确一致；不承诺永久防重。
- 畸形 phase/status NBT 一律 fail closed 为 MANUAL_REVIEW（绝不默认成入金成功）。

## 8. LC 未安装时的行为

- 桥接不可用（`UNAVAILABLE`）；`/market` 银行桥接区显示「银行桥接不可用」并禁用按钮；
  BuildShop 商品若误配该货币会因「货币不可用」无法购买；服务器不崩溃。

## 9. 构建注意

- LC 参考 JAR 通过 `compileOnly files(lcReferenceJar())` 进编译、`testImplementation` 进测试；
  绝不 bundling。
- 业务 JAR 解析支持 `-PbuildshopJar/-PstockmarketJar/-PchineseOracleJar`，工作区相对路径，
  以及本机服务器 mods 目录（`-PserverModsDir`）兜底；错误信息列出全部候选与可用 property。
