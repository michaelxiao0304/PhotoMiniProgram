# 下载进度显示功能实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在小程序下载时显示下载进度（已下载大小 / 总大小），替代当前无限转圈的状态

**Architecture:** 使用 wx.request 替代 wx.downloadFile，利用微信原生的 onProgressUpdate 回调获取下载进度。circular-progress 组件扩展支持进度显示。

**Tech Stack:** 微信小程序 ES5

---

### Task 1: 修改 circular-progress 组件支持进度显示

**Files:**
- Modify: `miniapp/components/circular-progress/circular-progress.js`
- Modify: `miniapp/components/circular-progress/circular-progress.wxml`
- Modify: `miniapp/components/circular-progress/circular-progress.wxss`

**Step 1: 修改组件属性**

```javascript
// circular-progress.js - 添加 progress 属性
properties: {
  size: { type: Number, value: 120 },
  status: { type: String, value: '下载中...' },
  color: { type: String, value: '#07c160' },
  progress: { type: String, value: '' }  // 新增：进度字符串，如 "2.5MB / 10.5MB"
},
```

**Step 2: 修改 wxml 显示进度**

```xml
<!-- circular-progress.wxml -->
<view class="progress-content">
  <text class="progress-label">{{status}}</text>
  <text class="progress-size" wx:if="{{progress}}">{{progress}}</text>
</view>
```

**Step 3: 添加进度文字样式**

```css
/* circular-progress.wxss */
.progress-size {
  display: block;
  font-size: 14px;
  color: #666;
  margin-top: 8px;
}
```

**Step 4: 提交**

```bash
git add miniapp/components/circular-progress/* && git commit -m "feat: add progress display support to circular-progress"
```

---

### Task 2: 修改 index.js 使用 wx.request 获取下载进度

**Files:**
- Modify: `miniapp/pages/index/index.js`

**Step 1: 修改 doDownload 函数使用 wx.request**

```javascript
// 在 onSave 函数中，修改 doDownload 函数
var doDownload = function(downloadUrl, mediaType) {
  var self = this;
  wx.request({
    url: downloadUrl,
    method: 'GET',
    responseType: 'arraybuffer',
    success: function(res) {
      self.setData({ downloading: false });
      // ... 后续保存逻辑不变
    },
    fail: function() {
      self.setData({ downloading: false });
      wx.showToast({ title: '下载失败', icon: 'none' });
    }
  });
};
```

**Step 2: 修改 stream.mp4 下载逻辑使用 wx.request**

在 `if (res.data && res.data.needsStreaming)` 分支中，将 wx.downloadFile 改为 wx.request，添加 onProgressUpdate 回调。

**Step 3: 添加进度更新逻辑**

在每个 wx.request 调用中添加：
```javascript
onProgressUpdate: function(res) {
  var progress = res.progress;  // 0-100
  var totalBytesWritten = res.totalBytesWritten;
  var totalBytesExpectedToWrite = res.totalBytesExpectedToWrite;

  // 转换为可读格式
  var written = formatSize(totalBytesWritten);
  var total = formatSize(totalBytesExpectedToWrite);
  var progressStr = written + ' / ' + total;

  self.setData({ downloadStatus: '下载中... ' + progressStr });
}
```

**Step 4: 添加 formatSize 辅助函数**

```javascript
// 在 Page({ 外部添加
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
```

**Step 5: 提交**

```bash
git add miniapp/pages/index/index.js && git commit -m "feat: add download progress display using wx.request"
```

---

### Task 3: 验证功能

**Step 1: 测试 Twitter 视频下载**
- URL: https://x.com/claudeai/status/2023817132581208353?s=46
- 验证：下载时显示进度（如 "2.5MB / 10.5MB"）

**Step 2: 测试 TikTok 视频下载**
- URL: https://vt.tiktok.com/ZSmDJsbcg/
- 验证：下载时显示进度

**Step 3: 测试图片下载**
- 验证：图片下载也显示进度

---

### Task 4: 推送代码

```bash
git push
```

---

## 验证命令

测试完成后运行：
```bash
# 确认代码已提交
git log --oneline -3

# 确认已推送到远程
git status
```
