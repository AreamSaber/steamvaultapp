# SDA 对比当前项目全链路可行性评估

## 1. 结论先行

结论可以分成两句：

1. **从架构角度看，我们可以像 SDA 一样做到“登录 Steam → 绑定手机令牌 → 本地保存完整令牌材料 → 管理会话 → 批准交易/市场确认”的全链路打通。**
2. **但按当前项目这条“外部浏览器/网页会话采集 + 手动补材料 + 再发绑定请求”的路线，做不到 SDA 那种稳定、闭环、长期可维护的全链路。**

也就是说：

- **不是做不到**
- **而是不能继续靠浏览器会话抓取这条路做**

如果想真正达到 SDA 的效果，必须把核心能力从“网页登录态采集”升级为“协议级登录 + 协议级绑定 + 本地持有完整状态”。

---

## 2. 这三份 SDA 文档共同说明了什么

结合以下三份文档：

- `docs/SDA_项目端到端流程阅读总结.md`
- `docs/SDA_Complete_Analysis.md`
- `docs/SDA_FLOW_ANALYSIS.md`

可以把 SDA 能打通全链路的根本原因归纳为 6 点。

### 2.1 它不是在“抓网页 Cookie 登录”

SDA 的登录主链路不是 WebView，也不是浏览器 Cookie 复制，而是：

- 直接用 `SteamKit2.Authentication`
- `BeginAuthSessionViaCredentialsAsync(...)`
- `PollingWaitForResultAsync()`
- 登录平台显式声明为：
  - `PlatformType = MobileApp`
  - `ClientOSType = Android9`

这意味着 SDA 拿到的是 **协议层认可的移动端登录会话**，不是“网页上碰巧能用的一组 Cookie”。

### 2.2 它同时持有“令牌身份”与“登录会话身份”

SDA 本地不是只保存一类数据，而是保存两组核心状态：

#### A. 手机令牌身份

- `shared_secret`
- `identity_secret`
- `device_id`
- `revocation_code`
- `token_gid`

这组数据负责：

- 生成 Steam Guard 动态码
- 生成交易确认签名
- 模拟固定移动设备

#### B. 登录会话身份

- `SteamID`
- `AccessToken`
- `RefreshToken`
- `SessionID`

这组数据负责：

- 访问 Steam 社区接口
- 刷新过期 access token
- 组装移动端 cookie

SDA 正是把这两组状态都掌握在本地，才有能力把全流程串起来。

### 2.3 它不是“下载 maFile”，而是本地生成 maFile

SDA 的关键不是从 Steam 下载一个现成的 `.maFile`，而是：

1. 先完成协议级登录
2. 调用 `ITwoFactorService/AddAuthenticator`
3. 从响应中拿到 `shared_secret / identity_secret / token_gid / revocation_code`
4. 组装 `SteamGuardAccount`
5. 立即序列化成本地 `.maFile`
6. 再执行 `FinalizeAddAuthenticator`

所以 `.maFile` 是 **本地构建出来的完整状态快照**，不是网页登录后抓出来的附属物。

### 2.4 它能自己恢复和刷新会话

SDA 的 `SessionData` 会：

- 解析 JWT 的 `exp`
- 判断 `AccessToken` 是否过期
- 用 `RefreshToken` 调用 `GenerateAccessTokenForApp`
- 刷新会话

也就是说，SDA 不是一次性拿个 Cookie 用完就算了，而是有完整的 **会话生命周期管理**。

### 2.5 它能自己构造移动端 cookie

SDA 的 `SessionData.GetCookies()` 会自己拼：

- `steamLoginSecure = steamid||accessToken`
- `sessionid`
- `mobileClient = android`
- `mobileClientVersion = ...`

这一点非常重要。

它不是依赖浏览器“赐给它”一份 cookie，而是当它已经拥有协议级 token 后，**自己就能生成访问确认接口所需的移动端登录态**。

### 2.6 它的流程职责分离非常清晰

SDA 明确把下面几件事分开：

- 新绑定一个手机令牌
- 导入已有 maFile
- 会话过期后重新登录修复 Session
- 查看/批准 confirmations
- 本地加密存储

这也是它不容易把“令牌绑定”“网页登录”“交易确认”“令牌详情”混成一团的重要原因。

---

## 3. 当前项目已经具备了哪些能力

和 SDA 对比，我们现在不是一片空白，已经有一些基础块。

### 3.1 会话管理与确认操作的 UI 已经独立

当前代码里，Steam 会话页面和新增令牌页面已经拆开：

- `app/src/main/java/com/example/steam_vault_app/feature/steamsession/SteamSessionScreen.kt`
- `app/src/main/java/com/example/steam_vault_app/feature/importtoken/SteamAddAuthenticatorScreen.kt`

这一步很重要，因为它至少避免了“交易批准用的 Steam 会话”和“新增手机令牌用的绑定材料”继续混线。

### 3.2 已有外部浏览器/Custom Tabs 回跳能力

当前项目已经支持：

- 外部浏览器/Custom Tabs 打开 Steam 登录页
- 通过 `steamvaultapp://steam-login/add-authenticator` 回跳

相关代码：

- `app/src/main/java/com/example/steam_vault_app/feature/importtoken/SteamExternalBrowserLoginManager.kt`
- `app/src/main/java/com/example/steam_vault_app/MainActivity.kt`

这说明我们已经具备“外部登录入口”和“回跳桥”的基础设施。

### 3.3 已有绑定接口客户端

当前项目已经存在绑定 API 抽象：

- `app/src/main/java/com/example/steam_vault_app/data/steam/SteamAuthenticatorBindingApiClient.kt`

从接口定义看，已经支持：

- `beginAuthenticatorBinding(...)`
- `finalizeAuthenticatorBinding(...)`

这说明“绑定流程的后半段”并不是完全没有。

### 3.4 已有确认与会话校验实现

当前项目不是只有接口，交易确认和会话校验已经有实际实现：

- `app/src/main/java/com/example/steam_vault_app/data/steam/DefaultSteamConfirmationSyncManager.kt`
- `app/src/main/java/com/example/steam_vault_app/data/steam/OkHttpSteamConfirmationApiClient.kt`
- `app/src/main/java/com/example/steam_vault_app/data/steam/DefaultSteamSessionValidationSyncManager.kt`
- `app/src/main/java/com/example/steam_vault_app/data/steam/OkHttpSteamSessionValidationApiClient.kt`

这说明当前工程已经具备：

- `/mobileconf/getlist`
- `/mobileconf/ajaxop`
- 会话有效性检查

也就是说，**确认侧和会话验证侧并不是零实现**。

---

## 4. 当前项目距离 SDA 还差在哪里

真正的差距不在 UI，而在“核心状态从哪里来”。

### 4.1 当前绑定前准备仍然依赖“网页会话采集”

最关键的现状在：

- `app/src/main/java/com/example/steam_vault_app/feature/importtoken/SteamAuthenticatorBindingPreparation.kt`

从这段代码可以直接看出，当前绑定准备依赖：

- `sessionid`
- `steamLoginSecure`
- `steamId`
- `oauthToken` 或 `webApiKey`

它的本质还是：

1. 先从网页侧或手动输入拿材料
2. 再把这些材料喂给绑定流程

这条路最大的问题是：

- 它没有掌握“登录协议本身”
- 只能依赖 Steam 当前网页行为是否愿意给你这些材料
- Steam 改网页、改 Cookie 策略、改 RSA 初始化策略时，整个链路就会断

### 4.2 外部浏览器回跳目前只解决“回跳”，没有解决“会话生成”

当前 `SteamExternalBrowserLoginManager.kt` 能做的是：

- 打开登录页
- 接收 `redir` 回跳

但它还做不到：

- 自动提取完整、稳定、长期可用的协议级会话
- 自动拿到 `AccessToken / RefreshToken`
- 自动构造后续移动端登录 cookie

所以它目前只是 **比内嵌 WebView 更稳一点的网页入口**，还不是 SDA 那种协议级登录引擎。

### 4.3 当前项目缺少“SDA 那个真正的中枢对象”

SDA 有一个非常明确的核心中枢：

- `SteamGuardAccount`
- `SessionData`

而当前项目虽然也有多个分散的数据结构，但还缺一个“真正统一的 Steam 手机令牌账户模型”，把下面这些一次性收拢：

- 令牌密钥材料
- 设备标识
- 会话 token
- SessionID
- 时间对齐信息
- 会话刷新状态
- 本地加密持久化状态

现在项目更像是：

- 一部分在 token 模型里
- 一部分在 session record 里
- 一部分在 enrollment draft 里
- 一部分在页面状态里

这会导致流程容易互相污染。

### 4.4 当前项目缺少“协议级登录 + 自动刷新”这条主干

SDA 的真正护城河不是页面，而是：

- 账号密码登录
- 2FA 挑战应答
- AccessToken/RefreshToken 获取
- 会话刷新

当前项目虽然能验证 session、能发确认请求、能开始绑定，但最难的这段主干还没补齐。

而如果这段主干没有补齐，就会一直出现下面这些问题：

- 需要用户手动粘 Cookie
- 需要用户猜该从网页里提取什么
- 登录链路受网页行为影响极大
- “保存 Steam 会话”和“新增手机令牌绑定材料”容易混在一起

---

## 5. 用一句话定义当前项目的真实状态

当前项目更接近：

**“已经实现了 Steam 手机令牌生态中的若干下游能力，但上游的协议级登录与状态生成还没有接管。”**

下游能力包括：

- 会话保存
- 会话校验
- 确认列表获取
- 确认批准/拒绝
- 绑定 API 的 begin/finalize 抽象

缺少的上游能力包括：

- 协议级登录
- 2FA 挑战处理
- AccessToken/RefreshToken 稳定获取
- 统一的移动端状态构造
- 本地生成完整的“maFile 等价物”

---

## 6. 我们能不能也做到 SDA 式全链路打通

答案是：**可以，但必须换主路径。**

### 6.1 不能继续把“浏览器登录”当成主链路

如果继续以这些能力为主：

- 外部浏览器登录
- 回跳记录 URL
- 用户手动填 `sessionid`
- 用户手动填 `steamLoginSecure`
- 从网页上猜 `oauthToken`

那么结果最多是：

- 偶尔可用
- 可以作为导入/临时修复工具
- 但很难达到 SDA 那种长期稳定的全链路

所以浏览器链路最多只能作为：

- 迁移过渡工具
- 降级兜底方案
- 手工修复入口

不能再作为最终架构的中心。

### 6.2 要做到 SDA 级别，需要建立新的核心层

我们需要在 Android 项目里补出一个等价于 SDA `SteamAuth` 的核心层，至少包含下面 5 类能力。

#### A. 协议级登录层

负责：

- 用户名密码登录
- 邮箱验证码挑战
- 已有令牌设备码挑战
- 获取 `SteamID / AccessToken / RefreshToken`

这是最关键、也是最难的一层。

#### B. 令牌绑定层

负责：

- `AddAuthenticator`
- 返回 secrets 后立即本地保存
- `FinalizeAddAuthenticator`
- 完成 enroll 状态更新

#### C. 时间与签名层

负责：

- `QueryTime`
- TOTP 动态码生成
- confirmations 签名生成

#### D. 会话生命周期层

负责：

- JWT 过期判断
- access token 刷新
- 生成移动端 cookie
- 会话修复

#### E. 本地状态持久化层

负责：

- 保存 Android 端的 maFile 等价模型
- 加密 secrets
- 保存会话状态
- 区分“新增绑定草稿”“已完成令牌”“交易批准会话”

---

## 7. 推荐的落地路线

如果目标是“最终做到 SDA 一样全链路打通”，建议按下面 4 个阶段推进。

### 阶段 1：先把领域模型重建完整

先不要继续堆页面。

优先抽出几个清晰模型：

- `SteamMobileSession`
  - `steamId`
  - `accessToken`
  - `refreshToken`
  - `sessionId`
- `SteamAuthenticatorSecrets`
  - `sharedSecret`
  - `identitySecret`
  - `revocationCode`
  - `tokenGid`
  - `deviceId`
- `SteamMobileAccountSnapshot`
  - 等价于 Android 端的 `.maFile`
- `SteamEnrollmentDraft`
  - 只负责新增绑定中间态
- `SteamConfirmationContext`
  - 只负责确认请求所需上下文

这一步的目标是先把“状态边界”理顺。

### 阶段 2：实现协议级登录

这是最重要的一刀。

需要一个真正的登录模块，直接产出：

- `SteamID`
- `AccessToken`
- `RefreshToken`

如果这一步做不出来，后面所有绑定、确认、会话刷新都会继续不稳。

### 阶段 3：把绑定流程改成“协议生成”，不是“网页采集后再绑定”

这一步要做到：

1. 使用协议级登录得到的 session
2. 调用 `beginAuthenticatorBinding`
3. 立即本地保存返回的 secrets
4. 再执行 finalize
5. 保存成稳定的本地令牌记录

做到这一步后，“网页登录拿 maFile”这套说法就应该彻底退出主流程。

### 阶段 4：统一确认、会话、令牌管理

最后把下游能力都挂到统一核心模型上：

- 动态码显示
- 交易确认列表
- 市场确认
- 会话刷新
- 令牌详情
- 受保护字段展示

到这一步，产品结构才会真正像 SDA，而不是“几个功能能点通，但底层状态彼此不统一”。

---

## 8. 现实风险与难点

### 8.1 最大难点不是确认接口，而是登录协议

当前项目里，确认和会话验证已经有实现基础。

最难的是：

- Android 端如何稳定完成 Steam 的现代登录认证
- 如何处理邮箱码、设备码、挑战轮询
- 如何稳定拿到 token

这部分是决定成败的关键。

### 8.2 需要面对非官方协议的维护成本

即使做成了，也要接受一个现实：

- 这不是官方 Android SDK
- Steam 改认证策略时，我们需要跟进

但 SDA 已经证明了一件事：

- **只要你掌握的是协议级状态，而不是网页表象，维护成本会远小于网页抓取方案。**

### 8.3 本地安全要比现在更严格

一旦我们真的做到 SDA 级别，就必须更严肃地处理：

- `shared_secret`
- `identity_secret`
- `refreshToken`
- `revocationCode`

建议最终至少做到：

- 本地库级加密
- Android Keystore 保护主密钥
- 受保护字段默认遮罩
- 导出/备份时单独加密

---

## 9. 最终判断

### 9.1 是否能像 SDA 一样全链路打通

**能。**

前提是我们愿意把当前项目从“网页会话驱动”升级成“协议状态驱动”。

### 9.2 当前这版架构是否已经接近 SDA

**只接近了一半。**

接近的部分是：

- 页面职责开始拆开了
- 确认与会话校验已经有基础实现
- 绑定 API 客户端已经存在
- 外部浏览器回跳已经有基础设施

没接近的核心部分是：

- 登录协议仍未接管
- token 生命周期仍未成为主链路
- 绑定前置材料仍依赖网页会话采集
- 本地核心模型还没有达到 SDA 那种统一程度

### 9.3 应该怎么定战略

建议直接采用下面这句作为后续开发方向：

> **保留浏览器链路作为过渡/兜底工具，但把主路线切换到“协议级登录 + 统一 Steam 手机令牌核心层”。**

只有这样，我们才有机会真正做出 Android 版的 SDA 全链路能力，而不是继续在 WebView、Cookie、网页 RSA 初始化失败这些外围问题上反复消耗。

---

## 10. 一句话版总结

SDA 之所以能全链路打通，不是因为它“网页登录做得更好”，而是因为它**根本不是靠网页登录在工作**；它掌握了 Steam 手机令牌体系所需的完整协议状态。  
我们当前项目如果也想达到这个级别，答案不是继续修网页，而是补出属于自己的 `SteamAuth` 核心层。
