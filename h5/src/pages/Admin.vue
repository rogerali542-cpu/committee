<template>
  <div class="page" style="overflow-y:auto">
    <div class="card" v-for="item in users" :key="item.id">
      <div class="user-head" @click="toggleExpand(item.id)">
        <span class="uh-name">{{ item.realName }}</span>
        <span class="uh-role">{{ item.role }}</span>
        <span class="uh-arrow">{{ expandedId === item.id ? '▼' : '▶' }}</span>
      </div>

      <!-- 展开的权限面板 -->
      <div v-if="expandedId === item.id" class="perm-panel">
        <span class="perm-note">角色默认：{{ item.defaultPerms.join(' / ') }}</span>
        <div class="perm-list">
          <div class="perm-item" v-for="p in item.allPerms" :key="p.code">
            <span class="pi-name">{{ p.label }}</span>
            <input
              type="checkbox"
              class="perm-switch"
              :checked="p.granted"
              :disabled="expandedId === 99"
              @change="togglePerm(expandedId, p.code, $event.target.checked)"
            />
          </div>
        </div>
        <div class="perm-actions">
          <button class="btn btn-ghost btn-mini" @click="resetPerms(item.id)">重置为角色默认</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { onActivated } from 'vue'
import { toast } from '@/utils/ui'

// 角色默认权限（迁移自 miniapp utils/mock）
const roleDefaultPerms = {
  '主任': ['committee.*', 'reception.*', 'learning.*', 'view.*'],
  '副主任': ['committee.*', 'reception.*', 'learning.*', 'view.*'],
  '委员': ['committee.sign_in', 'committee.sign', 'committee.vote', 'committee.evidence', 'committee.topic', 'committee.sign_all', 'reception.manage', 'learning.view', 'view.internal', 'view.public'],
  '业主': ['view.public'],
  '物业': ['view.public', 'reception.property_feedback'],
  '管理员': ['*']
}
// 个人权限覆写（空=用角色默认），运行期可变
const userPermOverrides = {}
// 所有用户列表
const allUsers = [
  { id: 1, realName: '张建国', role: '主任' },
  { id: 2, realName: '李秀英', role: '副主任' },
  { id: 3, realName: '王志强', role: '委员' },
  { id: 4, realName: '赵丽娟', role: '委员' },
  { id: 5, realName: '刘海涛', role: '委员' },
  { id: 6, realName: '陈晓梅', role: '委员' },
  { id: 7, realName: '杨国华', role: '委员' },
  { id: 8, realName: '秘书小李', role: '委员' },
  { id: 9, realName: '测试业主', role: '业主' },
  { id: 10, realName: '物业张经理', role: '物业' },
  { id: 99, realName: '系统管理员', role: '管理员' },
  { id: 11, realName: '张丽华', role: '业主' }
]

const users = ref([])
const expandedId = ref(null)

function loadUsers() {
  const perms = [
    { code: 'committee.sign_in', label: '签到' },
    { code: 'committee.sign', label: '签字' },
    { code: 'committee.vote', label: '投票' },
    { code: 'committee.create', label: '新建会议' },
    { code: 'committee.advance', label: '推进阶段' },
    { code: 'committee.delivery', label: '送达管理' },
    { code: 'committee.evidence', label: '上传佐证' },
    { code: 'committee.topic', label: '议题管理' },
    { code: 'committee.publish', label: '发起公示' },
    { code: 'learning.create', label: '创建学习' },
    { code: 'learning.advance', label: '推进学习阶段' },
    { code: 'learning.view', label: '查看学习' },
    { code: 'view.internal', label: '查看内部流程' },
    { code: 'view.public', label: '查看公示' }
  ]

  const overrides = userPermOverrides
  const defaults = roleDefaultPerms

  users.value = allUsers.map(u => {
    const def = defaults[u.role] || []
    const over = overrides[u.id] || {}
    return {
      id: u.id,
      realName: u.realName,
      role: u.role,
      defaultPerms: def,
      allPerms: perms.map(p => {
        // 覆写优先，否则用角色默认
        let granted = def.includes(p.code) || def.includes(p.code.split('.')[0] + '.*')
        if (p.code in over) granted = over[p.code]
        if (def.includes('*')) granted = true
        return { ...p, granted }
      })
    }
  })
}

function toggleExpand(id) {
  expandedId.value = expandedId.value === id ? null : id
}

function togglePerm(userId, code, granted) {
  if (!userPermOverrides[userId]) userPermOverrides[userId] = {}
  userPermOverrides[userId][code] = granted
  loadUsers()
  toast({ title: granted ? '已授权' : '已禁用', icon: 'success' })
}

function resetPerms(userId) {
  delete userPermOverrides[userId]
  loadUsers()
  toast({ title: '已重置', icon: 'success' })
}

onMounted(() => {
  loadUsers()
})
onActivated(() => {
  loadUsers()
})
</script>

<style scoped>
.page { min-height: 100vh; background: #f4f5f7; padding: 24rpx; box-sizing: border-box; }
.card { background: #fff; border-radius: 24rpx; padding: 28rpx 26rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.user-head { display: flex; align-items: center; gap: 16rpx; }
.uh-name { font-size: 34rpx; font-weight: 600; color: #1f2329; }
.uh-role { font-size: 28rpx; background: #FFF6E5; color: #C77800; padding: 4rpx 18rpx; border-radius: 10rpx; font-weight: 600; }
.uh-arrow { margin-left: auto; font-size: 28rpx; color: #666; }

.perm-panel { margin-top: 18rpx; padding-top: 18rpx; border-top: 2rpx solid #f0f0f0; }
.perm-note { font-size: 28rpx; color: #777; display: block; margin-bottom: 14rpx; line-height: 1.6; }
.perm-list { display: flex; flex-direction: column; }
.perm-item { display: flex; align-items: center; justify-content: space-between; padding: 14rpx 0; border-bottom: 2rpx solid #fafafa; }
.pi-name { font-size: 30rpx; color: #33373d; }
.perm-actions { margin-top: 18rpx; text-align: center; }

.perm-switch { width: 44rpx; height: 44rpx; accent-color: #FFA800; }
.btn-mini { display: inline-block; width: auto; padding: 8rpx 24rpx; font-size: 28rpx; }
</style>
