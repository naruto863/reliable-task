<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import EmptyState from '@/components/state/EmptyState.vue'
import ErrorState from '@/components/state/ErrorState.vue'
import LoadingState from '@/components/state/LoadingState.vue'
import { useAuditLogStore } from '@/stores/auditLogStore'
import { useConsoleStore } from '@/stores/consoleStore'

const route = useRoute()
const router = useRouter()
const auditLogStore = useAuditLogStore()
const consoleStore = useConsoleStore()

const maxPageSize = computed(() => consoleStore.capabilities?.maxPageSize || 200)
const totalPages = computed(() => Math.max(Math.ceil(auditLogStore.total / auditLogStore.pageSize), 1))

function formatDateTime(value: string | null): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 19)
}

function formatNullable(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return String(value)
}

function setFilter(key: 'operator' | 'createTimeStart' | 'createTimeEnd', event: Event) {
  const target = event.target as HTMLInputElement
  auditLogStore.setFilter(key, target.value)
}

function setPageSize(event: Event) {
  const target = event.target as HTMLInputElement
  auditLogStore.setPageSize(Number(target.value), maxPageSize.value)
}

async function syncRouteAndLoad() {
  // 审计查询同样同步到 URL，方便把某个 operator/时间窗口的排查结果分享给同事。
  await router.replace({ name: 'auditLogs', query: auditLogStore.toRouteQuery() })
  await auditLogStore.loadAuditLogs()
}

async function resetFilters() {
  auditLogStore.resetFilters()
  await syncRouteAndLoad()
}

async function goToPage(pageNum: number) {
  auditLogStore.setPage(pageNum)
  await syncRouteAndLoad()
}

onMounted(() => {
  // 先恢复 URL 查询，再发起列表请求，避免 mounted 时先加载默认条件又马上被 URL 覆盖。
  auditLogStore.applyRouteQuery(route.query, maxPageSize.value)
  if (import.meta.env.MODE !== 'test') {
    void auditLogStore.loadAuditLogs()
  }
})
</script>

<template>
  <section class="page-shell" aria-labelledby="audit-logs-title">
    <div class="page-panel dashboard-hero">
      <div>
        <h2 id="audit-logs-title" class="page-panel__title">Audit Logs</h2>
        <p class="page-panel__text">Manual operation records with operator, result, and trace context.</p>
      </div>
      <button class="state__action dashboard-controls__refresh" type="button" @click="syncRouteAndLoad">
        Refresh
      </button>
    </div>

    <form class="filter-panel filter-panel--audit" @submit.prevent="syncRouteAndLoad">
      <label class="control-field">
        <span>Operator</span>
        <input :value="auditLogStore.filters.operator" @input="setFilter('operator', $event)" />
      </label>
      <label class="control-field">
        <span>Created after</span>
        <input
          :value="auditLogStore.filters.createTimeStart"
          type="datetime-local"
          @input="setFilter('createTimeStart', $event)"
        />
      </label>
      <label class="control-field">
        <span>Created before</span>
        <input
          :value="auditLogStore.filters.createTimeEnd"
          type="datetime-local"
          @input="setFilter('createTimeEnd', $event)"
        />
      </label>
      <div class="filter-panel__actions">
        <button class="state__action" type="submit">Apply</button>
        <button class="state__action state__action--secondary" type="button" @click="resetFilters">
          Reset
        </button>
      </div>
    </form>

    <LoadingState v-if="auditLogStore.loading" message="Loading audit logs" />
    <ErrorState v-else-if="auditLogStore.error" :state="auditLogStore.error" @retry="syncRouteAndLoad" />

    <section v-if="!auditLogStore.loading && !auditLogStore.error" class="page-panel table-panel">
      <EmptyState
        v-if="auditLogStore.records.length === 0"
        title="No audit logs"
        description="No audit records match the current filters."
      />
      <div v-else class="table-scroll">
        <table class="task-table">
          <thead>
            <tr>
              <th>Operation</th>
              <th>Operator</th>
              <th>Target</th>
              <th>Result</th>
              <th>Request</th>
              <th>Trace</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="audit in auditLogStore.records" :key="audit.id">
              <td>
                <strong>{{ audit.operationType }}</strong>
                <span>#{{ audit.id }}</span>
              </td>
              <td>{{ formatNullable(audit.operator) }}</td>
              <td>
                <strong>{{ formatNullable(audit.targetType) }}</strong>
                <span>{{ formatNullable(audit.targetId) }}</span>
              </td>
              <td>
                <span
                  class="status-tag"
                  :class="audit.result === 'SUCCESS' ? 'status-tag--success' : 'status-tag--danger'"
                >
                  {{ formatNullable(audit.result) }}
                </span>
              </td>
              <td class="task-table__error">
                {{ formatNullable(audit.errorMsg || audit.requestSummary) }}
              </td>
              <td>{{ formatNullable(audit.traceId) }}</td>
              <td>{{ formatDateTime(audit.createTime) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination-bar">
        <span>Total {{ auditLogStore.total }}</span>
        <label class="control-field">
          <span>Page size</span>
          <input
            min="1"
            :max="maxPageSize"
            type="number"
            :value="auditLogStore.pageSize"
            @input="setPageSize"
          />
        </label>
        <button type="button" :disabled="auditLogStore.pageNum <= 1" @click="goToPage(auditLogStore.pageNum - 1)">
          Prev
        </button>
        <span>Page {{ auditLogStore.pageNum }} / {{ totalPages }}</span>
        <button
          type="button"
          :disabled="auditLogStore.pageNum >= totalPages"
          @click="goToPage(auditLogStore.pageNum + 1)"
        >
          Next
        </button>
      </div>
    </section>
  </section>
</template>
