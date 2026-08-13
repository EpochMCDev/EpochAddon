# EpochAddon

Epoch MC 国战服务器服务端插件集合（Paper 1.20+）。

## 模块结构

| 模块 | 说明 |
| --- | --- |
| `common` | EpochCommon 共享核心库（Kotlin），被打包为共享 Jar 供各插件依赖 |
| `EpochMarket` | EpochMarket 市场插件：分类商品、买卖、分类额度限制 |
| `plugin-two` | 示例插件 |
| `Slimefun4` | 定制版 Slimefun4（fork），内置 EpochRebirth 重生系统（复活图腾/缚魂瓶/灵魂机制） |

## 构建

要求 JDK 25（Slimefun4 子模块为独立 Java 21 构建）。

```bash
./gradlew build
```

构建产物位于各模块 `build/libs/`。

## 依赖

- Paper API（版本见 `gradle.properties` 的 `apiVersion`）
- Slimefun4 模块自带版本目录 `Slimefun4/gradle/libs.versions.toml`

## License

- 本仓库自研模块：见各模块说明
- `Slimefun4/` 目录基于 [SlimefunGuguProject/Slimefun4](https://github.com/SlimefunGuguProject/Slimefun4) fork，遵循其 `Slimefun4/LICENSE`（GPL-3.0）
