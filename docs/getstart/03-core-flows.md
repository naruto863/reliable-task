# 核心流程

ReliableTask 的关键流程可以分为投递、执行、失败重试、超时恢复和 Admin 运维操作。每个流程都以 MySQL 状态为最终事实源。

## 事务投递流程

```mermaid
sequenceDiagram
    participant C as Controller
    participant S as Business Service
    participant T as TransactionAwareTaskTemplate
    participant I as IdempotencyStrategy
    participant Codec as TaskPayloadCodec
    participant Store as TaskCommandStore
    participant DB as MySQL

    C->>S: 创建业务数据
    S->>T: submit(TaskSubmitRequest)
    T->>T: validateRequest()
    T->>I: decide(IdempotencyContext)
    alt 返回已有任务
        I-->>T: RETURN_EXISTING
        T-->>S: TaskSubmitResult(existing)
    else 允许创建
        T->>Codec: encode(payload, context)
        T->>Store: save(TaskInstance PENDING)
        Store->>DB: INSERT reliable_task
        DB-->>Store: task id
        Store-->>T: saved task
        T-->>S: TaskSubmitResult(created)
    else 拒绝
        I-->>T: REJECT
        T-->>S: throw ReliableTaskException
    end
```

代码入口：

- `reliable-task-demo/src/main/java/com/reliabletask/demo/service/OrderService.java`
- `reliable-task-executor/src/main/java/com/reliabletask/executor/template/TransactionAwareTaskTemplate.java`
- `reliable-task-store/src/main/java/com/reliabletask/store/impl/MyBatisTaskStore.java`

投递发生在业务事务内。业务事务回滚时，任务记录也随之回滚。幂等键最终由 `reliable_task.uk_biz_unique_key` 兜底裁决。

## Worker 执行流程

```mermaid
sequenceDiagram
    participant W as WorkerScheduler
    participant Store as TaskCommandStore
    participant DB as MySQL
    participant E as TaskExecutor
    participant R as TaskHandlerRegistry
    participant H as Business TaskHandler

    W->>Store: fetchPendingTasks(batchSize)
    Store->>DB: SELECT executable tasks
    DB-->>Store: PENDING / RETRYING candidates
    Store-->>W: candidate list
    loop each task
        W->>Store: claimTask(id, workerId, lockExpireAt)
        Store->>DB: UPDATE status to RUNNING with conditions
        alt 抢占成功
            W->>E: execute(claimedTask)
            E->>R: find handler by taskType
            R-->>E: TaskHandler
            E->>H: execute(task, payload)
            alt 执行成功
                E->>Store: markSuccess(lease)
                Store->>DB: UPDATE RUNNING to SUCCESS
                E->>Store: saveLog(success)
            else 执行失败
                E->>E: delegate to RetryEngine
            end
        else 抢占失败
            W-->>W: skip task
        end
    end
```

`fetchPendingTasks` 只是候选快照；真正的并发归属由 `claimTask` 的条件更新决定。执行成功回写优先使用 `TaskExecutionLease`，避免旧 worker 污染新租约状态。

## 失败、重试和死信流程

```mermaid
sequenceDiagram
    participant E as TaskExecutor
    participant RE as RetryEngine
    participant FC as FailureClassifier
    participant RS as RetryStrategy
    participant Store as TaskCommandStore
    participant DL as TaskDeadLetterDispatcher

    E->>RE: handleFailure(handler, task, error)
    RE->>RE: unwrap root cause
    RE->>FC: classify(task, error)
    FC-->>RE: FailureDecision
    alt 不可重试或次数耗尽
        RE->>Store: markDead(lease, errorCode, errorMsg)
        Store-->>RE: updated
        RE->>Store: saveLog(statusAfter=DEAD)
        RE->>DL: dispatch(DeadLetterContext)
    else 可重试
        RE->>RS: nextDelayMs(...)
        RS-->>RE: delay
        RE->>Store: markWaitRetry(lease, nextExecuteTime)
        Store-->>RE: updated
        RE->>Store: saveLog(statusAfter=RETRYING)
    end
```

代码入口：

- `RetryEngine.java`
- `DefaultFailureClassifier.java`
- `TaskDeadLetterDispatcher.java`
- `TaskStateMachine.java`

`NonRetryableException` 默认进入 DEAD；其他异常默认进入重试判断。自定义 `FailureClassifier` 可以提前判定 DEAD 或继续重试。

## 超时恢复流程

```mermaid
sequenceDiagram
    participant RS as TaskRecoveryScheduler
    participant Store as TaskCommandStore
    participant DB as MySQL
    participant Event as TaskEventPublisher

    RS->>Store: findTimeoutTasks(now, maxResetPerScan)
    Store->>DB: SELECT RUNNING where lock_expire_at <= now
    DB-->>Store: timeout tasks
    loop timeout task
        RS->>Store: resetTimeoutTask(TaskExecutionLease)
        Store->>DB: UPDATE RUNNING to PENDING by lease
        alt reset success
            RS->>Event: publish RECOVERED
        else lease changed
            RS-->>RS: skip stale recovery
        end
    end
```

恢复扫描不证明旧业务调用已经停止。`Future.cancel(true)` 只是请求中断，业务 handler 和外部系统仍必须具备幂等保护。

## Admin 写保护流程

```mermaid
sequenceDiagram
    participant UI as Console / Operator
    participant API as TaskAdminController
    participant Auth as TaskAuthorizationProvider
    participant Store as TaskOperationsStore
    participant DB as MySQL

    UI->>API: POST /tasks/{id}/retry with headers
    API->>API: check write-enabled
    API->>API: check auth.enabled
    API->>API: check audit.enabled
    API->>API: check X-Confirm-Operation=true
    API->>Auth: isAllowed(operator, action, taskId)
    alt allowed
        API->>Store: requeueTask / cancelTask / updatePayload
        Store->>DB: conditional UPDATE
        API->>Store: saveAuditLog
        API-->>UI: Result.success
    else denied or unsafe config
        API->>Store: saveAuditLog when audit enabled
        API-->>UI: Result.error
    end
```

Admin 写入口包括单任务 retry/cancel/requeue/payload update，以及批量 preview/requeue/cancel。前端会根据 `ConsoleCapabilitiesVO` 禁用按钮，但最终保护在后端。

