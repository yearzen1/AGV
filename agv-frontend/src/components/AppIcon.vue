<script setup lang="ts">
import { computed } from 'vue'

/**
 * 内联 SVG 图标：不引入图标库，统一线宽与端点，纯装饰时对读屏隐藏。
 * 新增图标时把路径写进 ICONS，并在调用处保证有文字标签或 aria-label。
 */
const ICONS: Record<string, string[]> = {
  dashboard: ['M4 4h6v6H4z', 'M14 4h6v6h-6z', 'M4 14h6v6H4z', 'M14 14h6v6h-6z'],
  map: ['M9 3 3 6v15l6-3 6 3 6-3V3l-6 3-6-3Z', 'M9 3v15', 'M15 6v15'],
  tasks: ['M8 6h11', 'M8 12h11', 'M8 18h11', 'M3.5 6h.01', 'M3.5 12h.01', 'M3.5 18h.01'],
  simulation: ['M3 12h3.5l2.5-7 4 14 2.5-7H21'],
  chart: ['M4 20V10', 'M10 20V4', 'M16 20v-7', 'M22 20H2'],
  users: [
    'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2',
    'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8',
    'M22 21v-2a4 4 0 0 0-3-3.87',
    'M16 3.13a4 4 0 0 1 0 7.75'
  ],
  dict: ['M4 19.5A2.5 2.5 0 0 1 6.5 17H20', 'M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z'],
  menu: ['M4 7h16', 'M4 12h16', 'M4 17h16'],
  close: ['M6 6l12 12', 'M18 6 6 18'],
  logout: ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'm16 17 5-5-5-5', 'M21 12H9'],
  pin: ['M12 21s-6-5.686-6-10a6 6 0 1 1 12 0c0 4.314-6 10-6 10Z', 'M12 11a2 2 0 1 0 0-4 2 2 0 0 0 0 4'],
  bolt: ['M13 2 3 14h7l-1 8 10-12h-7l1-8Z'],
  check: ['M20 6 9 17l-5-5'],
  plus: ['M12 5v14', 'M5 12h14'],
  search: ['M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16Z', 'm21 21-4.35-4.35'],
  arrow: ['m9 18 6-6-6-6'],
  layers: ['M12 2 2 7l10 5 10-5-10-5Z', 'm2 17 10 5 10-5', 'm2 12 10 5 10-5'],
  eye: ['M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7Z', 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z'],
  eyeOff: [
    'm3 3 18 18',
    'M10.6 5.2A10.8 10.8 0 0 1 12 5c6.4 0 10 7 10 7a17.5 17.5 0 0 1-3.2 4.1',
    'M6.6 6.6A17.7 17.7 0 0 0 2 12s3.6 7 10 7a10.7 10.7 0 0 0 5.4-1.4',
    'M9.9 9.9a3 3 0 0 0 4.2 4.2'
  ]
}

const props = withDefaults(
  defineProps<{
    name: string
    size?: number
    strokeWidth?: number
  }>(),
  {
    size: 18,
    strokeWidth: 1.8
  }
)

const paths = computed(() => ICONS[props.name] ?? [])
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path v-for="(d, index) in paths" :key="index" :d="d" />
  </svg>
</template>

<style scoped>
svg {
  display: block;
  flex: none;
}
</style>
