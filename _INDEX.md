# java-runtime — _INDEX.md

> visualR Java/JVM 编排层(DEVELOPMENT_PLAN §8 post-0.5 reserve)
> 对齐:Topology Operator ABI v0.1(frozen 2026-08-08)
> 权威:R = 语义参考实现;Java = 执行/编排 fabric,不重定义语义

## 产出清单

| 文件 | 类型 | 日期 | Agent | 任务 | 输入->输出链 | 校验 |
|------|------|------|-------|------|-------------|------|
| pom.xml | 构建 | 2026-08-10 | Hermes | Java 21 + JUnit5(阿里云镜像) | Maven 标准 | BUILD SUCCESS |
| src/main/java/io/visualr/runtime/PalState.java | 源码 | 2026-08-10 | Hermes | PAL 状态对象 | R visualr_pal -> Java | 测试绿 |
| src/main/java/io/visualr/runtime/PalCodec.java | 源码 | 2026-08-10 | Hermes | v0.2 编解码 | R format_pal/parse_pal -> Java | 字节级等价 |
| src/main/java/io/visualr/runtime/TopologyCell.java | 源码 | 2026-08-10 | Hermes | 拓扑单元(ABI §2.1) | R new_topology_cell/pal_to_cell -> Java | 测试绿 |
| src/main/java/io/visualr/runtime/TopologyCarrier.java | 源码 | 2026-08-10 | Hermes | 高维载体(ABI §2.2) | R new_topology_carrier -> Java | 测试绿 |
| src/main/java/io/visualr/runtime/Snapshot.java | 源码 | 2026-08-10 | Hermes | 快照冻结(ABI §5.2) | R snapshot -> Java | 测试绿 |
| src/main/java/io/visualr/runtime/LaneKernel.java | 源码 | 2026-08-10 | Hermes | lane 算子接口(3参ABI) | R lane_kernels -> Java | 测试绿 |
| src/main/java/io/visualr/runtime/LaneResult.java | 源码 | 2026-08-10 | Hermes | lane 结果 record | R lane_identity 输出 -> Java | 测试绿 |
| src/main/java/io/visualr/runtime/ReconcileResult.java | 源码 | 2026-08-10 | Hermes | 协调结果 record | R reconcile 输出 -> Java | 测试绿 |
| src/main/java/io/visualr/runtime/TopologyOperator.java | 源码 | 2026-08-10 | Hermes | 五接口编排(lanes/barrier/reconcile/commit/cell_to_pal/pipeline) | R topology_carrier.R -> Java | R 等价 |
| src/main/java/io/visualr/runtime/PipelineResult.java | 源码 | 2026-08-10 | Hermes | 全流程结果 record | R run_topology_pipeline 输出 -> Java | 测试绿 |
| src/test/java/io/visualr/runtime/PalCodecTest.java | 测试 | 2026-08-10 | Hermes | 编解码往返不变式 | Java 自测 | 6/6 |
| src/test/java/io/visualr/runtime/REquivalenceTest.java | 测试 | 2026-08-10 | Hermes | codec 与 R 字节级等价 | Rscript+pkgload -> Java | 2/2 |
| src/test/java/io/visualr/runtime/TopologyOperatorTest.java | 测试 | 2026-08-10 | Hermes | 五接口语义 | Java 自测 | 9/9 |
| src/test/java/io/visualr/runtime/PipelineEquivalenceTest.java | 测试 | 2026-08-10 | Hermes | pipeline 与 R 全流程等价(S4/S5) | Rscript+pkgload -> Java | 1/1 |

## 测试总账

- 44/44 全绿,BUILD SUCCESS(2026-08-10 Asia/Shanghai)
- R 等价性:Java format == R format_pal(4 样例字节级);pipeline pal_out == R run_topology_pipeline(S4/S5/rotate 字节级);jiugong grid/mirror_addr 与 R 字节级一致;RWorker 进程编排结果与 Java 内联/串行交叉验证一致

## 与 R 对齐的已知决策

1. DEFAULT_MAPPING_PACK_ID = pal-jiugong-v0.2(实测,非文档 v0.1)
2. Boolean 序列化 l:TRUE/l:FALSE(R 大写)
3. run_topology_pipeline 用 execute_lanes_ops 动态 lanes(每 orbit 一个 lane,S5 保全部 shell)——非 execute_lanes 固定 4 lanes
4. format 无尾换行(paste collapse="\n")
5. R paste(matrix) 列优先 → 等价性测试展平用列序
6. R 的 pack 解析链路实际恒 NULL(cell$origin$pal 是 format 字符串,$mapping_pack_id 为 NULL)——Java 按设计意图解析(默认行为等价,自定义 pack 优于 R bug),已在 TopologyOperator 注释标注

## 待办(下一刀)

- [ ] RWorker 长驻进程复用(当前每任务新进程,~3s 启动;进程池/长驻协议优化)
- [ ] git 远程推送(GitHub SSH 已切,仓库待定)
- [ ] 网络传输/缓存管理/包路由(DEVELOPMENT_PLAN §8 剩余职责)
