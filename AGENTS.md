# 安卓端工作约定

本目录是 Tocode 的安卓对话端，独立 git 仓库。治理不在本仓。

## 权威入口

- 治理清单：`../governance/architecture/governance.json`
- 语义地图：`../governance/architecture/traceability/semantic-map.json`
- 架构合同：`../governance/architecture/contracts/`
- 工作包：`../governance/architecture/work-packages/`
- 证据：`../governance/architecture/evidence/`

## 开发前准入

任何可能改变业务语义、权限、生命周期、数据归属、跨模块写入、Agent 上下文或持久化执行的任务，都必须声明以下结论之一：

1. 不影响 L0；
2. 修改已有 L0；
3. 新增 L0。

影响 L0 时，必须先有已批准工作包，再改代码。

## 实现协议

- 只修改任务信封与工作包声明的范围；
- 安卓只经中转与 Mac 上已关联的角色收发文字；
- 不在安卓保存角色提示词、模型密钥或微信凭据。

## 常用命令

```text
python3 ../governance/architecture/tools/audit_project.py --target ../governance
```
