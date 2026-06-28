<script setup lang="ts">
// 单独的 403 展示组件保留给需要阻断整页访问的场景；普通请求失败仍走 ErrorState。
withDefaults(
  defineProps<{
    message?: string
  }>(),
  {
    message: 'The current operator cannot access this Admin API resource.',
  },
)
</script>

<template>
  <div class="state state--forbidden" role="alert">
    <p class="state__title">Access denied</p>
    <p class="state__message">{{ message }}</p>
  </div>
</template>
