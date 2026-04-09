import os
import re

# Update strings.xml or create a new values-zh-rCN/strings.xml to ensure Chinese texts
strings_xml_path = "app/src/main/res/values/strings.xml"
with open(strings_xml_path, "r") as f:
    content = f.read()

# Replace or add the strings for Welcome, CreatePassword, Unlock, ChangePassword, TokenList, etc.
replacements = {
    # Welcome
    r'<string name="vault_brand_label">.*?</string>': r'<string name="vault_brand_label">SteamVault</string>',
    
    # Create Password
    r'<string name="create_password_modern_title">.*?</string>': r'<string name="create_password_modern_title">设置主密码</string>',
    r'<string name="create_password_modern_body">.*?</string>': r'<string name="create_password_modern_body">主密码用于解锁本地加密数据。我们无法替你找回。</string>',
    r'<string name="create_password_modern_caption">.*?</string>': r'<string name="create_password_modern_caption">请妥善保管您的主密码。</string>',
    r'<string name="create_password_modern_field_password">.*?</string>': r'<string name="create_password_modern_field_password">主密码</string>',
    r'<string name="create_password_modern_field_confirm">.*?</string>': r'<string name="create_password_modern_field_confirm">确认主密码</string>',
    r'<string name="create_password_modern_rule_title">.*?</string>': r'<string name="create_password_modern_rule_title">建议强度</string>',
    r'<string name="create_password_modern_rule_body">.*?</string>': r'<string name="create_password_modern_rule_body">为了您的资产安全，请满足以下条件：</string>',
    r'<string name="create_password_modern_rule_length">.*?</string>': r'<string name="create_password_modern_rule_length">至少 10 位</string>',
    r'<string name="create_password_modern_rule_mix">.*?</string>': r'<string name="create_password_modern_rule_mix">建议包含字母与数字</string>',
    r'<string name="create_password_modern_rule_match">.*?</string>': r'<string name="create_password_modern_rule_match">两次输入一致</string>',
    r'<string name="create_password_modern_action">.*?</string>': r'<string name="create_password_modern_action">完成设置</string>',
    r'<string name="create_password_modern_action_loading">.*?</string>': r'<string name="create_password_modern_action_loading">保存中...</string>',
    
    # Unlock
    r'<string name="unlock_modern_title">.*?</string>': r'<string name="unlock_modern_title">解锁令牌库</string>',
    r'<string name="unlock_modern_body">.*?</string>': r'<string name="unlock_modern_body">输入主密码以继续。</string>',
    r'<string name="unlock_modern_field_password">.*?</string>': r'<string name="unlock_modern_field_password">主密码</string>',
    r'<string name="unlock_modern_action">.*?</string>': r'<string name="unlock_modern_action">解锁</string>',
    r'<string name="unlock_modern_action_loading">.*?</string>': r'<string name="unlock_modern_action_loading">验证中...</string>',
    r'<string name="unlock_modern_biometric_action">.*?</string>': r'<string name="unlock_modern_biometric_action">使用生物识别解锁</string>',
    r'<string name="unlock_modern_restore_action">.*?</string>': r'<string name="unlock_modern_restore_action">从备份恢复</string>',
    
    # Change Password
    r'<string name="change_password_modern_title">.*?</string>': r'<string name="change_password_modern_title">修改主密码</string>',
    r'<string name="change_password_modern_body">.*?</string>': r'<string name="change_password_modern_body">修改后，你需要用新主密码解锁。</string>',
    r'<string name="change_password_modern_field_old">.*?</string>': r'<string name="change_password_modern_field_old">当前主密码</string>',
    r'<string name="change_password_modern_field_new">.*?</string>': r'<string name="change_password_modern_field_new">新主密码</string>',
    r'<string name="change_password_modern_field_confirm">.*?</string>': r'<string name="change_password_modern_field_confirm">确认新主密码</string>',
    r'<string name="change_password_modern_action">.*?</string>': r'<string name="change_password_modern_action">保存</string>',
    
    # Token List
    r'<string name="token_list_modern_title">.*?</string>': r'<string name="token_list_modern_title">令牌</string>',
    r'<string name="token_list_modern_body">.*?</string>': r'<string name="token_list_modern_body">点击卡片查看详情，长按或菜单可复制验证码。</string>',
    r'<string name="token_list_modern_empty_title">.*?</string>': r'<string name="token_list_modern_empty_title">还没有令牌</string>',
    r'<string name="token_list_modern_empty_body">.*?</string>': r'<string name="token_list_modern_empty_body">先添加一个 Steam 账号，验证码会在这里自动生成。</string>',
    r'<string name="token_list_modern_empty_primary">.*?</string>': r'<string name="token_list_modern_empty_primary">添加 Steam 账号</string>',
    r'<string name="token_list_modern_empty_secondary">.*?</string>': r'<string name="token_list_modern_empty_secondary">我有密钥，直接导入</string>',
    r'<string name="token_list_modern_account_label">.*?</string>': r'<string name="token_list_modern_account_label">账户</string>',
    r'<string name="token_list_modern_seconds_left">.*?</string>': r'<string name="token_list_modern_seconds_left">本轮剩余 %1$d 秒</string>',
    r'<string name="token_list_modern_copy">.*?</string>': r'<string name="token_list_modern_copy">复制验证码</string>',
    r'<string name="token_list_modern_trust_title">.*?</string>': r'<string name="token_list_modern_trust_title">快速操作</string>',
    r'<string name="token_list_modern_trust_body">.*?</string>': r'<string name="token_list_modern_trust_body">您还可以进行以下操作：</string>',
    r'<string name="token_list_modern_scan_login">.*?</string>': r'<string name="token_list_modern_scan_login">扫码登录</string>',
    r'<string name="token_list_modern_open_settings">.*?</string>': r'<string name="token_list_modern_open_settings">设置</string>',
}

for pattern, replacement in replacements.items():
    content = re.sub(pattern, replacement, content)

with open(strings_xml_path, "w") as f:
    f.write(content)
