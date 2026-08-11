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

- 69/69 全绿,BUILD SUCCESS(2026-08-11 Asia/Shanghai)
- R 等价性:Java format == R format_pal(字节级,含整值 double/科学计数 provenance);pipeline pal_out == R run_topology_pipeline(S4/S5/rotate 字节级);jiugong grid/mirror_addr 与 R 字节级一致;RWorker 进程编排结果与 Java 内联/串行交叉验证一致
- Claude Code 门控(2026-08-11):FAIL → 修复 1 P0 + 3 P1 + 10 P2 后 69/69 复绿

## 与 R 对齐的已知决策

1. DEFAULT_MAPPING_PACK_ID = pal-jiugong-v0.2(实测,非文档 v0.1)
2. Boolean 序列化 l:TRUE/l:FALSE(R 大写)
3. run_topology_pipeline 用 execute_lanes_ops 动态 lanes(每 orbit 一个 lane,S5 保全部 shell)——非 execute_lanes 固定 4 lanes
4. format 无尾换行(paste collapse="\n")
5. R paste(matrix) 列优先 → 等价性测试展平用列序
6. R 的 pack 解析链路实际恒 NULL(cell$origin$pal 是 format 字符串,$mapping_pack_id 为 NULL)——Java 按设计意图解析(默认行为等价,自定义 pack 优于 R bug),已在 TopologyOperator 注释标注
7. Double 序列化按 R as.character 语义(d:1 整值无小数点、d:1e-07 科学计数 e±NN、scipen 选短)——PalCodec.rDouble
8. commit 投影复用旧载体(镜像 R commit 传 carrier$projection)——投影是原始 pal 的 3×3 视图,不随 reconciled cell 重算(门控 P2-10 确认镜像)
9. 门控修复:P0 worker 错误帧终止行;P1 CI env 路径/未知 pack fail-closed;P2 trim 去掩/异常帧统一/共享 lane 池/worker 销毁/校验补洞/快照深拷贝/截断边界/死参数清理

## 待办(下一刀)

- [ ] CI 触发验证(GitHub Actions 状态,可能需用户启用)
- [ ] TLS/鉴权(HTTP 服务生产化,部署时按需)

## 远程(2026-08-10)

- https://github.com/lkh-cq/java-runtime(public,master 已推送)

## Benchmark(2026-08-10 实测)

- fresh-process RWorker: 3811.8 ms/task(pkgload 每次启动加载)
- persistent PersistentRWorker: 17.2 ms/task
- speedup: 221.6x(bench/Bench.java 可复现)
