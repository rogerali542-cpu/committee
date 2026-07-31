<template>
  <!-- 待办详情（0731 用户定）：聚合待办页的每一项都有自己的详情页，进来看信息、处理状态。
       会议来源的待办不再跳「单场会议待办管理页」（编号卡+编辑删除那套只留给会后确认流程）。
       根类 .todo-detail 兼软路由硬跳兜底哨兵 -->
  <div class="todo-detail">
    <div class="td-hd">
      <div class="td-hd-bar" @click="goBack">
        <i class="td-back"></i>
        <span class="td-hd-title">待办详情</span>
      </div>
    </div>

    <div v-if="loading" class="td-empty">加载中…</div>
    <div v-else-if="!item" class="td-empty">这条待办不存在或已被删除。</div>
    <template v-else>
      <div class="td-card">
        <div class="td-title">{{ item.title }}</div>
        <div class="td-rows">
          <div class="td-row"><span class="td-k">来源</span><span class="td-v">{{ sourceText }}</span></div>
          <div class="td-row"><span class="td-k">负责人</span><span class="td-v">{{ item.owner || '未指定' }}</span></div>
          <div v-if="item.dueText" class="td-row"><span class="td-k">截止</span><span class="td-v">{{ item.dueText }}</span></div>
          <div class="td-row"><span class="td-k">状态</span><span class="td-v td-status" :class="item.status">{{ statusText }}</span></div>
        </div>
      </div>

      <!-- 动作区：状态流转（主=标记完成，绿实心；次=浅底）。完成态给「重新打开」纠错口 -->
      <div class="td-actions">
        <template v-if="item.status !== 'done'">
          <button type="button" class="td-btn secondary" :disabled="busy" @click="setStatus(item.status === 'doing' ? 'todo' : 'doing')">
            {{ item.status === 'doing' ? '退回待处理' : '开始处理' }}
          </button>
          <button type="button" class="td-btn primary" :disabled="busy" @click="setStatus('done')">标记完成</button>
        </template>
        <template v-else>
          <button type="button" class="td-btn secondary" :disabled="busy" @click="setStatus('todo')">重新打开</button>
          <div class="td-done-note">✓ 已完成{{ item.lastActorName ? ('（' + item.lastActorName + '）') : '' }}</div>
        </template>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '@/api'
import { toast } from '@/utils/ui'
import { navigateBack } from '@/utils/navigate'
import { getStorage } from '@/utils/storage'

const loading = ref(true)
const busy = ref(false)
const item = ref(null)

const params = new URLSearchParams(window.location.search)
const todoId = Number(params.get('id'))
const meetingId = Number(params.get('meetingId'))

function shortMeetingName(title) {
  const m = String(title || '').match(/第\s*(\d+)\s*次/)
  return m ? ('第' + m[1] + '次例会') : String(title || '')
}
const sourceText = computed(() => {
  if (!item.value) return ''
  return [shortMeetingName(item.value.meetingTitle), item.value.sourceRef ? String(item.value.sourceRef).slice(0, 16) : '']
    .filter(Boolean).join(' · ') || '会议待办'
})
const statusText = computed(() => {
  const s = item.value && item.value.status
  return s === 'done' ? '已完成' : (s === 'doing' ? '处理中' : '待跟进')
})

async function load() {
  loading.value = true
  try {
    // 无单条接口：从跨会议聚合里取（数据量小，演示够用）
    const all = (await api.committeeTodosOverview()) || []
    item.value = all.find(t => Number(t.id) === todoId) || null
  } catch (e) { item.value = null }
  loading.value = false
}

async function setStatus(key) {
  if (busy.value) return
  busy.value = true
  try {
    const res = await api.committeeTodoStatus(item.value.meetingId || meetingId, item.value.id, key)
    item.value = Object.assign({}, item.value, res || { status: key })
    toast({ title: key === 'done' ? '已标记完成' : '状态已更新', icon: 'success' })
  } catch (e) {
    toast({ title: (e && e.message) || '操作失败，请重试', icon: 'none' })
  }
  busy.value = false
}

function goBack() { navigateBack() }

onMounted(() => {
  const role = getStorage('activeRole', null)
  if (!role) { window.location.replace('/login'); return }
  load()
})
</script>

<style scoped>
.todo-detail { min-height: 100%; background: #f2f3f5; }

/* 页头：中性深灰（待办家族色，与聚合页/历史记录同族） */
.td-hd { background: linear-gradient(160deg, #55606e 0%, #434d5a 100%); padding: calc(env(safe-area-inset-top) + 18rpx) 32rpx 26rpx; color: #fff; }
.td-hd-bar { display: flex; align-items: center; gap: 18rpx; min-height: 72rpx; cursor: pointer; }
.td-hd-bar:active { opacity: .75; }
.td-back { display: inline-block; width: 18rpx; height: 18rpx; border-left: 4rpx solid #fff; border-bottom: 4rpx solid #fff; transform: rotate(45deg); }
.td-hd-title { font-size: 34rpx; font-weight: 700; }

.td-empty { padding: 120rpx 40rpx; text-align: center; color: #6B7280; font-size: 30rpx; }

.td-card { margin: 24rpx; background: #fff; border-radius: 24rpx; padding: 34rpx 30rpx 10rpx; box-shadow: 0 2rpx 6rpx rgba(31,41,55,.05), 0 10rpx 26rpx rgba(31,41,55,.07); }
.td-title { font-size: 36rpx; font-weight: 700; color: #1F2937; line-height: 1.45; }
.td-rows { margin-top: 12rpx; }
.td-row { display: flex; align-items: baseline; gap: 24rpx; min-height: 88rpx; border-top: 2rpx solid #F0F2F5; padding: 20rpx 0; box-sizing: border-box; }
.td-row:first-child { border-top: 0; }
.td-k { flex-shrink: 0; width: 96rpx; font-size: 28rpx; color: #6B7280; }
.td-v { flex: 1; min-width: 0; font-size: 31rpx; color: #1F2937; line-height: 1.5; }
/* 状态三级：待跟进灰 / 处理中蓝 / 已完成绿 */
.td-status.todo { color: #6B7280; }
.td-status.doing { color: #2b5589; font-weight: 600; }
.td-status.done { color: #2E7D50; font-weight: 600; }

.td-actions { margin: 30rpx 24rpx 0; display: flex; align-items: center; gap: 20rpx; }
.td-btn { flex: 1; min-height: 108rpx; border: 0; border-radius: 18rpx; font-size: 33rpx; font-weight: 700; }
.td-btn.primary { background: #2E7D50; color: #fff; }
.td-btn.primary:active { background: #276b45; }
.td-btn.secondary { background: #EDEFF3; color: #4A5560; font-weight: 600; }
.td-btn.secondary:active { background: #E0E4EA; }
.td-btn:disabled { opacity: .55; }
.td-done-note { flex: 1; text-align: center; font-size: 31rpx; font-weight: 600; color: #2E7D50; }
</style>
