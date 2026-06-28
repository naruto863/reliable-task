import { createRouter, createWebHistory } from 'vue-router'

import AuditLogsView from '@/views/AuditLogsView.vue'
import DashboardView from '@/views/DashboardView.vue'
import TaskDetailView from '@/views/TaskDetailView.vue'
import TasksView from '@/views/TasksView.vue'
import WorkersView from '@/views/WorkersView.vue'

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 路由 meta 同时驱动顶部标题和占位页描述，页面组件不需要重复维护导航文案。
    {
      path: '/',
      name: 'dashboard',
      component: DashboardView,
      meta: {
        title: 'Dashboard',
      },
    },
    {
      path: '/tasks',
      name: 'tasks',
      component: TasksView,
      meta: {
        title: 'Tasks',
        description: 'Task list shell',
      },
    },
    {
      path: '/tasks/:id',
      name: 'taskDetail',
      component: TaskDetailView,
      meta: {
        title: 'Task Detail',
        description: 'Console-safe task detail',
      },
    },
    {
      path: '/workers',
      name: 'workers',
      component: WorkersView,
      meta: {
        title: 'Workers',
        description: 'Worker heartbeat and capacity',
      },
    },
    {
      path: '/audit-logs',
      name: 'auditLogs',
      component: AuditLogsView,
      meta: {
        title: 'Audit Logs',
        description: 'Audit log search',
      },
    },
  ],
})
