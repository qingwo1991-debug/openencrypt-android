# 09 Update + Release Design

## Source
GitHub Releases stable channel only.

## Check Flow
1. fetch latest stable release
2. compare semantic version
3. select ABI-matching asset
4. verify checksum
5. verify signing certificate consistency
6. prompt install

## Failure Handling
- failed checksum/signature blocks installation
- keep current version active
- record reason in update history
