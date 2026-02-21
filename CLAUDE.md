# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PhotoMiniProgram is a WeChat mini-program paired with a Spring Boot backend for downloading media (images/videos) from social media platforms (Twitter/X, Instagram, TikTok, Bilibili, YouTube).

## Architecture

```
┌─────────────────────────────┐
│   WeChat Mini-Program       │  ES5 JavaScript (WXML/WXSS/JS)
│   (miniapp/)                │  lazyCodeLoading enabled
└─────────────────────────────┘
              ↓ HTTP :9080
┌─────────────────────────────┐
│   Spring Boot Backend       │  JDK 17, Spring Boot 3.x
│   (backend/)                │  yt-dlp for media parsing
└─────────────────────────────┘
```

## Common Commands

### Backend (Spring Boot)
```bash
cd backend
mvn spring-boot:run      # Run in development
mvn clean package        # Build JAR
mvn test                 # Run tests
```

### Mini-Program
- Open `miniapp/` directory in WeChat Developer Tools
- AppID configured in `miniapp/project.config.json`

## Key Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/parse` | Parse URL, return media list |
| GET | `/api/media/{id}/preview` | Get thumbnail preview |
| GET | `/api/media/{id}/download-url` | Resolve download URL |
| GET | `/api/media/{id}/stream` | Stream download video |
| GET | `/api/history` | Get download history |
| DELETE | `/api/history` | Clear history |
| GET | `/api/health` | Health check |

## Important Constraints

### WeChat Mini-Program (ES5 Required)
- **MUST use ES5 syntax**: `var`, `function()`, `Object.defineProperty`
- **FORBIDDEN**: `const`, `let`, arrow functions, template literals, spread operator
- This is required for `lazyCodeLoading` compatibility in `app.json`

### Configuration
- Backend port: `9080` (configured in `application.yml`)
- yt-dlp path: `/home/ubuntu/.local/bin/yt-dlp` (configurable in `application.yml`)
- Cookies file: `~/.config/instagram-cookies.txt` (for private content)

### Media Types
- `VIDEO` - Requires streaming download via yt-dlp
- `AUDIO` - Audio-only content
- Images use direct URL download

## Code Structure

```
backend/src/main/java/com/photomini/
├── PhotoMiniProgramApplication.java  # Main entry
├── controller/MediaController.java   # REST endpoints
├── service/YtDlpService.java         # yt-dlp integration
├── model/                            # Data models
└── dto/                              # Request/Response DTOs

miniapp/
├── app.json                          # Mini-program config
├── app.js                            # App lifecycle
├── pages/
│   ├── index/                        # Main page (parse & download)
│   └── history/                      # Download history
└── components/                       # Custom components
```
