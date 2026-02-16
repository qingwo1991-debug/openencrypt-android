# 11 Test and Acceptance

## Functional
- cloud config CRUD
- encryption rule CRUD
- WebDAV upload/download
- encrypted playback with range seek
- automated matrix: `tools/playback-webdav-matrix-check.sh`

## Stability
- 72h soak with service state consistency
- background/foreground transitions stable
- restart recovery works
- automated soak runner/report: `tools/runtime-soak-check.sh` + `tools/soak-report.sh`

## Performance
- large directory listing latency improved
- no UI blocking from sync filesystem operations
- armv7 conservative profile remains usable

## Update
- stable update check/download/install path works
- checksum/signature mismatch path correctly blocked

## Automation Entry
- one-shot local acceptance: `tools/acceptance-check.sh`
- GitHub Actions acceptance workflow: `.github/workflows/acceptance.yml` (manual dispatch, hosted-runner soak is capped to 60 minutes for CI sanity)
- 72h soak final sign-off: run on real device or self-hosted runner (`tools/runtime-soak-check.sh 4320 15`) and archive `.reports/soak-report-*.md`

## Exit Criteria
All category checks pass on target ABI matrix before `v1.0.0`.
