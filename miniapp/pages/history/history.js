// history.js
var app = getApp();

Page({
  data: {
    historyList: [],
    downloadTasks: [],
    loading: false,
    empty: true,
    hasDownloadTasks: false,
    tasksExpanded: true,
    historyExpanded: true
  },

  onLoad: function() {
    this.loadHistory();
    this.startPolling();
  },

  toggleTasks: function() {
    this.setData({
      tasksExpanded: !this.data.tasksExpanded
    });
  },

  toggleHistory: function() {
    this.setData({
      historyExpanded: !this.data.historyExpanded
    });
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
    var completedTasks = [];

    // Format timestamps and check for completed tasks
    for (var i = 0; i < tasks.length; i++) {
      var timestamp = tasks[i].createdAt;
      if (timestamp) {
        var date = new Date(timestamp);
        var year = date.getFullYear();
        var hours = date.getHours();
        var minutes = date.getMinutes();
        var seconds = date.getSeconds();
        var month = date.getMonth() + 1;
        var day = date.getDate();
        tasks[i].createdAt = year + '/' + (month < 10 ? '0' : '') + month + '/' + (day < 10 ? '0' : '') + day + ' ' + (hours < 10 ? '0' : '') + hours + ':' + (minutes < 10 ? '0' : '') + minutes + ':' + (seconds < 10 ? '0' : '') + seconds;
      }

      // Collect completed tasks to move to history
      if (tasks[i].status === 'completed') {
        completedTasks.push(tasks[i]);
      }
    }

    // Move completed tasks to history
    if (completedTasks.length > 0) {
      this.moveCompletedToHistory(completedTasks);
      // Filter out completed tasks from downloadTasks
      var remainingTasks = [];
      for (var j = 0; j < tasks.length; j++) {
        if (tasks[j].status !== 'completed') {
          remainingTasks.push(tasks[j]);
        }
      }
      tasks = remainingTasks;
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

  moveCompletedToHistory: function(completedTasks) {
    var self = this;
    var historyList = this.data.historyList;

    // Add completed tasks to local history list
    for (var i = 0; i < completedTasks.length; i++) {
      var task = completedTasks[i];
      // Use originalUrl if available, otherwise use current URL
      var url = task.originalUrl || task.url || '';
      // If URL is still FORMAT:xxx, use title as fallback
      if (url && url.indexOf('FORMAT:') === 0) {
        url = task.title || '';
      }

      // Skip if URL is empty
      if (!url) {
        app.removeDownloadTask(task.id);
        continue;
      }

      // Check for duplicate - update timestamp if exists
      var exists = false;
      for (var j = 0; j < historyList.length; j++) {
        if (historyList[j].url === url) {
          // Update timestamp and move to top
          historyList[j].timestamp = task.createdAt;
          historyList.unshift(historyList.splice(j, 1)[0]);
          exists = true;
          break;
        }
      }

      if (exists) {
        app.removeDownloadTask(task.id);
        continue;
      }

      // Determine platform from URL
      var platform = 'Unknown';
      if (url.indexOf('twitter.com') !== -1 || url.indexOf('x.com') !== -1) {
        platform = 'Twitter';
      } else if (url.indexOf('instagram.com') !== -1) {
        platform = 'Instagram';
      } else if (url.indexOf('tiktok.com') !== -1) {
        platform = 'TikTok';
      }

      var historyItem = {
        id: task.id,
        url: url,
        platform: platform,
        timestamp: task.createdAt,
        mediaCount: 1,
        title: task.title || ''
      };

      // Add to beginning of list
      historyList.unshift(historyItem);

      // Remove from download tasks
      app.removeDownloadTask(task.id);
    }

    // Update local history list
    this.setData({
      historyList: historyList
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
    var self = this;
    var taskId = e.currentTarget.dataset.taskId;
    var task = app.getDownloadTask(taskId);

    if (!task) {
      wx.showToast({ title: '任务不存在', icon: 'none' });
      return;
    }

    // Check if it's a large file (has mediaId but no downloadUrl saved)
    // Large file retry uses browser download method
    if (task.mediaId && !task.downloadUrl) {
      // Get the API URL for resolving download link
      var resolveUrl = app.globalData.apiBase + '/media/' + task.mediaId + '/download-url';
      if (task.formatId) {
        resolveUrl = resolveUrl + '?formatId=' + encodeURIComponent(task.formatId);
      }

      app.updateDownloadTask(taskId, { status: 'resolving', progress: '获取链接...' });

      wx.request({
        url: resolveUrl,
        success: function(res) {
          if (res.data && res.data.browserDownload && res.data.downloadUrl) {
            var downloadUrl = res.data.downloadUrl;
            var fileSize = res.data.fileSize || '';

            // Copy URL to clipboard - user opens browser to download
            wx.setClipboardData({
              data: downloadUrl,
              success: function() {
                wx.showModal({
                  title: '链接已复制',
                  content: '文件较大(' + fileSize + ')，请打开手机浏览器，粘贴链接下载。',
                  showCancel: false,
                  confirmText: '知道了',
                  success: function() {
                    // User confirmed - mark task as completed
                    app.updateDownloadTask(taskId, { status: 'completed', progress: '已复制链接' });
                    self.loadDownloadTasks();
                  }
                });
              }
            });
          } else {
            // Fallback: try to get stream URL for retry
            var streamUrl = app.globalData.apiBase + '/media/' + task.mediaId + '/stream.mp4';
            if (task.formatId) {
              streamUrl = streamUrl + '?formatId=' + encodeURIComponent(task.formatId);
            }

            wx.setClipboardData({
              data: streamUrl,
              success: function() {
                wx.showModal({
                  title: '链接已复制',
                  content: '请点击"复制链接"，然后打开手机浏览器粘贴下载。',
                  showCancel: false,
                  confirmText: '知道了',
                  success: function() {
                    app.updateDownloadTask(taskId, { status: 'completed', progress: '已复制链接' });
                    self.loadDownloadTasks();
                  }
                });
              }
            });
          }
        },
        fail: function() {
          app.updateDownloadTask(taskId, { status: 'failed', error: '获取链接失败' });
          self.loadDownloadTasks();
          wx.showToast({ title: '获取链接失败', icon: 'none' });
        }
      });
      return;
    }

    // Check if we have a download URL saved (normal retry)
    if (task.downloadUrl) {
      // Update task status to retrying
      app.updateDownloadTask(taskId, { status: 'downloading', error: '', progress: '0 B / 0 B', progressPercent: 0 });

      // Refresh the task list UI
      this.loadDownloadTasks();

      // Use wx.downloadFile directly for retry
      var downloadTask = wx.downloadFile({
        url: task.downloadUrl,
        success: function(res) {
          console.log('Retry download success:', res);
          var tempPath = res.tempFilePath;
          app.updateDownloadTask(taskId, { tempPath: tempPath, status: 'saving' });

          // Save to album
          var mediaType = task.mediaType || 'IMAGE';
          if (mediaType === 'VIDEO') {
            wx.saveVideoToPhotosAlbum({
              filePath: tempPath,
              success: function() {
                app.updateDownloadTask(taskId, { status: 'completed', progress: '已完成' });
                self.loadDownloadTasks();
                wx.showToast({ title: '保存成功', icon: 'success' });
              },
              fail: function(err) {
                var errMsg = err.errMsg || '';
                if (errMsg.indexOf('exceed') !== -1 || errMsg.indexOf('max') !== -1) {
                  app.updateDownloadTask(taskId, { status: 'completed', progress: '已保存到文件' });
                  wx.showModal({
                    title: '文件已保存',
                    content: '文件较大，已保存到小程序存储空间。',
                    showCancel: false,
                    confirmText: '知道了'
                  });
                } else {
                  app.updateDownloadTask(taskId, { status: 'failed', error: '保存失败' });
                  wx.showToast({ title: '保存失败', icon: 'none' });
                }
                self.loadDownloadTasks();
              }
            });
          } else {
            wx.saveImageToPhotosAlbum({
              filePath: tempPath,
              success: function() {
                app.updateDownloadTask(taskId, { status: 'completed', progress: '已完成' });
                self.loadDownloadTasks();
                wx.showToast({ title: '保存成功', icon: 'success' });
              },
              fail: function(err) {
                var errMsg = err.errMsg || '';
                if (errMsg.indexOf('exceed') !== -1 || errMsg.indexOf('max') !== -1) {
                  app.updateDownloadTask(taskId, { status: 'completed', progress: '已保存到文件' });
                } else {
                  app.updateDownloadTask(taskId, { status: 'failed', error: '保存失败' });
                }
                self.loadDownloadTasks();
                wx.showToast({ title: '保存失败', icon: 'none' });
              }
            });
          }
        },
        fail: function(err) {
          console.log('Retry download fail:', err);
          var errMsg = err.errMsg || '未知错误';
          app.updateDownloadTask(taskId, { status: 'failed', error: '下载失败: ' + errMsg });
          self.loadDownloadTasks();
          wx.showToast({ title: '下载失败: ' + errMsg, icon: 'none', duration: 3000 });
        }
      });

      // Monitor progress
      downloadTask.onProgressUpdate(function(progressRes) {
        var totalBytesWritten = progressRes.totalBytesWritten;
        var totalBytesExpectedToWrite = progressRes.totalBytesExpectedToWrite;
        var written = self.formatSize(totalBytesWritten);
        var total = self.formatSize(totalBytesExpectedToWrite);
        var progressStr = written + ' / ' + total;
        var percent = 0;
        if (totalBytesExpectedToWrite > 0) {
          percent = Math.round((totalBytesWritten / totalBytesExpectedToWrite) * 100);
        }
        app.updateDownloadTask(taskId, {
          progress: progressStr,
          progressPercent: percent
        });
        self.loadDownloadTasks();
      });
    } else {
      // No download URL saved, need to re-parse
      wx.showToast({ title: '请重新点击下载', icon: 'none' });
    }
  },

  // Helper function to format file size
  formatSize: function(bytes) {
    if (!bytes || bytes === 0) return '0 B';
    var units = ['B', 'KB', 'MB', 'GB'];
    var i = 0;
    while (bytes >= 1024 && i < units.length - 1) {
      bytes = bytes / 1024;
      i++;
    }
    return bytes.toFixed(1) + ' ' + units[i];
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
    var year = date.getFullYear();
    var hours = date.getHours();
    var minutes = date.getMinutes();
    var seconds = date.getSeconds();
    var month = date.getMonth() + 1;
    var day = date.getDate();
    return year + '/' + (month < 10 ? '0' : '') + month + '/' + (day < 10 ? '0' : '') + day + ' ' + (hours < 10 ? '0' : '') + hours + ':' + (minutes < 10 ? '0' : '') + minutes + ':' + (seconds < 10 ? '0' : '') + seconds;
  }
});
