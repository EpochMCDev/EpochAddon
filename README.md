# EpochAddon

EpochMC 国战服务器使用的 Paper 26.2 插件集合。

## 模块

| Gradle 模块 | 插件 | 说明 |
| --- | --- | --- |
| `common` | EpochCommon | 共享服务与统一计分板，支持 Vault、Nodes 和 PlaceholderAPI |
| `epoch-market` | EpochMarket | 分类收购市场、每日额度、确认 GUI 和可配置音效 |
| `epoch-minerals` | EpochMinerals | 挖掘积分、周期矿物奖励、矿脉/指令增益和计分板模块 |
| `Slimefun4` | Slimefun | 定制 Slimefun4，内置 EpochRebirth 复活图腾、缚魂瓶、瓶装魂和治愈系统 |

## 环境

- EpochCommon、EpochMarket、EpochMinerals：JDK 25。
- Slimefun4：JDK 21。
- Paper/Leaf API 版本由根目录 `gradle.properties` 的 `apiVersion` 控制。

## 本地构建

构建全部插件：

```bash
./gradlew build
```

按模块构建可加载的非 `plain` JAR：

```bash
./gradlew :common:shadowJar
./gradlew :epoch-market:shadowJar
./gradlew :common:shadowJar :epoch-minerals:shadowJar
./gradlew :common:shadowJar :Slimefun4:shadowJar
```

构建产物位于对应模块的 `build/libs/`。

## GitHub Actions

仓库提供 `Build Plugins` 手动工作流。在 GitHub 的 Actions 页面运行该工作流，可选择构建全部插件或单独构建 `common`、`epoch-market`、`epoch-minerals`、`slimefun4`。完成后，可加载 JAR 会作为 workflow artifact 上传并保留 14 天。

## 运行依赖

- EpochMinerals：硬依赖 EpochCommon。
- EpochMarket：硬依赖 Vault；可选接入 CraftEngine、Slimefun。
- 定制 Slimefun：硬依赖 Vault、EpochCommon。
- EpochCommon 计分板：可选接入 Vault、Nodes、PlaceholderAPI；温度显示使用 RealisticSeasons 的 `%rs_temperature%`。

## 配置

- 统一计分板配置：`plugins/EpochCommon/config.yml`。
- 挖矿模块配置：`plugins/EpochMinerals/config.yml`。
- 重生模块文案：`plugins/Slimefun/epoch-rebirth-lang.yml`。
- 计分板完整示例：[docs/计分板修改示例.md](docs/计分板修改示例.md)。

## License

- 本仓库自研模块：见各模块说明。
- `Slimefun4/` 基于 [SlimefunGuguProject/Slimefun4](https://github.com/SlimefunGuguProject/Slimefun4) 定制，遵循 `Slimefun4/LICENSE`（GPL-3.0）。
