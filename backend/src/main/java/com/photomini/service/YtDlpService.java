package com.photomini.service;

import com.photomini.model.MediaInfo;
import com.photomini.model.MediaType;
import com.photomini.model.ParseResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class YtDlpService {

    @Value("${yt-dlp.command:yt-dlp}")
    private String ytDlpCommand;

    @Value("${yt-dlp.temp-dir:/tmp}")
    private String tempDir;

    @Value("${yt-dlp.timeout-seconds:300}")
    private int timeoutSeconds;

    /**
     * Parse URL to extract media information
     * Currently returns mock data for demonstration purposes
     *
     * @param url the social media URL to parse
     * @return ParseResult containing parsed media information
     */
    public ParseResult parseUrl(String url) {
        ParseResult result = new ParseResult();
        result.setSuccess(true);
        result.setPlatform(detectPlatform(url));
        result.setSessionId(UUID.randomUUID().toString());
        result.setTitle("Sample Title");

        // Mock implementation - returns sample data
        // Actual yt-dlp integration will be implemented in future iterations
        List<MediaInfo> mediaList = new ArrayList<>();

        MediaInfo media = new MediaInfo();
        media.setId(UUID.randomUUID().toString());
        media.setType(MediaType.VIDEO);
        media.setThumbnailUrl("https://example.com/thumbnail.jpg");
        media.setDownloadUrl(url);
        media.setFilename("sample_video.mp4");
        media.setWidth(1920);
        media.setHeight(1080);
        media.setResolution("1920x1080");
        media.setDefaultResolution("1080p");

        mediaList.add(media);
        result.setMediaList(mediaList);

        return result;
    }

    /**
     * Generate preview for the media
     * Currently returns a temporary file path
     *
     * @param media the media info
     * @return path to the preview file
     */
    public String generatePreview(MediaInfo media) {
        // Mock implementation - returns temp file path
        // Actual preview generation will be implemented in future iterations
        String previewPath = tempDir + File.separator + "preview_" + media.getId() + ".jpg";
        return previewPath;
    }

    /**
     * Get media stream for download
     * Currently returns null
     *
     * @param media the media info
     * @return input stream of the media, or null
     */
    public java.io.InputStream getMediaStream(MediaInfo media) {
        // Mock implementation - returns null
        // Actual stream retrieval will be implemented in future iterations
        return null;
    }

    /**
     * Detect platform from URL
     *
     * @param url the social media URL
     * @return platform name
     */
    private String detectPlatform(String url) {
        if (url.contains("instagram.com")) {
            return "Instagram";
        } else if (url.contains("twitter.com") || url.contains("x.com")) {
            return "Twitter";
        } else if (url.contains("tiktok.com")) {
            return "TikTok";
        } else if (url.contains("youtube.com") || url.contains("youtu.be")) {
            return "YouTube";
        } else if (url.contains("bilibili.com")) {
            return "Bilibili";
        }
        return "Unknown";
    }

    /**
     * Execute yt-dlp command with given arguments
     *
     * @param args command arguments
     * @return command output
     * @throws Exception if command execution fails
     */
    private String executeCommand(List<String> args) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(args);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        // Read output
        StringBuilder output = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        // Wait for process to complete with timeout
        boolean finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("Command execution timeout");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new Exception("Command failed with exit code: " + exitCode);
        }

        return output.toString();
    }
}
