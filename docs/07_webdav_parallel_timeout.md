# 07 WebDAV Parallel + Timeout Strategy

## Problem
Sequential traversal and timeout stacking cause slow listing and long stalls on 404/network jitter.

## Conservative Parallel Plan
- enable bounded parallel filename decrypt for list paths
- keep output ordering stable
- gate parallel mode by list size threshold

## Timeout Plan
- header timeout budget
- read-idle timeout for long streams
- separate probe budgets for list vs playback

## Fast-Fail Plan
- wire upstream backoff into request path
- return quick 503 with retry guidance while backoff active

## Safety Precondition
fix concurrent map access in crypto cache before enabling broader parallel execution.
