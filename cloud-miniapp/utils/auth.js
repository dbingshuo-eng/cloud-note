const { TOKEN_KEY, USER_KEY } = require('./config');
const { request } = require('./request');

function hasToken() {
  return Boolean(wx.getStorageSync(TOKEN_KEY));
}

function getLoginCode() {
  return new Promise((resolve, reject) => {
    wx.login({
      success(result) {
        if (result.code) {
          resolve(result.code);
          return;
        }
        reject(new Error('微信登录未返回有效凭证'));
      },
      fail() {
        reject(new Error('微信登录未完成，请稍后重试'));
      }
    });
  });
}

async function login() {
  const code = await getLoginCode();
  const result = await request({
    path: '/api/user/login',
    method: 'POST',
    data: { code }
  });

  if (!result || !result.token) {
    throw new Error('登录响应缺少访问凭证');
  }

  wx.setStorageSync(TOKEN_KEY, result.token);
  wx.setStorageSync(USER_KEY, result.userInfo || {});
  const app = getApp();
  app.globalData.token = result.token;
  app.globalData.userInfo = result.userInfo || {};
  return result;
}

function logout() {
  wx.removeStorageSync(TOKEN_KEY);
  wx.removeStorageSync(USER_KEY);
  const app = getApp();
  app.globalData.token = '';
  app.globalData.userInfo = null;
  wx.reLaunch({ url: '/pages/login/login?loggedOut=1' });
}

module.exports = {
  hasToken,
  login,
  logout
};
