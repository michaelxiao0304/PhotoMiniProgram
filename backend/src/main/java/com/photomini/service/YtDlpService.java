package com.photomini.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photomini.model.MediaInfo;
import com.photomini.model.MediaType;
import com.photomini.model.ParseResult;
import com.photomini.model.ResolutionOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class YtDlpService {

    private static final Logger logger = LoggerFactory.getLogger(YtDlpService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${yt-dlp.command:/home/ubuntu/.local/bin/yt-dlp}")
    private String ytDlpCommand;

    @Value("${yt-dlp.temp-dir:/tmp/photo-mini-program}")
    private String tempDir;

    @Value("${yt-dlp.timeout-seconds:300}")
    private int timeoutSeconds;

    public YtDlpService() {
        // Default constructor for tests
    }

    /**
     * Initialize the service with configured temp directory
     */
    @PostConstruct
    public void init() {
        try {
            if (tempDir != null) {
                Files.createDirectories(Paths.get(tempDir));
            }
        } catch (IOException e) {
            logger.warn("Failed to create temp directory: {}", e.getMessage());
        }
    }

    /**
     * Parse URL to extract media information using yt-dlp
     *
     * @param url the social media URL to parse
     * @return ParseResult containing parsed media information
     */
    public ParseResult parseUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }

        ParseResult result = new ParseResult();
        result.setSuccess(false);
        result.setPlatform(detectPlatform(url));
        result.setSessionId(UUID.randomUUID().toString());

        try {
            // Create temp directory for this session
            String sessionDir = tempDir + File.separator + result.getSessionId();
            Files.createDirectories(Paths.get(sessionDir));

            // Execute yt-dlp with --dump-json to get media info
            List<String> command = new ArrayList<>();
            command.add(ytDlpCommand);
            command.add("--dump-json");
            command.add("--no-download");
            command.add("--no-playlist");
            command.add(url);

            String jsonOutput = executeCommand(command, timeoutSeconds);
            if (jsonOutput == null || jsonOutput.trim().isEmpty()) {
                result.setErrorMessage("Failed to parse media info");
                return result;
            }

            // Parse JSON output
            JsonNode mediaJson = objectMapper.readTree(jsonOutput);

            // Extract basic info
            String title = mediaJson.has("title") ? mediaJson.get("title").asText() : "Untitled";
            result.setTitle(title);

            // Check if it's a playlist or single video
            if (mediaJson.has("entries")) {
                // It's a playlist, process entries
                List<MediaInfo> mediaList = new ArrayList<>();
                JsonNode entries = mediaJson.get("entries");
                for (int i = 0; i < entries.size(); i++) {
                    JsonNode entry = entries.get(i);
                    MediaInfo media = parseMediaEntry(entry, result.getSessionId(), i);
                    if (media != null) {
                        mediaList.add(media);
                    }
                }
                result.setMediaList(mediaList);
            } else {
                // Single media
                MediaInfo media = parseMediaEntry(mediaJson, result.getSessionId(), 0);
                List<MediaInfo> mediaList = new ArrayList<>();
                if (media != null) {
                    mediaList.add(media);
                }
                result.setMediaList(mediaList);
            }

            result.setSuccess(true);

        } catch (Exception e) {
            logger.error("Error parsing URL: {}", e.getMessage(), e);
            result.setErrorMessage("解析失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * Parse a single media entry from JSON
     */
    private MediaInfo parseMediaEntry(JsonNode entry, String sessionId, int index) {
        if (entry == null) {
            return null;
        }

        MediaInfo media = new MediaInfo();
        media.setId(sessionId + "_" + index);

        // Determine media type
        String format = entry.has("format") ? entry.get("format").asText() : "";
        String formatNote = entry.has("format_note") ? entry.get("format_note").asText() : "";

        if (format.contains("video") || entry.has("thumbnail")) {
            // Has video stream
            if (format.contains("audio") || formatNote.contains("audio only")) {
                media.setType(MediaType.AUDIO);
            } else {
                media.setType(MediaType.VIDEO);
            }
        } else if (entry.has("url") && (format.contains("audio") || formatNote.contains("audio"))) {
            media.setType(MediaType.AUDIO);
        } else {
            // Default to video if has thumbnail
            media.setType(MediaType.VIDEO);
        }

        // Set filename
        String filename = entry.has("filename") ? entry.get("filename").asText() :
                         (entry.has("id") ? entry.get("id").asText() : "media_" + index);
        media.setFilename(filename);

        // Set thumbnail URL
        if (entry.has("thumbnail")) {
            media.setThumbnailUrl(entry.get("thumbnail").asText());
        } else if (entry.has("thumbnails") && entry.get("thumbnails").isArray() &&
                   entry.get("thumbnails").size() > 0) {
            media.setThumbnailUrl(entry.get("thumbnails").get(0).get("url").asText());
        }

        // Set resolution info
        if (entry.has("width") && !entry.get("width").isNull()) {
            media.setWidth(entry.get("width").asInt());
        }
        if (entry.has("height") && !entry.get("height").isNull()) {
            media.setHeight(entry.get("height").asInt());
        }
        if (media.getWidth() != null && media.getHeight() != null) {
            media.setResolution(media.getWidth() + "x" + media.getHeight());
        }

        // Handle video formats (multiple quality options)
        if (media.getType() == MediaType.VIDEO) {
            List<ResolutionOption> resolutions = new ArrayList<>();
            String bestFormatId = null;
            String bestResolution = null;

            if (entry.has("formats")) {
                JsonNode formats = entry.get("formats");
                for (JsonNode formatInfo : formats) {
                    if (formatInfo.has("vcodec") && !formatInfo.get("vcodec").asText().equals("none")) {
                        ResolutionOption option = new ResolutionOption();
                        option.setId(media.getId() + "_" + formatInfo.get("format_id").asText());

                        String res = "";
                        if (formatInfo.has("height") && !formatInfo.get("height").isNull()) {
                            res = formatInfo.get("height").asText() + "p";
                        }
                        option.setLabel(res);

                        if (formatInfo.has("filesize") && !formatInfo.get("filesize").isNull()) {
                            long size = formatInfo.get("filesize").asLong();
                            option.setSize(formatFileSize(size));
                        } else if (formatInfo.has("filesize_approx") && !formatInfo.get("filesize_approx").isNull()) {
                            long size = formatInfo.get("filesize_approx").asLong();
                            option.setSize(formatFileSize(size));
                        }

                        if (!res.isEmpty()) {
                            resolutions.add(option);

                            // Track best quality
                            if (bestFormatId == null ||
                                (formatInfo.has("height") && !formatInfo.get("height").isNull() &&
                                 (bestResolution == null || formatInfo.get("height").asInt() > Integer.parseInt(bestResolution.replace("p", ""))))) {
                                bestFormatId = formatInfo.get("format_id").asText();
                                bestResolution = res;
                            }
                        }
                    }
                }
            }

            // If no formats found, create a default option
            if (resolutions.isEmpty() && media.getResolution() != null) {
                ResolutionOption option = new ResolutionOption();
                option.setId(media.getId() + "_best");
                option.setLabel(media.getResolution());
                option.setSize("Unknown");
                resolutions.add(option);
                bestResolution = media.getResolution();
            }

            media.setResolutions(resolutions);
            media.setDefaultResolution(bestResolution != null ? bestResolution : "best");
        } else {
            // For images, set direct download URL
            if (entry.has("url")) {
                media.setDownloadUrl(entry.get("url").asText());
            }
        }

        return media;
    }

    /**
     * Generate preview for the media using yt-dlp
     */
    public Path generatePreview(MediaInfo media) throws Exception {
        String previewDir = tempDir + File.separator + "preview";
        Files.createDirectories(Paths.get(previewDir));

        String previewPath = previewDir + File.separator + media.getId() + ".jpg";

        // If we already have a thumbnail URL, just return that path info
        if (media.getThumbnailUrl() != null && !media.getThumbnailUrl().isEmpty()) {
            // Download thumbnail to local file
            List<String> command = new ArrayList<>();
            command.add(ytDlpCommand);
            command.add("--output");
            command.add(previewPath);
            command.add("--skip-download");
            command.add("--write-thumbnail");
            command.add("--convert-thumbnails");
            command.add("jpg");
            command.add(media.getThumbnailUrl());

            try {
                executeCommand(command, 60);
            } catch (Exception e) {
                logger.warn("Failed to generate preview: {}", e.getMessage());
            }
        }

        return Paths.get(previewPath);
    }

    /**
     * Get media stream for download
     */
    public InputStream getMediaStream(String url, String formatId) throws Exception {
        String outputDir = tempDir + File.separator + "download";
        Files.createDirectories(Paths.get(outputDir));

        String outputTemplate = outputDir + File.separator + "%(title)s.%(ext)s";

        List<String> command = new ArrayList<>();
        command.add(ytDlpCommand);
        command.add("-o");
        command.add(outputTemplate);
        command.add("-f");
        command.add(formatId != null ? formatId : "best");
        command.add("-r");
        command.add("1M");  // Rate limit to prevent memory issues
        command.add("--no-part");
        command.add("--no-cache-dir");
        command.add("-g");  // Get direct URL without downloading
        command.add(url);

        String directUrl = executeCommand(command, timeoutSeconds);
        if (directUrl == null || directUrl.trim().isEmpty()) {
            throw new Exception("Failed to get direct download URL");
        }

        // Return the direct URL as stream (the actual download will be done by the client)
        // For streaming, we return a StringBufferInputStream of the URL
        return new ByteArrayInputStream(directUrl.trim().getBytes());
    }

    /**
     * Download media to a temp file and return the path
     */
    public Path downloadMedia(String url, String formatId, String filename) throws Exception {
        String downloadDir = tempDir + File.separator + "download";
        Files.createDirectories(Paths.get(downloadDir));

        String outputPath = downloadDir + File.separator + filename;

        List<String> command = new ArrayList<>();
        command.add(ytDlpCommand);
        command.add("-o");
        command.add(outputPath);
        if (formatId != null && !formatId.isEmpty()) {
            command.add("-f");
            command.add(formatId);
        }
        command.add("--no-part");
        command.add("--no-cache-dir");
        command.add(url);

        executeCommand(command, timeoutSeconds);

        return Paths.get(outputPath);
    }

    /**
     * Execute yt-dlp command with given arguments
     */
    private String executeCommand(List<String> args, int timeout) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(args);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        // Read output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        // Wait for process to complete with timeout
        boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("Command execution timeout after " + timeout + " seconds");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String errorMsg = output.toString();
            if (errorMsg.contains("HTTP Error 429")) {
                throw new Exception("Rate limited by server. Please try again later.");
            } else if (errorMsg.contains("Unable to extract")) {
                throw new Exception("Unable to parse media. The URL may be private or unavailable.");
            } else if (errorMsg.contains("Video unavailable")) {
                throw new Exception("Video is unavailable.");
            }
            throw new Exception("Command failed with exit code: " + exitCode);
        }

        return output.toString();
    }

    /**
     * Detect platform from URL
     */
    private String detectPlatform(String url) {
        if (url == null) {
            return "Unknown";
        }
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
     * Format file size to human readable string
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1fMB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * Get yt-dlp command path
     */
    public String getYtDlpCommand() {
        return ytDlpCommand;
    }

    /**
     * Check if yt-dlp is available
     */
    public boolean isYtDlpAvailable() {
        try {
            List<String> command = new ArrayList<>();
            command.add(ytDlpCommand);
            command.add("--version");
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
