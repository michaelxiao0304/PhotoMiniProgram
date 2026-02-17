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
            return {
              id: item.id,
              type: item.type,
              thumbnailUrl: app.globalData.apiBase + item.thumbnailUrl,
              downloadUrl: item.downloadUrl ? app.globalData.apiBase + item.downloadUrl : null,
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
            // 更新对应媒体的 downloadUrl
            mediaList[i].downloadUrl = app.globalData.apiBase + resolutions[j].downloadUrl;
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
    if (!url) return;

    wx.downloadFile({
      url: url,
      success: function(res) {
        var tempPath = res.tempFilePath;
        wx.saveImageToPhotosAlbum({
          filePath: tempPath,
          success: function() {
            wx.showToast({ title: '保存成功', icon: 'success' });
          },
          fail: function(err) {
            if (err.errMsg.indexOf('auth deny') !== -1) {
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
  }
});
