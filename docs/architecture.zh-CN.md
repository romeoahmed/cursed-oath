# 技术架构

本文记录当前实现及维护约束。玩法参数见[战斗规格](combat.zh-CN.md#当前原型)，制作目标见[设计总纲](design.zh-CN.md)。

## 技术基线

| 技术                     | 锁定版本                                                             | 用途                                       |
| ------------------------ | -------------------------------------------------------------------- | ------------------------------------------ |
| Minecraft / Fabric       | 26.3 / Loader 0.19.5 / API 0.161.0+26.3                              | 单一 Fabric 目标、原生生命周期与未混淆名称 |
| Java / Kotlin            | JDK 25 / Kotlin 2.4.20 / FLK 1.14.1+kotlin.2.4.20                    | Kotlin 实现行为，Java 承担两处 Mixin       |
| Gradle / Loom            | Wrapper 9.7.1 / Loom 1.18.2                                          | 单工程、common/client 源集分离、配置缓存   |
| Player Animation Library | 1.2.7+mc.26.3                                                        | 原版玩家动作层                             |
| 格式与分析               | Spotless 8.10.2、ktlint 1.8.0、Palantir 2.98.0、Detekt 2.0.0-alpha.6 | 默认格式化、类型分析、警告即失败           |
| 测试                     | kotlin-test-junit5、JUnit BOM 6.1.3、Kover 0.9.8、Fabric GameTest    | 单元、世界交互与客户端测试                 |

版本由 [gradle.properties](../gradle.properties)、[build.gradle.kts](../build.gradle.kts) 和 [Gradle Wrapper](../gradle/wrapper/gradle-wrapper.properties) 声明；安装要求见 [fabric.mod.json](../src/main/resources/fabric.mod.json)。运行依赖通过 Gradle 解析，不打包进成品 JAR。

Kotlin 2.4.20 兼容表的 Gradle 上限为 9.7.0；仓库采用的修复版 9.7.1 需以实际构建验证。Detekt 使用固定 Alpha 版本；Kover 尚有来自插件内部的 Gradle 弃用警告。升级时同时检查兼容表、解析后的依赖和测试结果。[Kotlin 兼容表](https://kotlinlang.org/docs/gradle-configure-project.html)、[Gradle 9.7.1](https://docs.gradle.org/9.7.1/release-notes.html)、[Fabric 26.3](https://fabricmc.net/2026/09/15/263.html)

仅在 `kotlin.jvmToolchain(25)` 声明工具链，Java/Kotlin 目标由插件推导。当前实现不需要 preview 或实验性语法；采用新特性前确认收益及目标版本支持。[Java 25](https://docs.oracle.com/en/java/javase/25/language/java-language-changes-summary.html)、[Kotlin 2.4](https://kotlinlang.org/docs/whatsnew24.html)、[Kotlin 2.4.20](https://kotlinlang.org/docs/whatsnew2420.html)

## 目录与依赖

包根为 `io.github.romeoahmed.cursedoath`，mod ID 为 `cursed-oath`。

```text
src/main/kotlin/.../cursedoath/
  combat/       # 动作、咒力、近战伤害、持久附件
  technique/    # 术式、飞行实体、吸引场与防御
  world/        # 扫掠几何、区块检查、分批破坏
  network/      # 请求防护、快照与事件编解码
  command/      # 练习命令
src/main/java/.../mixin/        # 原版攻击与挥空钩子
src/client/kotlin/.../client/  # input、hud、animation、render
src/main/resources/           # 元数据、翻译、PAL 动画
src/test/kotlin/               # 纯规则、编解码、几何与翻译测试
src/gametest/                  # 测试 Mod、场景与环境分组
```

按已有职责建包。类型文件与类型同名，多声明文件按共同职责命名；测试使用 `Test`、`GameTest`、`ClientGameTest` 后缀。内部重命名保留注册键、存档键与协议编号。[Kotlin 命名约定](https://kotlinlang.org/docs/coding-conventions.html#source-code-organization)、[Fabric 项目结构](https://docs.fabricmc.net/develop/getting-started/project-structure)

Fabric Data Attachments 管理持久状态，PAL 管理玩家动画。现阶段保持单模块，不增加重复的组件系统、动画库或通用技能框架。复杂咒灵模型出现时再评估 GeckoLib；新增依赖需有具体用途、兼容构件及安装侧别说明。[Fabric Attachments](https://docs.fabricmc.net/develop/serialization/data-attachments)、[PAL](https://docs.zigythebird.com/pal/intro/)

## 状态与联网

服务端拥有咒力、动作、防御、命中和地形队列；客户端提交意图、显示确认结果。`Fighter` 绑定玩家实体与世界；死亡、断线、换维度或取消解除准备，已付启动费与剩余恢复时间保留。

`SorcererProfile`、`SorcererResources` 是不可变持久附件，以 Codec 校验范围与版本。存档保存余额和恢复负担，活动施法与费用预留不续存。资源快照每四 tick 检查一次，仅变化时发给本人；加入、重生和换维度强制同步。附件自动同步关闭，避免重复传输。

请求携带连接会话 UUID、单调序号和显式 wire ID。`RequestGate` 限频并拒绝重放。确认事件携带 UUID、维度、服务器 tick、阶段和位置，按发生位置广播；客户端以有界集合去重。飞行实体的位置、插值与移除使用原生跟踪。[Fabric Networking](https://docs.fabricmc.net/develop/networking)

世界读写在服务端线程完成。客户端在提取阶段生成渲染快照，提交阶段不读取活动实体；测试通过 Fabric 上下文调度跨线程工作。[Fabric World Rendering](https://docs.fabricmc.net/develop/rendering/world)

## 命中与地形

`TechniqueProjectile` 是密封基类：`TechniqueOrb` 承载苍/赫，`TechniqueWave` 保留茈/解的实体注册标识，茈由独立的 `PurpleFlight` 推进。共享原生 `SynchedEntityData` 与 `SteppedInterpolationHandler`，渲染裁剪范围独立于物理碰撞箱。苍/赫、解与吸引场绑定施放时的玩家实体；茈保留原始施术者归属，但不依赖其存活。原生所有者引用可能按 UUID 找到重生后的新实体，因此不作为效果存续依据。

- **苍/赫：**逐段调用 `clipIncludingBorder` 与 `ProjectileUtil.getManyEntityHitResult`，检查当前方块和实体。苍的吸引与压缩由核心的原生实体 tick 驱动，共用位置和寿命，不维护独立场注册表。赫用 `ServerExplosion.getSeenPercent` 计算冲击时遮挡，以原生伤害和 `push` 提交三维排斥。
- **茈：**`SphereSweep` 在 AABB 面穿越点之间求解距离二次式，得到连续球体接触；同一段的路径与运动向量复用于静止方块，包围盒只用于宽相位筛选。
- **解：**在局部坐标中用 `AABB.clip` 检查保守旋转盒体。茈/解均纳入目标一 tick 相对运动，宽相位额外覆盖四格移动；任意高速移动或传送不在保证范围内。
- **捌：**`CleaveLattice` 共享方块、实体和客户端网格几何。初始接触先判伤，开路完成后再检查其余目标的当前位置与遮挡，每个目标最多受击一次。

茈按固定速度扫掠，伤害不等待地形且不检查遮挡；结束时封闭地形提交，队列继续完成。解仍按短段挖掘后推进，等待时检查可见主体内的接触。伤害经过原版护甲、受伤间隔、图腾和死亡流程。两个 Mixin 分别在原版主攻击入口绑定预备/修改伤害，以及在挥空包处理后消费预备；扫击不重复抽取黑闪。

`TerrainDestruction` 在准备时预留容量，以整个服务器为单位轮转处理。球形挖掘沿原生曼哈顿顺序向外；切割逐个枚举包围盒，将相交位置增量加入优先队列，再按前进深度处理。茈按短段直接枚举，排除先前轨迹覆盖的方块，不排序、不做遮挡射线；每发最多 32 段，队列设 64 段防错上限。每个枚举位置和提交候选都计入预算，方块状态在提交时读取并复核。

| 服务器总限制                 | 当前值    |
| ---------------------------- | --------- |
| 工作容量（包含准备中的施法） | 16        |
| 每 tick 几何/提交访问        | 16,384    |
| 每 tick 直接移除方块         | 1,024     |
| 协作式时间预算               | 4 ms/tick |
| 准备、球形挖掘与切割的工作寿命 | 600 tick |

已释放茈的有限地形段不因时间预算或施术者死亡被丢弃，容量槽保留到处理结束。一次几何检查、射线或方块连锁更新不能被计时器中断，仍需单独测量尾部耗时。

方块提交使用 `Level.destroyBlock` 与 Fabric BEFORE / CANCELED / AFTER 事件；回调改变状态或关闭任务时放弃本次移除。容器、流体、不可破坏块和权限否决保留。解与捌从原始攻击后缘发出平行遮挡射线，保护障碍后方路径；解外围障碍不取消其他路径，中心路径被挡才终止飞行。茈与球形挖掘跳过保护块，茈不因保留方块产生遮挡。普通破坏遵循 `minecraft:block_drops`，茈采用无掉落粉碎。

`ServerChunkCache.getChunkNow` 检查实际就绪区块；票据资格不足以证明查询不会等待。飞行术式在进入非实体模拟区块前结束，避免悬停。苍/赫和切割在所有者失效、区块不可用或超时时终止未提交工作。茈只检查当前飞行段；排队后卸载的区块跳过，不强制加载，也不阻止其他已加载段。已发生的伤害与破坏不回滚。

## 渲染与动作

`CastingAnimation` 隔离 PAL 接口，分别播放准备和释放动作；动画不驱动伤害或位移。特效复用球/环几何、动态丝带和游戏渲染管线；苍的装饰碎屑使用有限原生 BLOCK 粒子，通过客户端实体生命周期维护活动集合，换世界和断线时清空。事件特效按距离与视锥裁剪，球/环和丝带在 48 格外减少细节。黑闪使用独立事件阶段；捌挥空或被拦截时只清理准备表现。

特效通过 `SubmitNodeCollector` 提交几何，使用原版 shader、`RenderPipeline` 与 `OitPipelineSet`。26.3 的 `VulkanCommandEncoder` 管理提交同步和延迟销毁；Mod 不直接持有 Vulkan 句柄或插入 GPU 等待。普通透明路径保留与原版闪电一致的上传排序，不能只因使用加色混合就忽略深度写入。设备与显示表面的直接创建仅用于测试预检。

苍的飞行、停留与消失均由实体跟踪驱动；事件只负责准备、赫的冲击、捌、自疗与黑闪等瞬时表现。现有形态、制作流程及待完成效果见[表现制作](presentation.zh-CN.md)。

## 验证

开发命令见 [AGENTS.md](../AGENTS.md)，CI 流程见 [build.yml](../.github/workflows/build.yml)。Spotless 发现源集文件并规范文档/资源空白；Detekt 的 main/client/test/gametest 类型分析任务接入 `check`。检查失败应修复原因，不以 baseline 或宽泛抑制绕过。[Kotlin 代码检查](https://kotlinlang.org/docs/jvm-code-analysis.html)、[Detekt](https://detekt.dev/docs/gettingstarted/gradle/)、[Gradle 最佳实践](https://docs.gradle.org/current/userguide/best_practices_general.html)

| 验证层          | 职责与限制                                                                        |
| --------------- | --------------------------------------------------------------------------------- |
| JUnit           | 咒力账本、协议/存档、三语占位符和几何边界；几何采样对照原版距离函数               |
| 服务端 GameTest | 命中、保护、资源与原生世界交互；按 `test_environment` 分组隔离共享容量            |
| 客户端 GameTest | 真实按键/联网、飞行与位移；固定随机种子的原生攻击验证黑闪；独立夹具检查姿态和效果 |
| 视觉与性能验收  | 直接查看截图、联机试用及压力测量；像素差异只证明效果出现/消失                     |

`kotlin-test-junit5` 的已解析元数据提供 engine/launcher，JUnit BOM 统一版本；GameTest 编译关联 main。Kover 的 90% 行覆盖门槛只覆盖 `CursedEnergy` 和 `RequestGate`。[Kotlin 关联编译](https://kotlinlang.org/docs/gradle-configure-project.html)、[JUnit 6.1.3](https://docs.junit.org/6.1.3/overview.html)

几何夹具可关闭 AI/重力，位移测试需保留运动模拟；`spawnWithNoFreeWill` 不会固定实体位置。异步结果使用测试序列等待，避免重复推进已注册实体。客户端以固定机位、分辨率和同次运行基线截图。先运行 `build`，再运行 `runClientGameTest`，避免服务端测试清理任务删除客户端结果。[Fabric 自动测试](https://docs.fabricmc.net/develop/automatic-testing)

客户端测试指定 Vulkan，并断言游戏实际使用该后端，防止静默回退。`runClientGameTest` 自动先运行 `checkClientGraphics`，在 30 秒内通过 Minecraft 原生 API 创建 Vulkan 设备与显示表面，避免初始化失败后卡在错误对话框。Linux CI 使用 Xvfb/X11，以 `VK_DRIVER_FILES` 选择 Mesa Lavapipe 软件 Vulkan 驱动。[SDL 显示驱动](https://wiki.libsdl.org/SDL3/SDL_HINT_VIDEO_DRIVER)、[Vulkan 驱动选择](https://vulkan.lunarg.com/doc/view/latest/linux/LoaderDriverInterface.html)

CI 仅授予仓库读取权限，同一分支的新运行取消旧运行。失败时保留验证报告、日志与截图，全部检查通过后上传 JAR。Gradle 配置缓存与构建缓存分别复用任务图和可缓存输出。[GitHub Actions 并发控制](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency)

修改 Minecraft 调用时以 `genSources` 和实际 Fabric 构件为准；旧版指南用于理解机制，不能替代目标版本签名。
