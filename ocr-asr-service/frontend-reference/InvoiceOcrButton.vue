<template>
  <div class="flex items-center gap-2 flex-wrap">
    <label
      class="inline-flex items-center h-8 px-3 text-sm font-medium text-white bg-gray-900 rounded-lg cursor-pointer hover:bg-gray-800 whitespace-nowrap"
      :class="loading ? 'opacity-60 pointer-events-none' : ''"
    >
      <input
        type="file"
        accept=".pdf,.jpg,.jpeg,.png,.webp"
        class="hidden"
        @change="onChange"
      >
      {{ loading ? '识别中...' : '上传发票识别金额' }}
    </label>
    <span class="text-xs text-gray-400">{{ hint }}</span>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { recognizeInvoiceOcr, type InvoiceOcrResult } from '@/api/finProjectFinance'
import { message } from '@/utils/message'

// 发票 OCR 识别按钮(可复用): 选发票 -> 调后端识别 -> emit recognized(结果).
// 仅识别, 不落库; 防御: 识别结果只供父组件预填草稿, 用户确认提交时才落库.
// 兜底: ok=false 或异常一律提示手填, 不阻塞录入.
withDefaults(
  defineProps<{ hint?: string }>(),
  { hint: '仅支持增值税发票, 识别金额供核对, 也可直接手填' },
)
const emit = defineEmits<{ (e: 'recognized', result: InvoiceOcrResult): void }>()

const loading = ref(false)

async function onChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  loading.value = true
  try {
    const res = (await recognizeInvoiceOcr(file)) as unknown as InvoiceOcrResult
    if (!res || !res.ok) {
      message.warning((res && res.message) || '识别失败, 请手动填写金额')
    } else {
      emit('recognized', res)
    }
  } catch (err) {
    const e2 = err as { response?: { data?: { detail?: string } }; message?: string }
    message.error(
      (e2.message && e2.message !== '请求失败' ? e2.message : e2.response?.data?.detail)
      || '识别失败, 请手动填写金额',
    )
  } finally {
    loading.value = false
    input.value = ''
  }
}
</script>
