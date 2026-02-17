package com.photomini.controller;

import com.photomini.dto.ParseRequest;
import com.photomini.model.MediaInfo;
import com.photomini.model.ParseResult;
import com.photomini.service.YtDlpService;
import jakarta.validation.Valid;
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

            // Store media info for later retrieval by ID
            if (result.getMediaList() != null) {
                for (MediaInfo media : result.getMediaList()) {
                    mediaStore.put(media.getId(), media);
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
     * Get preview image for media
     * GET /api/media/{mediaId}/preview
     */
    @GetMapping("/media/{mediaId}/preview")
    public ResponseEntity<?> getPreview(@PathVariable String mediaId) {
        MediaInfo media = mediaStore.get(mediaId);
        if (media == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Media not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        // Generate preview using yt-dlp
        try {
            java.nio.file.Path previewPath = ytDlpService.generatePreview(media);
            Map<String, Object> response = new HashMap<>();
            response.put("mediaId", mediaId);
            response.put("previewPath", previewPath.toString());
            response.put("thumbnailUrl", media.getThumbnailUrl());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to generate preview: " + e.getMessage());
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
