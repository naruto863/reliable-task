import type {
  ApiResult,
  AuditLog,
  AuditLogQuery,
  BatchOperationRequest,
  BatchOperationResult,
  ConsoleCapabilities,
  ConsoleTaskDetail,
  FailureTop,
  FailureTopQuery,
  PageResult,
  QueryParams,
  RecentFailuresQuery,
  TaskExecutionLog,
  TaskFailure,
  TaskListQuery,
  TaskStats,
  TaskSummary,
  TaskTimelineItem,
  WorkerHeartbeat,
} from './types'

export type ApiClientErrorKind = 'business' | 'http' | 'network' | 'parse'

/**
 * 前端统一错误类型。
 *
 * API 层把失败分成四类：业务错误、HTTP 状态错误、网络错误和响应解析错误。
 * Store/View 只消费归一化后的错误，不需要知道 fetch、Result 包装或 JSON 解析细节。
 */
export interface ApiClientErrorOptions {
  kind: ApiClientErrorKind
  message: string
  code?: number
  status?: number
  cause?: unknown
}

export class ApiClientError extends Error {
  readonly kind: ApiClientErrorKind
  readonly code?: number
  readonly status?: number
  readonly cause?: unknown

  constructor(options: ApiClientErrorOptions) {
    super(options.message)
    this.name = 'ApiClientError'
    this.kind = options.kind
    this.code = options.code
    this.status = options.status
    this.cause = options.cause
  }
}

export type Fetcher = (input: string, init: RequestInit) => Promise<Response>

export interface AdminApiClientOptions {
  baseUrl?: string
  fetcher?: Fetcher
  operatorProvider?: () => string | undefined
  traceIdFactory?: () => string
}

export interface RequestOptions {
  method?: string
  query?: QueryParams
  body?: unknown
  /**
   * 是否为 Admin 写操作。
   *
   * 写操作会附带 operator、traceId 和可选确认头；读操作保持轻量，避免无意义生成 traceId。
   */
  write?: boolean
  /**
   * 是否发送 X-Confirm-Operation: true。
   *
   * 这是控制台与后端写保护合同的一部分，前端显式确认不替代后端 auth/audit/write gate。
   */
  confirmOperation?: boolean
  traceId?: string
}

export interface AdminApiClient {
  get<T>(path: string, query?: RequestOptions['query']): Promise<T>
  post<T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>): Promise<T>
  put<T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>): Promise<T>
  request<T>(path: string, options?: RequestOptions): Promise<T>
  getConsoleCapabilities(): Promise<ConsoleCapabilities>
  getConsoleTaskDetail(id: number): Promise<ConsoleTaskDetail>
  getTaskLogs(id: number): Promise<TaskExecutionLog[]>
  getTaskTimeline(id: number): Promise<TaskTimelineItem[]>
  getTaskAuditLogs(id: number): Promise<AuditLog[]>
  retryTask(id: number): Promise<boolean>
  cancelTask(id: number): Promise<boolean>
  requeueTask(id: number): Promise<boolean>
  updateTaskPayload(id: number, payload: string): Promise<boolean>
  previewBatch(request: BatchOperationRequest): Promise<BatchOperationResult>
  batchRequeue(request: BatchOperationRequest): Promise<BatchOperationResult>
  batchCancel(request: BatchOperationRequest): Promise<BatchOperationResult>
  listWorkers(): Promise<WorkerHeartbeat[]>
  listStaleWorkers(): Promise<WorkerHeartbeat[]>
  listAuditLogs(query?: AuditLogQuery): Promise<PageResult<AuditLog>>
  getTaskStats(): Promise<TaskStats>
  listFailureTop(query?: FailureTopQuery): Promise<FailureTop[]>
  listRecentFailures(query?: RecentFailuresQuery): Promise<TaskFailure[]>
  listTasks(query?: TaskListQuery): Promise<PageResult<TaskSummary>>
}

const DEFAULT_API_BASE = import.meta.env.VITE_RELIABLE_TASK_API_BASE || '/api/reliable-task'
const DEFAULT_OPERATOR = 'local-demo'
const OPERATOR_STORAGE_KEY = 'reliable-task.console.operator'

function defaultOperatorProvider(): string {
  if (typeof localStorage === 'undefined') {
    return DEFAULT_OPERATOR
  }
  return localStorage.getItem(OPERATOR_STORAGE_KEY) || DEFAULT_OPERATOR
}

function defaultTraceIdFactory(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `rt-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function normalizeBaseUrl(baseUrl: string): string {
  return baseUrl.replace(/\/+$/, '')
}

function buildUrl(baseUrl: string, path: string, query?: RequestOptions['query']): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  const url = `${normalizeBaseUrl(baseUrl)}${normalizedPath}`
  const params = new URLSearchParams()

  // 过滤空字符串和 null/undefined，避免把未填写筛选项序列化成后端会误判的查询条件。
  Object.entries(query || {}).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '') {
      params.set(key, String(value))
    }
  })

  const queryString = params.toString()
  return queryString ? `${url}?${queryString}` : url
}

function isApiResult<T>(value: unknown): value is ApiResult<T> {
  return (
    typeof value === 'object' &&
    value !== null &&
    'code' in value &&
    'message' in value &&
    'data' in value
  )
}

function messageFrom(error: unknown): string {
  return error instanceof Error ? error.message : 'Network request failed'
}

export function createAdminApiClient(options: AdminApiClientOptions = {}): AdminApiClient {
  const baseUrl = options.baseUrl || DEFAULT_API_BASE
  const fetcher = options.fetcher || ((input, init) => fetch(input, init))
  const operatorProvider = options.operatorProvider || defaultOperatorProvider
  const traceIdFactory = options.traceIdFactory || defaultTraceIdFactory

  async function request<T>(path: string, requestOptions: RequestOptions = {}): Promise<T> {
    const headers = new Headers()
    headers.set('Accept', 'application/json')
    headers.set('X-Operator', operatorProvider()?.trim() || DEFAULT_OPERATOR)

    if (requestOptions.write) {
      // 后端 Admin 写操作要求可追踪、可确认。traceId 供审计串联，确认头供防误操作校验。
      headers.set('X-Trace-Id', requestOptions.traceId || traceIdFactory())
      if (requestOptions.confirmOperation) {
        headers.set('X-Confirm-Operation', 'true')
      }
    }

    let body: BodyInit | undefined
    if (requestOptions.body !== undefined) {
      headers.set('Content-Type', 'application/json')
      body = JSON.stringify(requestOptions.body)
    }

    let response: Response
    try {
      response = await fetcher(buildUrl(baseUrl, path, requestOptions.query), {
        method: requestOptions.method || 'GET',
        headers,
        body,
      })
    } catch (error) {
      throw new ApiClientError({
        kind: 'network',
        message: messageFrom(error),
        cause: error,
      })
    }

    let payload: unknown
    try {
      payload = await response.json()
    } catch (error) {
      throw new ApiClientError({
        kind: 'parse',
        status: response.status,
        message: 'Invalid JSON response from ReliableTask Admin API',
        cause: error,
      })
    }

    if (!isApiResult<T>(payload)) {
      throw new ApiClientError({
        kind: 'parse',
        status: response.status,
        message: 'Unexpected ReliableTask Admin API response',
      })
    }

    if (!response.ok) {
      throw new ApiClientError({
        kind: 'http',
        status: response.status,
        code: payload.code,
        message: payload.message || response.statusText,
      })
    }

    if (payload.code !== 200) {
      // HTTP 200 但 Result.code 非成功时，视为业务错误，保留后端 message 给页面展示。
      throw new ApiClientError({
        kind: 'business',
        code: payload.code,
        status: response.status,
        message: payload.message || 'ReliableTask Admin API returned an error',
      })
    }

    return payload.data
  }

  return {
    request,
    get: (path, query) => request(path, { query }),
    post: (path, body, postOptions) => request(path, { ...postOptions, method: 'POST', body }),
    put: (path, body, putOptions) => request(path, { ...putOptions, method: 'PUT', body }),
    getConsoleCapabilities: () => request('/console/capabilities'),
    getConsoleTaskDetail: (id) => request(`/console/tasks/${id}`),
    getTaskLogs: (id) => request(`/tasks/${id}/logs`),
    getTaskTimeline: (id) => request(`/tasks/${id}/timeline`),
    getTaskAuditLogs: (id) => request(`/tasks/${id}/audit-logs`),
    retryTask: (id) => request(`/tasks/${id}/retry`, { method: 'POST', write: true, confirmOperation: true }),
    cancelTask: (id) => request(`/tasks/${id}/cancel`, { method: 'POST', write: true, confirmOperation: true }),
    requeueTask: (id) => request(`/tasks/${id}/requeue`, { method: 'POST', write: true, confirmOperation: true }),
    updateTaskPayload: (id, payload) =>
      request(`/tasks/${id}/payload`, {
        method: 'PUT',
        body: { payload },
        write: true,
        confirmOperation: true,
      }),
    previewBatch: (body) =>
      request('/tasks/batch/preview', { method: 'POST', body, write: true, confirmOperation: true }),
    batchRequeue: (body) =>
      request('/tasks/batch/requeue', { method: 'POST', body, write: true, confirmOperation: true }),
    batchCancel: (body) =>
      request('/tasks/batch/cancel', { method: 'POST', body, write: true, confirmOperation: true }),
    listWorkers: () => request('/workers'),
    listStaleWorkers: () => request('/workers/stale'),
    listAuditLogs: (query) => request('/audit-logs', { query }),
    getTaskStats: () => request('/tasks/stats'),
    listFailureTop: (query) => request('/tasks/failure-top', { query }),
    listRecentFailures: (query) => request('/tasks/recent-failures', { query }),
    listTasks: (query) => request('/tasks', { query }),
  }
}

export const adminApiClient = createAdminApiClient()
export { DEFAULT_OPERATOR, OPERATOR_STORAGE_KEY }
