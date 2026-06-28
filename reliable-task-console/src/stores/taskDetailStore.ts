import { defineStore } from 'pinia'

import { adminApiClient, type AdminApiClient } from '@/api/client'
import type { AuditLog, ConsoleTaskDetail, TaskExecutionLog, TaskTimelineItem } from '@/api/types'
import { toAppErrorState, type AppErrorState } from '@/utils/errorState'

type TaskDetailReadApiClient = Pick<
  AdminApiClient,
  'getConsoleTaskDetail' | 'getTaskLogs' | 'getTaskTimeline' | 'getTaskAuditLogs'
>
type TaskDetailWriteApiClient = TaskDetailReadApiClient &
  Pick<AdminApiClient, 'retryTask' | 'cancelTask' | 'requeueTask' | 'updateTaskPayload'>

export type TaskWriteOperation = 'retry' | 'cancel' | 'requeue'

export const useTaskDetailStore = defineStore('taskDetail', {
  state: () => ({
    taskId: null as number | null,
    detail: null as ConsoleTaskDetail | null,
    logs: [] as TaskExecutionLog[],
    timeline: [] as TaskTimelineItem[],
    auditLogs: [] as AuditLog[],
    loading: false,
    operationLoading: false,
    error: null as AppErrorState | null,
    logsError: null as AppErrorState | null,
    timelineError: null as AppErrorState | null,
    auditError: null as AppErrorState | null,
    operationError: null as AppErrorState | null,
    operationSuccess: '',
  }),
  actions: {
    resetPanels() {
      this.logs = []
      this.timeline = []
      this.auditLogs = []
      this.logsError = null
      this.timelineError = null
      this.auditError = null
    },
    async loadTask(taskId: number, api: TaskDetailReadApiClient = adminApiClient) {
      this.taskId = taskId
      this.loading = true
      this.error = null
      this.detail = null
      this.resetPanels()

      try {
        this.detail = await api.getConsoleTaskDetail(taskId)
      } catch (error) {
        // 主详情失败时停止加载附属面板，因为后续日志/时间线都需要一个有效任务上下文。
        this.error = toAppErrorState(error)
        this.loading = false
        return
      }

      // 附属面板彼此独立降级：执行日志、时间线、审计任一失败，不应遮挡已经加载的任务详情。
      try {
        this.logs = await api.getTaskLogs(taskId)
      } catch (error) {
        this.logsError = toAppErrorState(error)
      }

      try {
        this.timeline = await api.getTaskTimeline(taskId)
      } catch (error) {
        this.timelineError = toAppErrorState(error)
      }

      try {
        this.auditLogs = await api.getTaskAuditLogs(taskId)
      } catch (error) {
        this.auditError = toAppErrorState(error)
      } finally {
        this.loading = false
      }
    },
    async runTaskOperation(operation: TaskWriteOperation, api: TaskDetailWriteApiClient = adminApiClient) {
      if (!this.taskId) {
        return
      }

      this.operationLoading = true
      this.operationError = null
      this.operationSuccess = ''

      try {
        if (operation === 'retry') {
          await api.retryTask(this.taskId)
        } else if (operation === 'cancel') {
          await api.cancelTask(this.taskId)
        } else {
          await api.requeueTask(this.taskId)
        }
        this.operationSuccess = `${operation} succeeded`
        // 写操作成功后立即重载详情，确保状态、日志、时间线和审计展示同一次后端事实。
        await this.loadTask(this.taskId, api)
      } catch (error) {
        this.operationError = toAppErrorState(error)
      } finally {
        this.operationLoading = false
      }
    },
    async updatePayload(payload: string, api: TaskDetailWriteApiClient = adminApiClient) {
      if (!this.taskId) {
        return
      }

      this.operationLoading = true
      this.operationError = null
      this.operationSuccess = ''

      try {
        await api.updateTaskPayload(this.taskId, payload)
        this.operationSuccess = 'payload update succeeded'
        // payload 更新同样走写保护合同，成功后重新读取 console-safe payload view。
        await this.loadTask(this.taskId, api)
      } catch (error) {
        this.operationError = toAppErrorState(error)
      } finally {
        this.operationLoading = false
      }
    },
  },
})
