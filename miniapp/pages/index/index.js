var app = getApp();

Page({
  data: {
    url: '',
    loading: false,
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

    this.setData({ loading: true });

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
        self.setData({ loading: false });
      }
    });
  },

  onSelectResolution: function(e) {
    var mediaId = e.currentTarget.dataset.mediaId;
    var resId = e.currentTarget.dataset.resId;
    var selectedResolutions = this.data.selectedResolutions;
    selectedResolutions[mediaId] = resId;
    this.setData({ selectedResolutions: selectedResolutions });
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
