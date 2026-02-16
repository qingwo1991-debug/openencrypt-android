# 05 Android UI Field Coverage

## Field Model
All editable fields come from a single schema source in `schemas/`.

## Display Levels
- Standard: high-frequency safe fields
- Expert: full field coverage with risk labels

## Validation Gates
- required checks
- range checks
- port conflict checks
- encryption path overlap checks

## Save Flow
validate -> show diff -> apply atomically -> snapshot backup

## Anti-Duplicate Rule
each field has exactly one primary editable page; cross-page references are read-only.
