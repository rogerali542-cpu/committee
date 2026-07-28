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

        <div class="form-group">
          <span class="form-label">日期与时间 *</span>
          <!-- 与发起会议同款日历/时间选择器（中文、点选即定） -->
          <PlanDateTimeField v-model:date="form.date" v-model:time="form.time" :min-today="true" />
        </div>

        <div class="form-group">
          <span class="form-label">地点 *</span>
          <!-- 与发起会议一致：常用地点点选，也可选「其他地点」手动输入 -->
          <div v-if="locationPreset === '__other__'" class="loc-other">
            <input class="form-input" v-model="form.location" placeholder="请输入学习地点" />
            <span class="loc-switch" @click="openLocPicker">选择常用地点</span>
          </div>
          <div v-else class="form-input loc-pick" @click="openLocPicker">
            <span :class="{ ph: !form.location }">{{ form.location || '选择地点' }}</span>
            <span class="loc-arrow">›</span>
          </div>
        </div>

        <div class="form-group">
          <span class="form-label">学习内容 *</span>
          <textarea class="form-textarea" v-model="form.description"></textarea>
        </div>

        <div class="form-group">
          <span class="form-label">计划参加人员</span>
          <!-- 内部学习只涉及业委会成员：默认全选，展开可单独勾选（与会议通知页同款） -->
          <div class="member-card">
            <div class="member-head" @click="membersOpen = !membersOpen">
              <span class="member-title">业委会成员</span>
              <div class="member-right">
                <div class="mc-all" @click.stop="toggleAll">
                  <div class="mc-check" :class="{ on: allChecked }">{{ allChecked ? '✓' : '' }}</div>
                  <span class="mc-all-label">全选</span>
                  <span class="mc-count">已选 {{ selectedCount }}/{{ members.length }} 人</span>
                </div>
                <span class="member-toggle" :class="{ open: membersOpen }">{{ membersOpen ? '收起' : '展开' }}<span class="mt-arr"></span></span>
              </div>
            </div>
            <div v-if="membersOpen" class="mc-list">
              <div v-for="m in members" :key="m.userRoleId" class="mc-item" @click="toggleMember(m.userRoleId)">
                <div class="mc-check" :class="{ on: m.checked }">{{ m.checked ? '✓' : '' }}</div>
                <div class="mc-person">
                  <span class="mc-name">{{ m.name }}</span>
                  <span v-if="m.role" class="mc-role">{{ m.role }}</span>
                </div>
              </div>
              <div v-if="!members.length" class="mc-empty">暂无业委会成员</div>
            </div>
          </div>
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
import { reactive, ref, computed, onMounted } from 'vue'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import PlanDateTimeField from '@/components/PlanDateTimeField.vue'
import { toast, showActionSheet } from '@/utils/ui'
import { navigateBack } from '@/utils/navigate'

// 日期默认今天，且不早于今天（发起=面向未来的内部学习）
function todayStr() {
  const d = new Date()
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
}
const form = reactive({ title: '', date: todayStr(), time: '14:00', location: '社区活动室', description: '' })
const saving = ref(false)

// 地点：常用地点点选 + 其他地点手动输入（与发起会议一致，去掉地图选点）
const commonLocations = ['社区活动室', '社区会议室']
const locationPreset = ref('社区活动室')
async function openLocPicker() {
  const res = await showActionSheet({ itemList: [...commonLocations, '其他地点（手动填写）'] })
  if (!res || res.tapIndex == null || res.tapIndex < 0) return
  const i = res.tapIndex
  if (i < commonLocations.length) { locationPreset.value = commonLocations[i]; form.location = commonLocations[i] }
  else { locationPreset.value = '__other__'; form.location = '' }
}

// 计划参加人员：业委会成员，默认全选，可展开单独勾选
const members = ref([])          // [{ userRoleId, name, role, checked }]
const membersOpen = ref(false)
const selectedCount = computed(() => members.value.filter(m => m.checked).length)
const allChecked = computed(() => members.value.length > 0 && members.value.every(m => m.checked))
function toggleAll() {
  const target = !allChecked.value
  members.value.forEach(m => { m.checked = target })
}
function toggleMember(id) {
  const m = members.value.find(x => x.userRoleId === id)
  if (m) m.checked = !m.checked
}
async function loadMembers() {
  try {
    const list = await api.committeeMembers()
    members.value = (list || [])
      .map(m => ({ userRoleId: Number(m.userRoleId), name: m.name || '委员', role: m.role || '', checked: true }))
      .filter(m => m.userRoleId)
  } catch (e) { members.value = [] }
}
function selectedNames() {
  return members.value.filter(m => m.checked).map(m => m.name).join('、')
}

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
      attendees: selectedNames(),
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

onMounted(loadMembers)
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

/* 地点：点选行（值+箭头）/ 其他地点手动输入 */
.loc-pick { display: flex; align-items: center; justify-content: space-between; }
.loc-pick .ph { color: #9AA2AB; }
.loc-arrow { flex-shrink: 0; color: #c4c8cd; font-size: 30rpx; line-height: 1; }
.loc-other { display: flex; flex-direction: column; gap: 12rpx; }
.loc-switch { align-self: flex-start; font-size: 25rpx; color: var(--c-primary-dark); }

/* 计划参加人员：业委会成员卡（默认全选、可展开勾选），与会议通知页同款 */
.member-card { border: 2rpx solid #DFE5E9; border-radius: 16rpx; background: #fff; overflow: hidden; }
.member-head { display: flex; align-items: center; justify-content: space-between; gap: 10rpx; padding: 0 18rpx; min-height: 88rpx; box-sizing: border-box; }
.member-head:active { background: #FAFAFA; }
.member-title { font-size: 29rpx; color: #1f2329; font-weight: 700; }
.member-right { flex-shrink: 0; display: flex; align-items: center; gap: 14rpx; }
.mc-all { display: flex; align-items: center; gap: 8rpx; padding: 0 6rpx; }
.mc-all-label { font-size: 27rpx; color: #A85800; font-weight: 700; white-space: nowrap; }
.mc-count { font-size: 25rpx; color: #8A9099; white-space: nowrap; }
/* 展开入口：品牌色胶囊 + 边框画的箭头（字符 ⌄ 基线偏低难居中，改 CSS 绘制精确对齐），0728 用户定 */
.member-toggle { flex-shrink: 0; display: inline-flex; align-items: center; gap: 10rpx; padding: 8rpx 18rpx; border-radius: 999rpx; background: var(--c-primary-soft); color: var(--c-primary-dark); font-size: 25rpx; font-weight: 700; }
.mt-arr { display: inline-block; width: 12rpx; height: 12rpx; border-right: 3rpx solid currentColor; border-bottom: 3rpx solid currentColor; transform: rotate(45deg); position: relative; top: -2rpx; transition: transform .18s ease, top .18s ease; }
.member-toggle.open .mt-arr { transform: rotate(-135deg); top: 2rpx; }
.mc-list { border-top: 2rpx solid #F0F2F4; }
.mc-item { display: flex; align-items: center; gap: 14rpx; padding: 16rpx 20rpx; border-bottom: 1px solid #f0f0f2; }
.mc-item:last-child { border-bottom: none; }
.mc-item:active { background: #fafafa; }
.mc-check { flex-shrink: 0; width: 38rpx; height: 38rpx; border-radius: 50%; border: 3rpx solid #cfd4da; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 24rpx; font-weight: 700; box-sizing: border-box; }
.mc-check.on { background: var(--c-primary); border-color: var(--c-primary); }
.mc-person { display: flex; flex-direction: column; gap: 2rpx; }
.mc-name { font-size: 28rpx; color: #1f2329; font-weight: 600; }
.mc-role { font-size: 20rpx; color: #9aa0a6; }
.mc-empty { text-align: center; color: #9aa0a6; font-size: 24rpx; padding: 28rpx 0; }

.create-actions { display: flex; gap: 20rpx; margin-top: 32rpx; }
.btn-ghost { flex: 1; height: 92rpx; border: 2rpx solid #C9D0D6; border-radius: 20rpx; background: #fff; color: #5B6570; font-size: 32rpx; }
.btn-primary { flex: 2; height: 92rpx; border: 0; border-radius: 20rpx; background: var(--c-primary); color: #fff; font-size: 32rpx; font-weight: 700; }
.btn-primary:disabled { opacity: .6; }
</style>
