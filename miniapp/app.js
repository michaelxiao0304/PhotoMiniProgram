App({
  onLaunch: function() {
    // 检查系统权限
  },
  globalData: {
    apiBase: 'https://1ammoss.com/photo/api',
    downloadTasks: {}
  },

  // 添加下载任务
  addDownloadTask: function(task) {
    this.globalData.downloadTasks[task.id] = task;
  },

  // 更新下载任务状态
  updateDownloadTask: function(taskId, updates) {
    if (this.globalData.downloadTasks[taskId]) {
      var task = this.globalData.downloadTasks[taskId];
      for (var key in updates) {
        task[key] = updates[key];
      }
    }
  },

  // 获取下载任务
  getDownloadTask: function(taskId) {
    return this.globalData.downloadTasks[taskId];
  },

  // 获取所有下载任务
  getAllDownloadTasks: function() {
    var tasks = [];
    for (var id in this.globalData.downloadTasks) {
      tasks.push(this.globalData.downloadTasks[id]);
    }
    // 按创建时间倒序
    tasks.sort(function(a, b) {
      return (b.createdAt || 0) - (a.createdAt || 0);
    });
    return tasks;
  },

  // 移除下载任务
  removeDownloadTask: function(taskId) {
    delete this.globalData.downloadTasks[taskId];
  }
})
