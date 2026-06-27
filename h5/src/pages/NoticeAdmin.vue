<template>
  <div class="page" style="overflow-y:auto">
    <PublishNav title="公告管理" style="margin: 0 -3.73vw 0" />
    <div class="na-pub-row">
      <button class="na-pub-btn" @click="openCreate">＋ 发布新公告</button>
    </div>

    <!-- 公告列表 -->
    <div v-if="list.length" class="notice-list">
      <div class="notice-card pub-card" v-for="item in list" :key="item.id">
        <div class="nc-top">
          <span class="nc-date">📅 {{ item.date }}</span>
          <!-- 状态徽标位：复用蓝色公示体系预留的“上链状态”位 -->
          <span class="pub-badge" :class="item.published ? 'is-pub' : 'is-wait'">{{ item.published ? '已发布' : '未发布' }}</span>
        </div>
        <span class="nc-title">{{ item.title }}</span>
        <span class="nc-detail" v-if="item.detail">{{ item.detail }}</span>
        <div class="nc-actions">
          <button class="btn btn-outline" @click="openEdit(item)">编辑</button>
          <button class="btn btn-ghost" @click="remove(item)">删除</button>
        </div>
      </div>
    </div>

    <div v-else-if="!loading" class="empty-state">
      <span class="empty-emoji">📢</span>
      <span>还没有公告，点右上角「发布」新建</span>
    </div>
    <div v-else class="empty-state"><span>加载中...</span></div>

    <!-- 新建 / 编辑 面板 -->
    <div v-if="sheetVisible" class="modal-mask" @click="closeSheet">
      <div class="form-sheet" @click.stop>
        <div class="sheet-head">
          <span class="sheet-title">{{ editing ? '编辑公告' : '发布公告' }}</span>
          <span class="sheet-close" @click="closeSheet">×</span>
        </div>

        <div class="form-group">
          <span class="form-label">公告日期</span>
          <input type="date" class="picker-field" :value="form.date" @change="onDateChange" />
        </div>

        <div class="form-group">
          <span class="form-label">公告标题 *</span>
          <input class="form-input" v-model="form.title" placeholder="如：关于小区电梯年度维保的通知" maxlength="100" />
        </div>

        <div class="form-group">
          <span class="form-label">公告内容</span>
          <textarea class="form-textarea" v-model="form.detail" placeholder="详细说明公告内容、时间、注意事项等" maxlength="2000"></textarea>
        </div>

        <div class="form-switch-row">
          <div>
            <span class="form-label" style="margin:0;">立即对居民公开</span>
            <span class="form-hint">关闭则保存为草稿，居民暂时看不到</span>
          </div>
          <input type="checkbox" :checked="form.published" @change="onPublishedChange" />
        </div>

        <div class="sheet-actions">
          <button class="btn btn-ghost" @click="closeSheet">取消</button>
          <button class="btn btn-primary" @click="submit" :disabled="saving">{{ editing ? '保存修改' : '确认发布' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { onMounted } from 'vue'
import api from '@/api'
import PublishNav from '@/components/PublishNav.vue'
import { toast, showModal } from '@/utils/ui'

function today() {
  const d = new Date()
  const mm = ('0' + (d.getMonth() + 1)).slice(-2)
  const dd = ('0' + d.getDate()).slice(-2)
  return d.getFullYear() + '-' + mm + '-' + dd
}

const EMPTY_FORM = { id: null, date: '', title: '', detail: '', published: true }

const list = ref([])
const loading = ref(true)
const sheetVisible = ref(false)
const editing = ref(false)   // true=编辑，false=新建
const form = ref({ ...EMPTY_FORM })
const saving = ref(false)

async function loadList() {
  loading.value = true
  try {
    const data = await api.noticeManageList()
    list.value = Array.isArray(data) ? data : []
    loading.value = false
  } catch (e) {
    loading.value = false
    toast({ title: (e && e.message) || '加载失败', icon: 'none' })
  }
}

// ── 新建 / 编辑 ──
function openCreate() {
  editing.value = false
  form.value = Object.assign({}, EMPTY_FORM, { date: today() })
  sheetVisible.value = true
}

function openEdit(item) {
  editing.value = true
  form.value = {
    id: item.id,
    date: item.date || today(),
    title: item.title || '',
    detail: item.detail || '',
    published: item.published !== false
  }
  sheetVisible.value = true
}

function closeSheet() {
  sheetVisible.value = false
}

function onDateChange(e) {
  form.value.date = e.target.value
}

function onPublishedChange(e) {
  form.value.published = e.target.checked
}

async function submit() {
  const f = form.value
  if (!f.title || !f.title.trim()) {
    toast({ title: '请填写公告标题', icon: 'none' })
    return
  }
  if (saving.value) return
  saving.value = true

  const payload = {
    title: f.title.trim(),
    detail: f.detail,
    date: f.date,
    published: f.published
  }
  try {
    if (editing.value) {
      await api.noticeUpdate(f.id, payload)
    } else {
      await api.noticeCreate(payload)
    }
    sheetVisible.value = false
    saving.value = false
    toast({ title: '已保存', icon: 'success' })
    loadList()
  } catch (e) {
    saving.value = false
    toast({ title: (e && e.message) || '保存失败', icon: 'none' })
  }
}

async function remove(item) {
  const res = await showModal({
    title: '删除公告',
    content: '确定删除「' + item.title + '」？删除后无法恢复。',
    confirmText: '删除',
    confirmColor: '#E74C3C'
  })
  if (!res.confirm) return
  try {
    await api.noticeRemove(item.id)
    toast({ title: '已删除', icon: 'success' })
    loadList()
  } catch (err) {
    toast({ title: (err && err.message) || '删除失败', icon: 'none' })
  }
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  box-sizing: border-box;
  background: #F4F2EB;
  padding: 0 28rpx 80rpx;  /* 顶部不留 padding：PageNav 自己贴顶，避免负 margin 把后续内容上拉 */
}

/* ── 顶部 ── */
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 32rpx;
}
.page-title {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: #1a1a1a;
}
.page-sub {
  display: block;
  font-size: 30rpx;
  color: #5A6473;
  margin-top: 8rpx;
}
.head-btn {
  background: #EA8A2A;        /* 主操作「发布」暖橙（橙=蓝互补色，在蓝页上最跳眼）；压深降饱和不刺眼、不与蓝冲突；加大尺寸便于老人识别点按 */
  color: #fff;
  font-size: 34rpx;
  font-weight: 600;
  border-radius: 16rpx;
  padding: 0 40rpx;
  margin: 0;
  line-height: 2.6;
  border: none;
}
.head-btn:active { background: #D2771A; }

/* 顶部「发布新公告」主按钮：去掉冗余副标题后，居中加大便于老人识别点按 */
/* 按钮与上方橙色头部之间留出背景缝隙，不紧贴 */
.na-pub-row { display: flex; justify-content: center; margin: 28rpx 0 28rpx; }
.na-pub-btn { background: #3D70A0; color: #fff; font-size: 36rpx; font-weight: 700; border: none; border-radius: 18rpx; padding: 0 88rpx; line-height: 2.9; box-shadow: 0 0 0 6rpx #F4F2EB, 0 0 0 10rpx #3D70A0; }
.na-pub-btn:active { background: #2D5A88; box-shadow: 0 0 0 6rpx #F4F2EB, 0 0 0 10rpx #2D5A88; }

/* ── 公告卡片（蓝色公示卡视觉，卡内编辑/删除按钮仍保持中性/橙色） ── */
.notice-list { display: flex; flex-direction: column; gap: 24rpx; }
/* 卡片基底走 .pub-card（白圆角卡+轻阴影+蓝色点缀），顶部加一条蓝色细线点缀 */
.notice-card {
  border-top: 6rpx solid var(--pub-blue);
  margin-bottom: 0;          /* 间距交给 .notice-list 的 gap，避免与 .pub-card 自带 margin 叠加 */
}
.nc-top {
  display: flex;
  align-items: center;
  /* 右上角的 .pub-badge 为绝对定位浮在卡角，这里留出空间避免日期被压住 */
  padding-right: 160rpx;
  margin-bottom: 16rpx;
}
.nc-date { font-size: 30rpx; color: var(--pub-sub); font-weight: 600; }
.nc-title {
  display: block;
  font-size: 46rpx;
  font-weight: 700;
  color: var(--pub-ink);
  line-height: 1.4;
}
.nc-detail {
  display: block;
  font-size: 36rpx;
  color: var(--pub-text);
  line-height: 1.7;
  margin-top: 16rpx;
  white-space: pre-wrap;
}
.nc-actions {
  display: flex;
  justify-content: flex-end;
  gap: 20rpx;
  margin-top: 28rpx;
  padding-top: 24rpx;
  border-top: 2rpx solid var(--pub-blue-line);
}
/* 方案B：卡内操作按钮加大；「编辑」宝蓝描边，「删除」保持中性低调（仅本页覆盖全局 .btn） */
.nc-actions .btn { padding: 14rpx 38rpx; font-size: 30rpx; border-radius: 16rpx; line-height: 1.6; }
.nc-actions .btn-outline { background: #fff; color: #2D63D6; border: 3rpx solid #2D63D6; }

/* 空状态：覆盖全局 .empty-state 的 #999/26rpx 低对比小字，适老化提升对比与字号 */
.empty-state { color: var(--pub-sub); font-size: 32rpx; line-height: 1.6; }
.empty-emoji { font-size: 80rpx; margin-bottom: 20rpx; }

/* ── 弹窗 ── */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.45);
  z-index: 100;
  display: flex;
  align-items: flex-end;
}
.form-sheet {
  width: 100%;
  background: #fff;
  border-radius: 40rpx 40rpx 0 0;
  padding: 40rpx 36rpx calc(40rpx + env(safe-area-inset-bottom));
  max-height: 88vh;
  overflow-y: auto;
}
.sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 32rpx;
}
.sheet-title { font-size: 36rpx; font-weight: 700; color: #1a1a1a; }
.sheet-close { font-size: 52rpx; color: #5A6473; line-height: 1; padding: 0 12rpx; }

.form-group { margin-bottom: 32rpx; }
.form-label {
  display: block;
  font-size: 28rpx;
  color: #666;
  margin-bottom: 16rpx;
}
.form-input {
  background: #f6f6f8;
  border-radius: 24rpx;
  padding: 24rpx 28rpx;
  font-size: 32rpx;
  color: #1a1a1a;
  width: 100%;
  box-sizing: border-box;
  border: none;
}
.form-textarea {
  background: #f6f6f8;
  border-radius: 24rpx;
  padding: 24rpx 28rpx;
  font-size: 32rpx;
  color: #1a1a1a;
  width: 100%;
  box-sizing: border-box;
  min-height: 240rpx;
  height: 240rpx;
  border: none;
}
.picker-field {
  background: #f6f6f8;
  border-radius: 24rpx;
  padding: 24rpx 28rpx;
  font-size: 32rpx;
  color: #1a1a1a;
  width: 100%;
  box-sizing: border-box;
  border: none;
}
.form-input::placeholder,
.form-textarea::placeholder { color: #666; }

.form-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8rpx 0 16rpx;
}
.form-hint {
  display: block;
  font-size: 28rpx;
  color: #666;
  margin-top: 8rpx;
}

.sheet-actions {
  display: flex;
  gap: 24rpx;
  margin-top: 16rpx;
}
.sheet-actions .btn { flex: 1; line-height: 2.6; font-size: 32rpx; }
.sheet-actions .btn-primary { background: var(--pub-blue); }
.sheet-actions .btn-primary:active { background: var(--pub-blue-deep); }
</style>
