<template>
  <div class="page" style="overflow-y:auto">
    <div class="head-bar" v-if="list.length">
      <span class="hb-title">{{ unread > 0 ? '未读 ' + unread + ' 条' : '全部已读' }}</span>
      <span class="hb-action" v-if="unread > 0" @click="markAllRead">全部已读</span>
    </div>

    <div v-if="list.length">
      <div
        v-for="item in list"
        :key="item.id"
        class="noti-item"
        :class="{ unread: !item.read }"
        @click="openMeeting(item)"
      >
        <div class="ni-dot" v-if="!item.read"></div>
        <div class="ni-body">
          <div class="ni-head">
            <span class="ni-type" :class="item.type">{{ item.type === 'notice' ? '会议通知' : item.type === 'owner_notice' ? '已停用通知' : item.type === 'reception' ? '接待事项' : '会议材料' }}</span>
            <span class="ni-time">{{ item._time }}</span>
          </div>
          <span class="ni-title">{{ item.title }}</span>
          <span class="ni-content">{{ item.content }}</span>
        </div>
        <span class="ni-arrow">›</span>
      </div>
    </div>

    <div v-else class="empty-state">
      <span v-if="!loading">暂无通知</span>
      <span v-else>加载中...</span>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { onMounted, onActivated } from 'vue'
import api from '@/api'
import { toast } from '@/utils/ui'
import { navigateTo } from '@/utils/navigate'
import { getStorage } from '@/utils/storage'

const list = ref([])
const unread = ref(0)
const loading = ref(true)

async function loadList() {
  loading.value = true
  try {
    const res = await api.notificationList()
    const newList = (res.list || []).map(function (n) {
      n._time = formatTime(n.createdAt)
      return n
    })
    list.value = newList
    unread.value = res.unread || 0
    loading.value = false
  } catch (e) {
    loading.value = false
  }
}

function openMeeting(item) {
  const meetingId = item.meetingId
  const nid = item.id
  const type = item.type
  // 标记已读
  api.notificationRead(nid).catch(function () {})
  list.value = list.value.map(function (n) {
    if (n.id === nid) n.read = true
    return n
  })
  unread.value = Math.max(0, unread.value - 1)
  if (meetingId) {
    if (type === 'owner_notice') {
      toast({ title: '业主大会模块已停用', icon: 'none' })
      return
    }
    navigateTo('/pages/committee-detail/committee-detail?id=' + meetingId + '&fromNotice=1')
    return
  }
  // 兜底：无 meetingId 时回首页。
  // 原先这里按角色跳接待页/物业工作台，但两页都已随 0716 接待重做删除；
  // 且这个分支实际不可达——NotificationController.toNotification 恒设 meetingId，上面就 return 了。
  navigateTo('/pages/main/main')
}

async function markRead(item) {
  const nid = item.id
  try {
    await api.notificationRead(nid)
    list.value = list.value.map(function (n) {
      if (n.id === nid) n.read = true
      return n
    })
    unread.value = Math.max(0, unread.value - 1)
  } catch (e) {}
}

async function markAllRead() {
  if (unread.value === 0) return
  try {
    await api.notificationReadAll()
    list.value = list.value.map(function (n) { n.read = true; return n })
    unread.value = 0
    toast({ title: '已全部已读', icon: 'success' })
  } catch (e) {}
}

function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return (d.getMonth() + 1) + '/' + d.getDate() + ' ' + d.getHours() + ':' + String(d.getMinutes()).padStart(2, '0')
}

onMounted(loadList)
onActivated(loadList)
</script>

<style scoped>
.page { height: 100vh; background: #F4F2EB; }

.head-bar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 24rpx 32rpx; background: #fff; border-bottom: 2rpx solid #eee;
}
.hb-title { font-size: 32rpx; font-weight: 600; color: #1f2329; }
.hb-action { font-size: 32rpx; color: #FFA800; font-weight: 600; }

.noti-item {
  display: flex; align-items: center; padding: 28rpx 32rpx;
  background: #fff; border-bottom: 2rpx solid #f0f0f0;
}
.noti-item.unread { background: #FFF9ED; }
.ni-dot {
  width: 16rpx; height: 16rpx; border-radius: 50%; background: #FFA800;
  margin-right: 18rpx; flex-shrink: 0;
}
.noti-item.unread .ni-dot { background: #E74C3C; }
.ni-body { flex: 1; min-width: 0; }
.ni-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8rpx; }
.ni-type { font-size: 32rpx; font-weight: 600; padding: 4rpx 14rpx; border-radius: 8rpx; }
.ni-type.notice { background: #E8F5E9; color: #2E7D32; }
.ni-type.material { background: #E3F2FD; color: #1565C0; }
.ni-type.reception { background: #FFF3E0; color: #E67E22; }
.ni-time { font-size: 32rpx; color: #666; }
.ni-title { font-size: 34rpx; font-weight: 700; color: #1f2329; display: block; margin-bottom: 6rpx; }
.ni-content { font-size: 32rpx; color: #6b7785; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ni-arrow { color: #666; font-size: 40rpx; flex-shrink: 0; margin-left: 12rpx; }
.noti-item.unread .ni-arrow { color: #FFA800; }

.empty-state { text-align: center; padding: 120rpx 0; color: #777; font-size: 32rpx; }
</style>
