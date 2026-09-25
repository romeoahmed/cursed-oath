# 技术架构

本文说明代码职责、状态所有权和维护约束。玩法数值见[战斗规格](combat.zh-CN.md)，美术与验收标准见[表现制作](presentation.zh-CN.md)。

## 技术基线

| 技术                     | 仓库版本                                                             | 用途                             |
| ------------------------ | -------------------------------------------------------------------- | -------------------------------- |
| Minecraft / Fabric       | 26.3 / Loader 0.19.5 / API 0.161.0+26.3                              | 原生生命周期与未混淆名称         |
| Java / Kotlin            | JDK 25 / Kotlin 2.4.20 / FLK 1.14.1+kotlin.2.4.20                    | Kotlin 行为实现、Java Mixin      |
| Gradle / Loom            | Wrapper 9.7.1 / Loom 1.18.2                                          | 单工程、环境源集分离、配置缓存   |
| Player Animation Library | 1.2.7+mc.26.3                                                        | 原版玩家动作层                   |
| 格式与分析               | Spotless 8.10.2、ktlint 1.8.0、Palantir 2.98.0、Detekt 2.0.0-alpha.6 | 默认格式化、类型分析、警告即失败 |
| 测试                     | kotlin-test-junit5、JUnit BOM 6.1.3、Kover 0.9.9、Fabric GameTest    | 规则、世界交互与客户端验证       |

版本以 [gradle.properties](../gradle.properties)、[build.gradle.kts](../build.gradle.kts)、[Wrapper](../gradle/wrapper/gradle-wrapper.properties) 为准；安装约束见 [fabric.mod.json](../src/main/resources/fabric.mod.json)。依赖不打包进成品 JAR。`kotlin.jvmToolchain(25)` 统一编译目标，不启用预览语法。

升级时核对 [Kotlin/Gradle 兼容表](https://kotlinlang.org/docs/gradle-configure-project.html)、实际解析依赖和构建结果。修改版本敏感调用前运行 `genSources`，直接检查目标 Minecraft 源码与 Fabric 构件；其他版本的指南不能替代签名核验。

## 目录与依赖

包根为 `io.github.romeoahmed.cursedoath`，mod ID 为 `cursed-oath`。源集位置见 [AGENTS.md](../AGENTS.md)。

| 包                           | 职责                                                             |
| ---------------------------- | ---------------------------------------------------------------- |
| `combat`                     | 玩家运行状态、咒力账本、近战、施法资格与持久附件                 |
| `technique`                  | 术式释放、飞行实体、吸引场和中性防御                             |
| `domain`                     | 领域索引、生命周期、边界、必中与保护                             |
| `world`                      | 扫掠几何、就绪区块查询、分批地形破坏                             |
| `network` / `command`        | 请求、快照、事件协议与练习命令                                   |
| `client/input` / `gui`       | 按键请求、HUD、术式轮盘                                          |
| `client/animation` / `sound` | PAL 动作与本地领域音效                                           |
| `client/render`              | 共用几何、管线和事件表现；`domain`、`limitless` 子包存放专属视觉 |

Fabric Data Attachments 管理附件状态，PAL 管理玩家动画。按职责建包，保持单模块；新增依赖须说明用途、兼容版本与安装侧别。类型文件与类型同名，测试使用 `Test`、`GameTest`、`ClientGameTest` 后缀。重命名保留注册键、存档键和协议编号。[Kotlin 约定](https://kotlinlang.org/docs/coding-conventions.html#source-code-organization)、[Fabric 项目结构](https://docs.fabricmc.net/develop/getting-started/project-structure)

## 状态与联网

服务端拥有咒力、动作、防御、伤害和地形队列；客户端提交意图并显示确认结果。世界访问留在所属线程，客户端提取阶段生成渲染快照，提交阶段不读取活动实体。

- `Fighter` 绑定一个玩家实体及其世界。死亡、断线、换维度或取消会结束准备，保留已付费用与恢复负担。
- `SorcererProfile`、`SorcererResources` 是 Codec 校验的不可变持久附件，保存资格、余额、恢复和熔断。活动施法及费用预留不续存。
- 资源快照每四 tick 检查，仅变化时发给本人；加入、重生和换维度强制同步。个人资源附件不重复自动同步；信息过载和反领域表现使用临时、全观察者同步附件。
- 请求携带连接会话 UUID、单调序号和显式 wire ID，`RequestGate` 拒绝重放并限频。确认事件包含 UUID、维度、服务端 tick、阶段和位置，按发生位置广播；客户端有界去重。
- 飞行位置、插值和移除沿用原生实体跟踪。协议使用 `StreamCodec.composite`，稳定字段顺序与字节编码由兼容性测试约束。

接口参考：[Data Attachments](https://docs.fabricmc.net/develop/serialization/data-attachments)、[Networking](https://docs.fabricmc.net/develop/networking)、[World Rendering](https://docs.fabricmc.net/develop/rendering/world)。

## 命中与地形

`TechniqueProjectile` 是密封基类：`TechniqueOrb` 承载苍/赫，`TechniqueWave` 承载茈/解，`PurpleFlight` 独立推进茈。实体共享 `SynchedEntityData` 和 `SteppedInterpolationHandler`；渲染裁剪范围独立于碰撞箱。苍、赫、解绑定释放时的玩家实体；茈保留原始归属并独立存续。不能用原生所有者查询判断旧施术者是否存活，因为 UUID 查询可能找到重生后的实体。

| 攻击    | 接触与遮挡                                                                                                                                                                              |
| ------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 苍 / 赫 | 逐段使用 `clipIncludingBorder`、`ProjectileUtil.getManyEntityHitResult`；苍的场由可见核心 tick 驱动。赫用 `ServerExplosion.getSeenPercent` 采样冲击时遮挡，以原生伤害和三维 `push` 结算 |
| 茈      | `SphereSweep` 在 AABB 面穿越点间求解距离二次式，判定连续球体接触；不等待挖掘，也不追加遮挡射线                                                                                          |
| 解      | 局部坐标中的保守旋转盒体扫掠；前方短段挖掘后推进，等待时仍检查主体接触                                                                                                                  |
| 捌      | `CleaveLattice` 共用实体、地形和显示网格；初始接触立即判伤，其余目标在开路后复核位置与遮挡                                                                                              |

茈/解纳入目标一 tick 的相对运动，宽相位额外覆盖四格移动；任意高速位移或传送不在保证范围内。扫掠缓冲只在所属服务器线程顺序复用。伤害回调可能移除攻击实体、关闭地形任务或改变后续目标；每次回调后复核有效性。

`TerrainDestruction` 在普通破坏术式准备时预留工作槽，以服务器为单位轮转处理。领域按需申请地形任务，必中不依赖申请结果。

| 服务器总限制                       | 当前值    |
| ---------------------------------- | --------- |
| 工作槽（含准备中的施法）           | 16        |
| 每 tick 几何与提交访问             | 16,384    |
| 每 tick 直接移除方块               | 1,024     |
| 协作式时间预算                     | 4 ms/tick |
| 未释放任务、球形挖掘与普通切割寿命 | 600 tick  |

球形挖掘按曼哈顿顺序向外枚举；普通切割增量筛选后按前进深度排序。茈逐段直接枚举并排除上段覆盖位置，每发最多 32 段，队列上限 64 段。每次几何访问及提交都计入预算，提交时重新读取方块状态。

破坏通过 `Level.destroyBlock` 与 Fabric BEFORE/CANCELED/AFTER 事件完成；回调关闭任务或更换方块后放弃本次提交。容器、流体、不可破坏块及权限否决保留。解/捌从原始攻击后缘发出平行遮挡射线，茈/球形挖掘跳过保护块。普通破坏遵循 `minecraft:block_drops`；茈与御厨子不掉落物品。

`ServerChunkCache.getChunkNow` 检查实际就绪区块，不以票据资格代替就绪状态。飞行术式在非实体模拟区块前结束。苍/赫及普通切割在所有者失效、区块不可用或超时时终止；茈封闭提交后继续处理有限队列，跳过已卸载区块，不强制加载。已提交伤害和破坏不回滚。

时间预算无法中断单次几何检查、射线或方块连锁更新；预算内调度不等于整次 tick 耗时有硬上限。测量方法见[性能验收](presentation.zh-CN.md#验收与性能)。

## 领域生命周期

`DomainEntity` 是不存盘的跟踪锚点，同步术式、半径、开始时间、所有者和外壳强度；位置与朝向沿用实体跟踪。`DomainIndex` 通过 Fabric 实体加载事件登记，原生移除回调清理。每个 Level 的临时附件独立持有索引，客户端与集成服务器不共享可变集合。

`Domains` 在服务器 tick 尾部依次处理失效、外壳侵蚀、相持、保护和必中。所有重叠领域参与；伤害前复核当前目标与来源。信息过载按全部有效来源重新汇总，结束一个领域不能清除另一个仍有效的控制。

简易领域先收集受压保护，再在目标批次结束后统一扣强度，避免目标数量或顺序改变剥离速度。接触豁免不消耗保护强度。领域移除关闭地形工作、更新控制并持久化熔断，不重新创建 `Fighter`。

`DomainBoundary` 提供球面包含与穿越查询。移动钩子和服务器玩家/坐骑包共同约束跨界；投射物选最近外壳后再检查接触点前的地形。茈按完整扫掠球体判壳，记录本发命中 UUID 后继续飞行。涉及移除的遍历使用快照。

## 渲染与动作

`CastingAnimation` 隔离 PAL，按确认阶段播放准备和释放动作，动画不驱动伤害或位移。`LimitlessEffects` 生成不可变形态：`EnergySurface` 用缓存球面、分块顶点色及深度写入绘制主体；`EnergyTrails` 绘制加色光晕、流带和尾迹。空间相位与调色板缓存，融合时更新颜色；48 格外降低细节。苍的碎屑用有限原生 BLOCK 粒子，暂停时停止发射，换世界时清空。

几何通过 `SubmitNodeCollector`、原生 `RenderPipeline` 和 `OitPipelineSet` 提交。普通效果复用原版 shader；无量空处的片元程序只采样全景纹理并处理显现和接缝。Mod 不持有 Vulkan 句柄或插入 GPU 等待，修改混合方式时同时核对深度写入与透明排序。[Minecraft 渲染说明](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3)、[GLSL 规范](https://github.com/KhronosGroup/GLSL)

御厨子在资源重载时读取预变换、预着色的模型面，使用不透明深度管线。装饰斩线由领域年龄与局部空间格决定，不创建斩击实体或逐斩网络消息；近镜头淡出，减闪光选项来自提取快照。本地斩击音按固定节奏产生。

无量空处的内部球面随相机平移并保持施术朝向，外壳固定于领域中心。环境 Mixin 读取当前提取帧，隐藏内部地形、天气、云和方块实体，保留原世界碰撞与模拟；退出后恢复正常提交。

## 验证

命令见 [AGENTS.md](../AGENTS.md)，CI 见 [build.yml](../.github/workflows/build.yml)。Spotless、四个源集的 Detekt 类型分析和 scoped Kover 接入构建；编译警告视为错误。[Kotlin 代码检查](https://kotlinlang.org/docs/jvm-code-analysis.html)、[Detekt](https://detekt.dev/docs/gettingstarted/gradle/)

| 层次                       | 检查内容                                             |
| -------------------------- | ---------------------------------------------------- |
| JUnit                      | 咒力账本、协议/存档兼容、三语占位符、几何边界        |
| 服务端 GameTest            | 原生攻击与伤害、保护、资源、领域、地形预算与生命周期 |
| 客户端 GameTest            | 真实输入与联网、飞行、姿态、三语界面、效果出现和消失 |
| Python unittest / 导出检查 | 模型变换、面方向、材质、错误输入及编辑源与导出一致性 |
| 实机与性能验收             | 画面质量、设备兼容、多人行为与持续负载               |

断言以外部行为为主；账本、稳定协议字节和几何边界保留直接测试，不固定私有布局或遍历顺序。Kover 的 90% 行覆盖只约束 `CursedEnergy` 与 `RequestGate`。`kotlin-test-junit5` 提供测试依赖，JUnit BOM 统一版本，GameTest 编译关联 main。

服务端夹具由 `CombatFixtures`、`TestLifecycle` 按测试持有；原生完成监听器在成功、失败及超时时清理。测试 accessor 仅用于访问 `GameTestHelper.testInfo`，不进入发布 JAR。Fabric 事件只注册一次，临时回调随测试结束移除；共享容量通过测试环境分组隔离，测试不得调用全局清理。

异步行为使用测试序列等待；只有隔离攻击/碰撞钩子的夹具手动推进实体。位移测试保留物理模拟。客户端使用同次运行基线、固定机位与分辨率，旁观者用于独立效果，普通玩家用于输入和施法。像素断言不评价美术质量。[Fabric 自动测试](https://docs.fabricmc.net/develop/automatic-testing)

先 `build`，再完整运行 `runClientGameTest`，防止服务端清理删除截图。客户端任务先做 30 秒原生 Vulkan 设备/显示表面预检，再断言游戏实际后端。Linux CI 使用 Xvfb/X11 与 Mesa Lavapipe；设备直接创建只存在于测试预检。[SDL 驱动](https://wiki.libsdl.org/SDL3/SDL_HINT_VIDEO_DRIVER)、[Vulkan 驱动选择](https://vulkan.lunarg.com/doc/view/latest/linux/LoaderDriverInterface.html)

CI 使用仓库只读权限，同一分支的新运行取消旧运行；无论成败均收集现有报告、日志和截图，成功后上传 JAR。共享 CI 耗时不作为性能基准。模型工具只依赖 Python 标准库，制作检查见[模型说明](../art/shrine/README.md)。
