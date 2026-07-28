const auth = require('../../utils/auth');

Page({
  data: {
    loading: false,
    errorMessage: '',
    expired: false
  },

  onLoad(options) {
    const expired = options.expired === '1';
    const loggedOut = options.loggedOut === '1';
    this.setData({ expired });
    if (auth.hasToken() && !expired && !loggedOut) {
      wx.reLaunch({ url: '/pages/index/index' });
      return;
    }
    if (!loggedOut) {
      this.signIn();
    }
  },

  async signIn() {
    if (this.data.loading) {
      return;
    }
    this.setData({ loading: true, errorMessage: '' });
    try {
      await auth.login();
      wx.reLaunch({ url: '/pages/index/index' });
    } catch (error) {
      this.setData({
        loading: false,
        errorMessage: error.message || '登录未完成，请稍后重试'
      });
    }
  }
});
