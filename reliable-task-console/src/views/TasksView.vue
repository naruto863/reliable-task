<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import EmptyState from '@/components/state/EmptyState.vue'
import ErrorState from '@/components/state/ErrorState.vue'
import LoadingState from '@/components/state/LoadingState.vue'
import { useBatchOperationStore } from '@/stores/batchOperationStore'
import type { BatchExecuteAction, BatchFilterKey } from '@/stores/batchOperationStore'
import { useConsoleStore } from '@/stores/consoleStore'
import { useTaskListStore, type TaskFilterKey } from '@/stores/taskListStore'

const route = useRoute()
const router = useRouter()
const consoleStore = useConsoleStore()
const taskListStore = useTaskListStore()
const batchStore = useBatchOperationStore()

const maxPageSize = computed(() => consoleStore.capabilities?.maxPageSize || 200)
const maxBatchLimit = computed(() => consoleStore.capabilities?.maxBatchLimit || 1000)
const totalPages = computed(() => Math.max(Math.ceil(taskListStore.total / taskListStore.pageSize), 1))
const batchConfirmAction = ref<BatchExecuteAction | null>(null)
const batchConfirmationText = ref('')

const statusOptions = [
  { label: 'All', value: '' },
  { label: 'PENDING', value: 0 },
  { label: 'RUNNING', value: 1 },
  { label: 'SUCCESS', value: 2 },
  { label: 'FAILED', value: 3 },
  { label: 'RETRYING', value: 4 },
  { label: 'DEAD', value: 5 },
  { label: 'CANCELLED', value: 6 },
]

const batchStatusOptions = [
  { label: 'Any', value: '' },
  { label: 'PENDING', value: 0 },
  { label: 'RETRYING', value: 4 },
  { label: 'DEAD', value: 5 },
]

const batchAvailable = computed(() =>
  Boolean(
    consoleStore.capabilities?.writeEnabled &&
      consoleStore.capabilities.authEnabled &&
      consoleStore.capabilities.auditEnabled &&
      consoleStore.capabilities.batchEnabled,
  ),
)

const batchStatusText = computed(() => {
  if (!consoleStore.capabilities) {
    return 'Batch operations unavailable: capabilities are not loaded.'
  }
  if (!consoleStore.capabilities.writeEnabled) {
    return 'Batch operations unavailable: write capability is disabled.'
  }
  if (!consoleStore.capabilities.authEnabled) {
    return 'Batch operations unavailable: authorization is disabled.'
  }
  if (!consoleStore.capabilities.auditEnabled) {
    return 'Batch operations unavailable: audit logging is disabled.'
  }
  if (!consoleStore.capabilities.batchEnabled) {
    return 'Batch operations unavailable: batch capability is disabled.'
  }
  return 'Batch operations enabled with preview, confirmation header, trace id, and audit trail.'
})

function statusName(statusCode: number, fallback: string): string {
  return statusOptions.find((item) => item.value === statusCode)?.label || fallback || String(statusCode)
}

function statusClass(statusCode: number): string {
  if (statusCode === 2) {
    return 'status-tag--success'
  }
  if (statusCode === 5 || statusCode === 3) {
    return 'status-tag--danger'
  }
  if (statusCode === 4 || statusCode === 0) {
    return 'status-tag--warning'
  }
  return 'status-tag--neutral'
}

function formatDateTime(value: string | null): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 19)
}

function formatError(value: string | null): string {
  if (!value) {
    return '-'
  }
  return value.length > 96 ? `${value.slice(0, 96)}...` : value
}

function setTextFilter(key: TaskFilterKey, event: Event) {
  const target = event.target as HTMLInputElement
  taskListStore.setFilter(key, target.value)
}

function setStatus(event: Event) {
  const target = event.target as HTMLSelectElement
  taskListStore.setFilter('status', target.value === '' ? null : Number(target.value))
}

function setPageSize(event: Event) {
  const target = event.target as HTMLInputElement
  taskListStore.setPageSize(Number(target.value), maxPageSize.value)
}

function setBatchTextFilter(key: BatchFilterKey, event: Event) {
  const target = event.target as HTMLInputElement
  batchStore.setFilter(key, target.value)
}

function setBatchStatus(event: Event) {
  const target = event.target as HTMLSelectElement
  batchStore.setFilter('status', target.value === '' ? null : Number(target.value))
}

function setBatchLimit(event: Event) {
  const target = event.target as HTMLInputElement
  batchStore.setLimit(Number(target.value), maxBatchLimit.value)
}

async function previewBatch() {
  await batchStore.preview()
}

function openBatchConfirm(action: BatchExecuteAction) {
  if (!batchStore.previewResult || !batchAvailable.value) {
    return
  }
  batchConfirmAction.value = action
  batchConfirmationText.value = ''
}

function closeBatchConfirm() {
  batchConfirmAction.value = null
  batchConfirmationText.value = ''
}

async function confirmBatchOperation() {
  if (!batchConfirmAction.value || batchConfirmationText.value !== 'CONFIRM') {
    return
  }
  await batchStore.execute(batchConfirmAction.value)
  if (!batchStore.error) {
    closeBatchConfirm()
  }
}

async function syncRouteAndLoad() {
  await router.replace({ name: 'tasks', query: taskListStore.toRouteQuery() })
  await taskListStore.loadTasks()
}

async function resetFilters() {
  taskListStore.resetFilters()
  await syncRouteAndLoad()
}

async function goToPage(pageNum: number) {
  taskListStore.setPage(pageNum)
  await syncRouteAndLoad()
}

onMounted(() => {
  taskListStore.applyRouteQuery(route.query, maxPageSize.value)
  if (import.meta.env.MODE !== 'test') {
    void taskListStore.loadTasks()
  }
})
</script>

<template>
  <section class="page-shell" aria-labelledby="tasks-title">
    <div class="page-panel dashboard-hero">
      <div>
        <h2 id="tasks-title" class="page-panel__title">Tasks</h2>
        <p class="page-panel__text">Filter task records by status, business keys, trace, worker, and time.</p>
      </div>
      <button class="state__action dashboard-controls__refresh" type="button" @click="syncRouteAndLoad">
        Refresh
      </button>
    </div>

    <form class="filter-panel" @submit.prevent="syncRouteAndLoad">
      <label class="control-field">
        <span>Status</span>
        <select :value="taskListStore.filters.status ?? ''" @change="setStatus">
          <option v-for="item in statusOptions" :key="String(item.value)" :value="item.value">
            {{ item.label }}
          </option>
        </select>
      </label>
      <label class="control-field">
        <span>Task type</span>
        <input :value="taskListStore.filters.taskType" @input="setTextFilter('taskType', $event)" />
      </label>
      <label class="control-field">
        <span>Biz type</span>
        <input :value="taskListStore.filters.bizType" @input="setTextFilter('bizType', $event)" />
      </label>
      <label class="control-field">
        <span>Biz id</span>
        <input :value="taskListStore.filters.bizId" @input="setTextFilter('bizId', $event)" />
      </label>
      <label class="control-field">
        <span>Trace id</span>
        <input :value="taskListStore.filters.traceId" @input="setTextFilter('traceId', $event)" />
      </label>
      <label class="control-field">
        <span>Worker</span>
        <input :value="taskListStore.filters.workerId" @input="setTextFilter('workerId', $event)" />
      </label>
      <label class="control-field">
        <span>Tenant</span>
        <input :value="taskListStore.filters.tenantId" @input="setTextFilter('tenantId', $event)" />
      </label>
      <label class="control-field">
        <span>Created after</span>
        <input
          :value="taskListStore.filters.createTimeStart"
          type="datetime-local"
          @input="setTextFilter('createTimeStart', $event)"
        />
      </label>
      <label class="control-field">
        <span>Created before</span>
        <input
          :value="taskListStore.filters.createTimeEnd"
          type="datetime-local"
          @input="setTextFilter('createTimeEnd', $event)"
        />
      </label>
      <div class="filter-panel__actions">
        <button class="state__action" type="submit">Apply</button>
        <button class="state__action state__action--secondary" type="button" @click="resetFilters">
          Reset
        </button>
      </div>
    </form>

    <section class="page-panel detail-panel" aria-labelledby="batch-title">
      <div class="detail-panel__header">
        <div>
          <h3 id="batch-title">Batch Operations</h3>
          <p>{{ batchStatusText }}</p>
        </div>
      </div>
      <form class="filter-panel filter-panel--batch" @submit.prevent="previewBatch">
        <label class="control-field">
          <span>Task type</span>
          <input :value="batchStore.filters.taskType" @input="setBatchTextFilter('taskType', $event)" />
        </label>
        <label class="control-field">
          <span>Status</span>
          <select :value="batchStore.filters.status ?? ''" @change="setBatchStatus">
            <option v-for="item in batchStatusOptions" :key="String(item.value)" :value="item.value">
              {{ item.label }}
            </option>
          </select>
        </label>
        <label class="control-field">
          <span>Created after</span>
          <input
            :value="batchStore.filters.createTimeStart"
            type="datetime-local"
            @input="setBatchTextFilter('createTimeStart', $event)"
          />
        </label>
        <label class="control-field">
          <span>Created before</span>
          <input
            :value="batchStore.filters.createTimeEnd"
            type="datetime-local"
            @input="setBatchTextFilter('createTimeEnd', $event)"
          />
        </label>
        <label class="control-field">
          <span>Limit</span>
          <input
            min="1"
            :max="maxBatchLimit"
            type="number"
            :value="batchStore.limit"
            @input="setBatchLimit"
          />
        </label>
        <div class="filter-panel__actions">
          <button
            class="state__action"
            type="button"
            :disabled="!batchAvailable || batchStore.loading"
            @click="previewBatch"
          >
            Preview batch
          </button>
        </div>
      </form>

      <ErrorState v-if="batchStore.error" :state="batchStore.error" @retry="previewBatch" />
      <div v-if="batchStore.previewResult" class="operation-message operation-message--success">
        {{ batchStore.previewResult.totalCount }} matched · limit {{ batchStore.limit }} · batch
        #{{ batchStore.previewResult.batchOperationId ?? '-' }}
      </div>
      <div v-if="batchStore.executeResult" class="operation-message operation-message--success">
        Success {{ batchStore.executeResult.successCount }} · failed {{ batchStore.executeResult.failCount }}
        <span v-if="batchStore.executeResult.failedTaskIds.length">
          · failed IDs {{ batchStore.executeResult.failedTaskIds.join(', ') }}
        </span>
      </div>
      <div class="action-grid">
        <button
          class="state__action"
          type="button"
          :disabled="!batchAvailable || !batchStore.previewResult || batchStore.loading"
          @click="openBatchConfirm('requeue')"
        >
          Requeue DEAD
        </button>
        <button
          class="state__action"
          type="button"
          :disabled="!batchAvailable || !batchStore.previewResult || batchStore.loading"
          @click="openBatchConfirm('cancel')"
        >
          Cancel pending/retrying
        </button>
      </div>

      <div v-if="batchConfirmAction" class="confirm-dialog" role="dialog" aria-modal="true">
        <h4>Confirm batch {{ batchConfirmAction }}</h4>
        <p>Batch execution requires an existing preview result and sends a confirmed write request.</p>
        <label class="control-field">
          <span>Type CONFIRM</span>
          <input
            v-model="batchConfirmationText"
            aria-label="Batch confirmation text"
            autocomplete="off"
            placeholder="CONFIRM"
          />
        </label>
        <div class="confirm-dialog__actions">
          <button
            class="state__action"
            type="button"
            :disabled="batchConfirmationText !== 'CONFIRM' || batchStore.loading"
            @click="confirmBatchOperation"
          >
            Confirm batch operation
          </button>
          <button class="state__action state__action--secondary" type="button" @click="closeBatchConfirm">
            Close
          </button>
        </div>
      </div>
    </section>

    <LoadingState v-if="taskListStore.loading" message="Loading tasks" />
    <ErrorState v-else-if="taskListStore.error" :state="taskListStore.error" @retry="syncRouteAndLoad" />

    <div v-if="!taskListStore.loading && !taskListStore.error" class="page-panel table-panel">
      <EmptyState
        v-if="taskListStore.records.length === 0"
        title="No tasks"
        description="No task records match the current filters."
      />
      <div v-else class="table-scroll">
        <table class="task-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Task</th>
              <th>Status</th>
              <th>Retries</th>
              <th>Worker</th>
              <th>Error summary</th>
              <th>Created</th>
              <th>Next run</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="task in taskListStore.records" :key="task.id">
              <td>
                <RouterLink :to="{ name: 'taskDetail', params: { id: task.id } }">#{{ task.id }}</RouterLink>
              </td>
              <td>
                <strong>{{ task.taskType }}</strong>
                <span>{{ task.bizType || '-' }} · {{ task.bizId || '-' }}</span>
              </td>
              <td>
                <span class="status-tag" :class="statusClass(task.statusCode)">
                  {{ statusName(task.statusCode, task.statusDesc) }}
                </span>
              </td>
              <td>{{ task.executeCount }} / {{ task.maxRetryCount }}</td>
              <td>{{ task.workerId || '-' }}</td>
              <td class="task-table__error">{{ formatError(task.errorMsg) }}</td>
              <td>{{ formatDateTime(task.createTime) }}</td>
              <td>{{ formatDateTime(task.nextExecuteTime) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination-bar">
        <span>Total {{ taskListStore.total }}</span>
        <label class="control-field">
          <span>Page size</span>
          <input
            min="1"
            :max="maxPageSize"
            type="number"
            :value="taskListStore.pageSize"
            @input="setPageSize"
          />
        </label>
        <button type="button" :disabled="taskListStore.pageNum <= 1" @click="goToPage(taskListStore.pageNum - 1)">
          Prev
        </button>
        <span>Page {{ taskListStore.pageNum }} / {{ totalPages }}</span>
        <button
          type="button"
          :disabled="taskListStore.pageNum >= totalPages"
          @click="goToPage(taskListStore.pageNum + 1)"
        >
          Next
        </button>
      </div>
    </div>
  </section>
</template>
