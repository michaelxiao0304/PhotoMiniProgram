// history.js
var app = getApp();

Page({
  data: {
    historyList: [],
    loading: false,
    empty: true
  },

  onLoad: function() {
    this.loadHistory();
  },

  onShow: function() {
    this.loadHistory();
  },

  loadHistory: function() {
    var self = this;
    self.setData({ loading: true });

    wx.request({
      url: app.globalData.apiBase + '/history',
      success: function(res) {
        if (res.data && Array.isArray(res.data)) {
          self.setData({
            historyList: res.data,
            empty: res.data.length === 0,
            loading: false
          });
        } else {
          self.setData({ loading: false });
        }
      },
      fail: function() {
        self.setData({ loading: false });
        wx.showToast({ title: '加载失败', icon: 'none' });
      }
    });
  },

  onCopyUrl: function(e) {
    var url = e.currentTarget.dataset.url;
    wx.setClipboardData({
      data: url,
      success: function() {
        wx.showToast({ title: '已复制', icon: 'success' });
      }
    });
  },

  onUseUrl: function(e) {
    var url = e.currentTarget.dataset.url;
    // Go back to index with the URL
    var pages = getCurrentPages();
    var indexPage = pages[0];
    if (indexPage) {
      indexPage.setData({
        url: url
      });
    }
    wx.navigateBack();
  },

  onClearHistory: function() {
    var self = this;
    wx.showModal({
      title: '提示',
      content: '确定清空历史记录?',
      success: function(res) {
        if (res.confirm) {
          wx.request({
            url: app.globalData.apiBase + '/history',
            method: 'DELETE',
            success: function() {
              self.setData({
                historyList: [],
                empty: true
              });
              wx.showToast({ title: '已清空', icon: 'success' });
            }
          });
        }
      }
    });
  }
});
