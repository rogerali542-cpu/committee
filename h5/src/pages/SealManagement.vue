<template>
  <div class="page" style="overflow-y:auto">
    <!-- 印章为「业委会」tab 下的二级视图（0728 用户定：并入业委会，不单独占底栏）。
         作为 tab 级视图不挂返回箭头、标题居中；下方二级切换可回会议 -->
    <PageNav title="印章管理">
      <template #left><div class="nav-left-spacer"></div></template>
    </PageNav>
    <GovSubTabs active="seal" />

    <!-- 印章保管（0728 用户定口径：不带行政区、保管人直说业委会秘书；确认/驳回权限仍按主任/副主任走） -->
    <div class="seal-info-card">
      <!-- 说明卡只留标题+保管口径；三枚印章名与「现有印章三枚」重复，且申请页下拉已可选，故不再罗列 chips -->
      <div class="sic-head">
        <span class="sic-title">印章保管</span>
        <span class="sic-note">阳光花园小区现有印章三枚，由业委会秘书保管</span>
      </div>
    </div>

    <!-- 申请用印：本页主动作 -->
    <button v-if="canApply" class="seal-apply-card" type="button" @click="openApply">
      <span class="sac-icon">＋</span>
      <span class="sac-copy"><strong>申请用印</strong></span>
      <span class="sac-arrow">›</span>
    </button>

    <!-- 用印台账 -->
    <div class="records-head">
      <span class="records-title">用印台账</span>
      <span class="records-count">共{{ allRecords.length }}条</span>
    </div>
    <div class="filter-tabs">
      <div class="f-tab" :class="{ active: filter === 'all' }" @click="filter = 'all'">全部 {{ counts.all }}</div>
      <div class="f-tab" :class="{ active: filter === 'pending' }" @click="filter = 'pending'">处理中 {{ counts.pending }}</div>
      <div class="f-tab" :class="{ active: filter === 'approved' }" @click="filter = 'approved'">已用印 {{ counts.approved }}</div>
    </div>

    <div class="seal-list">
      <template v-if="records.length">
        <div v-for="r in records" :key="r.id" class="seal-record">
          <div class="sr-top">
            <span class="sr-seal">{{ r.sealLabel }}</span>
            <span class="sr-status" :class="r.status">{{ r.statusLabel }}</span>
          </div>
          <!-- 台账正文按《印章管理制度》登记项展示：用印时间、用途/事项、文件、申请人、保管人确认、附件 -->
          <div class="sr-rows">
            <div class="sr-row"><span class="sr-k">用印时间</span><span class="sr-v">{{ useTimeText(r) }}</span></div>
            <div class="sr-row"><span class="sr-k">用途/事项</span><span class="sr-v sr-v-strong">{{ r.purpose || '（未填）' }}</span></div>
            <div class="sr-row"><span class="sr-k">文件</span><span class="sr-v">{{ fileText(r) }}</span></div>
            <div class="sr-row"><span class="sr-k">申请人</span><span class="sr-v">{{ applicantText(r) }}</span></div>
            <div class="sr-row">
              <span class="sr-k">保管人确认</span>
              <span class="sr-v" :class="{ 'sr-confirmed': r.status === 'approved', 'sr-rejected': r.status === 'rejected' }">{{ custodianText(r) }}</span>
            </div>
            <div class="sr-row">
              <span class="sr-k">附件</span>
              <span v-if="!(r.attachments && r.attachments.length)" class="sr-v">无</span>
              <div v-else class="sr-atts">
                <template v-for="(a, i) in r.attachments" :key="i">
                  <img v-if="isImg(a)" class="sr-att-thumb" :src="a.url" alt="" @click="viewAtt(a)" />
                  <span v-else class="sr-att-file" @click="viewAtt(a)">📄 {{ a.name || '附件' }}</span>
                </template>
              </div>
            </div>
          </div>

          <!-- 底部操作行：删除（仅主任，靠左）｜ 保管人确认/驳回（靠右），避免与右上角状态胶囊重叠 -->
          <div v-if="canRemove || (r.status === 'pending' && canConfirm)" class="sr-foot">
            <button v-if="canRemove" type="button" class="sr-del" @click="removeRecord(r)">删除</button>
            <span v-else class="sr-foot-sp"></span>
            <div v-if="r.status === 'pending' && canConfirm" class="sr-actions">
              <button type="button" class="sr-btn ghost" @click="openReject(r)">驳回</button>
              <button type="button" class="sr-btn primary" @click="confirmUse(r)">确认用印</button>
            </div>
          </div>
        </div>
      </template>
      <div v-else class="empty-state"><span>{{ filter === 'all' ? '暂无用印记录' : '当前没有需要显示的记录' }}</span></div>
    </div>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated } from 'vue';
import api from '@/api';
import PageNav from '@/components/PageNav.vue';
import GovSubTabs from '@/components/GovSubTabs.vue';
import perm from '@/utils/perm';
import { toast, showModal } from '@/utils/ui';

const allRecords = ref([]);
const filter = ref('all');

// 申请：委员及以上；确认/驳回：保管人（主任/副主任）；删除：仅主任
const canApply = computed(() => perm.can('seal.apply'));
const canConfirm = computed(() => perm.can('seal.approve'));
const canRemove = computed(() => perm.isLegalChair());

const counts = computed(() => ({
  all: allRecords.value.length,
  pending: allRecords.value.filter(r => r.status === 'pending').length,
  approved: allRecords.value.filter(r => r.status === 'approved').length
}));
const records = computed(() => {
  if (filter.value === 'pending') return allRecords.value.filter(r => r.status === 'pending');
  if (filter.value === 'approved') return allRecords.value.filter(r => r.status === 'approved');
  return allRecords.value;
});

async function load() {
  try {
    allRecords.value = (await api.sealRecords('all')) || [];
  } catch (e) { /* 网络异常已由 core 统一提示 */ }
}

// 申请用印改为独立页（0728 用户定：统一不用弹层）；返回台账时 onActivated 会重新拉取
function openApply() {
  window.location.assign('/seal-apply');
}

async function confirmUse(r) {
  try {
    await api.sealConfirm(r.id);
    toast({ title: '已确认用印', icon: 'success' });
    await load();
  } catch (e) { /* */ }
}

// 驳回改为独立页；带上印章名/用途，便于页内确认是哪条申请
function openReject(r) {
  const q = 'id=' + r.id
    + '&seal=' + encodeURIComponent(r.sealLabel || '')
    + '&purpose=' + encodeURIComponent(r.purpose || '');
  window.location.assign('/seal-reject?' + q);
}

async function removeRecord(r) {
  const res = await showModal({ title: '删除用印记录', content: '删除后不可恢复，确定删除这条用印记录？', showCancel: true, confirmText: '删除' });
  if (!res || !res.confirm) return;
  try {
    await api.sealRemove(r.id);
    toast({ title: '已删除', icon: 'none' });
    await load();
  } catch (e) { /* */ }
}

// 2026-06-12T10:20:00 → 6月12日 10:20
function fmtTime(s) {
  if (!s) return '';
  const m = String(s).match(/(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/);
  return m ? (Number(m[2]) + '月' + Number(m[3]) + '日 ' + m[4] + ':' + m[5]) : String(s);
}

// ── 台账登记项文案（按制度：用印时间、用途/事项、文件、申请人、保管人确认、附件）──
// 用印时间＝保管人确认盖章的时间；申请中还没盖章显示「待用印」
function useTimeText(r) {
  if (r.status === 'approved') return fmtTime(r.confirmedAt) || '—';
  return r.status === 'pending' ? '待用印' : '—';
}
// 文件名：新申请从附件名取；老记录兼容展示原「关联文件」字段；都没有则提示见用途
function fileText(r) {
  const names = (r.attachments || []).map(a => a && a.name).filter(Boolean);
  if (names.length) return names.join('、');
  return r.documentName || '见用途说明';
}
function applicantText(r) {
  let s = r.applicantName || '—';
  if (r.applicantRole) s += '（' + r.applicantRole + '）';
  if (r.createdAt) s += ' · ' + fmtTime(r.createdAt) + ' 提交';
  return s;
}
function custodianText(r) {
  if (r.status === 'approved') return '已由 ' + (r.custodianName || '保管人') + ' 确认用印';
  if (r.status === 'rejected') return '已驳回' + (r.rejectReason ? '：' + r.rejectReason : '');
  return '处理中，待保管人确认';
}
function isImg(a) {
  const s = (((a && a.type) || '') + ' ' + ((a && a.url) || '')).toLowerCase();
  return s.indexOf('image') >= 0 || /\.(jpg|jpeg|png|gif|webp)(\?|$)/.test(s);
}
function viewAtt(a) { if (a && a.url) window.open(a.url, '_blank'); }

onMounted(load);
onActivated(load);
</script>

<style scoped>
/* tab 根页：左侧占位与 PageNav 右侧 96rpx 占位对齐，标题居中（无返回箭头） */
.nav-left-spacer { width: 96rpx; }

.seal-info-card {
  margin: 20rpx 24rpx 0; padding: 26rpx 28rpx; box-sizing: border-box;
  background: linear-gradient(145deg, #FFFEFC 0%, #FAF4EB 100%);
  border: 2rpx solid #E7DAC6; border-radius: 24rpx;
  box-shadow: 0 8rpx 22rpx rgba(96, 72, 40, 0.07);
}
.sic-head { display: flex; align-items: baseline; flex-wrap: wrap; gap: 16rpx; }
.sic-title { font-size: 34rpx; font-weight: 750; color: #7E571C; }
.sic-note { font-size: 26rpx; color: #9A8A72; }

/* 申请用印大按钮：暖橙、去光晕（与接待/学习登记卡同款克制配色） */
.seal-apply-card {
  display: flex; align-items: center; gap: 16rpx; width: 92%; box-sizing: border-box;
  margin: 30rpx auto 8rpx; padding: 22rpx 26rpx; text-align: left;
  background: linear-gradient(135deg, #FFFEFC 0%, #FAF4EB 100%); border: 2rpx solid #E6D7BF;
  border-radius: 20rpx; box-shadow: 0 6rpx 18rpx rgba(96, 72, 40, 0.10);
}
.seal-apply-card:active { opacity: 0.72; }
.sac-icon {
  display: flex; align-items: center; justify-content: center; width: 62rpx; height: 62rpx;
  border-radius: 15rpx; background: #B0772E; color: #fff; font-size: 36rpx; font-weight: 500;
}
.sac-copy { flex: 1; }
.sac-copy strong { font-size: 34rpx; font-weight: 700; color: #7E571C; }
.sac-arrow {
  display: flex; align-items: center; justify-content: center; width: 50rpx; height: 50rpx;
  border-radius: 50%; background: #F0E6D2; color: #916619; font-size: 36rpx; font-weight: 700;
}

.records-head { display: flex; align-items: baseline; justify-content: space-between; margin: 34rpx 30rpx 0; }
.records-title { font-size: 32rpx; font-weight: 750; color: var(--c-text-strong); }
.records-count { font-size: 26rpx; color: var(--c-text-weak); }

.filter-tabs { display: flex; gap: 14rpx; margin: 18rpx 24rpx 0; }
.f-tab {
  flex: 1; text-align: center; padding: 16rpx 0; border-radius: 14rpx;
  background: #EEF0F3; color: #5B6675; font-size: 28rpx; font-weight: 600;
}
.f-tab.active { background: #B0772E; color: #fff; }

/* 底部留白避开固定底栏（100rpx + 安全区），否则台账最后一条会被 tab 栏遮住 */
.seal-list { padding: 12rpx 24rpx calc(140rpx + env(safe-area-inset-bottom)); }
.seal-record {
  position: relative; margin-top: 20rpx; padding: 24rpx 26rpx; box-sizing: border-box;
  background: var(--c-bg-card); border: 2rpx solid #EBEEF1; border-radius: 20rpx;
  box-shadow: 0 6rpx 18rpx rgba(20, 42, 58, 0.05);
}
.sr-top { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
.sr-seal { font-size: 32rpx; font-weight: 750; color: var(--c-text-strong); }
.sr-status { flex-shrink: 0; padding: 6rpx 18rpx; border-radius: 999rpx; font-size: 25rpx; font-weight: 600; }
.sr-status.pending { color: #9A5A13; background: #FFF1D8; }
.sr-status.approved { color: #287653; background: #E8F5EE; }
.sr-status.rejected { color: #9A3F33; background: #FBE9E6; }
/* 台账登记行：左侧固定宽标签 + 右侧值，对齐成登记表样式 */
.sr-rows { margin-top: 16rpx; display: flex; flex-direction: column; gap: 12rpx; }
.sr-row { display: flex; align-items: flex-start; gap: 16rpx; }
.sr-k { flex-shrink: 0; width: 150rpx; font-size: 26rpx; line-height: 1.5; color: var(--c-text-weak); }
.sr-v { flex: 1; min-width: 0; font-size: 27rpx; line-height: 1.5; color: var(--c-text-mid); word-break: break-all; }
.sr-v-strong { color: var(--c-text-strong); font-weight: 600; }
.sr-confirmed { color: #3B7150; }
.sr-rejected { color: #9A3F33; }
.sr-atts { flex: 1; min-width: 0; display: flex; flex-wrap: wrap; gap: 12rpx; }
.sr-att-thumb { width: 108rpx; height: 108rpx; border-radius: 10rpx; object-fit: cover; background: #EEE; border: 2rpx solid #E6E9EC; }
.sr-att-file {
  display: inline-flex; align-items: center; max-width: 100%; padding: 8rpx 16rpx;
  border: 2rpx solid #E3E6E9; border-radius: 10rpx; background: #F7F8FA;
  font-size: 25rpx; color: #4E6076; word-break: break-all;
}

.sr-foot { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; margin-top: 20rpx; }
.sr-foot-sp { flex: 1; }
.sr-actions { display: flex; align-items: center; gap: 18rpx; }
.sr-btn {
  min-height: 64rpx; padding: 0 30rpx; border: 0; border-radius: 14rpx;
  font-size: 28rpx; font-weight: 650; white-space: nowrap; display: inline-flex; align-items: center;
}
.sr-btn.ghost { background: #fff; border: 2rpx solid #C9D0D6; color: #4E6076; }
.sr-btn.ghost:active { background: #F1F3F5; }
.sr-btn.primary { background: #B0772E; color: #fff; box-shadow: 0 4rpx 12rpx rgba(120, 88, 34, 0.16); }
.sr-btn.primary:active { filter: brightness(0.96); }
.sr-del {
  padding: 12rpx 6rpx; border: 0; background: transparent; color: #9AA4B0; font-size: 26rpx; font-weight: 500;
}
.sr-del:active { color: #C0392B; }

.empty-state { padding: 70rpx 0; text-align: center; color: var(--c-text-weak); font-size: 28rpx; }
</style>
