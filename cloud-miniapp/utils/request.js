const { SERVER_URL, TOKEN_KEY, USER_KEY } = require('./config');

let redirectingToLogin = false;

function authHeader() {
  const token = wx.getStorageSync(TOKEN_KEY);
  return token ? { Authorization: `Bearer ${token}` } : {};
}

function redirectToLogin() {
  wx.removeStorageSync(TOKEN_KEY);
  wx.removeStorageSync(USER_KEY);
  const app = getApp();
  app.globalData.token = '';
  app.globalData.userInfo = null;

  if (redirectingToLogin) {
    return;
  }

  const pages = getCurrentPages();
  const currentPage = pages[pages.length - 1];
  if (currentPage && currentPage.route === 'pages/login/login') {
    return;
  }

  redirectingToLogin = true;
  wx.reLaunch({
    url: '/pages/login/login?expired=1',
    complete() {
      redirectingToLogin = false;
    }
  });
}

function apiError(message, code, statusCode) {
  const error = new Error(message || '请求未完成，请稍后重试');
  error.code = code;
  error.statusCode = statusCode;
  return error;
}

function handleResponse(statusCode, envelope) {
  const responseCode = envelope && Number(envelope.code);
  if (statusCode === 401 || responseCode === 401) {
    redirectToLogin();
    throw apiError((envelope && envelope.message) || '登录状态已失效', 401, statusCode);
  }

  if (!envelope || typeof envelope !== 'object') {
    throw apiError('服务器响应格式不正确', statusCode, statusCode);
  }

  if (statusCode >= 200 && statusCode < 300 && responseCode === 200) {
    return envelope.data;
  }

  throw apiError(envelope.message, responseCode || statusCode, statusCode);
}

function request(options) {
  const { path, method = 'GET', data, header = {} } = options;

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${SERVER_URL}${path}`,
      method,
      data,
      header: {
        'content-type': 'application/json',
        ...authHeader(),
        ...header
      },
      success(response) {
        try {
          resolve(handleResponse(response.statusCode, response.data));
        } catch (error) {
          reject(error);
        }
      },
      fail() {
        reject(apiError('无法连接服务器，请检查网络和服务地址', 0, 0));
      }
    });
  });
}

function downloadFile(path) {
  return new Promise((resolve, reject) => {
    wx.downloadFile({
      url: `${SERVER_URL}${path}`,
      header: authHeader(),
      success(response) {
        if (response.statusCode === 401) {
          redirectToLogin();
          reject(apiError('登录状态已失效', 401, 401));
          return;
        }
        if (response.statusCode !== 200) {
          reject(apiError('文件下载失败', response.statusCode, response.statusCode));
          return;
        }
        resolve(response.tempFilePath);
      },
      fail() {
        reject(apiError('文件下载失败，请检查网络后重试', 0, 0));
      }
    });
  });
}

module.exports = {
  authHeader,
  downloadFile,
  handleResponse,
  redirectToLogin,
  request
};
