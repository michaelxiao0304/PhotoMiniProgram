# Twitter Embedded Media Extraction Design

**Date**: 2026-02-24
**Status**: Approved

## Problem Statement

When parsing Twitter/X URLs that contain embedded YouTube or TikTok videos (e.g., quote tweets), the current yt-dlp Twitter extractor fails because:
- Twitter's GraphQL API only returns Twitter-hosted media
- Embedded external videos (YouTube, TikTok) are not exposed through the API
- yt-dlp returns "No video could be found in this tweet"

## Solution Overview

Add a fallback mechanism that:
1. Detects when Twitter parsing fails
2. Extracts embedded external URLs from the tweet HTML
3. Uses yt-dlp to parse the extracted URLs (YouTube/TikTok)
4. Returns results seamlessly to the user

## Architecture

```
parseUrl(url)
    │
    ├─► detectPlatform(url)
    │
    ├─► yt-dlp --dump-json url
    │       │
    │       └─► Success → Return ParseResult
    │
    └─► If Twitter + parse failed
            │
            └─► extractEmbeddedMedia(url)
                    │
                    ├─► curl Twitter page
                    ├─► Regex extract YouTube/TikTok URLs
                    └─► yt-dlp parse external URL
                            │
                            └─► Return ParseResult
```

## Implementation Details

### 1. New Method: extractEmbeddedMedia(String twitterUrl)

**Purpose**: Extract embedded external URLs from Twitter page HTML

**Process**:
1. Use curl to fetch Twitter page content
2. Use regex patterns to extract:
   - YouTube: `youtube.com/watch?v=xxx`, `youtu.be/xxx`
   - TikTok: `tiktok.com/@xxx/video/xxx`
3. Return first matched external URL or null

**Regex Patterns**:
```java
// YouTube
"https?://(?:www\\.)?youtube\\.com/watch\\?v=[\\w-]+"
"https?://youtu\\.be/[\\w-]+"

// TikTok
"https?://(?:www\\.)?tiktok\\.com/@[\\w.-]+/video/\\d+"
```

### 2. Modified: parseUrl(String url)

**Change**: Add fallback logic after yt-dlp parsing fails

```java
// In catch block or after parse failure:
if (!result.isSuccess() && isTwitterUrl(url)) {
    String embeddedUrl = extractEmbeddedMedia(url);
    if (embeddedUrl != null) {
        // Parse the embedded URL with yt-dlp
        result = parseUrl(embeddedUrl);
        if (result.isSuccess()) {
            result.setPlatform(detectPlatform(url)); // Keep original platform
        }
    }
}
```

### 3. Platform Detection

Add new platform detection for embedded content:
- If external URL detected → platform becomes "Twitter-YouTube" or "Twitter-TikTok"
- Or keep original "Twitter" platform with note in title

## Error Handling

1. **No embedded URL found**: Return original error message
2. **Embedded URL parse fails**: Log error, return original Twitter error
3. **Network/curl fails**: Log error, return original error

## Testing

Test URLs:
- Working: `https://x.com/claudeai/status/2023817132581208353` (Twitter Amplify video)
- To test: Quote tweet with YouTube/TikTok embed

## Files Modified

- `backend/src/main/java/com/photomini/service/YtDlpService.java`
