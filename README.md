# weatherwatch

OPPO Watch 3 上的离线优先天气表盘应用。

手表通过蓝牙借手机上网，连接并不总是可用。所以这个 App 的核心不是"联网取天气"，
而是**缓存即真相来源**：界面永远先渲染本地快照，同步在后台跑完再刷新。
"不联网也能看"因此是架构的自然结果，而不是一条需要单独维护的降级分支。

## 设备与约束

| 项 | 实测值 |
|---|---|
| 机型 | OPPO Watch 3（`QUALCOMM OWW212`），ColorOS Watch |
| 系统 | Android 11 / API 30，**无 GMS** |
| 屏幕 | 372×430 px，density 2.0 → **186×215 dp**，圆角矩形 |
| 联网 | 手机网络经**蓝牙隧道**下发，`transports = [BLUETOOTH, VPN]`，接口 `tun0` |
| 链路 | 空响应 RTT ≈ 490 ms，2.2 KB JSON ≈ 960 ms |

这些数字来自 `probe/` 模块（见下），不是文档推测。它们直接决定了若干实现选择：

- **绝不按 transport 过滤网络。** `addTransportType(TRANSPORT_WIFI)` 之类的约束会把这块表判定为"无网络"。只看 `NET_CAPABILITY_INTERNET` + `VALIDATED`
- **一次请求拉全量数据。** RTT 接近 1 秒，拆成多次往返会让同步耗时线性放大
- **超时放宽到 15 s / 20 s**

## 模块

```
core/    纯 Kotlin/JVM，不碰任何 Android API
         数据模型、Open-Meteo 解析、时区换算、同步策略、城市搜索
         —— 最容易出错的逻辑都在这里，可以在 PC 上跑单测
watch/   Android 应用。Compose UI、原子文件缓存、HTTP 实现
probe/   M0 探针。零依赖，用来实测屏幕参数和蓝牙链路连通性
```

`core` 独立成 Kotlin-only 模块是刻意的：无线 adb 调试一轮要好几分钟，
把解析和策略逻辑放进可 JVM 单测的模块，省掉一大半装机验证。

## 关键设计决定

**缓存不用 Room，用原子文件。** 查询需求只有"按 cityId 取一条"，为此拖进 KSP
注解处理器不划算。写入走 `.tmp` + `rename`，手表被系统杀掉时不会留下半个文件；
解析失败直接删文件当无缓存处理，避免崩溃循环。

**不引 material / material3。** 其组件按手机尺寸设计，在 186 dp 宽的表盘上不可用
（一个默认 `Button` 就占掉 1/5 屏高）。UI 全部基于 `compose.foundation` + `BasicText`。

**23 个天气图标全部用 Canvas 绘制。** 由太阳/月牙/云/雨滴/雪点/闪电/雾线几个图元组合，
任意尺寸锐利，APK 里零图片资源。月牙用背景色挖出来——全局背景是纯黑，比 Path 布尔运算省。

**主屏的"现在"会随时间降级。** `current` 是抓取那一刻的实测值，永远冻结；
但同一份缓存里还躺着 24 小时逐时预报和 7 天日预报。`nowReading()` 按可用性挑：

| 缓存年龄 | 来源 | 主屏 |
|---|---|---|
| < 1 h | 实测 | `27°` · 体感 29° · 更新于 8 分钟前 |
| 1–24 h | 覆盖当前小时的逐时预报 | `26°` · **预报** · 更新于 5 小时前 |
| 超出逐时范围 | 今日日预报 | `23~33°` · **今日预报** · 已离线 1 天 |
| 今天已不在缓存 | 最后已知值 | 全灰 · 已离线 9 天 |

每一档都在界面上标明来源。拿预报冒充实况是不能接受的。体感温度只有实测值有，
其余档位一律不显示。

**没有后台同步。** 没有 `AlarmManager`、没有 `WorkManager`、没有开机自启。
只有两个触发点，都由用户动作直接引发：点击屏幕（永远放行）、打开 App
（45 分钟内不重复请求，夜间放宽到 3 小时）。ColorOS 杀后台很凶，
与其为不可靠的定时任务耗电，不如把抬腕这个动作本身当作触发器。

## 数据源

[Open-Meteo](https://open-meteo.com/)，免费、免 key、免注册。

- 天气：`api.open-meteo.com/v1/forecast`，整包约 2.2 KB
- 城市搜索：`geocoding-api.open-meteo.com/v1/search`，输入拼音，`language=zh` 让结果返回中文

两个参数不能动，改了会静默出错而不是报错：

- `timeformat=unixtime` —— 所有时间变成真 UTC 秒，绕开 ISO 字符串和本地时间歧义。
  注意 `daily.time` 是"本地零点对应的 UTC 瞬时"，取日期必须先加 `utc_offset_seconds`，
  否则整个七日预报错一天
- `forecast_hours=24` —— 去掉的话默认返回 7×24=168 条逐时数据，payload 涨到 10 KB 以上

数据源是抽象的（`WeatherSource` 接口），换源只需实现一个方法，UI / 缓存 / 策略都不用动。

## 开发环境

全绿色解压，不需要 Android Studio，不装模拟器：

| 组件 | 版本 |
|---|---|
| JDK | Temurin / Microsoft OpenJDK 17 |
| Gradle | 8.9（仓库自带 wrapper） |
| Android SDK | platform 34 + build-tools 34.0.0 + platform-tools |
| AGP / Kotlin | 8.6.1 / 2.0.21 |

`local.properties` 里写 `sdk.dir`，或设 `ANDROID_HOME`。

## 构建与装机

```powershell
.\build.ps1                          # 构建 watch 的 debug APK
.\gradlew.bat :watch:assembleRelease # release（R8 + 资源压缩，约 1.8 MB）
.\deploy.ps1 <手表IP>                # 无线 adb 安装并拉日志
.\deploy.ps1 <手表IP> watch release
```

OPPO Watch 3 是磁吸充电、没有 USB 数据口，只能走无线 adb：
手表 → 设置 → 关于 → 连点版本号开开发者选项 → 打开无线调试 → 记下 Wi-Fi IP。

debug 约 18 MB（未混淆的 Compose dex），release 约 1.8 MB。
两者用**同一把签名密钥**，可以互相覆盖安装。

## 测试

```powershell
.\gradlew.bat :core:test :watch:testDebugUnitTest
.\gradlew.bat :core:test -Plive=true    # 额外真打线上 API，发现接口漂移
```

全部在 JVM 上跑，不需要设备或模拟器。重点覆盖的是那些**在表上很难发现**的问题：
时区导致日期整体错一天、同一天气码的昼夜图标、德语 Locale 下经纬度变成逗号小数点、
预报边缘的 null 值、gzip 未解压、缓存放久后渲染过去的时间点。

`LiveApiTest` 默认跳过，用 `-Plive=true` 开启。它打真实接口，
用来发现 fixture 测不出的线上 API 变更。

## CI 与发布

`.github/workflows/release-apk.yml`：push 到 `main` 且**改动影响 APK** 时，
跑单测 → 构建 release → 校验签名指纹 → 发布 GitHub Release。
改 README 不会产出新版本。

版本号用 `github.run_number`，tag 形如 `v0.1.42`。

### 一次性设置：签名密钥

CI 必须用和本地**同一把**密钥，否则产出的 APK 装不到已装本地包的表上
（runner 自带的 debug keystore 是另一把）。

```powershell
# 1. 把本机调试密钥转成 base64 并复制到剪贴板
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\.android\debug.keystore")) | Set-Clipboard
```

2. GitHub 仓库 → Settings → Secrets and variables → Actions → New repository secret
3. 名称 `DEBUG_KEYSTORE_BASE64`，值粘贴剪贴板内容

密钥本身不入库（`.gitignore` 里排除了 `*.keystore`）。
workflow 有一步会校验签名指纹，Secret 传错会直接失败，
而不是发布一堆看起来正常、实际装不上的包：

```
SHA-256  2F:95:A3:42:B0:EC:66:05:C6:1E:4D:DF:74:D7:34:10:
         71:50:55:CC:87:48:F3:BE:5B:23:53:76:4D:72:25:77
```

密钥解析顺序（见 `watch/build.gradle.kts`）：
`WW_KEYSTORE` 环境变量 → 仓库根 `keystore/debug.keystore` → `~/.android/debug.keystore`。

## 已知限制

- **城市搜索只能输入英文/拼音。** Open-Meteo 的 geocoding 接口对中文查询支持不稳定，
  而表上切中文输入法也不方便。结果显示为中文
- **本地构建的 versionCode 固定为 1。** 装过 CI 版本后要用本地包覆盖，
  需要同样设 `WW_VERSION_CODE` 环境变量，否则会被当作降级安装拒绝
- **weather.com 未接入。** 其 `api.weather.com` 接口需要商用 key，
  数据源抽象已就位，拿到 key 后加一个实现即可
- 逐时曲线页、详情页（湿度/风/气压/UV/日出日落）尚未实现，数据已在缓存里
