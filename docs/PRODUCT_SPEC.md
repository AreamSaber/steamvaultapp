# Steam Vault App 产品规格

## 1. 产品目标

Steam Vault 是一个面向个人用户的移动端二步验证管理 App，首版目标如下：

- 导入已有的 Steam Guard 令牌数据
- 在本地离线生成 Steam 登录验证码
- 对令牌进行安全存储
- 提供端到端加密的云同步
- 提供加密备份与恢复能力

本产品默认只服务于令牌持有者本人，不包含绕过 Steam 官方验证流程的能力。

## 2. 首版 MVP

### 2.1 必做功能

- 首次启动引导
- 本地主密码创建
- Steam 令牌导入
- Steam 验证码展示与 30 秒倒计时
- 多令牌列表管理
- 端到端加密云备份
- 设备间恢复同步
- 本地加密导出
- 从加密备份恢复

### 2.2 暂缓功能

- 交易确认
- 登录会话接管
- 推送通知审批
- 桌面端同步客户端
- 自动抓取第三方 maFile

## 3. 用户流程

### 3.1 首次使用

1. 打开 App
2. 阅读风险提示与隐私说明
3. 设置本地主密码
4. 选择是否开启云备份
5. 进入导入流程

### 3.2 导入已有 Steam 令牌

支持三种输入方式：

- 粘贴完整 JSON
- 扫描包含 otpauth URI 的二维码
- 手动录入关键字段

最低可用字段：

- `account_name`
- `shared_secret`

建议同时保存但不在普通验证码页面直接展示的字段：

- `serial_number`
- `revocation_code`
- `identity_secret`
- `secret_1`
- `device_id`
- `token_gid`
- `uri`

### 3.3 生成验证码

1. 读取本地加密存储的 `shared_secret`
2. 使用 Steam 的 30 秒时间片算法计算验证码
3. 展示 5 位 Steam 字符集验证码
4. 展示剩余秒数与下一个周期进度条

### 3.4 云恢复

1. 用户登录云同步账号
2. 输入本地主密码或恢复密码
3. 下载加密后的令牌数据
4. 在本地解密并恢复

## 4. 安全设计

### 4.1 本地安全

- 所有令牌明文仅在内存短时存在
- 密钥材料不写入日志
- 使用系统安全存储保存主密钥
- Android 使用 Keystore
- iOS 使用 Keychain
- 敏感页面禁止系统截图可作为可选项

### 4.2 加密方案

- 用户输入主密码
- 使用 Argon2id 派生主密钥
- 使用主密钥加密令牌集合
- 数据层使用 AES-256-GCM 或 XChaCha20-Poly1305
- 每条令牌单独生成随机 nonce

推荐结构：

```json
{
  "version": 1,
  "kdf": {
    "name": "argon2id",
    "memory": 65536,
    "iterations": 3,
    "parallelism": 2,
    "salt": "base64..."
  },
  "vault": {
    "cipher": "aes-256-gcm",
    "nonce": "base64...",
    "ciphertext": "base64..."
  }
}
```

### 4.3 云同步

- 服务端仅存储密文
- 服务端不可见 `shared_secret`
- 支持多设备同步的冲突解决策略
- 默认以 `updated_at` 为准
- 删除操作采用软删除墓碑记录，避免多端误恢复

### 4.4 风险提示

- 用户直接贴出的密钥应视为已暴露
- 正式版中不应通过聊天、工单或日志传输密钥
- 如果密钥曾在不可信环境中暴露，应建议用户在 Steam 侧重新绑定或轮换

## 5. Steam 验证码实现规则

### 5.1 已确认事实

- 生成 Steam 登录验证码只需要 `shared_secret`
- 令牌导入可从完整 JSON 或 `uri` 中提取秘密字段
- Steam 验证码不是标准 6 位数字 TOTP，而是 Steam 自定义 5 位字符集

### 5.2 算法流程

1. 当前 Unix 时间戳除以 30，得到时间片
2. 将时间片编码为 8 字节大端序
3. 使用 `shared_secret` 作为 HMAC-SHA1 密钥计算摘要
4. 根据动态截断取出 31 位正整数
5. 使用 Steam 字符集反复取模生成 5 位验证码

Steam 字符集：

```text
23456789BCDFGHJKMNPQRTVWXY
```

### 5.3 Dart 参考伪代码

```dart
String generateSteamCode(Uint8List sharedSecret, int timestamp) {
  const chars = '23456789BCDFGHJKMNPQRTVWXY';
  final timeSlice = timestamp ~/ 30;
  final msg = ByteData(8)..setInt64(0, timeSlice, Endian.big);
  final digest = Hmac(sha1, sharedSecret).convert(msg.buffer.asUint8List()).bytes;

  final start = digest[19] & 0x0F;
  final fullCode = ((digest[start] & 0x7F) << 24) |
      ((digest[start + 1] & 0xFF) << 16) |
      ((digest[start + 2] & 0xFF) << 8) |
      (digest[start + 3] & 0xFF);

  var code = fullCode;
  final out = StringBuffer();
  for (var i = 0; i < 5; i++) {
    out.write(chars[code % chars.length]);
    code ~/= chars.length;
  }
  return out.toString();
}
```

## 6. 建议技术栈

### 6.1 客户端

- Flutter
- Riverpod 或 Bloc
- go_router
- freezed + json_serializable
- flutter_secure_storage
- local_auth
- cryptography
- drift 或 isar

### 6.2 云端

推荐两条路线，优先 A：

- A: Supabase
- B: 自建轻量 API + PostgreSQL

Supabase 适合 MVP，原因：

- 有现成用户体系
- 有数据库与对象存储
- 便于快速上线测试

无论选哪条路线，云端都只保存密文。

## 7. 数据模型

### 7.1 本地 TokenRecord

```json
{
  "id": "uuid",
  "platform": "steam",
  "account_name": "string",
  "shared_secret": "base64",
  "identity_secret": "base64|null",
  "serial_number": "string|null",
  "revocation_code": "string|null",
  "device_id": "string|null",
  "token_gid": "string|null",
  "uri": "string|null",
  "created_at": "iso8601",
  "updated_at": "iso8601",
  "deleted_at": "iso8601|null"
}
```

### 7.2 云端 VaultBlob

```json
{
  "user_id": "uuid",
  "device_id": "uuid",
  "version": 1,
  "vault_ciphertext": "base64",
  "vault_nonce": "base64",
  "vault_kdf": "json",
  "updated_at": "iso8601"
}
```

## 8. 页面结构

### 8.1 首版页面

- 欢迎页
- 创建密码页
- 解锁页
- 令牌列表页
- 导入页
- 粘贴导入页
- 扫码导入页
- 令牌详情页
- 云同步设置页
- 备份导出页
- 恢复导入页
- 安全设置页

### 8.2 UI 风格

- 深色为主，支持浅色模式
- 安全工具产品风格，避免花哨插画
- 首页强调验证码可读性与倒计时
- 所有按钮触控面积不低于 44x44

## 9. 版本路线

### v0.1 原型

- 单账户导入
- 本地生成验证码
- 无云同步

### v0.2 MVP

- 多账户管理
- 端到端加密云同步
- 加密备份恢复

### v0.3 增强版

- Face ID / 指纹解锁
- 多设备冲突处理
- 账户标签与搜索

## 10. 当前阻塞项

- 开发机尚未安装 Flutter SDK
- 需要确认云端是使用 Supabase 还是自建 API
- 需要决定首版是否支持 iOS 同步上线

## 11. 下一步实施顺序

1. 安装 Flutter SDK
2. 初始化 `steam-vault-app` 客户端工程
3. 实现本地加密与 Steam 验证码生成
4. 实现导入流程
5. 实现云同步与恢复
6. 补充集成测试与安全测试
