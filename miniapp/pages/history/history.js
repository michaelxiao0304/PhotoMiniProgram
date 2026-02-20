// history.js
var app = getApp();

Page({
  data: {
    historyList: [],
    downloadTasks: [],
    loading: false,
    empty: true,
    hasDownloadTasks: false
  },

  onLoad: function() {
    this.loadHistory();
    this.startPolling();
  },

  onShow: function() {
    this.loadHistory();
    this.loadDownloadTasks();
  },

  onHide: function() {
    this.stopPolling();
  },

  onUnload: function() {
    this.stopPolling();
  },

  startPolling: function() {
    var self = this;
    this.pollingTimer = setInterval(function() {
      self.loadDownloadTasks();
    }, 3000);
  },

  stopPolling: function() {
    if (this.pollingTimer) {
      clearInterval(this.pollingTimer);
      this.pollingTimer = null;
    }
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
            empty: res.data.length === 0 && !self.data.hasDownloadTasks,
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

  loadDownloadTasks: function() {
    var self = this;
    var tasks = app.getAllDownloadTasks();

    // Format timestamps
    for (var i = 0; i < tasks.length; i++) {
      var timestamp = tasks[i].createdAt;
      if (timestamp) {
        var date = new Date(timestamp);
        var hours = date.getHours();
        var minutes = date.getMinutes();
        var seconds = date.getSeconds();
        var month = date.getMonth() + 1;
        var day = date.getDate();
        tasks[i].createdAt = month + '-' + day + ' ' + hours + ':' + (minutes < 10 ? '0' : '') + minutes + ':' + (seconds < 10 ? '0' : '') + seconds;
      }
    }

    var hasTasks = tasks.length > 0;

    // Update page data
    if (hasTasks || this.data.hasDownloadTasks) {
      self.setData({
        downloadTasks: tasks,
        hasDownloadTasks: hasTasks,
        empty: !hasTasks && self.data.historyList.length === 0
      });
    }
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
    var pages = getCurrentPages();
    var indexPage = pages[0];
    if (indexPage) {
      indexPage.setData({
        url: url
      });
    }
    wx.navigateBack();
  },

  onRetryDownload: function(e) {
    var taskId = e.currentTarget.dataset.taskId;
    var task = app.getDownloadTask(taskId);
    if (task && task.error) {
      // Remove failed task and trigger re-download from index page
      // For now, just show a message
      wx.showToast({ title: '请重新点击下载', icon: 'none' });
    }
  },

  onClearTask: function(e) {
    var taskId = e.currentTarget.dataset.taskId;
    app.removeDownloadTask(taskId);
    this.loadDownloadTasks();
  },

  onClearAllTasks: function() {
    var self = this;
    wx.showModal({
      title: '提示',
      content: '确定清空所有下载任务?',
      success: function(res) {
        if (res.confirm) {
          var tasks = self.data.downloadTasks;
          for (var i = 0; i < tasks.length; i++) {
            app.removeDownloadTask(tasks[i].id);
          }
          self.setData({
            downloadTasks: [],
            hasDownloadTasks: false,
            empty: self.data.historyList.length === 0
          });
          wx.showToast({ title: '已清空', icon: 'success' });
        }
      }
    });
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
                empty: !self.data.hasDownloadTasks
              });
              wx.showToast({ title: '已清空', icon: 'success' });
            }
          });
        }
      }
    });
  },

  // Format status for display
  getStatusText: function(status) {
    var statusMap = {
      'resolving': '获取链接',
      'downloading': '下载中',
      'saving': '保存中',
      'completed': '已完成',
      'failed': '失败'
    };
    return statusMap[status] || status;
  },

  // Format timestamp for display
  formatTime: function(timestamp) {
    if (!timestamp) return '';
    var date = new Date(timestamp);
    var hours = date.getHours();
    var minutes = date.getMinutes();
    var seconds = date.getSeconds();
    var month = date.getMonth() + 1;
    var day = date.getDate();
    return month + '-' + day + ' ' + hours + ':' + (minutes < 10 ? '0' : '') + minutes + ':' + (seconds < 10 ? '0' : '') + seconds;
  }
});
