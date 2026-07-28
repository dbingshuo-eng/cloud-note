const auth = require('../../utils/auth');
const { downloadFile, request } = require('../../utils/request');

const SORTS = [
  { field: 'fileName', label: '名称' },
  { field: 'fileSize', label: '大小' },
  { field: 'fileType', label: '类型' },
  { field: 'createTime', label: '创建时间' },
  { field: 'updateTime', label: '更新时间' }
];

function formatBytes(bytes) {
  const size = Number(bytes) || 0;
  if (size < 1000) {
    return `${size} B`;
  }
  if (size < 1000000) {
    return `${(size / 1000).toFixed(1)} KB`;
  }
  if (size < 1000000000) {
    return `${(size / 1000000).toFixed(1)} MB`;
  }
  return `${(size / 1000000000).toFixed(1)} GB`;
}

function formatDate(value) {
  if (!value) {
    return '时间未知';
  }
  return String(value).replace('T', ' ').slice(0, 16);
}

function displayItem(item) {
  const isFolder = item.isFolder === true;
  const fileType = isFolder ? 'DIR' : String(item.fileType || 'FILE').toUpperCase().slice(0, 4);
  return {
    ...item,
    id: Number(item.id),
    parentId: Number(item.parentId || 0),
    selected: false,
    isFolder,
    typeLabel: fileType,
    sizeLabel: isFolder ? '文件夹' : formatBytes(item.fileSize),
    timeLabel: formatDate(item.updateTime || item.createTime)
  };
}

Page({
  data: {
    state: 'loading',
    stateMessage: '正在读取你的云端目录',
    files: [],
    parentId: 0,
    breadcrumbs: [{ id: 0, name: '全部文件' }],
    recycleMode: false,
    manageMode: false,
    selectMode: false,
    searchKeyword: '',
    searchMode: false,
    selectedRecycleIds: [],
    openingId: null,
    sortField: 'createTime',
    sortLabel: '创建时间',
    sortOrder: 'desc',
    busyId: null
  },

  onLoad() {
    this._loadSequence = 0;
    if (!auth.hasToken()) {
      wx.reLaunch({ url: '/pages/login/login' });
      return;
    }
    this.loadFiles(false);
  },

  onShow() {
    const app = getApp();
    if (app.globalData.refreshFiles) {
      app.globalData.refreshFiles = false;
      this.loadFiles(true);
    }
  },

  onPullDownRefresh() {
    this.loadFiles(true);
  },

  async loadFiles(silent) {
    if (!this.data.recycleMode && this.data.searchMode && String(this.data.searchKeyword || '').trim()) {
      return this.performSearch(silent);
    }
    const requestSequence = ++this._loadSequence;
    const recycleMode = this.data.recycleMode;
    const parentId = this.data.parentId;
    const sortField = this.data.sortField;
    const sortOrder = this.data.sortOrder;
    if (!silent) {
      this.setData({
        state: 'loading',
        stateMessage: this.data.recycleMode ? '正在查看回收站' : '正在读取你的云端目录'
      });
    }

    const path = recycleMode
      ? '/api/file/recycle'
      : `/api/file/list?parentId=${parentId}&sort=${sortField}&order=${sortOrder}`;

    try {
      const result = await request({ path });
      if (requestSequence !== this._loadSequence) {
        return;
      }
      const files = (Array.isArray(result) ? result : []).map(displayItem);
      this.setData({
        files,
        state: files.length ? 'ready' : 'empty',
        stateMessage: recycleMode
          ? '删除的文件会暂存在这里，可以随时恢复'
          : '上传一个文件，或建立第一只文件夹'
      });
    } catch (error) {
      if (requestSequence !== this._loadSequence) {
        return;
      }
      this.setData({
        files: [],
        state: 'error',
        stateMessage: error.message || '请检查网络后重试'
      });
    } finally {
      if (requestSequence === this._loadSequence) {
        wx.stopPullDownRefresh();
      }
    }
  },

  onUnload() {
    this._loadSequence += 1;
    clearTimeout(this._openTimer);
  },

  retryLoad() {
    this.loadFiles(false);
  },

  openItem(event) {
    const item = event.currentTarget.dataset.item;
    if (!item || this.data.recycleMode) {
      return;
    }
    if (this.data.manageMode) {
      this.toggleRecycleSelection({ currentTarget: { dataset: { id: item.id } } });
      return;
    }
    if (!item.isFolder) {
      this.downloadItem({ currentTarget: { dataset: { id: item.id } } });
      return;
    }
    if (this.data.searchMode) {
      this.setData({ searchMode: false, searchKeyword: '' });
    }
    clearTimeout(this._openTimer);
    this.setData({ openingId: item.id });
    this._openTimer = setTimeout(() => {
      this.setData({
        openingId: null,
        parentId: item.id,
        breadcrumbs: [...this.data.breadcrumbs, { id: item.id, name: item.fileName }]
      });
      this.loadFiles(false);
    }, 160);
  },

  openBreadcrumb(event) {
    const index = Number(event.currentTarget.dataset.index);
    const breadcrumb = this.data.breadcrumbs[index];
    if (!breadcrumb || breadcrumb.id === this.data.parentId) {
      return;
    }
    this.setData({
      parentId: breadcrumb.id,
      breadcrumbs: this.data.breadcrumbs.slice(0, index + 1)
    });
    this.loadFiles(false);
  },

  toggleRecycle() {
    this.setData({
      recycleMode: !this.data.recycleMode,
      manageMode: false,
      selectMode: false,
      searchKeyword: '',
      searchMode: false,
      selectedRecycleIds: [],
      openingId: null,
      parentId: 0,
      breadcrumbs: [{ id: 0, name: '全部文件' }],
      files: []
    });
    this.loadFiles(false);
  },

  toggleManageMode() {
    const nextManageMode = !this.data.manageMode;
    this.setData({
      manageMode: nextManageMode,
      selectMode: nextManageMode
    });
    this.syncSelection([]);
  },

  toggleSelectMode() {
    this.toggleManageMode();
  },

  onSearchInput(event) {
    this.setData({ searchKeyword: event.detail.value || '' });
  },

  async performSearch() {
    const keyword = String(this.data.searchKeyword || '').trim();
    if (!keyword) {
      this.setData({ searchMode: false });
      this.loadFiles(false);
      return;
    }
    const requestSequence = ++this._loadSequence;
    this.setData({ state: 'loading', searchMode: true, selectedRecycleIds: [] });
    try {
      const result = await request({
        path: `/api/file/search?keyword=${encodeURIComponent(keyword)}`
      });
      if (requestSequence !== this._loadSequence) {
        return;
      }
      const files = (Array.isArray(result) ? result : []).map(displayItem);
      this.setData({
        files,
        state: files.length ? 'ready' : 'empty',
        stateMessage: files.length ? '' : '没有找到匹配的文件'
      });
    } catch (error) {
      this.setData({ files: [], state: 'error', stateMessage: error.message || '搜索失败' });
    }
  },

  clearSearch() {
    this.setData({ searchKeyword: '', searchMode: false });
    this.loadFiles(false);
  },

  toggleSelectAllActive() {
    const ids = this.data.files.map((item) => Number(item.id));
    const allSelected = ids.length > 0 && ids.every((id) => this.data.selectedRecycleIds.includes(id));
    this.syncSelection(allSelected ? [] : ids);
  },

  confirmBatchDelete() {
    const ids = this.data.selectedRecycleIds;
    if (!ids.length) {
      return;
    }
    const selectedItems = this.data.files.filter((item) => ids.includes(Number(item.id)));
    const folderCount = selectedItems.filter((item) => item.isFolder).length;
    const content = folderCount > 0
      ? `已选择 ${ids.length} 项，其中包含 ${folderCount} 个文件夹。删除文件夹会同时移入其中的内容，确定继续吗？`
      : `确定删除已选 ${ids.length} 项吗？之后仍可恢复。`;
    wx.showModal({
      title: '批量移入回收站',
      content,
      confirmText: '删除',
      confirmColor: '#A0443D',
      success: async ({ confirm }) => {
        if (!confirm) {
          return;
        }
        this.setData({ busyId: 'active-batch' });
        try {
          await request({ path: '/api/file/batch', method: 'DELETE', data: { ids } });
          this.setData({ manageMode: false, selectMode: false, selectedRecycleIds: [] });
          wx.showToast({ title: '已移入回收站', icon: 'success' });
          await this.loadFiles(true);
        } catch (error) {
          wx.showToast({ title: error.message || '操作未完成', icon: 'none' });
        } finally {
          this.setData({ busyId: null });
        }
      }
    });
  },

  toggleRecycleSelection(event) {
    const id = Number(event.currentTarget.dataset.id);
    const selected = new Set(this.data.selectedRecycleIds);
    if (selected.has(id)) {
      selected.delete(id);
    } else {
      selected.add(id);
    }
    this.syncSelection(Array.from(selected));
  },

  toggleSelectAllRecycle() {
    const ids = this.data.files.map((item) => Number(item.id));
    const allSelected = ids.length > 0
      && ids.every((id) => this.data.selectedRecycleIds.includes(id));
    this.syncSelection(allSelected ? [] : ids);
  },

  syncSelection(ids) {
    const selectedIds = Array.from(new Set((ids || []).map((id) => Number(id))));
    const selectedIdSet = new Set(selectedIds);
    this.setData({
      selectedRecycleIds: selectedIds,
      files: this.data.files.map((item) => ({
        ...item,
        selected: selectedIdSet.has(Number(item.id))
      }))
    });
  },

  confirmBatchPermanentDelete() {
    const ids = this.data.selectedRecycleIds;
    if (!ids.length) {
      return;
    }
    const selectedItems = this.data.files.filter((item) => ids.includes(Number(item.id)));
    const folderCount = selectedItems.filter((item) => item.isFolder).length;
    const content = folderCount > 0
      ? `已选择 ${ids.length} 项，其中包含 ${folderCount} 个文件夹及其内容。永久删除后无法恢复，确定继续吗？`
      : `确定永久删除已选 ${ids.length} 项吗？删除后无法恢复。`;
    wx.showModal({
      title: '批量永久删除',
      content,
      confirmText: '永久删除',
      confirmColor: '#B42318',
      success: async ({ confirm }) => {
        if (!confirm) {
          return;
        }
        this.setData({ busyId: 'batch' });
        try {
          await request({
            path: '/api/file/recycle/batch',
            method: 'DELETE',
            data: { ids }
          });
          this.setData({ manageMode: false, selectedRecycleIds: [] });
          wx.showToast({ title: '已永久删除', icon: 'success' });
          await this.loadFiles(true);
        } catch (error) {
          wx.showToast({ title: error.message || '操作未完成', icon: 'none' });
        } finally {
          this.setData({ busyId: null });
        }
      }
    });
  },

  chooseSort() {
    if (this.data.recycleMode) {
      return;
    }
    wx.showActionSheet({
      itemList: SORTS.map((item) => item.label),
      success: ({ tapIndex }) => {
        const selected = SORTS[tapIndex];
        this.setData({
          sortField: selected.field,
          sortLabel: selected.label
        });
        this.loadFiles(false);
      }
    });
  },

  toggleSortOrder() {
    if (this.data.recycleMode) {
      return;
    }
    this.setData({
      sortOrder: this.data.sortOrder === 'asc' ? 'desc' : 'asc'
    });
    this.loadFiles(false);
  },

  createFolder() {
    wx.showModal({
      title: '新建文件夹',
      editable: true,
      placeholderText: '输入文件夹名称',
      confirmText: '创建',
      success: async ({ confirm, content }) => {
        if (!confirm) {
          return;
        }
        const folderName = String(content || '').trim();
        if (!folderName) {
          wx.showToast({ title: '请输入文件夹名称', icon: 'none' });
          return;
        }
        try {
          await request({
            path: '/api/file/folder',
            method: 'POST',
            data: { folderName, parentId: this.data.parentId }
          });
          wx.showToast({ title: '文件夹已创建', icon: 'success' });
          this.loadFiles(true);
        } catch (error) {
          wx.showToast({ title: error.message || '创建失败', icon: 'none' });
        }
      }
    });
  },

  goToUpload() {
    wx.navigateTo({
      url: `/pages/upload/upload?parentId=${this.data.parentId}`
    });
  },

  openFileActions(event) {
    const item = event.currentTarget.dataset.item;
    if (!item || this.data.recycleMode) {
      return;
    }
    const actions = item.isFolder
      ? ['移动到文件夹', '重命名', '删除']
      : ['下载', '分享', '移动到文件夹', '重命名', '删除'];
    wx.showActionSheet({
      itemList: actions,
      success: ({ tapIndex }) => {
        const action = actions[tapIndex];
        if (action === '下载') {
          this.downloadItem({ currentTarget: { dataset: { id: item.id } } });
        } else if (action === '分享') {
          this.shareItem({ currentTarget: { dataset: { id: item.id } } });
        } else if (action === '移动到文件夹') {
          this.chooseMoveTarget(item);
        } else if (action === '重命名') {
          this.renameItem({ currentTarget: { dataset: { id: item.id, name: item.fileName } } });
        } else if (action === '删除') {
          this.confirmDelete({ currentTarget: { dataset: { id: item.id, name: item.fileName, folder: item.isFolder } } });
        }
      }
    });
  },

  chooseMoveTarget(item) {
    const targets = [
      { id: 0, name: '全部文件' },
      ...this.data.files
        .filter((candidate) => candidate.isFolder && candidate.id !== item.id)
        .map((candidate) => ({ id: candidate.id, name: candidate.fileName }))
    ].slice(0, 6);
    if (targets.length === 1) {
      wx.showToast({ title: '当前没有可移动到的文件夹', icon: 'none' });
      return;
    }
    wx.showActionSheet({
      itemList: targets.map((target) => target.name),
      success: async ({ tapIndex }) => {
        const target = targets[tapIndex];
        this.setData({ busyId: item.id });
        try {
          await request({
            path: `/api/file/${item.id}/parent`,
            method: 'PUT',
            data: { parentId: target.id }
          });
          wx.showToast({ title: '已移动', icon: 'success' });
          await this.loadFiles(true);
        } catch (error) {
          wx.showToast({ title: error.message || '移动失败', icon: 'none' });
        } finally {
          this.setData({ busyId: null });
        }
      }
    });
  },

  confirmDelete(event) {
    const { id, name, folder } = event.currentTarget.dataset;
    const content = folder === true || folder === 'true'
      ? `删除文件夹“${name}”会同时移入其中的所有文件和子文件夹，之后仍可从回收站恢复。确定继续吗？`
      : `确定删除“${name}”吗？之后仍可从回收站恢复。`;
    wx.showModal({
      title: '移入回收站',
      content,
      confirmText: '删除',
      confirmColor: '#A0443D',
      success: async ({ confirm }) => {
        if (!confirm) {
          return;
        }
        await this.runItemAction(id, () =>
          request({ path: `/api/file/${id}`, method: 'DELETE' })
        );
      }
    });
  },

  renameItem(event) {
    const { id, name } = event.currentTarget.dataset;
    wx.showModal({
      title: '重命名',
      editable: true,
      content: name,
      placeholderText: '输入新名称',
      confirmText: '保存',
      success: async ({ confirm, content }) => {
        if (!confirm) {
          return;
        }
        this.setData({ busyId: id });
        try {
          await request({
            path: `/api/file/${id}/name`,
            method: 'PUT',
            data: { fileName: content }
          });
          wx.showToast({ title: '已重命名', icon: 'success' });
          await this.loadFiles(true);
        } catch (error) {
          wx.showToast({ title: error.message || '重命名失败', icon: 'none' });
        } finally {
          this.setData({ busyId: null });
        }
      }
    });
  },

  recoverItem(event) {
    const { id } = event.currentTarget.dataset;
    this.runItemAction(id, () =>
      request({ path: `/api/file/recover/${id}`, method: 'PUT' })
    );
  },

  confirmPermanentDelete(event) {
    const { id, name } = event.currentTarget.dataset;
    wx.showModal({
      title: '永久删除',
      content: `确定永久删除“${name}”吗？删除后无法恢复。`,
      confirmText: '永久删除',
      confirmColor: '#B42318',
      success: async ({ confirm }) => {
        if (!confirm) {
          return;
        }
        await this.runItemAction(
          id,
          () => request({ path: `/api/file/recycle/${id}`, method: 'DELETE' }),
          '已永久删除'
        );
      }
    });
  },

  async runItemAction(id, operation, successMessage) {
    this.setData({ busyId: id });
    try {
      await operation();
      wx.showToast({
        title: successMessage || (this.data.recycleMode ? '已恢复' : '已移入回收站'),
        icon: 'success'
      });
      await this.loadFiles(true);
    } catch (error) {
      wx.showToast({ title: error.message || '操作未完成', icon: 'none' });
    } finally {
      this.setData({ busyId: null });
    }
  },

  async downloadItem(event) {
    const { id } = event.currentTarget.dataset;
    this.setData({ busyId: id });
    wx.showLoading({ title: '正在下载' });
    try {
      const filePath = await downloadFile(`/api/file/download/${id}`);
      wx.hideLoading();
      wx.openDocument({
        filePath,
        showMenu: true,
        fail() {
          wx.showToast({ title: '文件已下载到临时目录', icon: 'none' });
        }
      });
    } catch (error) {
      wx.hideLoading();
      wx.showToast({ title: error.message || '下载失败', icon: 'none' });
    } finally {
      this.setData({ busyId: null });
    }
  },

  async shareItem(event) {
    const { id } = event.currentTarget.dataset;
    this.setData({ busyId: id });
    try {
      const share = await request({
        path: '/api/share/create',
        method: 'POST',
        data: {
          fileId: id,
          password: null,
          expireTime: null
        }
      });
      wx.setClipboardData({
        data: share.code,
        success() {
          wx.showModal({
            title: '分享码已复制',
            content: `分享码 ${share.code} 已复制，请通过安全渠道发送。`,
            showCancel: false,
            confirmText: '知道了'
          });
        }
      });
    } catch (error) {
      wx.showToast({ title: error.message || '创建分享失败', icon: 'none' });
    } finally {
      this.setData({ busyId: null });
    }
  },

  logout() {
    wx.showModal({
      title: '退出云笺',
      content: '退出后需要重新进行微信身份验证。',
      confirmText: '退出',
      success({ confirm }) {
        if (confirm) {
          auth.logout();
        }
      }
    });
  }
});
