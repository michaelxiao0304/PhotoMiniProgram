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
      mediaId: mediaId || null,  // 保存 mediaId 用于大文件重试
      formatId: formatId || null,  // 保存 formatId 用于大文件重试
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

    // Helper function to save to file system (fallback for large files)
    var saveToFileSystem = function(tempPath, mediaType, taskId, callback) {
      var fs = wx.getFileSystemManager();
      var filename = 'video_' + Date.now() + '.mp4';
      if (mediaType !== 'VIDEO') {
        filename = 'image_' + Date.now() + '.jpg';
      }
      var destPath = wx.env.USER_DATA_PATH + '/' + filename;

      fs.saveFile({
        tempFilePath: tempPath,
        filePath: destPath,
        success: function(res) {
          callback(true, res.savedFilePath);
        },
        fail: function(err) {
          callback(false, err);
        }
      });
    };

    // Helper function to save to album (with fallback to file system)
    var saveToAlbum = function(tempPath, mediaType, taskId) {
      app.updateDownloadTask(taskId, { status: 'saving' });

      var doSave = function() {
        if (mediaType === 'VIDEO') {
          wx.saveVideoToPhotosAlbum({
            filePath: tempPath,
            success: function() {
              app.updateDownloadTask(taskId, { status: 'completed', progress: '已完成' });
              wx.showToast({ title: '保存成功', icon: 'success' });
            },
            fail: function(err) {
              // Check if it's a size limit error
              var errMsg = err.errMsg || '';
              if (errMsg.indexOf('exceed') !== -1 || errMsg.indexOf('max') !== -1) {
                // Fallback to file system for large files
                saveToFileSystem(tempPath, mediaType, taskId, function(success, result) {
                  if (success) {
                    app.updateDownloadTask(taskId, { status: 'completed', progress: '已保存到文件' });
                    wx.showModal({
                      title: '文件已保存',
                      content: '文件较大，已保存到小程序存储空间。请在微信"我-设置-通用-存储空间"中查看，或通过文件管理器导出。',
                      showCancel: false,
                      confirmText: '知道了'
                    });
                  } else {
                    app.updateDownloadTask(taskId, { status: 'failed', error: '保存失败' });
                    wx.showToast({ title: '保存失败', icon: 'none' });
                  }
                });
              } else if (errMsg.indexOf('auth deny') !== -1) {
                app.updateDownloadTask(taskId, { status: 'failed', error: '需要授权' });
                wx.showModal({
                  title: '提示',
                  content: '需要授权保存到相册，是否授权？',
                  success: function(res) {
                    if (res.confirm) {
                      wx.openSetting();
                    }
                  }
                });
                wx.showToast({ title: '需要授权保存到相册', icon: 'none' });
              } else {
                app.updateDownloadTask(taskId, { status: 'failed', error: '保存失败' });
                wx.showToast({ title: '保存失败', icon: 'none' });
              }
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
              var errMsg = err.errMsg || '';
              if (errMsg.indexOf('exceed') !== -1 || errMsg.indexOf('max') !== -1) {
                saveToFileSystem(tempPath, mediaType, taskId, function(success, result) {
                  if (success) {
                    app.updateDownloadTask(taskId, { status: 'completed', progress: '已保存到文件' });
                    wx.showModal({
                      title: '文件已保存',
                      content: '文件较大，已保存到小程序存储空间。请在微信"我-设置-通用-存储空间"中查看。',
                      showCancel: false,
                      confirmText: '知道了'
                    });
                  } else {
                    app.updateDownloadTask(taskId, { status: 'failed', error: '保存失败' });
                    wx.showToast({ title: '保存失败', icon: 'none' });
                  }
                });
              } else if (errMsg.indexOf('auth deny') !== -1) {
                app.updateDownloadTask(taskId, { status: 'failed', error: '需要授权' });
                wx.showModal({
                  title: '提示',
                  content: '需要授权保存到相册',
                  success: function(res) {
                    if (res.confirm) {
                      wx.openSetting();
                    }
                  }
                });
                wx.showToast({ title: '需要授权保存到相册', icon: 'none' });
              } else {
                app.updateDownloadTask(taskId, { status: 'failed', error: '保存失败' });
                wx.showToast({ title: '保存失败', icon: 'none' });
              }
            }
          });
        }
      };

      doSave();
    };

    // Helper function to do the actual download
    var doDownload = function(downloadUrl, mediaType, taskId) {
      // Save the actual download URL for retry
      app.updateDownloadTask(taskId, { downloadUrl: downloadUrl });

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
          var errMsg = err.errMsg || '未知错误';
          app.updateDownloadTask(taskId, { status: 'failed', error: '下载失败: ' + errMsg });
          wx.showToast({ title: '下载失败: ' + errMsg, icon: 'none', duration: 3000 });
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
          // Check if should use browser download
          if (res.data && res.data.browserDownload && res.data.downloadUrl) {
            var downloadUrl = res.data.downloadUrl;
            var fileSize = res.data.fileSize || '';

            console.log('Large file detected, fileSize:', fileSize);

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
                  }
                });
              }
            });
            return;
          }

          if (res.data && res.data.needsStreaming) {
            var streamUrl = app.globalData.apiBase + '/media/' + mediaId + '/stream.mp4';
            if (formatId) {
              streamUrl = streamUrl + '?formatId=' + encodeURIComponent(formatId);
            }

            // Check file size to decide download method
            var fileSize = res.data.fileSize || '';
            var isLargeFile = fileSize.indexOf('MB') !== -1 && parseFloat(fileSize) > 100;

            app.updateDownloadTask(taskId, { status: 'downloading', progress: '下载视频...' });

            // For large files (>100MB), use wx.request
            if (isLargeFile) {
              var fs = wx.getFileSystemManager();
              var filename = 'video_' + Date.now() + '.mp4';
              var destPath = wx.env.USER_DATA_PATH + '/' + filename;

              console.log('Large file detected (' + fileSize + '), using wx.request...');

              // No fixed timeout - let it download as long as needed
              wx.request({
                url: streamUrl,
                responseType: 'arraybuffer',
                success: function(dlRes) {
                  console.log('Large file download success:', dlRes.statusCode);
                  if (dlRes.statusCode !== 200) {
                    app.updateDownloadTask(taskId, { status: 'failed', error: '下载失败: HTTP ' + dlRes.statusCode });
                    wx.showToast({ title: '下载失败: ' + dlRes.statusCode, icon: 'none' });
                    return;
                  }

                  app.updateDownloadTask(taskId, { status: 'saving', progress: '保存中...' });

                  fs.writeFile({
                    filePath: destPath,
                    data: dlRes.data,
                    encoding: 'binary',
                    success: function() {
                      console.log('File saved to:', destPath);
                      app.updateDownloadTask(taskId, { status: 'completed', progress: '已保存到文件', tempPath: destPath });
                      wx.showModal({
                        title: '下载完成',
                        content: '文件已保存到: ' + filename + '\n\n请在微信"我-设置-通用-存储空间"中查看导出。',
                        showCancel: false,
                        confirmText: '知道了'
                      });
                    },
                    fail: function(err) {
                      console.log('Write file fail:', err);
                      var errMsg = err.errMsg || '未知错误';
                      app.updateDownloadTask(taskId, { status: 'failed', error: '保存失败: ' + errMsg });
                      wx.showToast({ title: '保存失败: ' + errMsg, icon: 'none', duration: 3000 });
                    }
                  });
                },
                fail: function(err) {
                  console.log('Large file download fail:', err);
                  var errMsg = err.errMsg || '未知错误';
                  app.updateDownloadTask(taskId, { status: 'failed', error: '下载超时' });
                  wx.showModal({
                    title: '下载超时',
                    content: '文件较大(' + fileSize + ')，下载时间过长。请尝试选择更小的分辨率。',
                    showCancel: false,
                    confirmText: '知道了'
                  });
                }
              });
              return;
            }

            // For small files (<=20MB), use regular downloadFile
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
                var errMsg = err.errMsg || '未知错误';
                app.updateDownloadTask(taskId, { status: 'failed', error: '下载失败: ' + errMsg });
                wx.showToast({ title: '下载失败: ' + errMsg, icon: 'none', duration: 3000 });
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
