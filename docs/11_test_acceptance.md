# 11 Test and Acceptance

## Functional
- cloud config CRUD
- encryption rule CRUD
- WebDAV upload/download
- encrypted playback with range seek

## Stability
- 72h soak with service state consistency
- background/foreground transitions stable
- restart recovery works

## Performance
- large directory listing latency improved
- no UI blocking from sync filesystem operations
- armv7 conservative profile remains usable

## Update
- stable update check/download/install path works
- checksum/signature mismatch path correctly blocked

## Exit Criteria
All category checks pass on target ABI matrix before `v1.0.0`.
