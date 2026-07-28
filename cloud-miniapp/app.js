const { TOKEN_KEY, USER_KEY } = require('./utils/config');

App({
  globalData: {
    token: '',
    userInfo: null,
    refreshFiles: false
  },

  onLaunch() {
    this.globalData.token = wx.getStorageSync(TOKEN_KEY) || '';
    this.globalData.userInfo = wx.getStorageSync(USER_KEY) || null;
  }
});
