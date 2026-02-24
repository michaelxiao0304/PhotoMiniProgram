var app = getApp();

// 格式化文件大小
var formatSize = function(bytes) {
  if (!bytes || bytes === 0) return '0 B';
  var units = ['B', 'KB', 'MB', 'GB'];
  var i = 0;
  while (bytes >= 1024 && i < units.length - 1) {
    bytes = bytes / 1024;
    i++;
  }
  return bytes.toFixed(1) + ' ' + units[i];
};

// 将 ArrayBuffer 保存为临时文件并返回路径
var saveArrayBufferToTempFile = function(arrayBuffer, extension, title, callback) {
  var fs = wx.getFileSystemManager();
  // Sanitize title for filename - remove invalid characters
  var safeTitle = '';
  if (title) {
    // Keep only valid filename characters
    safeTitle = title.replace(/[<>:"/\\|?*]/g, '').trim().substring(0, 50);
    if (safeTitle) {
      safeTitle = safeTitle + '_';
    }
  }
  var tempPath = wx.env.USER_DATA_PATH + '/' + safeTitle + Date.now() + '.' + extension;
  fs.writeFile({
    filePath: tempPath,
    data: arrayBuffer,
    encoding: 'binary',
    success: function() {
      callback(tempPath);
    },
    fail: function(err) {
      callback(null, err);
    }
  });
};

Page({
  data: {
    url: '',
    loading: false,
    btnDisabled: false,
    mediaList: [],
    selectedResolutions: {},
    downloading: false,
    downloadStatus: '准备中...',
    downloadProgress: ''
  },

  onUrlChange: function(e) {
    this.setData({ url: e.detail.value });
  },

  onClearUrl: function() {
    this.setData({ url: '', mediaList: [] });
  },

  // 图片加载失败时使用占位图
  onImageError: function(e) {
    var mediaId = e.currentTarget.dataset.mediaId;
    var mediaList = this.data.mediaList;
    for (var i = 0; i < mediaList.length; i++) {
      if (mediaList[i].id === mediaId) {
        mediaList[i].thumbnailUrl = '/images/placeholder.png';
        break;
      }
    }
    this.setData({ mediaList: mediaList });
  },

  onParse: function() {
    var self = this;
    var url = this.data.url;

    if (!url) {
      wx.showToast({ title: '请输入链接', icon: 'none' });
      return;
    }

    // URL 格式验证
    if (url.indexOf('http://') !== 0 && url.indexOf('https://') !== 0) {
      wx.showToast({ title: '请输入有效的URL', icon: 'none' });
      return;
    }

    this.setData({ loading: true, btnDisabled: true });

    wx.request({
      url: app.globalData.apiBase + '/parse',
      method: 'POST',
      data: { url: url },
      header: { 'Content-Type': 'application/json' },
      success: function(res) {
        if (res.data.success) {
          // Store title from parse result
          var parseTitle = res.data.title || '';
          var mediaList = res.data.mediaList.map(function(item) {
            // If URL is already complete (starts with http/https), use it directly
            var thumbnailUrl = item.thumbnailUrl && item.thumbnailUrl.indexOf('http') === 0
              ? item.thumbnailUrl
              : app.globalData.apiBase + item.thumbnailUrl;
            // Don't concatenate for FORMAT: selectors - they need to be resolved via API
            var downloadUrl;
            if (item.downloadUrl && item.downloadUrl.indexOf('FORMAT:') === 0) {
              downloadUrl = item.downloadUrl;  // Keep as-is for now, will resolve in onSave
            } else if (item.downloadUrl && item.downloadUrl.indexOf('http') === 0) {
              downloadUrl = item.downloadUrl;
            } else {
              downloadUrl = item.downloadUrl ? app.globalData.apiBase + item.downloadUrl : null;
            }

            return {
              id: item.id,
              type: item.type,
              thumbnailUrl: thumbnailUrl,
              downloadUrl: downloadUrl,
              filename: item.filename,
              title: parseTitle,  // Add title for filename
              resolution: item.resolution,
              resolutions: item.resolutions,
              defaultResolution: item.defaultResolution
            };
          });
          self.setData({ mediaList: mediaList });
        } else {
          wx.showToast({ title: res.data.errorMessage || '解析失败', icon: 'none' });
        }
      },
      fail: function() {
        wx.showToast({ title: '网络错误', icon: 'none' });
      },
      complete: function() {
        self.setData({ loading: false, btnDisabled: false });
      }
    });
  },

  onSelectResolution: function(e) {
    var self = this;
    var mediaId = e.currentTarget.dataset.mediaId;
    var resId = e.currentTarget.dataset.resId;
    var mediaList = this.data.mediaList;
    var selectedResolutions = this.data.selectedResolutions;

    // 查找对应的媒体和分辨率
    for (var i = 0; i < mediaList.length; i++) {
      if (mediaList[i].id === mediaId && mediaList[i].resolutions) {
        var resolutions = mediaList[i].resolutions;
        for (var j = 0; j < resolutions.length; j++) {
          if (resolutions[j].id === resId) {
            // 更新对应媒体的 downloadUrl - 使用外部URL或拼接
            var resDownloadUrl = resolutions[j].downloadUrl;
            if (resDownloadUrl && resDownloadUrl.indexOf('http') === 0) {
              mediaList[i].downloadUrl = resDownloadUrl;
            } else if (resDownloadUrl) {
              mediaList[i].downloadUrl = app.globalData.apiBase + resDownloadUrl;
            }
            mediaList[i].resolution = resolutions[j].label;
            break;
          }
        }
        break;
      }
    }

    selectedResolutions[mediaId] = resId;
    self.setData({
      selectedResolutions: selectedResolutions,
      mediaList: mediaList
    });
  },

  onSave: function(e) {
    var self = this;
    var url = e.currentTarget.dataset.url;
    var mediaId = e.currentTarget.dataset.mediaId;
    var selectedResolutions = this.data.selectedResolutions;
    var formatId = null;
    var mediaType = 'IMAGE';
    var mediaTitle = '';

    // Get media type and title from mediaList
    var mediaList = this.data.mediaList;
    if (mediaId && mediaList) {
      for (var i = 0; i < mediaList.length; i++) {
        if (mediaList[i].id === mediaId) {
          mediaType = mediaList[i].type || 'IMAGE';
          mediaTitle = mediaList[i].title || '';
          break;
        }
      }
    }

    // Get selected resolution format ID if available
    if (mediaId && selectedResolutions && selectedResolutions[mediaId]) {
      var resId = selectedResolutions[mediaId];
      for (var i = 0; i < mediaList.length; i++) {
        if (mediaList[i].id === mediaId && mediaList[i].resolutions) {
          var resolutions = mediaList[i].resolutions;
          for (var j = 0; j < resolutions.length; j++) {
            if (resolutions[j].id === resId) {
              formatId = resolutions[j].formatId;
              break;
            }
          }
          break;
        }
      }
    }

    // If no resolution selected, use default resolution's formatId
    if (!formatId && mediaId && mediaList) {
      for (var i = 0; i < mediaList.length; i++) {
        if (mediaList[i].id === mediaId && mediaList[i].resolutions && mediaList[i].defaultResolution) {
          var resolutions = mediaList[i].resolutions;
          for (var j = 0; j < resolutions.length; j++) {
            if (resolutions[j].label === mediaList[i].defaultResolution) {
              formatId = resolutions[j].formatId;
              break;
            }
          }
          break;
        }
      }
    }

    if (!url) return;

    // Create task ID
    var taskId = 'task_' + Date.now();

    // Initialize download task in global data
    app.addDownloadTask({
      id: taskId,
      url: url,
      originalUrl: this.data.url || '',  // 保存原始分享链接
      title: mediaTitle,
      mediaType: mediaType,
      status: 'downloading',
      progress: '0 B / 0 B',
      progressPercent: 0,
      tempPath: '',
      error: '',
      createdAt: Date.now()
    });

    // Show toast and navigate to history
    wx.showToast({
      title: '开始下载',
      icon: 'success'
    });

    // Helper function to save to album
    var saveToAlbum = function(tempPath, mediaType, taskId) {
      app.updateDownloadTask(taskId, { status: 'saving' });

      if (mediaType === 'VIDEO') {
        wx.saveVideoToPhotosAlbum({
          filePath: tempPath,
          success: function() {
            app.updateDownloadTask(taskId, { status: 'completed', progress: '已完成' });
            wx.showToast({ title: '保存成功', icon: 'success' });
          },
          fail: function(err) {
            var errorMsg = '保存失败';
            if (err.errMsg && err.errMsg.indexOf('auth deny') !== -1) {
              errorMsg = '需要授权保存到相册';
              wx.showModal({
                title: '提示',
                content: '需要授权保存到相册',
                success: function(res) {
                  if (res.confirm) {
                    wx.openSetting();
                  }
                }
              });
            }
            app.updateDownloadTask(taskId, { status: 'failed', error: errorMsg });
            wx.showToast({ title: errorMsg, icon: 'none' });
          }
        });
      } else {
        wx.saveImageToPhotosAlbum({
          filePath: tempPath,
          success: function() {
            app.updateDownloadTask(taskId, { status: 'completed', progress: '已完成' });
            wx.showToast({ title: '保存成功', icon: 'success' });
          },
          fail: function(err) {
            var errorMsg = '保存失败';
            if (err.errMsg && err.errMsg.indexOf('auth deny') !== -1) {
              errorMsg = '需要授权保存到相册';
              wx.showModal({
                title: '提示',
                content: '需要授权保存到相册',
                success: function(res) {
                  if (res.confirm) {
                    wx.openSetting();
                  }
                }
              });
            }
            app.updateDownloadTask(taskId, { status: 'failed', error: errorMsg });
            wx.showToast({ title: errorMsg, icon: 'none' });
          }
        });
      }
    };

    // Helper function to do the actual download
    var doDownload = function(downloadUrl, mediaType, taskId) {
      var downloadTask = wx.downloadFile({
        url: downloadUrl,
        success: function(res) {
          console.log('Download success:', res);
          var tempPath = res.tempFilePath;
          app.updateDownloadTask(taskId, { tempPath: tempPath });
          saveToAlbum(tempPath, mediaType, taskId);
        },
        fail: function(err) {
          console.log('Download fail:', err);
          app.updateDownloadTask(taskId, { status: 'failed', error: '下载失败' });
          wx.showToast({ title: '下载失败', icon: 'none' });
        }
      });

      // Register progress callback
      downloadTask.onProgressUpdate(function(progressRes) {
        var totalBytesWritten = progressRes.totalBytesWritten;
        var totalBytesExpectedToWrite = progressRes.totalBytesExpectedToWrite;
        var written = formatSize(totalBytesWritten);
        var total = formatSize(totalBytesExpectedToWrite);
        var progressStr = written + ' / ' + total;
        var percent = 0;
        if (totalBytesExpectedToWrite > 0) {
          percent = Math.round((totalBytesWritten / totalBytesExpectedToWrite) * 100);
        }
        app.updateDownloadTask(taskId, {
          progress: progressStr,
          progressPercent: percent
        });
      });
    };

    // Check if URL needs to be resolved
    // For all videos, use the backend API to get download URL (handles large files via streaming)
    if (mediaType === 'VIDEO' && mediaId) {
      app.updateDownloadTask(taskId, { status: 'resolving', progress: '获取链接...' });

      var resolveUrl = app.globalData.apiBase + '/media/' + mediaId + '/download-url';
      if (formatId) {
        resolveUrl = resolveUrl + '?formatId=' + encodeURIComponent(formatId);
      }

      wx.request({
        url: resolveUrl,
        success: function(res) {
          if (res.data && res.data.needsStreaming) {
            app.updateDownloadTask(taskId, { status: 'downloading', progress: '下载视频...' });

            var streamUrl = app.globalData.apiBase + '/media/' + mediaId + '/stream.mp4';
            if (formatId) {
              streamUrl = streamUrl + '?formatId=' + encodeURIComponent(formatId);
            }

            var downloadTask = wx.downloadFile({
              url: streamUrl,
              success: function(res) {
                console.log('Stream download success:', res);
                var tempPath = res.tempFilePath;
                app.updateDownloadTask(taskId, { tempPath: tempPath });
                saveToAlbum(tempPath, mediaType, taskId);
              },
              fail: function(err) {
                console.log('Stream download fail:', err);
                app.updateDownloadTask(taskId, { status: 'failed', error: '下载失败' });
                wx.showToast({ title: '下载失败', icon: 'none' });
              }
            });

            downloadTask.onProgressUpdate(function(progressRes) {
              var totalBytesWritten = progressRes.totalBytesWritten;
              var totalBytesExpectedToWrite = progressRes.totalBytesExpectedToWrite;
              var written = formatSize(totalBytesWritten);
              var total = formatSize(totalBytesExpectedToWrite);
              var progressStr = written + ' / ' + total;
              var percent = 0;
              if (totalBytesExpectedToWrite > 0) {
                percent = Math.round((totalBytesWritten / totalBytesExpectedToWrite) * 100);
              }
              app.updateDownloadTask(taskId, {
                progress: progressStr,
                progressPercent: percent
              });
            });
          } else if (res.data && res.data.downloadUrl) {
            doDownload(res.data.downloadUrl, mediaType, taskId);
          } else {
            app.updateDownloadTask(taskId, { status: 'failed', error: '获取链接失败' });
            wx.showToast({ title: '获取链接失败', icon: 'none' });
          }
        },
        fail: function() {
          app.updateDownloadTask(taskId, { status: 'failed', error: '获取链接失败' });
          wx.showToast({ title: '获取链接失败', icon: 'none' });
        }
      });
    } else {
      // Direct URL, download directly
      app.updateDownloadTask(taskId, { status: 'downloading', progress: '下载中...' });
      doDownload(url, mediaType, taskId);
    }
  },

  goToHistory: function() {
    wx.navigateTo({
      url: '/pages/history/history'
    });
  },

  onCopyTitle: function(e) {
    var title = e.currentTarget.dataset.title;
    wx.setClipboardData({
      data: title,
      success: function() {
        wx.showToast({ title: '已复制', icon: 'success' });
      }
    });
  }
});
