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
