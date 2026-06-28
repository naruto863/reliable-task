<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import {
  AuditOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  ProfileOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons-vue'

import { useConsoleStore } from '@/stores/consoleStore'

const route = useRoute()
const consoleStore = useConsoleStore()

// 导航项保持静态数组，避免每次路由切换重建菜单结构；选中态由 route.name 派生。
const navItems = [
  {
    key: 'dashboard',
    label: 'Dashboard',
    to: { name: 'dashboard' },
    icon: DashboardOutlined,
  },
  {
    key: 'tasks',
    label: 'Tasks',
    to: { name: 'tasks' },
    icon: DatabaseOutlined,
  },
  {
    key: 'workers',
    label: 'Workers',
    to: { name: 'workers' },
    icon: SafetyCertificateOutlined,
  },
  {
    key: 'auditLogs',
    label: 'Audit Logs',
    to: { name: 'auditLogs' },
    icon: AuditOutlined,
  },
]

const selectedKeys = computed(() => [String(route.name || 'dashboard')])

const capabilityTag = computed(() => {
  if (consoleStore.loading) {
    return { color: 'processing', text: 'Loading capabilities' }
  }
  if (consoleStore.error?.kind === 'forbidden') {
    return { color: 'error', text: 'Access denied' }
  }
  if (consoleStore.error?.kind === 'network') {
    return { color: 'warning', text: 'API unreachable' }
  }
  if (consoleStore.capabilities) {
    // 顶部标签只表达当前控制台能力摘要，真正按钮是否可点仍由各页面按能力逐项判断。
    return {
      color: consoleStore.isWriteAvailable ? 'orange' : 'blue',
      text: consoleStore.isWriteAvailable ? 'Write guarded' : 'Read-only',
    }
  }
  return { color: 'blue', text: 'Read-only foundation' }
})

onMounted(() => {
  // 测试环境由单测直接注入 store 状态，避免挂载时自动发起真实 HTTP 请求。
  if (import.meta.env.MODE !== 'test' && !consoleStore.capabilities && !consoleStore.loading) {
    void consoleStore.loadCapabilities()
  }
})
</script>

<template>
  <a-layout class="app-shell">
    <a-layout-sider class="app-shell__sider" :width="248" breakpoint="lg" collapsed-width="0">
      <RouterLink class="brand" :to="{ name: 'dashboard' }" aria-label="ReliableTask Console">
        <span class="brand__mark">
          <ProfileOutlined />
        </span>
        <span class="brand__text">ReliableTask Console</span>
      </RouterLink>

      <a-menu class="app-shell__menu" mode="inline" theme="dark" :selected-keys="selectedKeys">
        <a-menu-item v-for="item in navItems" :key="item.key">
          <template #icon>
            <component :is="item.icon" />
          </template>
          <RouterLink :to="item.to">{{ item.label }}</RouterLink>
        </a-menu-item>
      </a-menu>
    </a-layout-sider>

    <a-layout class="app-shell__main">
      <a-layout-header class="topbar">
        <div>
          <p class="topbar__eyebrow">v0.7 Preview</p>
          <h1 class="topbar__title">{{ route.meta.title || 'Dashboard' }}</h1>
        </div>
        <a-tag :color="capabilityTag.color">{{ capabilityTag.text }}</a-tag>
      </a-layout-header>

      <a-layout-content class="content">
        <RouterView />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>
