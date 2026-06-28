import { defineStore } from 'pinia'

import { adminApiClient, type AdminApiClient } from '@/api/client'
import type { WorkerHeartbeat } from '@/api/types'
import { toAppErrorState, type AppErrorState } from '@/utils/errorState'

type WorkerApiClient = Pick<AdminApiClient, 'listWorkers' | 'listStaleWorkers'>

export const useWorkerStore = defineStore('workers', {
  state: () => ({
    workers: [] as WorkerHeartbeat[],
    staleWorkers: [] as WorkerHeartbeat[],
    loading: false,
    error: null as AppErrorState | null,
    staleError: null as AppErrorState | null,
  }),
  getters: {
    // 汇总值只用于运维展示，不参与调度；真实调度容量仍由后端 Worker/Executor 控制。
    totalCapacity: (state) =>
      state.workers.reduce((sum, worker) => sum + Number(worker.maxConcurrency || 0), 0),
    availableCapacity: (state) =>
      state.workers.reduce((sum, worker) => sum + Number(worker.availableCapacity || 0), 0),
    runningTaskCount: (state) =>
      state.workers.reduce((sum, worker) => sum + Number(worker.runningTaskCount || 0), 0),
  },
  actions: {
    async loadWorkers(api: WorkerApiClient = adminApiClient) {
      this.loading = true
      this.error = null
      this.staleError = null

      try {
        this.workers = await api.listWorkers()
      } catch (error) {
        this.workers = []
        this.staleWorkers = []
        this.error = toAppErrorState(error)
        this.loading = false
        return
      }

      // stale 列表是辅助诊断面板。它加载失败时保留主 Worker 列表，并单独展示 staleError。
      try {
        this.staleWorkers = await api.listStaleWorkers()
      } catch (error) {
        this.staleWorkers = []
        this.staleError = toAppErrorState(error)
      } finally {
        this.loading = false
      }
    },
  },
})
