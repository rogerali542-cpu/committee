<template>
  <div class="page" style="overflow-y:auto">
    <PageNav title="印章管理" backTo="/main" />

    <!-- 印章保管：制度口径——三枚印章由主任、副主任分人保管 -->
    <div class="seal-info-card">
      <div class="sic-head">
        <span class="sic-title">印章保管</span>
        <span class="sic-note">三枚印章，由主任、副主任分人保管</span>
      </div>
      <div class="seal-chip-row">
        <div v-for="s in seals" :key="s.type" class="seal-chip">
          <span class="seal-chip-ico">印</span>
          <span class="seal-chip-name">{{ s.label }}</span>
        </div>
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
      <div class="f-tab" :class="{ active: filter === 'pending' }" @click="filter = 'pending'">待确认 {{ counts.pending }}</div>
      <div class="f-tab" :class="{ active: filter === 'approved' }" @click="filter = 'approved'">已用印 {{ counts.approved }}</div>
    </div>

    <div class="seal-list">
      <template v-if="records.length">
        <div v-for="r in records" :key="r.id" class="seal-record">
          <div class="sr-top">
            <span class="sr-seal">{{ r.sealLabel }}</span>
            <span class="sr-status" :class="r.status">{{ r.statusLabel }}</span>
          </div>
          <div class="sr-purpose">{{ r.purpose || '（未填用途）' }}</div>
          <div v-if="r.documentName" class="sr-doc">关联文件：{{ r.documentName }}</div>
          <div class="sr-meta">
            申请人：{{ r.applicantName || '—' }}<template v-if="r.applicantRole">（{{ r.applicantRole }}）</template>
            · {{ fmtTime(r.createdAt) }}
          </div>
          <div v-if="r.status === 'approved'" class="sr-meta sr-confirmed">
            已由 {{ r.custodianName || '保管人' }} 确认用印 · {{ fmtTime(r.confirmedAt) }}
          </div>
          <div v-if="r.status === 'rejected'" class="sr-meta sr-rejected">
            已驳回<template v-if="r.rejectReason">：{{ r.rejectReason }}</template>
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

    <!-- 申请用印弹层 -->
    <div v-if="applyOpen" class="seal-mask" @click="applyOpen = false">
      <div class="seal-sheet" @click.stop>
        <div class="sheet-head">
          <span class="sheet-title">申请用印</span>
          <span class="sheet-close" @click="applyOpen = false">×</span>
        </div>
        <div class="form-group">
          <span class="form-label">选择印章 *</span>
          <div class="seal-pick-row">
            <span v-for="s in seals" :key="s.type" class="seal-pick" :class="{ on: applyForm.sealType === s.type }" @click="applyForm.sealType = s.type">{{ s.label }}</span>
          </div>
        </div>
        <div class="form-group">
          <span class="form-label">用印事由 / 用途 *</span>
          <textarea class="form-textarea" v-model="applyForm.purpose" placeholder="例如：业委会决议公示盖章"></textarea>
        </div>
        <div class="form-group">
          <span class="form-label">关联文件（选填）</span>
          <input class="form-input" v-model="applyForm.documentName" placeholder="例如：第3次业委会会议决议" />
        </div>
        <div class="sheet-actions">
          <button class="btn btn-ghost" @click="applyOpen = false">取消</button>
          <button class="btn btn-primary" :disabled="applying" @click="submitApply">{{ applying ? '提交中…' : '提交申请' }}</button>
        </div>
      </div>
    </div>

    <!-- 驳回弹层 -->
    <div v-if="rejectOpen" class="seal-mask" @click="rejectOpen = false">
      <div class="seal-sheet" @click.stop>
        <div class="sheet-head">
          <span class="sheet-title">驳回用印申请</span>
          <span class="sheet-close" @click="rejectOpen = false">×</span>
        </div>
        <div class="form-group">
          <span class="form-label">驳回理由（选填）</span>
          <textarea class="form-textarea" v-model="rejectReason" placeholder="说明驳回原因，便于申请人修改后再申请"></textarea>
        </div>
        <div class="sheet-actions">
          <button class="btn btn-ghost" @click="rejectOpen = false">取消</button>
          <button class="btn btn-primary" @click="submitReject">确认驳回</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated } from 'vue';
import api from '@/api';
import PageNav from '@/components/PageNav.vue';
import perm from '@/utils/perm';
import { toast, showModal } from '@/utils/ui';

const seals = ref([]);
const allRecords = ref([]);
const filter = ref('all');

const applyOpen = ref(false);
const applying = ref(false);
const applyForm = ref({ sealType: '', purpose: '', documentName: '' });

const rejectOpen = ref(false);
const rejectReason = ref('');
const rejectTarget = ref(null);

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
    const [s, recs] = await Promise.all([api.sealList(), api.sealRecords('all')]);
    seals.value = s || [];
    allRecords.value = recs || [];
  } catch (e) { /* 网络异常已由 core 统一提示 */ }
}

function openApply() {
  applyForm.value = { sealType: (seals.value[0] && seals.value[0].type) || '', purpose: '', documentName: '' };
  applyOpen.value = true;
}
async function submitApply() {
  if (!applyForm.value.sealType) { toast({ title: '请选择印章', icon: 'none' }); return; }
  if (!applyForm.value.purpose || !applyForm.value.purpose.trim()) { toast({ title: '请填写用印用途', icon: 'none' }); return; }
  applying.value = true;
  try {
    await api.sealApply({
      sealType: applyForm.value.sealType,
      purpose: applyForm.value.purpose.trim(),
      documentName: (applyForm.value.documentName || '').trim()
    });
    toast({ title: '已提交用印申请', icon: 'success' });
    applyOpen.value = false;
    await load();
  } catch (e) { /* */ } finally {
    applying.value = false;
  }
}

async function confirmUse(r) {
  try {
    await api.sealConfirm(r.id);
    toast({ title: '已确认用印', icon: 'success' });
    await load();
  } catch (e) { /* */ }
}

function openReject(r) {
  rejectTarget.value = r;
  rejectReason.value = '';
  rejectOpen.value = true;
}
async function submitReject() {
  if (!rejectTarget.value) return;
  try {
    await api.sealReject(rejectTarget.value.id, (rejectReason.value || '').trim());
    toast({ title: '已驳回', icon: 'none' });
    rejectOpen.value = false;
    await load();
  } catch (e) { /* */ }
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

onMounted(load);
onActivated(load);
</script>

<style scoped>
.seal-info-card {
  margin: 20rpx 24rpx 0; padding: 26rpx 28rpx; box-sizing: border-box;
  background: linear-gradient(145deg, #FFFEFC 0%, #FAF4EB 100%);
  border: 2rpx solid #E7DAC6; border-radius: 24rpx;
  box-shadow: 0 8rpx 22rpx rgba(96, 72, 40, 0.07);
}
.sic-head { display: flex; align-items: baseline; flex-wrap: wrap; gap: 16rpx; }
.sic-title { font-size: 34rpx; font-weight: 750; color: #7E571C; }
.sic-note { font-size: 26rpx; color: #9A8A72; }
.seal-chip-row { display: flex; flex-wrap: wrap; gap: 16rpx; margin-top: 22rpx; }
.seal-chip {
  display: flex; align-items: center; gap: 12rpx; padding: 12rpx 20rpx 12rpx 12rpx;
  background: #fff; border: 2rpx solid #EAD9C0; border-radius: 16rpx;
}
.seal-chip-ico {
  display: flex; align-items: center; justify-content: center; width: 46rpx; height: 46rpx;
  border-radius: 10rpx; background: #B0772E; color: #fff; font-size: 28rpx; font-weight: 700;
}
.seal-chip-name { font-size: 28rpx; font-weight: 600; color: #5A4A34; }

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

.seal-list { padding: 12rpx 24rpx 60rpx; }
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
.sr-purpose { margin-top: 14rpx; font-size: 30rpx; line-height: 1.45; color: var(--c-text-strong); }
.sr-doc { margin-top: 8rpx; font-size: 27rpx; line-height: 1.45; color: var(--c-text-mid); }
.sr-meta { margin-top: 10rpx; font-size: 26rpx; line-height: 1.5; color: var(--c-text-weak); }
.sr-confirmed { color: #3B7150; }
.sr-rejected { color: #9A3F33; }

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

/* 弹层 */
.seal-mask {
  position: fixed; inset: 0; z-index: 60; display: flex; align-items: flex-end;
  background: rgba(20, 28, 40, 0.42);
}
.seal-sheet {
  width: 100%; box-sizing: border-box; padding: 30rpx 30rpx calc(34rpx + env(safe-area-inset-bottom));
  background: var(--c-bg-card); border-radius: 28rpx 28rpx 0 0;
}
.sheet-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14rpx; }
.sheet-title { font-size: 34rpx; font-weight: 750; color: var(--c-text-strong); }
.sheet-close { font-size: 44rpx; line-height: 1; color: #9AA4B0; padding: 0 8rpx; }
.form-group { margin-top: 22rpx; }
.form-label { display: block; margin-bottom: 12rpx; font-size: 28rpx; font-weight: 600; color: var(--c-text-mid); }
.form-input, .form-textarea {
  width: 100%; box-sizing: border-box; padding: 20rpx 22rpx;
  border: 2rpx solid #DCE1E6; border-radius: 14rpx; background: #fff;
  font-size: 30rpx; color: var(--c-text-strong);
}
.form-textarea { min-height: 132rpx; resize: none; line-height: 1.5; }
.seal-pick-row { display: flex; flex-wrap: wrap; gap: 14rpx; }
.seal-pick {
  padding: 16rpx 26rpx; border: 2rpx solid #DCE1E6; border-radius: 14rpx;
  background: #fff; font-size: 29rpx; color: #4E6076; font-weight: 600;
}
.seal-pick.on { border-color: #B0772E; background: #FBF4EB; color: #7E571C; }
.sheet-actions { display: flex; gap: 20rpx; margin-top: 32rpx; }
.sheet-actions .btn { flex: 1; min-height: 84rpx; border-radius: 16rpx; font-size: 32rpx; font-weight: 700; border: 0; }
.btn-ghost { background: #EEF0F3; color: #4E6076; }
.btn-primary { background: #B0772E; color: #fff; }
.btn-primary:disabled { opacity: 0.6; }
</style>
