# 06 Service Lifecycle

## State Machine
- Idle
- Starting
- Running
- Degraded
- Recovering
- Stopped

## Start Procedure
1. preload config
2. start OpenList runtime
3. verify `/ping` on OpenList
4. start encryption gateway
5. verify gateway health
6. publish Running state

## Recovery
- classify error: transient vs fatal
- transient: exponential backoff restart
- fatal: enter Degraded, require user action

## Background Policy
- foreground service for runtime
- battery optimization diagnostics + guided remediation
- explicit user override for manual stop
