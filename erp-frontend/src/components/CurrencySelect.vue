<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listSupportedCurrencies, type CurrencyOption } from '@/api/currency'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const props = withDefaults(defineProps<{
  modelValue?: string
  disabled?: boolean
  placeholder?: string
}>(), {
  modelValue: '',
  disabled: false,
  placeholder: '请选择或输入币种'
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const options = ref<CurrencyOption[]>([
  { code: 'CNY', name: '人民币', base: true },
  { code: 'USD', name: '美元', base: false }
])

const value = computed({
  get: () => props.modelValue,
  set: (next: string) => emit('update:modelValue', next?.trim().toUpperCase() || '')
})

onMounted(async () => {
  if (!userStore.hasPermission('currency:list')) return
  try {
    const res = await listSupportedCurrencies()
    if (res.data?.length) options.value = res.data
  } catch {
    // Keep the two primary settlement currencies available for restricted roles.
  }
})
</script>

<template>
  <el-select
    v-model="value"
    :disabled="disabled"
    :placeholder="placeholder"
    filterable
    allow-create
    default-first-option
    style="width: 100%"
  >
    <el-option
      v-for="item in options"
      :key="item.code"
      :label="`${item.code} · ${item.name}`"
      :value="item.code"
    />
  </el-select>
</template>
