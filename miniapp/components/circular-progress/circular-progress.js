// components/circular-progress/circular-progress.js
Component({
  properties: {
    size: {
      type: Number,
      value: 120
    },
    status: {
      type: String,
      value: '下载中...'
    },
    color: {
      type: String,
      value: '#07c160'
    },
    progress: {
      type: String,
      value: ''
    }
  },

  data: {},

  attached: function() {},

  methods: {}
});
