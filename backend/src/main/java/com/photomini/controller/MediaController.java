package com.photomini.controller;

import com.photomini.dto.ParseRequest;
import com.photomini.model.HistoryRecord;
import com.photomini.model.MediaInfo;
import com.photomini.model.ParseResult;
import com.photomini.model.ResolutionOption;
import com.photomini.service.YtDlpService;

import java.security.MessageDigest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class MediaController {

    private static final Logger logger = LoggerFactory.getLogger(MediaController.class);
    private static final int timeoutSeconds = 300;

    @Autowired
    private YtDlpService ytDlpService;

    // Simple in-memory storage for media info (for mock implementation)
    private final Map<String, MediaInfo> mediaStore = new HashMap<>();

    // History storage - URL as key, keeps only latest (dedup by URL)
    private final Map<String, HistoryRecord> historyStore = new ConcurrentHashMap<>();

    // Keep history in order (most recent first)
    private final List<String> historyOrder = new ArrayList<>();

    /**
     * Parse URL to extract media information
     * POST /api/parse
     */
    @PostMapping("/parse")
    public ResponseEntity<ParseResult> parseUrl(@Valid @RequestBody ParseRequest request) {
        try {
            ParseResult result = ytDlpService.parseUrl(request.getUrl());

            // Store media info for later retrieval by ID BEFORE modifying thumbnailUrl
            if (result.getMediaList() != null) {
                for (MediaInfo media : result.getMediaList()) {
                    // IMPORTANT: Create a copy for storage to preserve original thumbnailUrl
                    MediaInfo mediaCopy = new MediaInfo();
                    mediaCopy.setId(media.getId());
                    mediaCopy.setType(media.getType());
                    mediaCopy.setFilename(media.getFilename());
                    mediaCopy.setThumbnailUrl(media.getThumbnailUrl());  // Keep original URL
                    mediaCopy.setDownloadUrl(media.getDownloadUrl());
                    mediaCopy.setSourceUrl(request.getUrl());  // Store original URL for resolving download
                    mediaCopy.setWidth(media.getWidth());
                    mediaCopy.setHeight(media.getHeight());
                    mediaCopy.setResolution(media.getResolution());
                    mediaCopy.setResolutions(media.getResolutions());
                    mediaCopy.setDefaultResolution(media.getDefaultResolution());
                    mediaStore.put(media.getId(), mediaCopy);

                    // Modify the returned result's thumbnailUrl for frontend
                    // Return relative path without /api prefix (frontend adds apiBase which already includes /api)
                    if (media.getThumbnailUrl() != null && !media.getThumbnailUrl().isEmpty()) {
                        media.setThumbnailUrl("/media/" + media.getId() + "/preview");
                    }
                }
            }

            // Save to history (dedup by URL, keep latest)
            if (result.isSuccess() && result.getMediaList() != null && !result.getMediaList().isEmpty()) {
                String normalizedUrl = normalizeUrl(request.getUrl());
                HistoryRecord history = new HistoryRecord();
                history.setId(normalizedUrl);
                history.setUrl(request.getUrl());
                history.setPlatform(result.getPlatform());
                history.setTitle(result.getTitle());
                history.setThumbnailUrl(result.getMediaList().get(0).getThumbnailUrl());
                history.setMediaCount(result.getMediaList().size());
                List<String> types = new ArrayList<>();
                for (MediaInfo m : result.getMediaList()) {
                    types.add(m.getType().name());
                }
                history.setMediaTypes(types);
                history.setTimestamp(System.currentTimeMillis());

                // Remove old position if exists
                historyOrder.remove(normalizedUrl);

                // Add to front (most recent)
                historyOrder.add(0, normalizedUrl);
                historyStore.put(normalizedUrl, history);

                // Keep only latest 50
                while (historyOrder.size() > 50) {
                    String oldest = historyOrder.remove(historyOrder.size() - 1);
                    historyStore.remove(oldest);
                }
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            ParseResult errorResult = new ParseResult();
            errorResult.setSuccess(false);
            errorResult.setErrorMessage(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }

    /**
     * Get preview image for media - returns image directly
     * GET /api/media/{mediaId}/preview
     */
    @GetMapping("/media/{mediaId}/preview")
    public ResponseEntity<byte[]> getPreview(@PathVariable String mediaId) {
        MediaInfo media = mediaStore.get(mediaId);
        if (media == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Media not found".getBytes());
        }

        // Download thumbnail via backend
        try {
            byte[] imageData = ytDlpService.generatePreview(media.getThumbnailUrl());
            if (imageData == null || imageData.length == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Failed to download thumbnail".getBytes());
            }

            // Determine content type
            String contentType = "image/jpeg";
            if (media.getThumbnailUrl() != null && media.getThumbnailUrl().toLowerCase().endsWith(".png")) {
                contentType = "image/png";
            } else if (media.getThumbnailUrl() != null && media.getThumbnailUrl().toLowerCase().endsWith(".webp")) {
                contentType = "image/webp";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));

            return ResponseEntity.ok().headers(headers).body(imageData);
        } catch (Exception e) {
            logger.error("Failed to generate preview: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage().getBytes());
        }
    }

    /**
     * Resolve actual download URL for a media item
     * GET /api/media/{mediaId}/download-url?formatId=xxx
     */
    @GetMapping("/media/{mediaId}/download-url")
    public ResponseEntity<?> getDownloadUrl(@PathVariable String mediaId,
                                            @RequestParam(required = false) String formatId) {
        MediaInfo media = mediaStore.get(mediaId);
        if (media == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Media not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        try {
            // Use sourceUrl (original media URL) to resolve download URL
            String sourceUrl = media.getSourceUrl();
            if (sourceUrl == null || sourceUrl.isEmpty()) {
                sourceUrl = media.getDownloadUrl();
            }

            String formatSelector;
            String directVideoUrl = null;

            if (formatId != null && !formatId.isEmpty()) {
                // Check if formatId is a direct video URL (from FxTwitter)
                if (formatId.startsWith("http")) {
                    // This is a direct video URL from FxTwitter - use it directly
                    directVideoUrl = formatId;
                    formatSelector = null;
                } else if (formatId.startsWith("hls-")) {
                    // HLS formats (hls-xxx) can't be combined with bestaudio
                    formatSelector = formatId;
                } else {
                    // For regular formats, combine with bestaudio
                    formatSelector = formatId + "+bestaudio/best";
                }
            } else {
                // No specific resolution - use best video+audio
                formatSelector = "bestvideo+bestaudio/best";
            }

            String resolvedUrl = directVideoUrl != null ? directVideoUrl : ytDlpService.resolveDownloadUrl(sourceUrl, formatSelector);

            Map<String, Object> response = new HashMap<>();
            // Only use streaming for videos (to handle HLS streams and merge audio)
            // For images, use direct download URL
            boolean isVideo = media.getType() == com.photomini.model.MediaType.VIDEO;
            boolean needsStreaming = isVideo;
            response.put("downloadUrl", isVideo ? null : resolvedUrl);
            response.put("needsStreaming", needsStreaming);
            response.put("formatId", formatId);

            // Add file size estimate for videos
            if (isVideo && media.getResolutions() != null) {
                for (ResolutionOption res : media.getResolutions()) {
                    if (formatId != null && formatId.equals(res.getFormatId())) {
                        response.put("fileSize", res.getSize());
                        break;
                    }
                }
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to resolve download URL: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to resolve download URL: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Stream media directly from yt-dlp (with .mp4 extension for correct filename)
     * GET /api/media/{mediaId}/stream.mp4?formatId=xxx
     */
    @GetMapping("/media/{mediaId}/stream.mp4")
    public ResponseEntity<?> streamMediaMp4(@PathVariable String mediaId,
                                           @RequestParam(required = false) String formatId) {
        return doStreamMedia(mediaId, formatId);
    }

    /**
     * Stream media directly from yt-dlp
     * GET /api/media/{mediaId}/stream?formatId=xxx
     */
    @GetMapping("/media/{mediaId}/stream")
    public ResponseEntity<?> streamMedia(@PathVariable String mediaId,
                                          @RequestParam(required = false) String formatId) {
        return doStreamMedia(mediaId, formatId);
    }

    private ResponseEntity<?> doStreamMedia(String mediaId, String formatId) {
        MediaInfo media = mediaStore.get(mediaId);
        if (media == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Media not found");
        }

        final String sourceUrlFinal;
        final String formatSelectorFinal;

        try {
            String sourceUrl = media.getSourceUrl();
            if (sourceUrl == null || sourceUrl.isEmpty()) {
                sourceUrl = media.getDownloadUrl();
            }

            // Determine format
            String formatSelector;
            String directVideoUrl = null;

            if (formatId != null && !formatId.isEmpty()) {
                // Check if formatId is a direct video URL (from FxTwitter)
                if (formatId.startsWith("http")) {
                    // This is a direct video URL from FxTwitter - use it directly
                    directVideoUrl = formatId;
                    formatSelector = null;
                } else if (formatId.startsWith("hls-")) {
                    formatSelector = formatId + "+hls-audio-best/best";
                } else {
                    formatSelector = formatId + "+bestaudio/best";
                }
            } else {
                formatSelector = "bestvideo+bestaudio/best";
            }

            sourceUrlFinal = directVideoUrl != null ? directVideoUrl : sourceUrl;
            formatSelectorFinal = formatSelector;

        } catch (Exception e) {
            logger.error("Failed to prepare stream: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to prepare stream: " + e.getMessage());
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            // Set correct Content-Type for video/mp4 so WeChat recognizes it
            headers.setContentType(MediaType.valueOf("video/mp4"));

            // Use .mp4 extension for video
            String filename = "video_" + System.currentTimeMillis() + ".mp4";
            headers.setContentDispositionFormData("attachment", filename);

            Path tempFile = ytDlpService.downloadMedia(sourceUrlFinal, formatSelectorFinal, filename);
            long fileSize = Files.size(tempFile);
            InputStream inputStream = Files.newInputStream(tempFile);

            // Delete temp file after opening stream (file will be deleted when stream is closed)
            Files.deleteIfExists(tempFile);

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(fileSize)
                    .body(new InputStreamResource(inputStream));

        } catch (Exception e) {
            logger.error("Failed to stream media: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Streaming failed: " + e.getMessage());
        }
    }

    /**
     * Download media file
     * GET /api/media/{mediaId}/download
     */
    @GetMapping("/media/{mediaId}/download")
    public ResponseEntity<?> downloadMedia(@PathVariable String mediaId) {
        MediaInfo media = mediaStore.get(mediaId);
        if (media == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Media not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        // Mock implementation - return placeholder response
        // Actual download will be implemented in future iterations
        Map<String, Object> response = new HashMap<>();
        response.put("mediaId", mediaId);
        response.put("filename", media.getFilename());
        response.put("downloadUrl", media.getDownloadUrl());
        response.put("resolution", media.getResolution());
        response.put("type", media.getType().name());
        response.put("message", "Download functionality requires actual yt-dlp integration");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    /**
     * Get history list
     * GET /api/history
     */
    @GetMapping("/history")
    public ResponseEntity<List<HistoryRecord>> getHistory() {
        List<HistoryRecord> history = new ArrayList<>();
        for (String key : historyOrder) {
            HistoryRecord record = historyStore.get(key);
            if (record != null) {
                history.add(record);
            }
        }
        return ResponseEntity.ok(history);
    }

    /**
     * Clear all history
     * DELETE /api/history
     */
    @DeleteMapping("/history")
    public ResponseEntity<Map<String, String>> clearHistory() {
        historyStore.clear();
        historyOrder.clear();
        Map<String, String> response = new HashMap<>();
        response.put("message", "History cleared");
        return ResponseEntity.ok(response);
    }

    /**
     * Normalize URL for deduplication - returns MD5 hash as key
     */
    private String normalizeUrl(String url) {
        if (url == null) return null;
        // Remove trailing slash
        String normalized = url.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        normalized = normalized.toLowerCase();

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(normalized.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // Fallback to hashCode
            return String.valueOf(normalized.hashCode());
        }
    }

    /**
     * Health check endpoint
     * GET /api/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "PhotoMiniProgram");
        return ResponseEntity.ok(response);
    }
}
