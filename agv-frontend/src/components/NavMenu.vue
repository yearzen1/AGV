<script setup lang="ts">
import { useRoute } from 'vue-router'
import { cn } from '@/lib/utils'
import AppIcon from '@/components/AppIcon.vue'

const props = withDefaults(
  defineProps<{
    menus: Array<{ path: string; title: string; icon: string }>
    alwaysShowLabels?: boolean
  }>(),
  { alwaysShowLabels: false }
)

const emit = defineEmits<{ select: [] }>()
const route = useRoute()

function isActive(path: string) {
  return route.path === path
}
</script>

<template>
  <nav aria-label="主导航" class="flex-1 overflow-y-auto px-3 py-4">
    <ul class="space-y-1">
      <li v-for="menu in props.menus" :key="menu.path">
        <router-link
          :to="menu.path"
          :title="menu.title"
          :aria-current="isActive(menu.path) ? 'page' : undefined"
          :class="
            cn(
              'group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors',
              props.alwaysShowLabels ? 'justify-start' : 'justify-center lg:justify-start',
              isActive(menu.path)
                ? 'bg-primary-soft text-indigo-700'
                : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
            )
          "
          @click="emit('select')"
        >
          <AppIcon
            :name="menu.icon"
            :size="19"
            :class="isActive(menu.path) ? 'text-indigo-600' : 'text-slate-400 group-hover:text-slate-600'"
          />
          <span :class="props.alwaysShowLabels ? 'inline' : 'hidden lg:inline'">{{ menu.title }}</span>
          <span
            v-if="isActive(menu.path)"
            :class="
              cn(
                'ml-auto h-1.5 w-1.5 rounded-full bg-indigo-500',
                props.alwaysShowLabels ? 'block' : 'hidden lg:block'
              )
            "
            aria-hidden="true"
          ></span>
        </router-link>
      </li>
    </ul>
  </nav>
</template>
