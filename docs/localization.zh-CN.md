# 三语与术语

同时维护 `zh_cn`、`en_us`、`ja_jp`，共享能力 ID 与玩法规则。用日文原作名称校准含义，用各语言的自然表达呈现。

项目名为 **Cursed Oath**，简体中文为 **咒誓**，日文保留英文名。内部标识为 `cursed-oath`。

## 译名依据

日文优先原作正文与作者修订，其次官方动画和附勘误的公式书；英文优先 VIZ 漫画；简体中文优先正式出版或授权字幕。采用其他授权版本时记录版本差异，繁简转换不能直接视为已核实的简中译名。

词表中的 **“暂”表示尚未直接核实的工作译名**，只在文档中标注。其他条目的出处列在表后；授权衍生作品只支持其名称，不证明漫画机制。概念 ID 用于文档对照，不表示能力已经注册或实现。

| 概念 ID                    | ja_jp            | zh_cn                | en_us                                | 名称依据                        |
| -------------------------- | ---------------- | -------------------- | ------------------------------------ | ------------------------------- |
| `limitless`                | 無下限呪術       | 无下限咒术（暂）     | Limitless                            | L1、L8                          |
| `blue`                     | 術式順転「蒼」   | 术式顺转「苍」（暂） | Cursed Technique Lapse: Blue（暂）   | L2                              |
| `red`                      | 術式反転「赫」   | 术式反转「赫」（暂） | Cursed Technique Reversal: Red（暂） | L2                              |
| `purple`                   | 虚式「茈」       | 虚式「茈」（暂）     | Hollow Purple                        | L2、L3、L5                      |
| `unlimited_void`           | 無量空処         | 无量空处（暂）       | Unlimited Void                       | L3、L4                          |
| `shrine`                   | 御厨子（暂）     | 御厨子（暂）         | Shrine（暂）                         | 待核正文                        |
| `dismantle`                | 解               | 解（暂）             | Dismantle（暂）                      | L7 支持日文名称；使用者差异另核 |
| `cleave`                   | 捌（暂）         | 捌（暂）             | Cleave（暂）                         | 待核正文                        |
| `furnace`                  | 竈（暂）         | 灶（暂）             | Furnace（暂）                        | 正式写法、读音与英译待核        |
| `malevolent_shrine`        | 伏魔御厨子（暂） | 伏魔御厨子（暂）     | Malevolent Shrine                    | L1；日文厨/廚字形待核版本       |
| `black_flash`              | 黒閃             | 黑闪（暂）           | Black Flash                          | L1、L2                          |
| `domain_amplification`     | 領域展延         | 领域展延（暂）       | Domain Amplification                 | L3                              |
| `reverse_cursed_technique` | 反転術式         | 反转术式（暂）       | Reverse Cursed Technique（暂）       | L6；与下一行分别核验            |
| `technique_reversal`       | 術式反転         | 术式反转（暂）       | Cursed Technique Reversal（暂）      | L2 支持日文分类                 |
| `simple_domain`            | 簡易領域（暂）   | 简易领域（暂）       | Simple Domain（暂）                  | 待核正文                        |

- **L1：**[VIZ 官方术式投票](https://www.viz.com/blog/posts/jujutsu-kaisen-poll-results-april-2022)，用于对应英文名称。
- **L2：**[集英社导演访谈](https://www.bungei.shueisha.co.jp/interview/jujutsutaidan1/)，用于黑闪与苍/赫/茈的日文名称。
- **L3：**[集英社游戏英文规则书](https://shueisha-games.com/wp-content/uploads/2024/05/96bd33ca03eb0b23834861b2301241ef.pdf)，用于 Hollow Purple、Unlimited Void、Domain Amplification；桌游规则不成为原作能力证据。
- **L4：**[万代授权卡片「無量空処」](https://www.unionarena-tcg.com/jp/cardlist/detail_iframe.php?card_no=UA02BT%2FJJK-1-028)。
- **L5：**[万代授权卡片「虚式『茈』」](https://www.unionarena-tcg.com/jp/cardlist/detail_iframe.php?card_no=UA02BT%2FJJK-1-029)。
- **L6：**[动画涩谷篇人物页](https://jujutsukaisen.jp/character/shibuyajihen.php)，家入硝子条目的「反転術式」。
- **L7：**[Jump 单行本列表](https://www.shonenjump.com/j/rensai/_list/jujutsu/)，第 30 卷简介中的术式「解」。
- **L8：**[动画怀玉篇人物页](https://jujutsukaisen.jp/character/category6.php)，五条悟介绍明确使用「無下限呪術」。

简体授权正文或字幕尚待直接核验。新增来源记录语种、发行方、版本及卷/话、时间点或页码；未核实名称保留工作译名和文档中的“暂”标记。发布前复核异名与依据。

## 文件与代码

资源路径为：

```text
src/client/resources/assets/cursed-oath/lang/
  en_us.json
  zh_cn.json
  ja_jp.json
```

`en_us` 为 Minecraft 默认回退语言，始终保持完整。文件使用 UTF-8、Tab 缩进并按键名排序；每个键和占位符在三份文件同步维护。

玩家可见名称、提示、按键、状态及拒绝原因均使用翻译键，例如 `ability.cursed-oath.limitless.blue`、`hud.cursed-oath.preparing`、`message.cursed-oath.busy`。显示名称变化不修改注册 ID、存档键或协议值。

用 `Component.translatable` 构造整句文本，参数采用可重排的 `%1$s`、`%2$s`。技能名作为可翻译子组件，按键名读取实际绑定；占位符允许调整语序；数字的分组、小数等本地化格式需在客户端另行处理。服务端传结构化原因或翻译组件，不调用客户端语言服务。[Fabric Text and Translations](https://docs.fabricmc.net/develop/text-and-translations)

## 验证

`LocalizationTest` 检查 JSON 字符串值、重复键、非空文本、三语键集、术式全称、轮盘短名称和索引占位符。键是否仍在使用需结合注册及动态生成规则判断，不能仅按文本搜索结果删除。

HUD 状态、轮盘全称和操作提示按字体宽度换行；HUD 术式短名称和轮盘按钮为单行，译文须控制长度。客户端测试覆盖三语 HUD、标准窗口与 640×480 窗口轮盘；大 UI 比例、罕见汉字、输入法和新增长译名仍需实机检查。

审校时区分反转术式与术式反转、极之番与最大输出、御厨子与伏魔御厨子。检查提示是否准确描述当前行为，不把计划或原作能力写成已实现功能。
