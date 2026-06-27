<template>
  <div class="page">
    <div class="hub-grid">
      <div class="mt-card" v-if="!isProperty" @click="goCommittee">
        <div class="mt-icon" style="background:#FFF3DC;">📅</div>
        <span class="mt-title">业主委员会</span>
        <span class="mt-desc">例会管理 · 三阶段跟踪</span>
        <span class="mt-badge">{{ counts.committeeActive }}</span>
      </div>
      <div class="mt-card" @click="goReception">
        <div class="mt-icon" style="background:#EBF8F2;">📝</div>
        <span class="mt-title">接待记录</span>
        <span class="mt-desc">来访接待登记</span>
        <span class="mt-badge" style="background:#2ECC71;">{{ counts.receptionPending }}</span>
      </div>
      <div class="mt-card" @click="goLearning">
        <div class="mt-icon" style="background:#F5EEF8;">📖</div>
        <span class="mt-title">业务学习</span>
        <span class="mt-desc">培训学习记录</span>
      </div>
    </div>
    <div class="hub-tip">
      <span>💡 提示：会议新建、开始与结束由主任/副主任操作；记录员负责送达、佐证与留痕；委员可查看本人相关会议。</span>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onActivated } from 'vue'
import api from '@/api'
import { navigateTo } from '@/utils/navigate'
import { getStorage } from '@/utils/storage'

const counts = ref({ committeeActive: 0, receptionPending: 0 })
const isProperty = ref(false)

async function refresh() {
  const activeRole = getStorage('activeRole')
  const role = activeRole && activeRole.role ? activeRole.role : ''
  // 物业不参与小区行政，不展示业委会入口
  isProperty.value = role === '物业'
  await loadCounts()
}

async function loadCounts() {
  try {
    const cs = await api.committeeStats()
    const rs = await api.receptionStats()
    counts.value = {
      committeeActive: (cs.preparing || 0) + (cs.ongoing || 0),
      receptionPending: rs.pending || 0
    }
  } catch (e) {
    counts.value = { committeeActive: 0, receptionPending: 0 }
  }
}

function goCommittee() { navigateTo('/pages/committee/committee') }
function goReception() { navigateTo('/pages/reception/reception') }
function goLearning() { navigateTo('/pages/learning/learning') }

let firstRun = true
onMounted(() => { firstRun = false; refresh() })
onActivated(() => { if (!firstRun) refresh() })
</script>

<style scoped>
.page { padding: 28rpx 24rpx; background: #f4f5f7; min-height: 100vh; box-sizing: border-box; }
.hub-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20rpx; }
.mt-card {
  background: #fff; border-radius: 24rpx; padding: 40rpx 24rpx;
  display: flex; flex-direction: column; align-items: center; gap: 14rpx;
  box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); position: relative;
}
.mt-card:active { background: #fafbfc; }
.mt-icon { width: 104rpx; height: 104rpx; border-radius: 26rpx; display: flex; align-items: center; justify-content: center; font-size: 52rpx; }
.mt-title { font-size: 34rpx; font-weight: 700; color: #1f2329; }
.mt-desc { font-size: 28rpx; color: #666; text-align: center; }
.mt-badge {
  position: absolute; top: 18rpx; right: 18rpx;
  background: #FFA800; color: #fff;
  font-size: 28rpx; font-weight: 700; min-width: 32rpx; text-align: center;
  padding: 2rpx 12rpx; border-radius: 16rpx;
}
.hub-tip {
  margin-top: 28rpx; background: #FFFBF0; border-radius: 18rpx; padding: 24rpx 26rpx;
  border: 2rpx solid #FFE9A0; font-size: 28rpx; color: #B8860B; line-height: 1.7;
}
</style>
