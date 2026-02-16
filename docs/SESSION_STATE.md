# SESSION_STATE

## Session Meta
- last_updated: 2026-02-16T15:57:04Z
- project: openencrypt-android
- active_branch: main
- last_commit: f2d31f0
- last_ci_run: 22064174943 (completed success)
- mode: implementing
- writeback_policy: event-driven + throttled (3 turns or 15 minutes)

## Recovery Entry
启动后第一问（固定）：
1. 这是“上下文结束”还是“程序意外结束”？

分支处理：
- 如果是“上下文结束”：执行 Resume Commands，读取 Task Breakdown 中 doing/blocked，直接续做。
- 如果是“程序意外结束”：先做一致性检查（工作区/是否已push/CI状态），再续做。

## Current Goal
- 一次性完成 openencrypt-android 的文档对齐与产品化：页面化架构、配置编辑闭环、发布自动化、应用内热更新与验收闭环。

## Task Breakdown
- [done] REL-001: CI 与 release 产物链修正（debug + preview + signed release）。
- [done] REL-002: 11 warnings 根因修复（action cache warning 清零路径）。
- [done] UPD-001: App 内更新链路（check/download/verify/install/history）。
- [done] UI-001: 中文化与底部导航实际切页反馈。
- [done] UI-002: 云/加密/任务可编辑表单与保存。
- [done] UI-003: 加密规则新增/更新/删除/清空 + 预览。
- [done] UI-004: 单 Activity + 多 Fragment 架构重整（容器化导航已落地）。
- [done] CFG-001: Standard/Expert 模式与 schema 全字段映射 UI（baseline 固定字段版）。
- [done] CFG-002: 保存流程增强为 validate -> diff -> confirm -> apply。
- [done] DIA-001: Diagnostics 页面（baseline 诊断聚合）。
- [done] SEC-001: Security & Backup 页面（快照列表/恢复）。
- [done] REL-003: main 每次 push 自动版本+1并发布 GitHub Releases。
- [done] TST-001: Playback/Range/WebDAV 自动矩阵验收（脚本+CI工作流）。
- [done] TST-002: Soak 自动化与报告链路（支持 72h 参数化）。
- [blocked] TST-003: 72h 真机 Soak 执行并归档报告（需真机或 self-hosted 执行；GitHub hosted runner 仅保留短时 soak）。

## Progress Log (Append-Only)
- 2026-02-16T10:59Z | 完成 release/ci 大改与更新中心链路 | commit:f950021
- 2026-02-16T11:06Z | 修复 BuildConfig 编译失败 | commit:da6d3d8
- 2026-02-16T11:35Z | 切页改造 + 中文文案 | commit:3cac872 | run:22061154927 success
- 2026-02-16T12:13Z | 三页可编辑表单（云/加密/任务） | commit:d8835c3 | run:22062291056 success
- 2026-02-16T12:25Z | 加密规则更新/删除增强 | commit:013cc4d | run:22062668702 success
- 2026-02-16T20:58Z | main push 自动版本+1与 release 发布 | commit:de419c1 | run:22063664239 success
- 2026-02-16T21:20Z | UI Fragment 化 + 专家模式/诊断/备份恢复 + diff确认保存 | commit:a6954d5
- 2026-02-16T21:54Z | 全字段 JSON 专家编辑 + 矩阵测试 + soak自动化/报告 + acceptance工作流 | commit:f2d31f0
- 2026-02-16T15:01Z | 恢复入口=程序意外结束；一致性检查完成；本地短时 soak(1m) 通过并产出报告 | report:.reports/soak-report-20260216-230040.md
- 2026-02-16T15:24Z | 72h Acceptance soak 已触发（run:22068420180, soak_minutes=4320, interval=15s）| status:queued
- 2026-02-16T15:42Z | run:22068420180 已取消（hosted runner 不用于 72h soak）| conclusion:cancelled
- 2026-02-16T15:44Z | 调整 acceptance workflow：hosted soak 限制 60min；72h 改为真机/self-hosted 执行 | file:.github/workflows/acceptance.yml
- 2026-02-16T15:44Z | Android runtime 二进制链路修复：按 ABI 资产安装 + 构建前强校验，防止产出缺核心二进制 APK | file:app-android/build.gradle.kts
- 2026-02-16T15:50Z | CI/Release 增加 Android runtime 二进制预检（tools/check-android-runtime-bins.sh），构建前快速失败并给出来源提示 | file:.github/workflows/ci.yml
- 2026-02-16T15:56Z | 远端 Acceptance 短测 run:22069404510 success（仍为远端旧 workflow，artifact 上传缺失）| note:本地已修复 include-hidden-files=true，待 push 生效

## Failures & Decisions
### Failures
- 22060069945: android-sanity / android-preview-release 失败，原因：BuildConfig 未生成导致 UpdateCoordinator 编译报错。
- 2026-02-16T15:01Z: `gh run list` 失败（HTTP 401 Bad credentials），`gh auth status` 显示 `GH_TOKEN` 已失效。
- 2026-02-16T15:23Z: 恢复发现本会话环境变量 `GH_TOKEN` 仍为旧值；通过 `unset GH_TOKEN` 后回退到 `~/.config/gh/hosts.yml` 凭据正常。
- 2026-02-16T15:42Z: Acceptance run 22068420180 触发 72h soak 后取消；原因：GitHub hosted runner 不适合作为 72h soak 执行面。
- 2026-02-16T15:56Z: Acceptance artifact 未上传根因确认：`upload-artifact@v4` 默认 `include-hidden-files=false`，`.reports/*.md` 被忽略（本地 workflow 已修复，待 push）。

### Decisions
- 每次 push main 都自动发布 release。
- 版本采用固定主次 + patch 自增。
- 每次发布走正式 Release（非 prerelease）。
- App 内热更新默认跟最新 release。
- 会话状态采用项目内独立维护 + /root/AI 公共模板复用。

## Resume Commands
```bash
git status
git log --oneline -n 15
gh run list --limit 5 --json databaseId,workflowName,status,conclusion,headSha,createdAt
# 如需查看最近 run 详情：
gh run view <run_id> --json status,conclusion,jobs
```

## Writeback Policy
强制回写场景：
- 子任务状态变化
- commit 完成后
- push 完成后
- CI 从 in_progress -> completed

节流回写场景：
- 普通进展每 3 轮或 15 分钟回写一次（先到先写）
- 会话结束前回写一次
