# Steam Vault Android 执行清单（P2 / P3 / P4）

## 1. 文档定位

- 本文档是当前阶段唯一的细化执行清单。
- 如果后续优先级变化，直接更新本文件，不新建新的平行规划文档。
- `docs/ANDROID_DEVELOPMENT_TASKS.md` 负责长期里程碑总览。
- `docs/HANDOFF_2026-04-07.md` 负责同步当前状态、最近修复和最近推荐顺序。

## 2. 当前执行基线

当前已经稳定可用的能力：

- [x] 主密码创建、解锁、锁定
- [x] Argon2id + Vault key 包装 + Android Keystore
- [x] 本地加密 Vault 持久化
- [x] Steam 令牌导入、列表、验证码生成
- [x] 本地加密备份导出 / 恢复
- [x] 欢迎页首次恢复与应用内恢复两条路径
- [x] WebDAV 手动上传 / 手动恢复
- [x] 云备份配置与安全设置进入本地备份
- [x] Steam 时间同步基础能力
- [x] Steam 会话本地密文持久化底座

当前关键能力进度：

- [x] 主要中文文案与错误提示资源化
- [x] WebDAV 自动备份、失败重试、多版本管理、系统级后台补偿同步
- [ ] 主密码修改闭环（代码已完成，待回归验收）
- [ ] Steam 登录会话与确认系统完整主链路

## 3. 当前推荐开发顺序（2026-04-07）

1. P2 验收收尾：WebDAV 真机闭环 + 主密码修改回归验收
2. Steam 会话 / 确认系统
3. 长期安全增强

说明：

- P2 的主要开发项已经完成，当前剩的是回归验收和真机联调。
- 下一项真正的新增开发主线是 Steam 会话 / 确认系统。

## 4. P2 收口任务

### 4.1 资源化与错误语义统一

- [x] 把欢迎页、创建密码页、解锁页、备份页、云备份页的主要中文文案迁移到 `res/values/strings.xml`
- [x] 把主密码、恢复、云备份相关错误提示统一成“用户可理解 + 开发可排查”的两层语义
- [x] 清理仍然使用硬编码中文的高频页面
- [x] 补一轮恢复、解锁、云备份状态页的文案校对

### 4.2 WebDAV 云备份 2.0

- [x] 定义自动云备份触发点：令牌变更、安全设置变更、云配置变更后的最小触发规则
- [x] 增加自动备份去抖与频率限制，避免短时间连续上传
- [x] 增加自动备份失败重试策略与状态记录
- [x] 明确远端只保留最新一份还是保留最近 N 份历史版本
- [x] 支持远端版本枚举与按版本恢复
- [x] 在云备份状态页展示最近一次自动备份结果与失败原因
- [x] 在云备份状态页展示可恢复版本摘要
- [x] 明确自动备份与手动恢复之间的交互规则
- [x] 手动恢复前清空排队中的自动备份，并在有进行中上传时等待其结束
- [x] 手动恢复完成后将自动备份状态切到“等待下一次本地变更重新触发”
- [x] 增加 WorkManager 后台补偿同步路径，覆盖应用进程被回收后的自动上传
- [x] 增加后台同步可访问的 WebDAV 配置快照与云备份状态明文镜像

### 4.3 主密码修改闭环

- [x] 增加主密码修改入口与交互流程
- [x] 复用现有 Vault key，完成主密码修改后的重新包装
- [x] 修改主密码后自动触发新的云备份安全快照上传
- [ ] 确保主密码修改后本地备份仍可导出 / 恢复
- [ ] 确保主密码修改后云备份配置、云恢复、本地恢复链路不被破坏
- [x] 补充主密码修改基础设备测试：旧密码失效、新密码生效、Vault key 保持不变

### 4.4 P2 测试与验收

必须至少覆盖：

- [x] JVM 单测覆盖备份包编解码
- [x] JVM 单测覆盖云备份状态流转
- [x] JVM 单测覆盖自动备份调度（去抖 / 重试 / 限频）
- [x] JVM 单测覆盖多版本远端保留 / 枚举 / 按版本恢复
- [x] JVM 单测覆盖“手动恢复清空待上传队列 / 等待进行中上传完成”
- [x] JVM 单测覆盖系统级后台自动备份调度与后台重试执行
- [x] 设备测试覆盖“欢迎页首次恢复”
- [x] 设备测试覆盖“应用内恢复保留设置”
- [x] 设备测试覆盖“恢复前取消自动云备份排队”
- [x] 设备测试覆盖“锁定态读取云备份状态镜像 / 后台配置快照”
- [ ] 手动验证 WebDAV 上传 / 下载 / 恢复闭环
- [ ] 手动验证自动触发云备份闭环
- [x] 设备测试覆盖“修改主密码后旧密码失效 / 新密码解锁成功 / Vault key 不变”
- [ ] 主密码修改后的本地恢复 / 云恢复回归测试

P2 当前验收口径：

- [x] 用户可以在 App 内配置 WebDAV / 坚果云
- [x] 用户可以手动上传当前加密备份到云端
- [x] 用户可以从云端恢复到当前设备
- [x] 用户可以从欢迎页直接恢复本地备份
- [x] 服务端只能看到密文备份包
- [x] 用户不需要每次手动点击上传才能保持云端备份最新
- [x] 用户可以看到最近几份可恢复的远端版本
- [x] 用户手动恢复时不会把旧的自动备份排队任务误传到云端
- [ ] 用户可以在不破坏现有数据的前提下修改主密码（待本地恢复 / 云恢复回归验收）

## 5. P3 任务：Steam 会话 + 确认系统

### 5.1 目标

在 P2 收口稳定后，再推进 Steam 相关功能主链路：

- [ ] Steam 账号登录
- [ ] 邮箱验证码 / 二次验证补充输入
- [ ] Session Cookie 复用与过期处理
- [ ] 待确认列表
- [ ] 批准 / 拒绝确认

### 5.2 页面层

- [x] 已有 Steam 会话状态页
- [x] 已有 Steam 会话手动录入 / 更新页（基于现有状态页扩展）
- [x] 已有内置浏览器登录 Steam 并自动提取 `steamcommunity.com` 会话的首版入口
- [ ] 新增 Steam 账号登录页
- [ ] 新增补充验证页
- [ ] 新增确认列表页
- [ ] 新增确认详情页
- [ ] 增加批准 / 拒绝反馈与会话过期提示

### 5.3 数据与网络层

- [x] 已有 Steam Session 模型与本地加密仓储
- [x] 已有 Steam 时间同步基础缓存
- [x] 已有会话编辑解析与 Cookie 规范化逻辑
- [x] 已有内置浏览器 Cookie 检测与自动会话提取底座
- [ ] 封装 Steam 登录 DTO 与会话写入流程
- [ ] 封装确认项模型与 API 映射
- [ ] 封装确认列表拉取接口
- [ ] 封装批准 / 拒绝接口
- [ ] 处理 Session 失效、重登与频率限制

### 5.4 测试与验收

- [x] 已有 `identity_secret` 签名单测
- [x] 已有 Steam 会话本地持久化设备测试
- [ ] 增加登录流程的 Mock 网络测试
- [ ] 增加确认列表 / 批准 / 拒绝的 Mock 网络测试
- [ ] 增加真机 / 模拟器联调验证

## 6. P4 任务：长期安全增强

### 6.1 目标

在主链路稳定后，再推进长期安全与质量项，而不是一次性引入过度复杂的新安全结构。

### 6.2 重点任务

- [ ] 建立主密码修改后的安全事件记录
- [ ] 建立 Vault key 轮换元数据与长期迁移策略
- [ ] 补更完整的损坏密文、错误密钥、旧版本迁移回归测试
- [ ] 研究更细粒度的内存驻留控制
- [ ] 研究是否需要更强的字段级加密或 Pepper 机制

## 7. 当前不直接进入主线的内容

- [ ] 大范围重写现有加密格式
- [ ] 没有测试前提下引入复杂多层嵌套加密
- [ ] 桌面端同步客户端
- [ ] 超出确认系统范围的更大 Steam 自动化功能

## 8. 关联文档

- `docs/ANDROID_DEVELOPMENT_TASKS.md`
- `docs/HANDOFF_2026-04-07.md`
- `docs/PRODUCT_SPEC.md`

## 9. 2026-04-08 进度更新

- [x] 新增确认列表页，可从令牌详情页和 Steam 会话页进入
- [x] 新增确认列表拉取主链路：`mobileconf/getlist`
- [x] 新增批准 / 拒绝主链路：`mobileconf/ajaxop`
- [x] 新增会话过期显式报错，避免只看到通用失败提示
- [x] 新增 `SteamConfirmationResponseParserTest`
- [x] 新增 `DefaultSteamConfirmationSyncManagerTest`
- [x] 已完成真机批准 / 拒绝交易联调验收
- [ ] 仍待补齐真实 Steam 登录 DTO、邮箱 / 二次验证补充流程
- [ ] 仍待补齐真实 Steam 登录 DTO 对应的真机 / 模拟器联调验收

## 10. 2026-04-08 会话验证增量

- [x] 新增 Steam 会话验证主链路，可用已保存的 Cookie 校验当前会话是否仍然有效
- [x] 新增会话验证状态持久化：未验证 / 成功 / 失败，以及最近错误消息
- [x] Steam 会话页新增“验证当前会话”动作和最近验证结果展示
- [x] 手动保存或内置浏览器抓取新会话后，会重置旧的验证状态，避免把旧结果误认为仍然有效
- [x] 新增 `DefaultSteamSessionValidationSyncManagerTest`
- [ ] 仍待补齐真实 Steam 登录 DTO、邮箱 / 二次验证补充流程
- [ ] 仍待补齐真实 Steam 登录 DTO 对应的真机 / 模拟器联调验收

## 11. 2026-04-08 内置浏览器登录阶段识别增量

- [x] 内置 Steam 浏览器观察扩展为：URL + 页面标题 + 页面文本片段，而不再只依赖 Cookie
- [x] 会话页能识别当前卡在账号密码、邮箱验证码、Steam Guard / 手机令牌验证码，还是额外挑战步骤
- [x] 浏览器区域新增当前页面标题展示，便于用户判断是否还停留在补充验证阶段
- [x] 新增 `SteamWebLoginSessionCaptureTest` 阶段识别覆盖
- [ ] 仍待补齐真实 Steam 登录 DTO、表单提交流程与对应真机联调验收

## 12. 2026-04-08 补充验证辅助提交增量

- [x] 内置 Steam 浏览器已支持在邮箱验证码 / Steam Guard 验证码阶段显示原生输入框
- [x] 会话页已支持把验证码填回当前 Steam 页面，并尝试触发表单提交
- [x] 新增 `SteamWebLoginAssist`，拆出补充验证提交脚本构造与结果解析
- [x] 新增 `SteamWebLoginAssistTest`
- [ ] 仍待补齐真实 Steam 登录 DTO 与对应真机联调验收

## 13. 2026-04-08 账号密码辅助提交增量

- [x] 内置 Steam 浏览器已支持在账号密码登录阶段显示原生账号名 / 密码输入框
- [x] 会话页已支持把账号密码填回当前 Steam 登录页，并尝试触发登录提交
- [x] 密码只保留在当前页面内存态中，不写入本地会话仓储
- [x] 已补 `SteamWebLoginAssistTest` 的账号密码阶段覆盖
- [ ] 仍待补齐真实 Steam 登录 DTO 与对应真机联调验收

## 14. 2026-04-08 结构化登录状态识别增量

- [x] 内置 Steam 浏览器已从单纯阶段识别升级为结构化登录分析：阶段 + 失败原因
- [x] 已支持识别常见失败原因：账号密码错误、邮箱验证码错误、Steam Guard 验证码错误、限流、额外人机验证
- [x] 会话页已显示当前浏览器阶段，并在提交后区分“等待 Steam 返回结果”和“已被 Steam 明确拒绝”
- [x] 已补 `SteamWebLoginSessionCaptureTest` 的失败原因覆盖
- [ ] 仍待补齐真实 Steam 登录 DTO 与对应真机联调验收

## 15. 2026-04-08 Steam 登录事务 DTO 增量

- [x] 新增 `SteamWebLoginTransaction` 与纯 Kotlin reducer，把内置浏览器登录过程收口成结构化事务快照
- [x] 已把 URL、页面标题、当前阶段、待处理辅助提交阶段、已处理会话签名统一纳入事务状态
- [x] 会话页已改为根据事务 reducer 映射“等待结果 / 已推进到下一步 / 失败原因 / 已抓到会话 / 重复会话”
- [x] 已补 `SteamWebLoginTransactionReducerTest`
- [ ] 仍待补齐真实 Steam 登录 DTO 的下一步：把当前事务快照继续推进到可追踪的登录流程对象与真机联调验收

## 16. 2026-04-08 Steam 登录流程对象增量

- [x] 新增 `SteamWebLoginFlow` 与 `SteamWebLoginNextAction`，把“当前建议下一步”从页面条件判断中抽成可复用流程对象
- [x] `SteamWebLoginTransaction` 已持有 `lastProgress`，流程对象现在可以直接从事务快照推导出等待、补码、重试、额外挑战和会话就绪状态
- [x] `SteamSessionScreen` 已改为根据 flow 决定是否显示账号密码输入、验证码输入，提交后会进入明确的“等待浏览器结果”状态
- [x] 会话页已新增“建议下一步”提示，明确告知用户当前该输入什么、等待什么、还是继续在页面内完成人机验证
- [x] 已补 `SteamWebLoginFlowFactoryTest`
- [ ] 仍待补齐真实 Steam 登录 DTO 的下一步：把当前 flow 继续推进成可追踪的完整登录流程对象，并完成真机联调验收

## 17. 2026-04-08 Steam 登录步骤链增量

- [x] `SteamWebLoginTransaction` 已新增 `completedStages`，开始记录这次登录尝试里哪些阶段已经走完
- [x] 新增 `SteamWebLoginJourney` 与 factory，把事务快照进一步映射成“步骤链”视图
- [x] 会话页已新增登录步骤链展示，可区分待进行、当前进行中、等待结果、需要重试、已完成、已跳过、需手动完成、会话已就绪
- [x] 已补 `SteamWebLoginJourneyFactoryTest`，并扩展 `SteamWebLoginTransactionReducerTest` 覆盖已完成步骤记录
- [ ] 仍待补齐真实 Steam 登录 DTO 的下一步：把当前步骤链继续推进到完整登录事务对象，并完成真机联调验收

## 18. 2026-04-08 Steam 登录事务终态增量

- [x] `SteamWebLoginTransaction` 已新增 `attemptNumber` 与 `result`，开始记录当前页面里的第几次登录尝试，以及最近一次终态
- [x] reducer 已覆盖失败、抓到会话、写入成功、写入失败、重复会话等终态，并在新一轮尝试开始时清空旧终态
- [x] `SteamSessionScreen` 已新增“当前尝试编号 / 最近一次终态”展示，配合事务状态、步骤链和最近事件一起定位问题
- [x] 已补 `SteamWebLoginTransactionReducerTest` 覆盖尝试编号、终态写入和 reload 后的事务重置行为
- [ ] 仍待补齐真实 Steam 登录 DTO 的下一步：把事务快照继续推进到包含时间线和最终提交闭环的完整登录事务对象，并完成真机联调验收

## 19. 2026-04-08 Steam 登录尝试归档增量

- [x] `SteamWebLoginTransaction` 已新增 `history`，开始归档已经结束的登录尝试摘要
- [x] 当同一浏览器里在失败、重复会话或保存完成后再次提交账号密码 / 验证码时，reducer 现在会自动开启新的尝试编号，并把上一轮终态压入历史
- [x] `SteamSessionScreen` 已新增“最近已结束的尝试”展示，能看到每次尝试的终态、已完成阶段和事件数
- [x] 已补 `SteamWebLoginTransactionReducerTest` 覆盖失败后重试归档和 reload 后保留历史的行为
- [ ] 仍待补齐真实 Steam 登录 DTO 的下一步：把当前尝试历史继续推进到包含提交时间线和最终提交闭环的完整登录事务对象，并完成真机联调验收

## 20. 2026-04-08 Steam 登录尝试时间线增量

- [x] `SteamWebLoginAttemptRecord` 已新增 `timeline`，开始保留每次已结束尝试的事务事件轨迹
- [x] reducer 现在会在浏览器页直接重试、应用内再次提交、或重新加载清空当前尝试前，先把已结束尝试归档进历史
- [x] `SteamSessionScreen` 已新增历史尝试时间线展示，能直接看到每次尝试是如何走到失败、重复会话或保存成功的
- [x] 已补 `SteamWebLoginTransactionReducerTest` 覆盖浏览器直接重试归档、reload 归档和历史时间线保留行为
- [ ] 仍待补齐真实 Steam 登录 DTO 的下一步：把当前尝试时间线继续推进到包含提交时序和最终提交闭环的完整登录事务对象，并完成真机联调验收

## 21. 2026-04-08 Steam 登录尝试时序摘要增量

- [x] `SteamWebLoginAttemptRecord` 已新增 `summary`，开始把每次已结束尝试归纳成“开始阶段 / 提交顺序 / 推进阶段 / 终点阶段 / 闭环结果”的结构化摘要
- [x] 新增 `SteamWebLoginAttemptTimelineSummaryFactory`，把原始事务事件串收口成更稳定的尝试摘要，避免页面直接依赖原始事件顺序做解释
- [x] `SteamSessionScreen` 已新增历史尝试的“时序摘要”展示，能更直观看到一次登录尝试是停在验证、抓到会话、保存成功还是保存失败
- [x] 已补 `SteamWebLoginAttemptTimelineSummaryFactoryTest` 和 `SteamWebLoginTransactionReducerTest`，覆盖失败闭环与已保存会话闭环的摘要归档行为
- [ ] 仍待补齐真实 Steam 登录 DTO 的下一步：把当前摘要继续推进到包含更明确提交节点和最终提交闭环的完整登录事务对象，并完成真机联调验收

## 22. 2026-04-08 Steam 当前尝试时序快照增量

- [x] 新增 `SteamWebLoginTransactionSnapshotFactory`，开始把当前进行中的登录事务归纳成“开始阶段 / 已提交阶段 / 已推进阶段 / 当前停留阶段 / 当前节点”的结构化快照
- [x] `SteamSessionScreen` 已在当前事务区域新增“当前尝试摘要”，不再只能依赖最近事件串来判断登录卡在哪一步
- [x] 已补 `SteamWebLoginTransactionSnapshotFactoryTest`，覆盖等待结果、推进到邮箱码、已保存会话三种当前事务节点
- [ ] 仍待补齐真实 Steam 登录 DTO 的下一步：把当前尝试快照继续推进到更明确的提交节点和最终提交闭环，并完成真机联调验收

## 23. 2026-04-08 ??????????

- [x] ????? `app/src/main/res/values/strings.xml` ???????????? `U+FFFD` ?????
- [x] ?? `SteamAuthenticatorBindingPreparationFactory`?????? Steam ??????????????????????
- [x] ??????? `sessionid`?`steamLoginSecure`?`steamLogin` ????? `SteamID`
- [x] ?? `SteamAuthenticatorBindingScreen`????????????????????????
- [x] ?????????????????????????????????????????
- [x] ?? `SteamAuthenticatorBindingPreparationFactoryTest`
- [ ] ??????????????????????????????????????????

## 24. 2026-04-08 ??????????

- [x] `SteamAuthenticatorEnrollmentDraft` ??????? `oauthToken`??????????
- [x] `SteamWebLoginSessionCapture` ???? cookie ? `oauth_token` / `access_token` ?????
- [x] ?? `SteamMobileDeviceId`??? `SteamID` ????? Android ?? `device_id` ???
- [x] `SteamAuthenticatorBindingPreparationFactory` ????? `OAuth Token` ?????? `device_id`
- [x] `SteamAuthenticatorBindingScreen` ??? `OAuth Token` ???????? `device_id` ???
- [x] ?? `SteamMobileDeviceIdTest`
- [ ] ??????????????????????????????????????????

## 24. 2026-04-08 Steam authenticator binding request chain

- [x] Added `OkHttpSteamAuthenticatorBindingApiClient` with begin/finalize request scaffolding for the Steam mobile authenticator binding flow
- [x] Added `SteamAuthenticatorBindingResponseParser` coverage for begin/finalize payload handling, including pending activation status
- [x] Added `SteamAuthenticatorBindingImportDraftFactory` so binding material can flow directly into the existing `ImportDraft -> saveImportedToken` path
- [x] `SteamAuthenticatorBindingScreen` now supports: begin binding request, activation code input, finalize request, direct token save, and automatic Steam session persistence onto the newly created token
- [x] Added tests: `SteamAuthenticatorBindingResponseParserTest` and `SteamAuthenticatorBindingImportDraftFactoryTest`
- [x] Re-scanned `app/src` for `U+FFFD`; no replacement-character corruption remains
- [ ] Remaining work: verify the real Steam binding endpoints/parameters against device traffic and finish end-to-end device validation for begin/finalize/save
## 25. 2026-04-08 Steam binding progress persistence

- [x] Added encrypted `SteamAuthenticatorBindingProgressDraft` storage so begin-result material survives app restart/backgrounding
- [x] Added `SteamAuthenticatorBindingProgressRepository` and `LocalSteamAuthenticatorBindingProgressRepository`
- [x] `SteamAuthenticatorBindingScreen` now restores pending begin-result progress when the saved sign-in draft signature matches
- [x] Begin binding now persists progress locally; successful token save clears both the binding progress and the original enrollment draft
- [x] Added `SteamAuthenticatorBindingProgressDraftCodecTest`
- [x] Re-scanned `app/src` for `U+FFFD`; no replacement-character corruption remains
- [ ] Remaining work: validate the real Steam binding request/response parameters on device and finish end-to-end activation testing

## 26. 2026-04-08 Steam binding auth mode selection

- [x] Added `SteamAuthenticatorBindingAuthModeFactory` so binding requests can explicitly choose `OAuth only`, `Web API key only`, or `OAuth + Web API key`
- [x] `SteamAuthenticatorBindingScreen` now exposes the auth-mode choice in the UI and uses the selected mode for both begin and finalize requests
- [x] Added `format=json` to the binding endpoints in `OkHttpSteamAuthenticatorBindingApiClient` to stabilize response parsing during device-side validation
- [x] Restored the missing default string for `steam_authenticator_binding_check_oauth_missing` and added new auth-mode resource strings
- [x] Added `SteamAuthenticatorBindingAuthModeFactoryTest`
- [x] Re-scanned `app/src` for `U+FFFD`; no replacement-character corruption remains
- [ ] Remaining work: verify which auth mode Steam actually accepts on device and align begin/finalize failure guidance with the real responses

## 27. 2026-04-08 Steam binding failure guidance

- [x] Added `SteamAuthenticatorBindingFailureGuidanceFactory` to classify begin/finalize failures into re-login, activation-code retry, retry-later, or auth-mode-switch guidance
- [x] `SteamAuthenticatorBindingScreen` now shows actionable guidance under begin/finalize failures and exposes one-tap mode switching or return-to-sign-in actions when applicable
- [x] Added `SteamAuthenticatorBindingFailureGuidanceFactoryTest`
- [x] Re-scanned `app/src` for `U+FFFD`; no replacement-character corruption remains
- [x] Verification passed: `:app:compileDebugKotlin`, focused `:app:testDebugUnitTest`, and `:app:compileDebugAndroidTestKotlin`
- [ ] Remaining work: validate the real Steam error payloads on device and tune the guidance heuristics to match the actual failure texts

## 28. 2026-04-08 Steam embedded login RSA-key stabilization

- [x] Reworked `SteamWebLoginAssist` so credential/code injection uses the native input value setter plus input/change/blur events, improving compatibility with controlled Steam login inputs
- [x] Added a `submit_not_ready` branch so the app no longer treats a disabled / not-yet-ready submit path as a successful pending submission
- [x] `SteamEmbeddedLoginWebView` now forwards WebView console messages back to the screen layer, allowing the app to react to Steam-side browser errors such as RSA-key/auth-session initialization failures
- [x] `SteamSessionScreen` and `SteamAddAuthenticatorScreen` now clear the pending assist state when the page was only filled but not actually submitted, and surface a specific retry hint when Steam reports RSA-key initialization failure
- [x] Cleaned the remaining mojibake hardcoded Chinese signals inside `SteamWebLoginAssist` and `SteamWebLoginSessionCapture`
- [x] Verification passed after clearing the corrupted Kotlin incremental cache: `:app:compileDebugKotlin`, focused `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `:app:installDebug`
- [ ] Remaining work: re-test the embedded Steam sign-in on device against the real page and confirm the RSA-key failure no longer reproduces during credential submission
