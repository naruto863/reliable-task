import { ApiClientError } from '@/api/client'

export type AppErrorKind = 'business' | 'forbidden' | 'notFound' | 'network' | 'http' | 'unknown'

export interface AppErrorState {
  kind: AppErrorKind
  title: string
  message: string
  statusCode?: number
}

export function toAppErrorState(error: unknown): AppErrorState {
  if (error instanceof ApiClientError) {
    // 后端业务码和 HTTP status 都可能承载 403/404 语义，这里统一折叠为页面可理解的错误状态。
    const statusCode = error.code || error.status

    if (statusCode === 403) {
      return {
        kind: 'forbidden',
        title: 'Access denied',
        message: error.message,
        statusCode,
      }
    }

    if (statusCode === 404) {
      return {
        kind: 'notFound',
        title: 'Resource not found',
        message: error.message,
        statusCode,
      }
    }

    if (error.kind === 'network') {
      // 网络错误没有稳定 statusCode，页面应引导用户检查 API base、代理或后端进程。
      return {
        kind: 'network',
        title: 'API unreachable',
        message: error.message,
      }
    }

    return {
      kind: error.kind === 'http' ? 'http' : 'business',
      title: 'Request failed',
      message: error.message,
      statusCode,
    }
  }

  return {
    kind: 'unknown',
    title: 'Unexpected error',
    message: error instanceof Error ? error.message : 'Unknown error',
  }
}
