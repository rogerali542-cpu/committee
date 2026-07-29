<template>
  <div class="page learning-create">
    <PageNav :title="pageTitle">
      <template #left>
        <button class="back-btn" type="button" aria-label="返回学习培训" @click="back">‹</button>
      </template>
    </PageNav>

    <main class="create-body">
      <section class="form-card">
        <!-- 占位示例文案不放（0725 用户定，同发起会议议题框）：标签已说明用途，灰字是重复噪音 -->
        <div class="form-group">
          <span class="form-label">学习主题 *</span>
          <input class="form-input large" v-model="form.title" />
        </div>

        <div class="form-row">
          <div class="form-group half">
            <span class="form-label">日期 *</span>
            <input type="date" class="picker-field" :value="form.date" @change="form.date = $event.target.value" />
          </div>
          <div class="form-group half">
            <span class="form-label">时间</span>
            <input type="time" class="picker-field" :value="form.time" @change="form.time = $event.target.value" />
          </div>
        </div>

        <div class="form-group">
          <span class="form-label">地点 *</span>
          <input class="form-input" v-model="form.location" />
        </div>

        <div class="form-group">
          <span class="form-label">组织单位/讲师 *</span>
          <input class="form-input" v-model="form.trainer" />
        </div>

        <!-- 学习记录登记的是已完成的学习(0725 用户定):填实际参加的人,不是"计划+发通知" -->
        <div class="form-group">
          <span class="form-label">参加人员</span>
          <input class="form-input" v-model="form.attendees" />
        </div>

        <div class="form-group">
          <span class="form-label">学习分类</span>
          <div class="type-row">
            <span class="type-chip" :class="{ on: form.category === 'internal' }" @click="form.category = 'internal'">内部学习</span>
            <span class="type-chip" :class="{ on: form.category === 'external' }" @click="form.category = 'external'">外部培训</span>
          </div>
        </div>

        <div class="form-group">
          <span class="form-label">学习内容说明</span>
          <textarea class="form-textarea" v-model="form.description"></textarea>
        </div>
      </section>

      <div class="create-actions">
        <button class="btn-ghost" type="button" @click="back">取消</button>
        <!-- 「登记」不是「创建」(0725 用户定):这里是把已完成的学习记录在案,与「登记接待」同一口径 -->
        <button class="btn-primary" type="button" :disabled="saving" @click="submit">{{ saving ? '正在登记…' : '确认登记' }}</button>
      </div>
    </main>
  </div>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import { toast } from '@/utils/ui'
import { navigateBack } from '@/utils/navigate'

// 从「登记外部培训」入口进来 type=external：默认分类外部培训、标题相应变化
const route = useRoute()
const defaultCategory = route.query.type === 'external' ? 'external' : 'internal'
const pageTitle = computed(() => defaultCategory === 'external' ? '登记外部培训' : '登记学习记录')

// 文本字段默认全空（0725 用户定）；日期/时间给默认值（今天/14:00，0725 用户定：原生控件的 mm/dd/yyyy 空态难看又难填）
function todayStr() {
  const d = new Date()
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
}
const form = reactive({ title: '', date: todayStr(), time: '14:00', location: '', trainer: '', attendees: '', category: defaultCategory, description: '' })
const saving = ref(false)

// 导航新规(0725 用户定):返回=历史上一页(本页只从学习列表 push 进入)
function back() { navigateBack() }

async function submit() {
  // 必填:标题/日期/地点/组织单位(0725 用户定,地点与组织也是记录要件)
  const missing = []
  if (!String(form.title).trim()) missing.push('主题')
  if (!form.date) missing.push('日期')
  if (!String(form.location).trim()) missing.push('地点')
  if (!String(form.trainer).trim()) missing.push('组织单位/讲师')
  if (missing.length) {
    toast({ title: '请补全：' + missing.join('、'), icon: 'none' })
    return
  }
  if (saving.value) return
  saving.value = true
  try {
    // type 由 category 派生以兼容按 type 拉取的列表：内部→internal，外部→street
    const result = await api.learningCreate({ ...form, type: form.category === 'internal' ? 'internal' : 'street' })
    toast({ title: '已登记', icon: 'success' })
    // replace:不把已提交的表单页留在历史里,详情页按返回直接回学习列表
    window.location.replace('/learning-detail?id=' + result.id)
  } catch (e) {
    toast({ title: (e && e.message) || '创建失败', icon: 'none' })
    saving.value = false
  }
}
</script>

<style scoped>
.learning-create { min-height: 100vh; background: #f5f5f7; }
.back-btn { width: 64rpx; height: 64rpx; border: 0; background: transparent; color: #fff; font-size: 54rpx; }
.create-body { padding: 24rpx 28rpx calc(40rpx + env(safe-area-inset-bottom)); }
.form-card { background: #fff; border-radius: 24rpx; padding: 30rpx 28rpx 10rpx; box-shadow: 0 6rpx 18rpx rgba(31, 45, 61, .06); }
.form-group { margin-bottom: 28rpx; }
.form-row { display: flex; gap: 20rpx; }
.form-group.half { flex: 1; min-width: 0; }
.form-label { display: block; margin-bottom: 12rpx; font-size: 28rpx; color: #4a5560; font-weight: 600; }
.form-input, .picker-field, .form-textarea { width: 100%; box-sizing: border-box; border: 2rpx solid #DFE5E9; border-radius: 16rpx; background: #FCFDFD; color: #202833; font-size: 30rpx; padding: 0 18rpx; height: 84rpx; outline: none; }
.form-input.large { font-size: 32rpx; }
.form-textarea { padding: 16rpx 18rpx; min-height: 160rpx; height: 160rpx; resize: none; line-height: 1.5; }
.type-row { display: flex; gap: 16rpx; flex-wrap: wrap; }
.type-chip { min-height: 60rpx; display: inline-flex; align-items: center; padding: 0 24rpx; border-radius: 999rpx; background: #F1F3F5; color: #5B6570; font-size: 28rpx; }
.type-chip.on { background: var(--c-primary-dark); color: #fff; font-weight: 700; }
.create-actions { display: flex; gap: 20rpx; margin-top: 32rpx; }
.btn-ghost { flex: 1; height: 92rpx; border: 2rpx solid #C9D0D6; border-radius: 20rpx; background: #fff; color: #5B6570; font-size: 32rpx; }
.btn-primary { flex: 2; height: 92rpx; border: 0; border-radius: 20rpx; background: var(--c-primary); color: #fff; font-size: 32rpx; font-weight: 700; }
.btn-primary:disabled { opacity: .6; }
</style>
