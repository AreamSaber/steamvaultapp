import re

file_path = "app/src/main/res/values/strings_frontend_modern.xml"
with open(file_path, "r") as f:
    content = f.read()

strings_to_add = """
    <!-- Change Password -->
    <string name="change_password_modern_title">修改主密码</string>
    <string name="change_password_modern_body">修改后，你需要用新主密码解锁。</string>
    <string name="change_password_modern_field_new">新主密码</string>
    <string name="change_password_modern_field_confirm">确认新主密码</string>
    <string name="change_password_modern_action">保存</string>
    
    <!-- Unlock -->
    <string name="unlock_modern_title">解锁令牌库</string>
    <string name="unlock_modern_body">输入主密码以继续。</string>
    <string name="unlock_modern_biometric_action">使用生物识别解锁</string>
    <string name="unlock_modern_action_loading">验证中...</string>
    <string name="unlock_modern_action">解锁</string>
    <string name="unlock_modern_restore_action">从备份恢复</string>
    
    <!-- Backup Export -->
    <string name="backup_export_modern_body">导出加密备份文件以防数据丢失。</string>
    <string name="backup_export_modern_card_title">导出注意事项</string>
    <string name="backup_export_modern_card_body">备份文件将包含所有令牌。请妥善保存该文件。</string>
    <string name="backup_export_modern_caution">请勿将备份文件发送给任何陌生人！</string>
    <string name="backup_export_modern_action_loading">生成中...</string>
    <string name="backup_export_modern_action">导出备份文件</string>

    <!-- Backup Restore -->
    <string name="backup_restore_modern_body">从先前导出的备份文件中恢复所有令牌数据。</string>
    <string name="backup_restore_modern_card_title">恢复注意事项</string>
    <string name="backup_restore_modern_card_body">恢复操作将覆盖当前的令牌库，并需要输入备份时的主密码。</string>
    <string name="backup_restore_modern_caution">恢复前建议先导出当前数据的备份以防万一。</string>
    <string name="backup_restore_modern_action_loading">恢复中...</string>
    <string name="backup_restore_modern_action">选择备份文件</string>
"""

content = content.replace("</resources>", strings_to_add + "\n</resources>")

with open(file_path, "w") as f:
    f.write(content)
