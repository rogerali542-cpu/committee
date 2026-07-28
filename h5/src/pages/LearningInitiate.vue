<template>
  <div class="page learning-initiate">
    <!-- 返回键用 PageNav 默认款（满高、居中）；back() 供取消按钮用 -->
    <PageNav title="发起内部学习" />

    <main class="create-body">
      <section class="form-card">
        <div class="form-group">
          <span class="form-label">学习主题 *</span>
          <input class="form-input large" v-model="form.title" />
        </div>

        <div class="form-row">
          <div class="form-group half">
            <span class="form-label">日期 *</span>
            <input type="date" class="picker-field" :value="form.date" :min="todayStr()" @change="form.date = $event.target.value" />
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
          <span class="form-label">学习内容 *</span>
          <textarea class="form-textarea" v-model="form.description"></textarea>
          <span class="form-hint">写清楚本次内部学习的主要内容 / 学习材料</span>
        </div>

        <div class="form-group">
          <span class="form-label">计划参加人员</span>
          <input class="form-input" v-model="form.attendees" />
          <span class="form-hint">多个姓名用顿号或逗号分隔；发起后可在通知页一并通知</span>
        </div>
      </section>

      <div class="create-actions">
        <button class="btn-ghost" type="button" @click="back">取消</button>
        <button class="btn-primary" type="button" :disabled="saving" @click="submit">{{ saving ? '发起中…' : '发起并去通知' }}</button>
      </div>
    </main>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import { toast } from '@/utils/ui'
import { navigateBack } from '@/utils/navigate'

// 日期默认今天，且不早于今天（发起=面向未来的内部学习）
function todayStr() {
  const d = new Date()
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
}
const form = reactive({ title: '', date: todayStr(), time: '14:00', location: '', description: '', attendees: '' })
const saving = ref(false)

function back() { navigateBack() }

async function submit() {
  const missing = []
  if (!String(form.title).trim()) missing.push('主题')
  if (!form.date) missing.push('日期')
  if (!String(form.location).trim()) missing.push('地点')
  if (!String(form.description).trim()) missing.push('学习内容')
  if (missing.length) { toast({ title: '请补全：' + missing.join('、'), icon: 'none' }); return }
  if (form.date < todayStr()) { toast({ title: '日期不能早于今天', icon: 'none' }); return }
  if (saving.value) return
  saving.value = true
  try {
    // 固定内部学习：type=internal + category=internal，阶段由后端置 preparing
    const result = await api.learningCreate({
      title: form.title.trim(),
      date: form.date,
      time: form.time || '14:00',
      location: form.location.trim(),
      description: form.description.trim(),
      attendees: (form.attendees || '').trim(),
      type: 'internal',
      category: 'internal'
    })
    // replace：不把表单页留在历史，通知页返回直接回学习列表
    window.location.replace('/learning-notify?id=' + result.id)
  } catch (e) {
    toast({ title: (e && e.message) || '发起失败', icon: 'none' })
    saving.value = false
  }
}
</script>

<style scoped>
.learning-initiate { min-height: 100vh; background: #f5f5f7; }
.create-body { padding: 24rpx 28rpx calc(40rpx + env(safe-area-inset-bottom)); }
.form-card { background: #fff; border-radius: 24rpx; padding: 30rpx 28rpx 10rpx; box-shadow: 0 6rpx 18rpx rgba(31, 45, 61, .06); }
.form-group { margin-bottom: 28rpx; }
.form-row { display: flex; gap: 20rpx; }
.form-group.half { flex: 1; min-width: 0; }
.form-label { display: block; margin-bottom: 12rpx; font-size: 28rpx; color: #4a5560; font-weight: 600; }
.form-hint { display: block; margin: 12rpx 4rpx 0; font-size: 25rpx; line-height: 1.5; color: #98A2AD; }
.form-input, .picker-field, .form-textarea { width: 100%; box-sizing: border-box; border: 2rpx solid #DFE5E9; border-radius: 16rpx; background: #FCFDFD; color: #202833; font-size: 30rpx; padding: 0 18rpx; height: 84rpx; outline: none; }
.form-input.large { font-size: 32rpx; }
.form-textarea { padding: 16rpx 18rpx; min-height: 160rpx; height: 160rpx; resize: none; line-height: 1.5; }
.create-actions { display: flex; gap: 20rpx; margin-top: 32rpx; }
.btn-ghost { flex: 1; height: 92rpx; border: 2rpx solid #C9D0D6; border-radius: 20rpx; background: #fff; color: #5B6570; font-size: 32rpx; }
.btn-primary { flex: 2; height: 92rpx; border: 0; border-radius: 20rpx; background: var(--c-primary); color: #fff; font-size: 32rpx; font-weight: 700; }
.btn-primary:disabled { opacity: .6; }
</style>
