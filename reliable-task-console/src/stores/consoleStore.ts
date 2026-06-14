import { defineStore } from 'pinia'

import { adminApiClient, DEFAULT_OPERATOR, OPERATOR_STORAGE_KEY, type AdminApiClient } from '@/api/client'
import type { ConsoleCapabilities } from '@/api/types'
import { toAppErrorState, type AppErrorState } from '@/utils/errorState'

function readInitialOperator(): string {
  if (typeof localStorage === 'undefined') {
    return DEFAULT_OPERATOR
  }
  return localStorage.getItem(OPERATOR_STORAGE_KEY) || DEFAULT_OPERATOR
}

function persistOperator(operator: string): void {
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem(OPERATOR_STORAGE_KEY, operator)
  }
}

export const useConsoleStore = defineStore('console', {
  state: () => ({
    operator: readInitialOperator(),
    capabilities: null as ConsoleCapabilities | null,
    loading: false,
    error: null as AppErrorState | null,
  }),
  getters: {
    isWriteAvailable: (state) =>
      Boolean(
        state.capabilities?.writeEnabled &&
          state.capabilities.authEnabled &&
          state.capabilities.auditEnabled,
      ),
  },
  actions: {
    setOperator(value: string) {
      const operator = value.trim() || DEFAULT_OPERATOR
      this.operator = operator
      persistOperator(operator)
    },
    async loadCapabilities(api: Pick<AdminApiClient, 'getConsoleCapabilities'> = adminApiClient) {
      this.loading = true
      this.error = null

      try {
        this.capabilities = await api.getConsoleCapabilities()
      } catch (error) {
        this.capabilities = null
        this.error = toAppErrorState(error)
      } finally {
        this.loading = false
      }
    },
  },
})
