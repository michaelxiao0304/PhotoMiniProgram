package com.photomini.controller;

import com.photomini.dto.ParseRequest;
import com.photomini.model.MediaInfo;
import com.photomini.model.ParseResult;
import com.photomini.service.YtDlpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MediaController {

    private static final Logger logger = LoggerFactory.getLogger(MediaController.class);

    @Autowired
    private YtDlpService ytDlpService;

    // Simple in-memory storage for media info (for mock implementation)
    private final Map<String, MediaInfo> mediaStore = new HashMap<>();

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

            String resolvedUrl;
            String formatSelector = formatId;

            if (formatId != null && !formatId.isEmpty()) {
                // Specific resolution selected - use format ID with best audio
                formatSelector = formatId + "+bestaudio/best";
            } else {
                // No specific resolution - use best video+audio
                formatSelector = "bestvideo+bestaudio/best";
            }

            resolvedUrl = ytDlpService.resolveDownloadUrl(sourceUrl, formatSelector);

            Map<String, Object> response = new HashMap<>();
            response.put("downloadUrl", resolvedUrl);
            response.put("formatId", formatId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to resolve download URL: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to resolve download URL: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
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
