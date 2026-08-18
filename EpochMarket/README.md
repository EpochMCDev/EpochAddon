# EpochMarket

适用于 Paper `26.2` 与 Java 25 的每日限额收购插件。玩家通过箱子 GUI 选择市场、选择出售数量并由 Vault 经济系统结算。

## 安装

1. 使用 Java 25 运行 Paper 26.2。
2. 在服务器安装 Vault 和一个 Vault 经济提供者。
3. 将 `build/libs/EpochMarket-1.0.0-SNAPSHOT.jar` 放到 `plugins` 目录。
4. 可选安装 CraftEngine 26.7.4 与/或 Slimefun。未安装时对应配置条目会显示为“暂不可用”，不影响原版物品市场。
5. 启动服务器后编辑 `plugins/EpochMarket/config.yml`、`markets/*.yml` 和 `lang/*.yml`，然后执行 `/epochmarket reload`。

## 命令

| 命令 | 权限 | 作用 |
| --- | --- | --- |
| `/epochmarket` | `epochmarket.use` | 打开市场选择 GUI。 |
| `/epochmarket open <market-id>` | `epochmarket.use` | 直接打开一个市场。 |
| `/epochmarket reload` | `epochmarket.admin` | 重载配置与语言文件，并关闭当前市场 GUI。 |
| `/epochmarket quota <player> <market-id> <entry-id>` | `epochmarket.admin` | 查询玩家当日已售和剩余额度。 |
| `/epochmarket reset <player> <market-id> <entry-id>` | `epochmarket.admin` | 重置玩家该条目当日额度。 |

市场的 `permission` 留空即公开。`epochmarket.admin` 可绕过市场权限。

## 市场配置

每个 `plugins/EpochMarket/markets/*.yml` 文件代表一个箱子 GUI，文件名为市场 ID。每个条目拥有自己的 `daily-limit`、`unit-price`、槽位和物品来源。

```yaml
title-key: markets.minerals.title
rows: 3
permission: ""
selector:
  icon: IRON_INGOT
  name-key: markets.minerals.selector-name
  lore-key: markets.minerals.selector-lore
entries:
  iron_ore:
    source: VANILLA
    item-id: IRON_ORE
    icon: IRON_ORE
    slot: 11
    unit-price: 8.0
    daily-limit: 256
    name-key: markets.minerals.entries.iron_ore.name
```

`source` 与 `item-id` 的规则：

| 来源 | `source` | `item-id` |
| --- | --- | --- |
| 原版物品 | `VANILLA` | Bukkit 材料名，例如 `IRON_ORE`。 |
| CraftEngine | `CRAFT_ENGINE` | CraftEngine 自定义物品 ID，例如 `epoch:crystal`。 |
| Slimefun | `SLIMEFUN` | Slimefun 或附属的物品 ID，例如 `COPPER_DUST`。 |

原版条目不会收购具有 CraftEngine 或 Slimefun 标识的同材质自定义物品。相同物品出现在不同市场或不同条目时，额度互相独立。

市场可以额外配置 `rotation` 区域。它会在每个周期从 `candidates` 中无重复地抽取商品，按配置顺序填入 `slots`；同一周期内所有玩家看到的结果一致，服务器重启也不会改变结果。周期从 `reset-timezone` 的当地日期零点开始。

```yaml
rotation:
  cycle-days: 3
  slots: [0, 1, 2, 9, 10, 11]
  candidates:
    seasonal_crop:
    source: CRAFT_ENGINE
    item-id: epoch:seasonal_crop
    icon: epoch:seasonal_crop
    unit-price: 12.0
    daily-limit: 256
    name-key: markets.plants.rotation.seasonal_crop.name
```

`rotation.slots` 的数量决定轮换位数量；候选数量必须不少于槽位数量。`source: CRAFT_ENGINE` 使用 CraftEngine 自定义物品 ID，CraftEngine 未安装或 ID 不存在时，该轮换位会显示为暂不可用。

`icon` 支持原版材料和 CraftEngine 物品。原版写法保持为 `icon: WHEAT`；当条目的 `source` 为 `CRAFT_ENGINE` 时，非原版材料字符串会按 CraftEngine ID 解析。也可以在任何图标位置使用显式写法：

```yaml
icon:
  source: CRAFT_ENGINE
  item-id: epoch:seasonal_crop
```

图标只影响 GUI 显示，实际收购匹配仍以条目的 `source` 和 `item-id` 为准。

## 数据与每日刷新

每日额度保存在 `plugins/EpochMarket/market.db`。记录以“玩家 UUID + 市场 ID + 条目 ID + 当地日期”为键，因此服务重启不会丢失额度，进入新的一天时会自然从零开始。

`config.yml` 的 `reset-timezone` 控制日界线，默认 `Asia/Shanghai`。出售时只会扫描玩家的主背包和快捷栏共 36 格，不会扣除副手、装备栏或末影箱物品。

## 构建

```powershell
.\gradlew.bat test shadowJar
```

构建后的单文件插件位于 `build/libs/EpochMarket-1.0.0-SNAPSHOT.jar`。
