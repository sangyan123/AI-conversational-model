<script setup lang="ts">
/**
 * 轻量内联 SVG 图标。
 *
 * 原来项目从 CDN 引 Font Awesome 全套 CSS（约 100KB+），只为用 3 个图标，
 * 且断网时全部图标丢失。改为内联 SVG 后：无外部依赖、离线可用、体积可忽略。
 */
defineProps<{
  name: 'robot' | 'user' | 'send'
}>()
</script>

<template>
  <svg
    class="icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="2"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <!-- 机器人 -->
    <template v-if="name === 'robot'">
      <rect x="4" y="8" width="16" height="12" rx="2" />
      <path d="M12 4v4" />
      <circle cx="12" cy="3" r="1" />
      <circle cx="9" cy="13" r="1" fill="currentColor" stroke="none" />
      <circle cx="15" cy="13" r="1" fill="currentColor" stroke="none" />
      <path d="M9 17h6" />
    </template>

    <!-- 用户 -->
    <template v-else-if="name === 'user'">
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21c0-4 3.6-6 8-6s8 2 8 6" />
    </template>

    <!-- 发送（纸飞机） -->
    <template v-else-if="name === 'send'">
      <path d="M22 2 11 13" />
      <path d="M22 2 15 22l-4-9-9-4z" />
    </template>
  </svg>
</template>

<style scoped>
.icon {
  width: 1em;
  height: 1em;
  display: block;
}
</style>
