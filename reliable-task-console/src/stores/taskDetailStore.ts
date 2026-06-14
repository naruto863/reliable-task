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
        this.error = toAppErrorState(error)
        this.loading = false
        return
      }

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
        await this.loadTask(this.taskId, api)
      } catch (error) {
        this.operationError = toAppErrorState(error)
      } finally {
        this.operationLoading = false
      }
    },
  },
})
