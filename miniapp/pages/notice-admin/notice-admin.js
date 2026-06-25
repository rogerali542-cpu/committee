const api = require('../../utils/api');

function today() {
  const d = new Date();
  const mm = ('0' + (d.getMonth() + 1)).slice(-2);
  const dd = ('0' + d.getDate()).slice(-2);
  return d.getFullYear() + '-' + mm + '-' + dd;
}

const EMPTY_FORM = { id: null, date: '', title: '', detail: '', published: true };

Page({
  data: {
    list: [],
    loading: true,
    sheetVisible: false,
    editing: false,        // true=编辑，false=新建
    form: EMPTY_FORM,
    saving: false
  },

  onLoad() {
    this.loadList();
  },

  async loadList() {
    this.setData({ loading: true });
    try {
      const list = await api.noticeManageList();
      this.setData({ list: Array.isArray(list) ? list : [], loading: false });
    } catch (e) {
      this.setData({ loading: false });
      wx.showToast({ title: (e && e.message) || '加载失败', icon: 'none' });
    }
  },

  // ── 新建 / 编辑 ──
  openCreate() {
    this.setData({
      sheetVisible: true,
      editing: false,
      form: Object.assign({}, EMPTY_FORM, { date: today() })
    });
  },

  openEdit(e) {
    const item = e.currentTarget.dataset.item;
    this.setData({
      sheetVisible: true,
      editing: true,
      form: {
        id: item.id,
        date: item.date || today(),
        title: item.title || '',
        detail: item.detail || '',
        published: item.published !== false
      }
    });
  },

  closeSheet() {
    this.setData({ sheetVisible: false });
  },

  noop() {},

  onFormInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['form.' + field]: e.detail.value });
  },

  onDateChange(e) {
    this.setData({ 'form.date': e.detail.value });
  },

  onPublishedChange(e) {
    this.setData({ 'form.published': e.detail.value });
  },

  async submit() {
    const f = this.data.form;
    if (!f.title || !f.title.trim()) {
      wx.showToast({ title: '请填写公告标题', icon: 'none' });
      return;
    }
    if (this.data.saving) return;
    this.setData({ saving: true });

    const payload = {
      title: f.title.trim(),
      detail: f.detail,
      date: f.date,
      published: f.published
    };
    try {
      if (this.data.editing) {
        await api.noticeUpdate(f.id, payload);
      } else {
        await api.noticeCreate(payload);
      }
      this.setData({ sheetVisible: false, saving: false });
      wx.showToast({ title: '已保存', icon: 'success' });
      this.loadList();
    } catch (e) {
      this.setData({ saving: false });
      wx.showToast({ title: (e && e.message) || '保存失败', icon: 'none' });
    }
  },

  remove(e) {
    const item = e.currentTarget.dataset.item;
    wx.showModal({
      title: '删除公告',
      content: '确定删除「' + item.title + '」？删除后无法恢复。',
      confirmText: '删除',
      confirmColor: '#E74C3C',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await api.noticeRemove(item.id);
          wx.showToast({ title: '已删除', icon: 'success' });
          this.loadList();
        } catch (err) {
          wx.showToast({ title: (err && err.message) || '删除失败', icon: 'none' });
        }
      }
    });
  }
});
