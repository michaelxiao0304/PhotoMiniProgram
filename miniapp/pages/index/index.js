var app = getApp();

Page({
  data: {
    url: '',
    loading: false,
    btnDisabled: false,
    mediaList: [],
    selectedResolutions: {}
  },

  onUrlChange: function(e) {
    this.setData({ url: e.detail.value });
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

    // Get selected resolution format ID if available
    if (mediaId && selectedResolutions && selectedResolutions[mediaId]) {
      var resId = selectedResolutions[mediaId];
      var mediaList = this.data.mediaList;
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

    // Helper function to do the actual download
    var doDownload = function(downloadUrl) {
      wx.downloadFile({
        url: downloadUrl,
        success: function(res) {
          var tempPath = res.tempFilePath;
          wx.saveImageToPhotosAlbum({
            filePath: tempPath,
            success: function() {
              wx.showToast({ title: '保存成功', icon: 'success' });
            },
            fail: function(err) {
              if (err.errMsg && err.errMsg.indexOf('auth deny') !== -1) {
                wx.showModal({
                  title: '提示',
                  content: '需要授权保存到相册',
                  success: function(res) {
                    if (res.confirm) {
                      wx.openSetting();
                    }
                  }
                });
              } else {
                wx.showToast({ title: '保存失败', icon: 'none' });
              }
            }
          });
        },
        fail: function() {
          wx.showToast({ title: '下载失败', icon: 'none' });
        }
      });
    };

    if (!url) return;

    // Check if URL needs to be resolved (for videos)
    if (url.indexOf('FORMAT:') === 0 && mediaId) {
      // Need to resolve URL first
      wx.showLoading({ title: '获取下载链接...' });
      var resolveUrl = app.globalData.apiBase + '/media/' + mediaId + '/download-url';
      if (formatId) {
        resolveUrl = resolveUrl + '?formatId=' + formatId;
      }

      wx.request({
        url: resolveUrl,
        success: function(res) {
          wx.hideLoading();
          if (res.data && res.data.downloadUrl) {
            doDownload(res.data.downloadUrl);
          } else {
            wx.showToast({ title: '获取链接失败', icon: 'none' });
          }
        },
        fail: function() {
          wx.hideLoading();
          wx.showToast({ title: '获取链接失败', icon: 'none' });
        }
      });
    } else {
      // Direct URL, download directly
      doDownload(url);
    }
  }
});
