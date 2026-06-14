<script setup lang="ts">
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
