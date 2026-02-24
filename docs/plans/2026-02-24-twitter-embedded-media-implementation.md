# Implementation Plan: Twitter Embedded Media Extraction

## Step 1: Add extractEmbeddedMedia method

**File**: `backend/src/main/java/com/photomini/service/YtDlpService.java`

**Action**: Add new private method after `detectPlatform()` method

```java
/**
 * Extract embedded external media URLs from Twitter page
 * (YouTube, TikTok links in quote tweets)
 */
private String extractEmbeddedMedia(String twitterUrl) {
    // 1. Use curl to fetch Twitter page
    // 2. Regex match YouTube/TikTok URLs
    // 3. Return first matched URL or null
}
```

**Regex patterns to use**:
- YouTube: `(?:https?://)?(?:www\\.)?youtube\\.com/watch\\?v=[\\w-]+`
- YouTube Shorts: `(?:https?://)?(?:www\\.)?youtu\\.be/[\\w-]+`
- TikTok: `(?:https?://)?(?:www\\.)?tiktok\\.com/@[\\w.-]+/video/\\d+`

---

## Step 2: Add isTwitterUrl helper method

**File**: Same file

**Action**: Add simple helper to check if URL is Twitter

```java
private boolean isTwitterUrl(String url) {
    return url != null && (url.contains("twitter.com") || url.contains("x.com"));
}
```

---

## Step 3: Modify parseUrl to add fallback

**File**: `YtDlpService.java`

**Location**: In the catch block after setting error message, add fallback logic

**Change**:
```java
} catch (Exception e) {
    logger.error("Error parsing URL: {}", e.getMessage(), e);

    // Fallback: try to extract embedded media for Twitter URLs
    if (isTwitterUrl(url)) {
        String embeddedUrl = extractEmbeddedMedia(url);
        if (embeddedUrl != null) {
            logger.info("Found embedded media in Twitter URL: {}", embeddedUrl);
            return parseUrl(embeddedUrl); // Recursively parse embedded URL
        }
    }

    result.setErrorMessage("解析失败: " + e.getMessage());
}
```

---

## Step 4: Verify changes compile

**Command**:
```bash
cd /home/ubuntu/Code/PhotoMiniProgram/backend && mvn compile
```

---

## Step 5: Test with provided URL

**Test URL**: `https://x.com/q7663thxjfzz2bz/status/2026197906261242086`

**Expected**:
- Extract YouTube URL from tweet
- Parse YouTube URL with yt-dlp
- Return video formats

---

## Implementation Order

1. Add `isTwitterUrl()` method
2. Add `extractEmbeddedMedia()` method
3. Modify `parseUrl()` catch block
4. Compile and test
