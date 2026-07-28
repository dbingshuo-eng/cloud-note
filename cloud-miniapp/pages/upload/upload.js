const { SERVER_URL } = require('../../utils/config');
const { authHeader, handleResponse } = require('../../utils/request');

const MAX_FILE_SIZE = 100000000;

Page({
  data: {
    parentId: 0,
    file: null,
    progress: 0,
    uploading: false,
    errorMessage: ''
  },

  onLoad(options) {
    const parentId = Number(options.parentId);
    this.setData({
      parentId: Number.isInteger(parentId) && parentId >= 0 ? parentId : 0
    });
  },

  chooseFile() {
    if (this.data.uploading) {
      return;
    }
    wx.chooseMessageFile({
      count: 1,
      type: 'all',
      success: ({ tempFiles }) => {
        const file = tempFiles[0];
        if (!file) {
          return;
        }
        if (file.size > MAX_FILE_SIZE) {
          wx.showToast({ title: '单个文件不能超过 100 MB', icon: 'none' });
          return;
        }
        this.setData({
          file: {
            name: file.name,
            size: file.size,
            sizeLabel: this.formatBytes(file.size),
            path: file.path
          },
          progress: 0,
          errorMessage: ''
        });
      },
      fail(error) {
        if (error.errMsg && !error.errMsg.includes('cancel')) {
          wx.showToast({ title: '没有选取到文件', icon: 'none' });
        }
      }
    });
  },

  formatBytes(bytes) {
    if (bytes < 1000) {
      return `${bytes} B`;
    }
    if (bytes < 1000000) {
      return `${(bytes / 1000).toFixed(1)} KB`;
    }
    return `${(bytes / 1000000).toFixed(1)} MB`;
  },

  renameSelectedFile() {
    if (!this.data.file || this.data.uploading) {
      return;
    }
    wx.showModal({
      title: '重命名文件',
      editable: true,
      content: this.data.file.name,
      placeholderText: '输入文件名',
      confirmText: '保存',
      success: ({ confirm, content }) => {
        if (!confirm) {
          return;
        }
        const fileName = String(content || '').trim();
        if (!fileName) {
          wx.showToast({ title: '文件名不能为空', icon: 'none' });
          return;
        }
        this.setData({
          file: {
            ...this.data.file,
            name: fileName
          }
        });
      }
    });
  },

  uploadFile() {
    if (!this.data.file || this.data.uploading) {
      return;
    }

    this.setData({ uploading: true, progress: 0, errorMessage: '' });
    this.uploadTask = wx.uploadFile({
      url: `${SERVER_URL}/api/file/upload`,
      filePath: this.data.file.path,
      name: 'file',
      formData: {
        parentId: String(this.data.parentId),
        fileName: this.data.file.name
      },
      header: authHeader(),
      success: (response) => {
        try {
          const envelope = JSON.parse(response.data);
          handleResponse(response.statusCode, envelope);
          getApp().globalData.refreshFiles = true;
          this.uploadTask = null;
          this.setData({ uploading: false, progress: 100 });
          wx.showToast({
            title: '上传完成',
            icon: 'success',
            success() {
              setTimeout(() => wx.navigateBack(), 700);
            }
          });
        } catch (error) {
          this.uploadTask = null;
          this.setData({
            uploading: false,
            errorMessage: error.message || '上传未完成，请重试'
          });
        }
      },
      fail: () => {
        this.uploadTask = null;
        this.setData({
          uploading: false,
          errorMessage: '无法上传文件，请检查网络后重试'
        });
      }
    });

    this.uploadTask.onProgressUpdate(({ progress }) => {
      this.setData({ progress });
    });
  },

  onUnload() {
    if (this.uploadTask) {
      this.uploadTask.abort();
    }
    this.uploadTask = null;
  }
});
