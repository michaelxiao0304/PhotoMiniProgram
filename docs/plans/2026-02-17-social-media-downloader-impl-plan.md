# 社交媒体下载器 - 实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现一个可运行的社交媒体下载器，包括 Spring Boot 后端服务 + 微信小程序前端

**Architecture:** 采用分层架构，后端通过 ProcessBuilder 调用 yt-dlp 子进程解析媒体，小程序通过 HTTP API 与后端通信获取媒体列表和下载文件。

**Tech Stack:**
- 后端: Spring Boot 3.x + JDK 17
- 媒体解析: yt-dlp (系统命令)
- 小程序: 微信原生开发 + Vant Weapp
- 构建: Maven + npm

---

## 阶段一：后端服务开发

### Task 1: 创建 Spring Boot 项目骨架

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/photomini/PhotoMiniProgramApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-dev.yml`

**Step 1: 创建 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.photomini</groupId>
    <artifactId>photo-mini-program</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Step 2: 创建主应用类**

```java
package com.photomini;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PhotoMiniProgramApplication {
    public static void main(String[] args) {
        SpringApplication.run(PhotoMiniProgramApplication.class, args);
    }
}
```

**Step 3: 创建配置文件 application.yml**

```yaml
server:
  port: 9080

spring:
  application:
    name: photo-mini-program
  profiles:
    active: dev

app:
  yt-dlp:
    command: yt-dlp
    temp-dir: /tmp/photo-mini-program
    timeout-seconds: 300
```

**Step 4: 创建 application-dev.yml**

```yaml
logging:
  level:
    com.photomini: DEBUG
```

**Step 5: 验证构建**

Run: `cd backend && mvn clean compile`
Expected: BUILD SUCCESS

**Step 6: 提交**

```bash
git add backend/
git commit -m "feat: create Spring Boot project skeleton"
```

---

### Task 2: 实现 yt-dlp 封装服务

**Files:**
- Create: `backend/src/main/java/com/photomini/service/YtDlpService.java`
- Create: `backend/src/main/java/com/photomini/model/MediaInfo.java`
- Create: `backend/src/main/java/com/photomini/model/ParseResult.java`
- Test: `backend/src/test/java/com/photomini/service/YtDlpServiceTest.java`

**Step 1: 创建 MediaInfo 模型**

```java
package com.photomini.model;

import lombok.Data;
import java.util.List;

@Data
public class MediaInfo {
    private String id;
    private MediaType type;
    private String thumbnailUrl;
    private String downloadUrl;
    private String filename;
    private Integer width;
    private Integer height;
    private String resolution;
    private List<ResolutionOption> resolutions;
    private String defaultResolution;
}

enum MediaType {
    IMAGE, VIDEO
}

@Data
class ResolutionOption {
    private String id;
    private String label;
    private String size;
}
```

**Step 2: 创建 ParseResult 模型**

```java
package com.photomini.model;

import lombok.Data;
import java.util.List;

@Data
public class ParseResult {
    private boolean success;
    private String platform;
    private List<MediaInfo> mediaList;
    private String title;
    private String errorMessage;
}
```

**Step 3: 创建 YtDlpService**

```java
package com.photomini.service;

import com.photomini.model.MediaInfo;
import com.photomini.model.ParseResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class YtDlpService {

    @Value("${app.yt-dlp.command:yt-dlp}")
    private String ytDlpCommand;

    @Value("${app.yt-dlp.temp-dir:/tmp/photo-mini-program}")
    private String tempDir;

    @Value("${app.yt-dlp.timeout-seconds:300}")
    private int timeoutSeconds;

    public ParseResult parseUrl(String url) {
        // 实现解析逻辑
        return null;
    }

    public Path generatePreview(MediaInfo media) {
        // 实现预览图生成
        return null;
    }

    public InputStream getMediaStream(MediaInfo media) {
        // 获取媒体流
        return null;
    }
}
```

**Step 4: 编写单元测试**

```java
package com.photomini.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class YtDlpServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void testParseTwitterUrl() {
        // 测试解析 Twitter 链接
    }

    @Test
    void testParseInstagramUrl() {
        // 测试解析 Instagram 链接
    }

    @Test
    void testParseTiktokUrl() {
        // 测试解析 TikTok 链接
    }
}
```

**Step 5: 提交**

```bash
git add backend/
git commit -m "feat: add yt-dlp service models and basic structure"
```

---

### Task 3: 实现 REST API 控制器

**Files:**
- Create: `backend/src/main/java/com/photomini/controller/MediaController.java`
- Create: `backend/src/main/java/com/photomini/dto/ParseRequest.java`
- Modify: `backend/src/main/java/com/photomini/service/YtDlpService.java`

**Step 1: 创建 ParseRequest DTO**

```java
package com.photomini.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ParseRequest {
    @NotBlank(message = "URL不能为空")
    private String url;
}
```

**Step 2: 创建 MediaController**

```java
package com.photomini.controller;

import com.photomini.dto.ParseRequest;
import com.photomini.model.ParseResult;
import com.photomini.service.YtDlpService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class MediaController {

    private final YtDlpService ytDlpService;
    private final Map<String, ParseResult> sessionCache = new ConcurrentHashMap<>();

    public MediaController(YtDlpService ytDlpService) {
        this.ytDlpService = ytDlpService;
    }

    @PostMapping("/parse")
    public ResponseEntity<ParseResult> parseUrl(@Valid @RequestBody ParseRequest request) {
        ParseResult result = ytDlpService.parseUrl(request.getUrl());
        if (result.isSuccess()) {
            String sessionId = UUID.randomUUID().toString();
            sessionCache.put(sessionId, result);
            result.setSessionId(sessionId);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/media/{mediaId}/preview")
    public ResponseEntity<byte[]> getPreview(@PathVariable String mediaId) {
        // 实现逻辑
        return ResponseEntity.ok().build();
    }

    @GetMapping("/media/{mediaId}/download")
    public ResponseEntity<InputStream> downloadMedia(@PathVariable String mediaId) {
        // 实现逻辑
        return ResponseEntity.ok().build();
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
```

**Step 3: 提交**

```bash
git add backend/
git commit -m "feat: add REST API controllers"
```

---

## 阶段二：微信小程序开发

### Task 4: 创建小程序项目骨架

**Files:**
- Create: `miniapp/app.json`
- Create: `miniapp/app.js`
- Create: `miniapp/app.wxss`
- Create: `miniapp/project.config.json`

**Step 1: 创建 app.json**

```json
{
  "pages": [
    "pages/index/index"
  ],
  "window": {
    "navigationBarTitleText": "社交媒体下载器",
    "navigationBarBackgroundColor": "#ffffff",
    "navigationBarTextStyle": "black"
  },
  "permission": {
    "scope.writePhotosAlbum": {
      "desc": "保存图片和视频到相册"
    }
  },
  "usingComponents": {
    "van-button": "/miniprogram_npm/@vant/weapp/button/index",
    "van-field": "/miniprogram_npm/@vant/weapp/field/index",
    "van-toast": "/miniprogram_npm/@vant/weapp/toast/index",
    "van-loading": "/miniprogram_npm/@vant/weapp/loading/index",
    "van-action-sheet": "/miniprogram_npm/@vant/weapp/action-sheet/index",
    "van-image": "/miniprogram_npm/@vant/weapp/image/index"
  }
}
```

**Step 2: 创建 app.js**

```javascript
App({
  onLaunch() {
    // 检查系统权限
  },
  globalData: {
    apiBase: 'http://localhost:9080/api'
  }
})
```

**Step 3: 创建 project.config.json**

```json
{
  "description": "社交媒体下载器",
  "packOptions": {
    "ignore": []
  },
  "setting": {
    "urlCheck": false
  },
  "compileType": "miniprogram",
  "libVersion": "3.0.0",
  "appid": "your-appid",
  "projectname": "PhotoMiniProgram",
  "condition": {}
}
```

**Step 4: 提交**

```bash
git add miniapp/
git commit -m "feat: create mini-program skeleton"
```

---

### Task 5: 实现首页（链接输入和解析）

**Files:**
- Create: `miniapp/pages/index/index.wxml`
- Create: `miniapp/pages/index/index.wxss`
- Create: `miniapp/pages/index/index.js`

**Step 1: 创建 index.wxml**

```xml
<view class="container">
  <view class="header">
    <text class="title">社交媒体下载器</text>
    <text class="subtitle">支持 Twitter / Instagram / TikTok</text>
  </view>

  <view class="input-section">
    <van-field
      value="{{ url }}"
      placeholder="粘贴分享链接..."
      border="{{ false }}"
      bind:change="onUrlChange"
      clearable
    />
    <van-button
      type="primary"
      block
      loading="{{ loading }}"
      bind:click="onParse"
    >
      解析
    </van-button>
  </view>

  <view class="media-list" wx:if="{{ mediaList.length > 0 }}">
    <block wx:for="{{ mediaList }}" wx:key="id">
      <view class="media-item">
        <image
          src="{{ item.thumbnailUrl }}"
          mode="aspectFill"
          class="media-thumb"
          bindtap="onPreview"
          data-url="{{ item.downloadUrl }}"
        />
        <view class="media-info">
          <text class="media-type">{{ item.type === 'video' ? '视频' : '图片' }}</text>
          <text class="media-resolution" wx:if="{{ item.resolution }}">
            {{ item.resolution }}
          </text>
          <view wx:if="{{ item.resolutions }}" class="resolution-selector">
            <van-button
              size="small"
              wx:for="{{ item.resolutions }}"
              wx:for-item="res"
              wx:key="id"
              bind:click="onSelectResolution"
              data-media-id="{{ item.id }}"
              data-res-id="{{ res.id }}"
            >
              {{ res.label }} ({{ res.size }})
            </van-button>
          </view>
          <van-button
            wx:if="{{ !item.resolutions }}"
            type="primary"
            size="small"
            bind:click="onSave"
            data-media="{{ item }}"
          >
            保存到相册
          </van-button>
        </view>
      </view>
    </block>
  </view>
</view>
```

**Step 2: 创建 index.wxss**

```css
.container {
  padding: 32rpx;
  background-color: #f5f5f5;
  min-height: 100vh;
}

.header {
  text-align: center;
  margin-bottom: 48rpx;
}

.title {
  font-size: 40rpx;
  font-weight: bold;
  color: #333;
  display: block;
}

.subtitle {
  font-size: 24rpx;
  color: #999;
  margin-top: 8rpx;
}

.input-section {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 32rpx;
}

.media-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.media-item {
  background: #fff;
  border-radius: 16rpx;
  overflow: hidden;
}

.media-thumb {
  width: 100%;
  height: 400rpx;
}

.media-info {
  padding: 24rpx;
}
```

**Step 3: 创建 index.js**

```javascript
const app = getApp();

Page({
  data: {
    url: '',
    loading: false,
    mediaList: []
  },

  onUrlChange(e) {
    this.setData({ url: e.detail });
  },

  async onParse() {
    const { url } = this.data;
    if (!url) {
      wx.showToast({ title: '请输入链接', icon: 'none' });
      return;
    }

    this.setData({ loading: true });

    try {
      const res = await wx.request({
        url: `${app.globalData.apiBase}/parse`,
        method: 'POST',
        data: { url },
        header: { 'Content-Type': 'application/json' }
      });

      if (res.data.success) {
        // 预处理图片URL
        const mediaList = res.data.mediaList.map(item => ({
          ...item,
          thumbnailUrl: `${app.globalData.apiBase}${item.thumbnailUrl}`,
          downloadUrl: item.downloadUrl
            ? `${app.globalData.apiBase}${item.downloadUrl}`
            : null
        }));
        this.setData({ mediaList });
      } else {
        wx.showToast({ title: res.data.errorMessage || '解析失败', icon: 'none' });
      }
    } catch (err) {
      wx.showToast({ title: '网络错误', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  async onSave(e) {
    const { url } = e.currentTarget.dataset;
    if (!url) return;

    try {
      const res = await wx.downloadFile({ url });
      const tempPath = res.tempFilePath;

      await wx.saveImageToPhotosAlbum({
        filePath: tempPath
      });

      wx.showToast({ title: '保存成功', icon: 'success' });
    } catch (err) {
      wx.showToast({ title: '保存失败', icon: 'none' });
    }
  }
});
```

**Step 4: 提交**

```bash
git add miniapp/
git commit -m "feat: implement index page with link parsing"
```

---

### Task 6: 实现视频分辨率选择和下载

**Files:**
- Modify: `miniapp/pages/index/index.js`
- Modify: `miniapp/pages/index/index.wxml`

**Step 1: 添加分辨率选择逻辑**

```javascript
// 在 index.js 中添加
data: {
  // ... existing data
  selectedResolutions: {}
},

onSelectResolution(e) {
  const { mediaId, resId } = e.currentTarget.dataset;
  const { selectedResolutions } = this.data;
  selectedResolutions[mediaId] = resId;
  this.setData({ selectedResolutions });
},

async onSaveVideo(e) {
  const { media } = e.currentTarget.dataset;
  const { selectedResolutions } = this.data;

  const resolutionId = selectedResolutions[media.id];
  const downloadUrl = resolutionId
    ? `${app.globalData.apiBase}/media/${resolutionId}/download`
    : `${app.globalData.apiBase}/api/media/${media.id}/download`;

  try {
    const res = await wx.downloadFile({ url: downloadUrl });
    await wx.saveVideoToPhotosAlbum({
      filePath: res.tempFilePath
    });
    wx.showToast({ title: '保存成功', icon: 'success' });
  } catch (err) {
    wx.showToast({ title: '保存失败', icon: 'none' });
  }
}
```

**Step 2: 提交**

```bash
git add miniapp/
git commit -m "feat: add video resolution selection and download"
```

---

## 阶段三：集成测试

### Task 7: 端到端测试

**Step 1: 启动后端服务**

Run: `cd backend && mvn spring-boot:run`
Expected: 服务启动在 9080 端口

**Step 2: 测试健康检查**

Run: `curl http://localhost:9080/api/health`
Expected: OK

**Step 3: 测试链接解析**

Run: `curl -X POST http://localhost:9080/api/parse -H "Content-Type: application/json" -d '{"url":"https://twitter.com/example/status/123"}'`
Expected: 返回媒体列表 JSON

**Step 4: 提交**

```bash
git commit -m "test: add integration tests"
```

---

## 阶段四：部署配置

### Task 8: 部署配置

**Files:**
- Create: `backend/Dockerfile`
- Create: `docker-compose.yml`

**Step 1: 创建 Dockerfile**

```dockerfile
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY backend/pom.xml .
COPY backend/src ./src
RUN apk add --no-cache maven
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN apk add --no-cache yt-dlp
EXPOSE 9080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Step 2: 创建 docker-compose.yml**

```yaml
version: '3.8'
services:
  backend:
    build: ./backend
    ports:
      - "9080:9080"
    volumes:
      - yt-dlp-cache:/tmp/photo-mini-program
    restart: unless-stopped

volumes:
  yt-dlp-cache:
```

**Step 3: 提交**

```bash
git add backend/Dockerfile docker-compose.yml
git commit -m "chore: add deployment configuration"
```

---

## 执行方式

**Plan complete and saved to `docs/plans/2026-02-17-social-media-downloader-impl-plan.md`**

**Two execution options:**

**1. Subagent-Driven (this session)** - 我为每个任务派遣独立的子代理，任务间进行代码审查，快速迭代

**2. Parallel Session (separate)** - 在新会话中打开，使用 executing-plans，带检查点的批量执行

**你想选择哪种方式？**
