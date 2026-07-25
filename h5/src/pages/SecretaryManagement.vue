<template>
  <div class="page">
    <PageNav title="秘书授权管理" />

    <section class="intro-card">
      <div class="intro-title">业委会秘书</div>
      <div class="intro-text">秘书可以协助发送通知、上传材料、撰写材料并发布公示。撤回授权后，秘书将立即无法继续操作。</div>
    </section>

    <section class="secretary-list">
      <div v-if="loading" class="empty">正在加载…</div>
      <div v-else-if="!secretaries.length" class="empty">暂未设置业委会秘书</div>
      <div v-for="item in secretaries" :key="item.id" class="secretary-card">
        <div class="avatar">秘</div>
        <div class="person">
          <div class="name">{{ item.realName }}</div>
          <div class="status" :class="{ active: item.enabled }">{{ item.enabled ? '已授权' : '授权已收回' }}</div>
        </div>
        <button v-if="item.enabled" class="action revoke" @click="changeAuthorization(item, false)">收回权限</button>
        <button v-else class="action authorize" @click="changeAuthorization(item, true)">重新授权</button>
      </div>
    </section>

    <section class="reserved-card">
      <div class="reserved-title">主任专属权限</div>
      <div class="reserved-item">任命或撤销秘书</div>
      <div class="reserved-item">调整成员身份和系统权限</div>
      <div class="reserved-item">撤销或作废正式公示、归档材料</div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import { showModal, toast } from '@/utils/ui'

const loading = ref(true)
const secretaries = ref([])

async function load() {
  loading.value = true
  try {
    secretaries.value = await api.secretaryList()
  } catch (e) {
    toast({ title: e.message || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function changeAuthorization(item, authorize) {
  const confirmed = await showModal({
    title: authorize ? '重新授权' : '收回权限',
    content: authorize
      ? `确认重新授权${item.realName}担任业委会秘书吗？`
      : `收回后，${item.realName}将立即无法继续处理业委会工作。`,
    confirmText: authorize ? '确认授权' : '确认收回'
  })
  if (!confirmed) return
  try {
    if (authorize) await api.authorizeSecretary(item.id)
    else await api.revokeSecretary(item.id)
    toast({ title: authorize ? '已授权' : '权限已收回', icon: 'success' })
    await load()
  } catch (e) {
    toast({ title: e.message || '操作失败', icon: 'none' })
  }
}

onMounted(load)
</script>

<style scoped>
.page { min-height: 100vh; box-sizing: border-box; background: #f3f5f7; padding: 0 24rpx 60rpx; }
.intro-card, .reserved-card, .secretary-list { margin-top: 24rpx; }
.intro-card, .reserved-card { background: #fff; border-radius: 24rpx; padding: 28rpx; box-shadow: 0 6rpx 22rpx rgba(35,48,68,.06); }
.intro-title, .reserved-title { font-size: 32rpx; color: #26334a; font-weight: 700; }
.intro-text { margin-top: 12rpx; color: #738096; font-size: 27rpx; line-height: 1.65; }
.secretary-list { display: flex; flex-direction: column; gap: 18rpx; }
.secretary-card { background: #fff; border-radius: 24rpx; padding: 26rpx; display: flex; align-items: center; box-shadow: 0 6rpx 22rpx rgba(35,48,68,.06); }
.avatar { width: 76rpx; height: 76rpx; border-radius: 22rpx; background: #e8eef8; color: #41689d; display: flex; align-items: center; justify-content: center; font-size: 34rpx; font-weight: 700; }
.person { flex: 1; margin-left: 20rpx; }
.name { font-size: 31rpx; color: #26334a; font-weight: 700; }
.status { margin-top: 6rpx; font-size: 24rpx; color: #9b6b6b; }
.status.active { color: #39805b; }
.action { border: 0; min-height: 68rpx; padding: 0 22rpx; border-radius: 18rpx; font-size: 26rpx; }
.action.revoke { color: #9b5555; background: #f8eeee; }
.action.authorize { color: #fff; background: #456f9f; }
.empty { background: #fff; border-radius: 24rpx; padding: 50rpx 24rpx; text-align: center; color: #7c8798; font-size: 28rpx; }
.reserved-item { color: #59677a; font-size: 27rpx; margin-top: 17rpx; padding-left: 26rpx; position: relative; }
.reserved-item::before { content: ''; position: absolute; left: 2rpx; top: 14rpx; width: 8rpx; height: 8rpx; border-radius: 50%; background: #a46b43; }
</style>
