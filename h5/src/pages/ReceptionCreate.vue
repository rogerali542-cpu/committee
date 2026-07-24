<template>
  <div class="page reception-create">
    <PageNav title="登记接待">
      <template #left>
        <button class="back-btn" type="button" aria-label="返回接待中心" @click="back">‹</button>
      </template>
    </PageNav>

    <main class="create-body">
      <section class="form-card">
        <div class="form-row">
          <label class="field half">
            <span>接待日期 *</span>
            <input v-model="form.date" class="control" type="date" :min="today" />
          </label>
          <label class="field half">
            <span>接待时间 *</span>
            <select v-model="form.time" class="control">
              <option value="" disabled>请选择</option>
              <option v-for="time in TIME_OPTIONS" :key="time" :value="time">{{ time }}</option>
            </select>
          </label>
        </div>

        <label class="field">
          <span>接待人 *</span>
          <select v-model="form.receiver" class="control">
            <option value="" disabled>请选择接待人</option>
            <option v-if="form.receiver && !members.some(member => member.name === form.receiver)" :value="form.receiver">
              {{ form.receiver }}
            </option>
            <option v-for="member in members" :key="member.id || member.name" :value="member.name">
              {{ member.name }}<template v-if="member.role">（{{ member.role }}）</template>
            </option>
          </select>
        </label>

        <button class="no-visit-btn" type="button" :disabled="saving" @click="submitNoVisit">
          {{ saving ? '正在登记…' : '本次无人来访' }}
        </button>
      </section>

      <section v-for="(visitor, index) in visitors" :key="visitor.key" class="visitor-card">
        <div class="visitor-head">
          <strong>来访业主 {{ index + 1 }}</strong>
          <button v-if="visitors.length > 1" type="button" @click="removeVisitor(index)">删除</button>
        </div>
        <div class="form-row">
          <label class="field half">
            <span>姓名 *</span>
            <input v-model="visitor.visitorName" class="control" />
          </label>
          <label class="field half">
            <span>房号</span>
            <input v-model="visitor.room" class="control" />
          </label>
        </div>
        <div class="field">
          <span>诉求分类</span>
          <div class="type-row">
            <button v-for="item in CATEGORIES" :key="item.value" type="button"
                    :class="{ active: visitor.category === item.value }"
                    @click="visitor.category = item.value">
              {{ item.label }}
            </button>
          </div>
        </div>
        <label class="field">
          <span>诉求内容 *</span>
          <textarea v-model="visitor.content" class="control textarea"></textarea>
        </label>
      </section>

      <button class="add-visitor" type="button" @click="addVisitor">＋ 继续添加业主</button>
      <button class="submit-btn" type="button" :disabled="saving" @click="submit">
        {{ saving ? '正在保存…' : '确认登记' }}
      </button>
    </main>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import { getStorage } from '@/utils/storage'
import { toast } from '@/utils/ui'

const today = new Date().toISOString().slice(0, 10)
const TIME_OPTIONS = Array.from({ length: 23 }, (_, index) => {
  const minutes = 10 * 60 + index * 30
  return String(Math.floor(minutes / 60)).padStart(2, '0') + ':' + String(minutes % 60).padStart(2, '0')
})
const CATEGORIES = [
  { value: 'property', label: '物业类' },
  { value: 'public_affairs', label: '公共事务' },
  { value: 'neighbor', label: '邻里纠纷' },
  { value: 'other', label: '其他' }
]

const activeRole = getStorage('activeRole', null) || {}
const form = reactive({
  date: today,
  time: '19:00',
  receiver: activeRole.realName || activeRole.name || ''
})
const members = ref([])
const visitors = ref([])
const saving = ref(false)
let visitorKey = 0

function newVisitor() {
  return { key: ++visitorKey, visitorName: '', room: '', category: 'property', content: '' }
}
function addVisitor() { visitors.value.push(newVisitor()) }
function removeVisitor(index) { visitors.value.splice(index, 1) }
function back() { window.location.assign('/reception-center') }

function validateBase() {
  if (!form.date || !form.time) {
    toast({ title: '请选择接待日期和时间', icon: 'none' })
    return false
  }
  if (!String(form.receiver || '').trim()) {
    toast({ title: '请选择接待人', icon: 'none' })
    return false
  }
  return true
}

async function submitNoVisit() {
  if (saving.value || !validateBase()) return
  saving.value = true
  try {
    await api.receptionCreateSession({ ...form, noVisit: true, visitors: [] })
    toast({ title: '已登记无人来访', icon: 'success' })
    back()
  } catch (error) {
    toast({ title: (error && error.message) || '登记失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

async function submit() {
  if (saving.value || !validateBase()) return
  const incomplete = visitors.value.some(visitor =>
    !String(visitor.visitorName || '').trim() || !String(visitor.content || '').trim())
  if (!visitors.value.length || incomplete) {
    toast({ title: '请补全每位业主的姓名和诉求内容', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await api.receptionCreateSession({ ...form, visitors: visitors.value })
    toast({ title: '本次接待已登记', icon: 'success' })
    back()
  } catch (error) {
    toast({ title: (error && error.message) || '登记失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  addVisitor()
  try {
    const [system, roster] = await Promise.all([
      api.receptionSystem().catch(() => null),
      api.committeeMembers().catch(() => [])
    ])
    members.value = roster || []
    const match = String((system && system.timeDesc) || '').match(/(\d{1,2})[:：](00|30)/)
    if (match) form.time = match[1].padStart(2, '0') + ':' + match[2]
  } catch (error) { /* 默认值可继续登记 */ }
})
</script>

<style scoped>
.reception-create { min-height: 100vh; background: #F3F5F7; overflow-y: auto; }
.back-btn { width: 64rpx; height: 64rpx; border: 0; background: transparent; color: #fff; font-size: 54rpx; }
.create-body { padding: 24rpx 22rpx 80rpx; }
.form-card, .visitor-card { margin-bottom: 22rpx; padding: 26rpx; border-radius: 24rpx; background: #fff; box-shadow: 0 6rpx 18rpx rgba(31,45,61,.06); }
.form-row { display: flex; gap: 18rpx; }
.field { display: block; margin-bottom: 22rpx; color: #46515D; font-size: 28rpx; font-weight: 700; }
.field.half { flex: 1; min-width: 0; }
.field > span { display: block; margin-bottom: 10rpx; }
.control { width: 100%; height: 84rpx; box-sizing: border-box; padding: 0 18rpx; border: 2rpx solid #DFE5E9; border-radius: 16rpx; background: #FCFDFD; color: #202833; font-size: 30rpx; outline: none; }
.textarea { height: 170rpx; padding-top: 16rpx; resize: none; }
.no-visit-btn { width: 100%; height: 80rpx; border: 2rpx solid #B9C4CC; border-radius: 18rpx; background: #fff; color: #4A5560; font-size: 29rpx; font-weight: 700; }
.visitor-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 22rpx; color: #202833; font-size: 32rpx; }
.visitor-head button { border: 0; background: transparent; color: #A86B60; font-size: 27rpx; }
.type-row { display: flex; flex-wrap: wrap; gap: 12rpx; }
.type-row button { min-height: 58rpx; padding: 8rpx 20rpx; border: 0; border-radius: 30rpx; background: #F1F3F5; color: #5B6570; font-size: 27rpx; }
.type-row button.active { background: #B96300; color: #fff; font-weight: 700; }
.add-visitor { width: 100%; height: 78rpx; margin-bottom: 28rpx; border: 2rpx dashed #AAB7C0; border-radius: 18rpx; background: #fff; color: #46515D; font-size: 29rpx; }
.submit-btn { display: block; width: 68%; height: 88rpx; margin: 0 auto; border: 0; border-radius: 18rpx; background: #B96300; color: #fff; font-size: 32rpx; font-weight: 700; }
button:disabled { opacity: .55; }
</style>
