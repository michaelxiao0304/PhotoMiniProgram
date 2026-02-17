# 社交媒体下载器 - 项目设计方案

## 1. 项目概述

**项目名称**: PhotoMiniProgram (社交媒体下载器)
**项目类型**: 微信小程序 + Spring Boot 后端服务
**核心功能**: 解析 Twitter/X、Instagram、TikTok 分享链接，提取图片/视频，提供高清去水印下载

---

## 2. 技术架构

### 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        微信小程序                                │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐    │
│  │ 粘贴链接  │ → │ 解析媒体  │ → │ 预览选择  │ → │ 保存相册 │    │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              ↓ HTTP API (9080)
┌─────────────────────────────────────────────────────────────────┐
│                   Spring Boot 后端 (JDK 17)                      │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐        │
│  │  LinkParser  │ → │  MediaInfo   │ → │  StreamProxy │        │
│  │  (链接解析)   │   │  (获取媒体列表) │  │  (流式转发)   │        │
│  └──────────────┘   └──────────────┘   └──────────────┘        │
│                              ↓                                   │
│                    ┌──────────────────┐                         │
│                    │  yt-dlp 子进程   │                         │
│                    │  (系统命令调用)   │                         │
│                    └──────────────────┘                         │
└─────────────────────────────────────────────────────────────────┘
```

### 技术栈

| 层级 | 技术选择 |
|------|---------|
| 后端框架 | Spring Boot 3.x (JDK 17) |
| 媒体解析 | yt-dlp (通过 ProcessBuilder 调用) |
| 流式传输 | Spring MVC 异步响应 |
| 小程序 | 微信原生开发 (WXML/WXSS/JS) |
| UI组件 | Vant Weapp (有赞开源) |

---

## 3. API 设计

### 接口列表

| 接口 | 方法 | 功能 |
|------|------|------|
| `/api/parse` | POST | 解析链接，返回媒体列表（含自动生成的预览图） |
| `/api/media/{id}/preview` | GET | 获取单个媒体的预览图 |
| `/api/media/{id}/download` | GET | 流式下载原始媒体文件 |
| `/api/health` | GET | 健康检查 |

### API 详情

#### 3.1 POST /api/parse

**请求**:
```json
{
  "url": "https://twitter.com/user/status/1234567890"
}
```

**响应**:
```json
{
  "success": true,
  "platform": "twitter",
  "mediaList": [
    {
      "id": "media_1",
      "type": "image",
      "thumbnailUrl": "/api/media/media_1/preview",
      "downloadUrl": "/api/media/media_1/download",
      "filename": "twitter_photo_1.jpg",
      "width": 1080,
      "height": 1350,
      "resolution": "1080x1350"
    },
    {
      "id": "media_2",
      "type": "video",
      "thumbnailUrl": "/api/media/media_2/preview",
      "filename": "twitter_video.mp4",
      "resolutions": [
        { "id": "media_2_720", "label": "720P", "size": "15.2MB" },
        { "id": "media_2_1080", "label": "1080P", "size": "28.5MB" }
      ],
      "defaultResolution": "1080p"
    }
  ],
  "title": "Tweet text content..."
}
```

#### 3.2 GET /api/media/{id}/preview

获取预览图，返回 JPEG 格式图片数据。

#### 3.3 GET /api/media/{id}/download

流式下载原始媒体文件，返回对应的图片/视频数据。

---

## 4. 核心流程

### 4.1 媒体解析流程

```
1. 用户在小程序输入分享链接
2. 小程序调用 /api/parse
3. 后端使用 yt-dlp --write-thumbnail --write-info-json 提取媒体信息
4. 后端自动生成所有媒体的预览图
5. 返回媒体列表（图片直接给下载URL，视频给分辨率选项）
```

### 4.2 下载保存流程

```
图片流程:
  解析结果 → 显示预览图 → 用户点击保存 → 调用 /api/media/{id}/download → 微信保存到相册

视频流程:
  解析结果 → 显示预览图 + 分辨率选项 → 用户选择分辨率 → 调用 /api/media/{resolution_id}/download → 微信保存到相册
```

---

## 5. 分辨率处理规则

| 媒体类型 | 处理方式 |
|---------|---------|
| **图片** | 自动返回最高分辨率，无需用户选择 |
| **视频** | 列出所有可用分辨率供用户选择 |

---

## 6. 部署配置

### 端口配置
- 后端服务端口: **9080**
- 微信公众号或小程序请求域名需配置对应地址

### yt-dlp 安装
```bash
pip install -U yt-dlp
```

### 支持的平台
- Twitter/X
- Instagram
- TikTok

---

## 7. 后续扩展

- [ ] 支持更多社交平台 (Facebook, YouTube, Pinterest)
- [ ] 添加下载历史记录
- [ ] 支持批量下载
- [ ] 添加下载进度显示
