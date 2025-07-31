# 后台切回自动重连设置实现

## 概述
本次修改为Moonlight Android应用添加了后台切回自动重连功能的设置选项，用户可以通过设置界面控制是否启用该功能以及调整超时时间。新增了"永不超时"选项，当设置为0时可以一直自动重连。超时时间单位为分钟，范围0-60分钟。

## 修改内容

### 1. 设置界面 (preferences.xml)
在高级设置部分添加了两个新的设置项：
- **启用后台切回自动重连**：开关选项，控制是否启用后台切回自动重连功能
- **后台重连超时时间**：滑动条选项，允许用户调整超时时间（0-60分钟）
  - 0 = 永不超时，可以一直自动重连
  - 1-60分钟 = 自定义超时时间

### 2. 配置管理 (PreferenceConfiguration.java)
- 添加了新的设置项常量：
  - `BACKGROUND_RECONNECT_ENABLED_PREF_STRING`
  - `BACKGROUND_RECONNECT_TIMEOUT_PREF_STRING`
- 添加了默认值：
  - `DEFAULT_BACKGROUND_RECONNECT_ENABLED = true`
  - `DEFAULT_BACKGROUND_RECONNECT_TIMEOUT = 0` (永不超时，单位：分钟)
- 在Configuration类中添加了相应字段：
  - `backgroundReconnectEnabled`
  - `backgroundReconnectTimeout`
- 在readPreferences方法中添加了读取这些设置的代码

### 3. 自定义显示逻辑 (SeekBarPreference.java)
- 在SeekBarPreference中添加了特殊处理逻辑
- 当设置项为"seekbar_background_reconnect_timeout"且值为0时，显示"永不超时"
- 其他值正常显示分钟数

### 4. 字符串资源
- 英文 (strings.xml)：
  - `title_background_reconnect_enabled`
  - `summary_background_reconnect_enabled`
  - `title_background_reconnect_timeout`
  - `summary_background_reconnect_timeout` (包含"0 = never timeout"说明)
  - `suffix_background_reconnect_timeout_minutes`
- 中文 (strings.xml)：
  - 相应的中文字符串翻译 (包含"0 = 永不超时"说明)

### 5. 游戏逻辑 (Game.java)
- 删除了硬编码的`MAX_BACKGROUND_DURATION`常量
- 修改了`onResume`方法：
  - 检查`prefConfig.backgroundReconnectEnabled`设置
  - 将分钟单位转换为毫秒进行计算
  - 添加了对0值（永不超时）的特殊处理
  - 只有在启用功能时才进行重连检查
- 修改了`onPause`方法：
  - 只有在启用后台重连功能时才设置重连标志
  - 只有在启用功能时才保存连接参数

## 功能特性

### 开关控制
- 用户可以通过设置界面完全禁用后台切回自动重连功能
- 禁用后，应用进入后台时不会保存连接参数，切回前台时也不会尝试重连

### 超时时间调整
- **永不超时（0分钟）**：设置为0时，应用可以一直在后台等待，切回前台时立即重连
- **自定义超时时间**：用户可以通过滑动条调整超时时间，范围从1分钟到60分钟
- 如果后台时间超过设定值（非0），应用会自动断开连接并退出

### 依赖关系
- 超时时间设置依赖于启用开关，只有在启用后台重连功能时才能调整超时时间

### 用户界面优化
- 当滑块设置为0时，显示"永不超时"而不是"0分钟"
- 其他值正常显示分钟数，如"30分钟"
- 时间单位改为分钟，更符合用户习惯

## 使用说明

1. 打开Moonlight应用设置
2. 进入"高级设置"部分
3. 找到"启用后台切回自动重连"选项，根据需要开启或关闭
4. 如果启用了该功能，可以调整"后台重连超时时间"滑块：
   - 设置为0：永不超时，可以一直自动重连
   - 设置为1-60分钟：自定义超时时间
5. 设置会立即生效，无需重启应用

## 技术细节

### 设置存储
- 使用Android SharedPreferences存储设置
- 设置会在应用重启后保持

### 性能影响
- 禁用功能时不会保存连接参数，减少内存使用
- 超时检查只在onResume时进行，不会影响后台性能
- 设置为"永不超时"时，不会进行超时检查，性能最优

### 兼容性
- 向后兼容：现有用户默认启用该功能，超时时间为0（永不超时）
- 设置迁移：新用户会使用默认值，现有用户保持原有行为

### 特殊处理
- 当超时时间设置为0时，应用不会进行超时检查
- 时间单位统一使用分钟，内部计算时转换为毫秒
- 自定义显示逻辑确保用户界面友好，明确显示"永不超时"选项 