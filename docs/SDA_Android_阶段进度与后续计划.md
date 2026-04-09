# SDA Android 阶段进度与后续计划

## 1. 文档定位

- 本文档记录截至 `2026-04-09` 的 SDA Android 转向开发进度。
- 本文档只聚焦 Steam 协议登录、手机令牌绑定、会话管理、动态码与 confirmations 主链路。
- 架构目标仍以 `docs/SDA_Android_转向实施蓝图.md` 为准。
- 全项目级执行顺序仍以 `docs/ANDROID_EXECUTION_CHECKLIST_P2_P4.md` 为准。
- 本文档不是新的总路线图，而是对 SDA 转向主线的阶段性落地审计与后续开发建议。

---

## 2. 目标链路

当前这条主线要对齐 SDA，而不是继续堆网页登录采集能力。

目标闭环是：

1. 协议级登录 Steam
2. 处理邮箱验证码 / 设备验证码 / 设备确认挑战
3. 获取 `SteamID / AccessToken / RefreshToken`
4. 调用 `AddAuthenticator / FinalizeAddAuthenticator`
5. 在本地保存 Android 端的 `maFile` 等价模型
6. 生成 Steam Guard 动态码
7. 拉取并批准 / 拒绝交易与市场确认
8. 管理会话刷新、过期修复与本地加密持久化

---

## 3. 当前阶段结论

按 `SDA_Android_转向实施蓝图` 里的 A / B / C / D 四个阶段看，当前状态可以概括为：

| 阶段 | 目标 | 当前状态 | 结论 |
|---|---|---|---|
| A | 统一核心状态模型 | 已补统一快照、扩展会话记录、协议登录模型 | **基本完成** |
| B | 协议级登录 | 协议登录引擎已实现，依赖注入已接入，但还未成为新增令牌主 UI 入口 | **后端完成，入口未切换** |
| C | 协议级绑定 | `AddAuthenticator / FinalizeAddAuthenticator` API 与页面已落地，但仍依赖浏览器 / 手工会话材料 | **部分完成** |
| D | 统一 confirmations / 刷新 / 令牌详情 | TOTP、时间同步、确认请求基础已具备，但仍偏 Web Session 驱动 | **部分完成** |

一句话总结：

> 当前项目已经从“只有网页登录采集工具”推进到了“模型统一 + 协议登录引擎可用 + 绑定 API / 页面已存在”的阶段，但新增手机令牌主链路还没有真正完成从浏览器材料驱动到协议登录结果驱动的切换。

---

## 4. 已经落地的能力

### 4.1 统一状态模型已基本成型

当前已经有了与 SDA `SteamGuardAccount + SessionData` 更接近的 Android 侧中枢模型：

- `SteamGuardAccountSnapshot`
  - 统一承载 authenticator secrets 与 session
  - 可以表达“有协议会话”和“有网页确认会话”两类状态
- `SteamSessionRecord`
  - 已扩展出 `steamId`
  - 已扩展出 `accessToken / refreshToken / guardData`
  - 已扩展出 `sessionId / cookies / oauthToken / platform`
- `SteamMobileSession`
  - 作为协议登录与刷新后的结构化会话对象
  - 可以与 `SteamSessionRecord` 双向转换
- `SteamProtocolLoginRequest / Result / Challenge`
  - 已能表达 `INITIAL / REFRESH / IMPORT`
  - 已能表达邮箱码、设备码、设备确认三类挑战
  - 已预留 `guardData / newGuardData`

这意味着当前仓库已经不再只有“网页 Cookie 会话”这一条状态表达方式，已经有条件承接 SDA 风格的移动端协议会话。

### 4.2 协议级登录引擎已经落地

当前 Android 端已经实现了协议级登录的核心链路：

- `GetPasswordRSAPublicKey`
- RSA 公钥加密密码
- `BeginAuthSessionViaCredentials`
- 登录挑战排序与处理
- `UpdateAuthSessionWithSteamGuardCode`
- `PollAuthSessionStatus`
- `GenerateAccessTokenForApp`
- 移动端会话 cookie 生成

具体来说，当前实现已经覆盖了：

- `OkHttpSteamWebApiClient`
  - 封装认证相关 WebAPI 调用
- `SteamAuthProtobufCodec`
  - 封装认证 protobuf 编解码
- `OkHttpSteamProtocolLoginRepository`
  - 负责完整认证状态机
  - 负责轮询、验证码提交和 access token 补发
  - 负责构造移动端 cookie
- `SteamProtocolLoginOrchestrator`
  - 负责已知 `guardData` 复用
  - 负责 `newGuardData` 持久化
  - 负责协议会话入库前的统一整理

目前这一层已经具备“像 SteamKit / SDA 那样直接拿到 `SteamID / AccessToken / RefreshToken`”的能力。

### 4.3 绑定 API 与绑定页面已经存在

当前仓库已经不是“还没有绑定实现”，而是已经有绑定能力的第一版：

- 已有 `SteamAuthenticatorBindingApiClient`
- 已有 `OkHttpSteamAuthenticatorBindingApiClient`
- 已有 `SteamAuthenticatorBindingScreen`
- 已有绑定进度草稿持久化
- 已有激活码输入与 finalize 重试承接
- 已有绑定成功后写回 Vault 与 SessionRepository 的保存逻辑

这说明 `AddAuthenticator / FinalizeAddAuthenticator` 这条线已经开始落地，不再只是停留在文档层面。

### 4.4 动态码、时间同步、confirmations 基础已经具备

当前与 SDA `SteamGuardAccount` / `SessionData` 接近的基础能力包括：

- `LocalVaultCryptography.generateSteamGuardCode(...)`
  - 已实现 Steam Guard 动态码算法
- `DefaultSteamTimeSyncManager`
  - 已有 Steam 时间同步基础
- `DefaultSteamConfirmationSyncManager`
  - 已能生成 confirmation key
  - 已能拉取 confirmations
  - 已能批准 / 拒绝确认

这意味着确认系统并不是从零开始，真正缺的是“上下文来源统一”和“会话模型彻底切换”。

### 4.5 浏览器 / 手工链路仍然保留

当前项目依然保留了较多网页登录与手工补材料能力，包括：

- 外部浏览器登录回跳
- 内置 WebView / 会话抓取
- 手工录入 `steamId / sessionId / oauthToken / cookies`
- 绑定前置校验依赖 `sessionid / steamLoginSecure / steamLogin`

这些能力作为导入、兼容、修复、调试工具仍然有价值，但不应再继续承担主链路职责。

---

## 5. 当前还没有完成的关键切换

### 5.1 协议级登录还没有成为新增令牌主入口

虽然协议登录仓储与 orchestrator 已经落地，但当前新增令牌导航主链路仍然是：

`Import -> SteamAddAuthenticatorScreen -> SteamAuthenticatorBindingScreen`

其中 `SteamAddAuthenticatorScreen` 仍然以：

- 外部浏览器打开 Steam 登录页
- 接收回跳 URL
- 手工补录会话材料
- 保存 `EnrollmentDraft`

作为主交互方式。

这说明 B 阶段虽然“实现完成”，但还没有在产品入口层真正落地。

### 5.2 绑定仍然依赖浏览器 / 手工材料，不是协议登录结果

当前 `SteamAuthenticatorBindingPreparationFactory` 的输入仍然来自 `SteamAuthenticatorEnrollmentDraft`，核心依赖仍然是：

- `sessionid` cookie
- `steamLoginSecure` cookie
- `steamLogin` cookie
- `oauthToken`
- `webApiKey`

这意味着绑定页虽然已经能发 API，但它消费的不是结构化的协议登录结果对象，而是旧链路采集来的材料。

这也是当前距离 SDA 主链路仍有一段距离的根本原因。

### 5.3 confirmations 仍然偏 Web Session 驱动

当前确认系统在取数前仍要求：

- `identity_secret`
- `device_id`
- `sessionid` cookie
- `steamLoginSecure` 或 `steamLogin` cookie

也就是说，现在虽然协议登录已经能生成移动端 cookie，但确认系统还没有被明确收口成“统一从结构化会话快照取上下文”的模式。

### 5.4 令牌详情与会话详情还没有完全统一到快照模型

当前快照模型已经出现，但还没有完成以下切换：

- 令牌详情直接以统一快照为数据源
- 会话刷新逻辑统一围绕快照与结构化协议会话
- 绑定中间态、已完成令牌态、确认上下文三者的职责彻底分离

也就是说，模型已经建好了，但还没有把所有读写入口都切过去。

### 5.5 浏览器链路仍然是“主流程的一部分”，而不是 fallback

蓝图要求浏览器链路后续只保留为：

- 导入工具
- 手工修复工具
- 调试兜底方案

但当前实际情况是：

- 浏览器链路仍承担新增令牌主入口
- 绑定页仍依赖它提供材料
- 主链路成败仍部分取决于网页登录态是否采集充分

这部分还没有完成转向。

---

## 6. 后续开发建议

后续开发不要再围绕“怎样把网页登录采集做得更复杂”展开，而应优先完成下面几步切换。

### 6.1 第一优先级：补齐协议登录主入口

目标：

- 新增真正的 Steam 协议登录页 / ViewModel
- 直接调用 `SteamProtocolLoginOrchestrator`
- 支持：
  - 用户名密码输入
  - 邮箱验证码输入
  - 设备验证码输入
  - 设备确认等待 / 轮询
- 登录成功后得到结构化 `SteamMobileSession`

这一阶段的完成标准是：

- 新增手机令牌流程不再必须先打开浏览器
- 协议登录结果能稳定拿到 `SteamID / AccessToken / RefreshToken`
- `guardData / newGuardData` 能被正确复用与保存

### 6.2 第二优先级：让绑定页改吃结构化会话上下文

目标：

- 不再让绑定页直接消费 `cookiesText`
- 不再让绑定页以 `EnrollmentDraft` 作为唯一材料来源
- 改为消费结构化的绑定上下文对象

这个上下文至少应稳定承载：

- `steamId`
- `accessToken`
- `refreshToken`
- `sessionId`
- 移动端 cookies
- `accountName`
- `guardData`

这样做的意义是：

- 绑定逻辑与网页登录采集彻底解耦
- 新增绑定、刷新修复、导入补全三类场景可以共用同一套上下文模型

### 6.3 第三优先级：把绑定流程改成真正的 SDA 式主链路

目标：

- 新增手机令牌主流程改成：
  - 协议登录成功
  - 直接开始 `AddAuthenticator`
  - 生成并保存 secrets
  - 输入激活码
  - 完成 `FinalizeAddAuthenticator`
  - 持久化统一快照

这一阶段建议额外补齐：

- `AddAuthenticator` 失败状态的归类
- `FinalizeAddAuthenticator` 的时间偏差与多次激活码重试策略
- 绑定中间态与已完成态的恢复机制

### 6.4 第四优先级：统一 confirmations / 刷新 / 详情页

目标：

- 令牌详情改为直接读取统一快照
- 会话刷新统一走协议会话刷新
- confirmations 统一从快照解析上下文
- 对“只有 authenticator secrets、没有协议会话”的旧导入数据做明确降级提示

这一阶段完成后，项目的核心数据流才算真正接近 SDA：

- 一份统一快照
- 一套会话刷新逻辑
- 一套确认上下文生成逻辑
- 一套主链路持久化入口

### 6.5 第五优先级：把浏览器链路降级为 fallback

目标：

- 浏览器登录与手工录入只保留在：
  - 导入页
  - 调试页
  - 修复页
- 默认新增手机令牌入口不再跳浏览器
- 文案层面明确标注“兼容模式 / 手工修复模式”

这一步不需要最先做，但必须在主链路切换完成后尽快完成，否则后续代码会继续混线。

---

## 7. 推荐执行顺序

建议按下面顺序推进：

1. **先把协议登录 UI 主入口补起来**
2. **再把绑定页的数据来源切到结构化协议会话**
3. **然后打通“登录 -> 绑定 -> 保存快照”的真正新增令牌主闭环**
4. **再统一 confirmations / 刷新 / 详情页的数据源**
5. **最后把浏览器链路降级成 fallback / import / debug**

不建议的顺序是：

- 继续给浏览器采集链路补更多页面状态
- 继续把关键业务状态塞进手工 draft
- 在未切换主入口前就扩张 confirmations 复杂逻辑

这些做法只会增加迁移成本，不会缩短转向路径。

---

## 8. 建议补充的验证与测试

### 8.1 单元测试

继续补强以下测试：

- 协议登录 challenge 顺序与 fallback
- `guardData / newGuardData` 复用与覆盖规则
- 协议会话与 `SteamSessionRecord` / 快照之间的映射
- 绑定上下文生成与缺失项判断
- 刷新 access token 后的会话合并逻辑
- confirmations 请求上下文构造逻辑

### 8.2 集成验证

后续需要至少覆盖下面几类联调场景：

- 纯用户名密码成功登录
- 邮箱验证码挑战
- 设备验证码挑战
- 设备确认后轮询成功
- `AddAuthenticator` 成功返回 materials
- `FinalizeAddAuthenticator` 成功 / 失败 / 需要继续等待
- refresh token 刷新 access token
- 已有旧导入令牌在无协议会话时的 confirmations 降级提示

### 8.3 真机与环境要求

完整回归仍需要：

- 可用 Android SDK
- 真机或稳定模拟器
- 可控的测试 Steam 账号
- 能覆盖邮箱验证、设备验证、已有 guardData、无 guardData 四类情况的测试样本

---

## 9. 当前最重要的工程原则

后续涉及 Steam 主链路的开发，建议持续遵守下面几条：

1. 新功能优先挂到统一快照模型与结构化协议会话，不继续扩张页面局部临时态。
2. 浏览器链路只做兼容、导入、修复、调试，不再承载主登录和主绑定职责。
3. 所有协议级能力优先对齐 SDA 的核心类职责，而不是对齐其 WinForms UI 形态。
4. 绑定中间态、已完成令牌态、确认上下文要明确分层，避免再次混线。
5. 先完成主链路切换，再做边缘流程优化。

---

## 10. 当前结论

截至 `2026-04-09`，这条 SDA Android 转向主线已经走到“核心模型与协议登录引擎已就位”的阶段，真正的阻塞点不再是“能不能做协议登录”，而是“何时把新增令牌主流程从浏览器材料驱动切到协议登录结果驱动”。

因此，下一阶段最关键的开发任务不是继续修网页登录，而是：

> **把协议登录结果正式接入新增手机令牌主链路，并据此完成绑定、快照持久化与 confirmations 上下文统一。**
