/**
 * 后端统一响应包装。
 *
 * Console 只接受 Result<T> 形态的响应；如果后端返回裸数据或非 JSON，client 会按 parse 错误处理。
 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface ConsoleCapabilities {
  /** Admin API 是否可用。 */
  adminEnabled: boolean
  /** 写操作总开关；即使为 true，也还需要 auth/audit 等能力同时满足。 */
  writeEnabled: boolean
  authEnabled: boolean
  auditEnabled: boolean
  batchEnabled: boolean
  maxPageSize: number
  maxBatchLimit: number
  payloadPlaintextEnabled: boolean
  payloadRevealAllowed: boolean
  payloadPreviewLength: number
  writeConfirmationRequired: boolean
}

/**
 * payload 的控制台安全视图。
 *
 * 前端不直接依赖任务详情里的原始 payload 字段，只根据这些开关展示预览、遮蔽状态和可选明文。
 */
export interface PayloadView {
  payloadVisible: boolean
  payloadMasked: boolean
  payloadPreview: string | null
  payloadLength: number
  payloadRevealAllowed: boolean
  payloadPlaintext: string | null
}

export interface ConsoleTaskDetail {
  id: number
  taskType: string
  bizType: string | null
  bizId: string | null
  bizUniqueKey: string | null
  statusCode: number
  statusDesc: string
  priority: number
  payloadView: PayloadView
  executeCount: number
  maxRetryCount: number
  retryStrategy: string | null
  retryIntervalMs: number | null
  nextExecuteTime: string | null
  shardKey: string | null
  tenantId: string | null
  workerId: string | null
  errorMsg: string | null
  traceId: string | null
  createTime: string | null
  updateTime: string | null
  finishTime: string | null
}

export interface TaskSummary {
  id: number
  taskType: string
  bizType: string | null
  bizId: string | null
  statusCode: number
  statusDesc: string
  priority: number
  executeCount: number
  maxRetryCount: number
  errorMsg: string | null
  workerId: string | null
  traceId: string | null
  tenantId: string | null
  nextExecuteTime: string | null
  createTime: string | null
  updateTime: string | null
  finishTime: string | null
}

export interface TaskStats {
  statusCount: Record<number, number>
  totalTasks: number
  todayNewTasks: number
  todaySuccessTasks: number
  todayFailedTasks: number
  pendingTasks: number
  deadTasks: number
  oldestPendingAgeSeconds: number
  taskTypeStats: Record<string, number>
}

export interface FailureTop {
  taskType: string | null
  errorCode: string | null
  failureCount: number
  createTimeStart: string | null
  createTimeEnd: string | null
}

export interface TaskFailure {
  taskId: number
  taskType: string
  bizType: string | null
  bizId: string | null
  statusAfter: string | null
  errorCode: string | null
  errorMsg: string | null
  durationMs: number | null
  workerId: string | null
  traceId: string | null
  executeTime: string | null
}

export interface TaskExecutionLog {
  id: number
  taskId: number
  attemptNo: number
  statusBefore: string | null
  statusAfter: string | null
  executeTime: string | null
  durationMs: number | null
  status: number
  statusDesc: string
  errorCode: string | null
  errorMsg: string | null
  workerId: string | null
  traceId: string | null
  createTime: string | null
}

export interface TaskTimelineItem {
  source: string
  eventType: string
  sourceId: number
  taskId: number
  eventTime: string | null
  statusBefore: string | null
  statusAfter: string | null
  statusCode: number | null
  statusDesc: string | null
  attemptNo: number | null
  durationMs: number | null
  errorCode: string | null
  errorMsg: string | null
  workerId: string | null
  operator: string | null
  operationType: string | null
  requestSummary: string | null
  result: string | null
  traceId: string | null
}

export interface AuditLog {
  id: number
  operationType: string
  operator: string | null
  targetType: string | null
  targetId: string | null
  taskId: number | null
  batchOperationId: number | null
  requestSummary: string | null
  result: string | null
  errorMsg: string | null
  traceId: string | null
  createTime: string | null
}

export interface WorkerHeartbeat {
  workerId: string
  appName: string | null
  hostName: string | null
  ipAddress: string | null
  processId: string | null
  status: string | null
  runningTaskCount: number
  maxConcurrency: number
  availableCapacity: number
  lastHeartbeatTime: string | null
  startTime: string | null
}

export type QueryValue = string | number | boolean | null | undefined

export interface QueryParams {
  [key: string]: QueryValue
}

export interface FailureTopQuery extends QueryParams {
  groupBy?: string
  taskType?: string
  createTimeStart?: string
  createTimeEnd?: string
  limit?: number
}

export interface RecentFailuresQuery extends QueryParams {
  taskType?: string
  errorCode?: string
  createTimeStart?: string
  createTimeEnd?: string
  limit?: number
}

export interface TaskListQuery extends QueryParams {
  pageNum?: number
  pageSize?: number
  status?: number | null
  taskType?: string
  bizType?: string
  bizId?: string
  workerId?: string
  traceId?: string
  tenantId?: string
  createTimeStart?: string
  createTimeEnd?: string
}

export interface AuditLogQuery extends QueryParams {
  operator?: string
  createTimeStart?: string
  createTimeEnd?: string
  pageNum?: number
  pageSize?: number
}

export interface BatchOperationRequest extends QueryParams {
  taskType?: string
  status?: number | null
  createTimeStart?: string
  createTimeEnd?: string
  limit?: number
  dryRun?: boolean
}

/**
 * 批量操作结果。
 *
 * preview 和真正执行共用该结构；dryRun 用来区分当前结果是否只是预览。
 */
export interface BatchOperationResult {
  batchOperationId: number | null
  totalCount: number
  successCount: number
  failCount: number
  failedTaskIds: number[]
  failedSummary: string | null
  dryRun: boolean
  success: boolean
  errorMsg: string | null
}
