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

    @Value("${yt-dlp.cookies-file:}")
    private String cookiesFile;

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
            command.add("--no-warnings");
            command.add("-q");

            // Add cookies if configured
            if (cookiesFile != null && !cookiesFile.isEmpty()) {
                command.add("--cookies");
                command.add(cookiesFile);
            }

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
        String vcodec = entry.has("vcodec") ? entry.get("vcodec").asText() : "none";
        String acodec = entry.has("acodec") ? entry.get("acodec").asText() : "none";

        // Check if it's video (has video codec and not "none")
        boolean hasVideo = vcodec != null && !vcodec.equals("none");
        // Check if it has separate audio (not audio-only)
        boolean hasSeparateAudio = acodec != null && !acodec.equals("none");

        if (hasVideo) {
            // Has video stream - it's a VIDEO (even if it also has audio)
            media.setType(MediaType.VIDEO);
        } else if (hasSeparateAudio || formatNote.contains("audio only") || format.contains("audio only")) {
            // No video, has audio only - it's AUDIO
            media.setType(MediaType.AUDIO);
        } else if (entry.has("thumbnail")) {
            // Has thumbnail but no video/audio info - assume VIDEO
            media.setType(MediaType.VIDEO);
        } else {
            // Default to VIDEO
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
                    // Only include formats that have video codec
                    if (formatInfo.has("vcodec") && !formatInfo.get("vcodec").asText().equals("none")) {
                        String formatId = formatInfo.get("format_id").asText();

                        // Skip if no height info (pure audio or invalid)
                        if (!formatInfo.has("height") || formatInfo.get("height").isNull()) {
                            continue;
                        }

                        ResolutionOption option = new ResolutionOption();
                        option.setId(media.getId() + "_" + formatId);
                        option.setFormatId(formatId);  // Store format ID for later use

                        String res = formatInfo.get("height").asText() + "p";
                        option.setLabel(res);

                        // Calculate combined file size (video + audio if available)
                        long totalSize = 0;
                        if (formatInfo.has("filesize") && !formatInfo.get("filesize").isNull()) {
                            totalSize = formatInfo.get("filesize").asLong();
                        } else if (formatInfo.has("filesize_approx") && !formatInfo.get("filesize_approx").isNull()) {
                            totalSize = formatInfo.get("filesize_approx").asLong();
                        }
                        // Check if audio is in separate stream (need to add audio size)
                        if (formatInfo.has("acodec") && !formatInfo.get("acodec").asText().equals("none")) {
                            // Has embedded audio
                        } else if (formatInfo.has("audio_filesize") && !formatInfo.get("audio_filesize").isNull()) {
                            // Add separate audio stream size estimate
                            totalSize += formatInfo.get("audio_filesize").asLong();
                        }
                        if (totalSize > 0) {
                            option.setSize(formatFileSize(totalSize));
                        }

                        resolutions.add(option);

                        // Track best quality (video+audio combined)
                        if (bestFormatId == null ||
                            (bestResolution == null || formatInfo.get("height").asInt() > Integer.parseInt(bestResolution.replace("p", "")))) {
                            bestFormatId = formatId;
                            bestResolution = res;
                        }
                    }
                }
            }

            // If no formats found, create a default option
            if (resolutions.isEmpty() && media.getResolution() != null) {
                ResolutionOption option = new ResolutionOption();
                option.setId(media.getId() + "_best");
                option.setLabel(media.getResolution());
                // Don't set size if unknown
                resolutions.add(option);
                bestResolution = media.getResolution();
            }

            media.setResolutions(resolutions);
            media.setDefaultResolution(bestResolution != null ? bestResolution : "best");

            // For videos, use bestvideo+bestaudio format to ensure audio is included
            // Store format selector instead of direct URL - it will be resolved when needed
            if (bestFormatId != null) {
                media.setDownloadUrl("FORMAT:" + bestFormatId);
            }
        } else {
            // For images, set direct download URL
            if (entry.has("url")) {
                media.setDownloadUrl(entry.get("url").asText());
            }
        }

        return media;
    }

    /**
     * Generate preview for the media - downloads thumbnail and returns as byte array
     */
    public byte[] generatePreview(String thumbnailUrl) throws Exception {
        if (thumbnailUrl == null || thumbnailUrl.isEmpty()) {
            return null;
        }

        // Use curl to download the image directly
        List<String> command = new ArrayList<>();
        command.add("/usr/bin/curl");
        command.add("-s");
        command.add("-L");
        command.add("--max-time");
        command.add("30");
        command.add(thumbnailUrl);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = process.getInputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
        }

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return null;
        }

        if (process.exitValue() != 0) {
            logger.warn("Failed to download thumbnail: exit code {}", process.exitValue());
            return null;
        }

        return baos.toByteArray();
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
    public Path downloadMedia(String url, String formatSelector, String filename) throws Exception {
        String downloadDir = tempDir + File.separator + "download";
        Files.createDirectories(Paths.get(downloadDir));

        // Ensure we get MP4 output (for HLS streams)
        String baseFilename = filename;
        if (filename.contains(".")) {
            // Remove existing extension to avoid .mp4.mp4
            baseFilename = filename.substring(0, filename.lastIndexOf('.'));
        }
        String outputPath = downloadDir + File.separator + baseFilename + ".mp4";

        List<String> command = new ArrayList<>();
        command.add(ytDlpCommand);
        command.add("-o");
        command.add(outputPath);
        if (formatSelector != null && !formatSelector.isEmpty()) {
            command.add("-f");
            command.add(formatSelector);
        }
        command.add("--merge-output-format");
        command.add("mp4");
        command.add("--no-part");
        command.add("--no-cache-dir");
        command.add(url);

        executeCommand(command, timeoutSeconds);

        return Paths.get(outputPath);
    }

    /**
     * Stream video directly from yt-dlp to output stream
     * Used for HLS streams that need to be merged
     */
    public void streamMedia(String url, String formatId, OutputStream out) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(ytDlpCommand);
        command.add("-o");
        command.add("-");  // Output to stdout
        command.add("-f");

        if (formatId != null && !formatId.isEmpty()) {
            command.add(formatId);
        } else {
            command.add("bestvideo+bestaudio/best");
        }

        command.add("--no-part");
        command.add("--no-cache-dir");
        command.add("--merge-output-format");
        command.add("mp4");
        command.add(url);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        Process process = pb.start();

        // Copy process output to stream
        try (InputStream is = process.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("Download timeout");
        }

        if (process.exitValue() != 0) {
            throw new Exception("Download failed with exit code: " + process.exitValue());
        }
    }

    /**
     * Resolve download URL using yt-dlp format selector
     * @param originalUrl the original media URL
     * @param formatSelector yt-dlp format selector (e.g., "best", "bestvideo+bestaudio", "137+140")
     * @return the resolved direct download URL
     */
    public String resolveDownloadUrl(String originalUrl, String formatSelector) throws Exception {
        // For video, use bestvideo+bestaudio to ensure audio is included
        // Exclude HLS streams (protocol!=http_hls) to get direct MP4/WebM URLs
        String format;
        if (formatSelector == null || formatSelector.isEmpty() || formatSelector.equals("best")) {
            format = "bestvideo[protocol!=http_hls]+bestaudio[protocol!=http_hls]/best[protocol!=http_hls]";
        } else if (formatSelector.matches("\\d+")) {
            // It's a format ID, combine with best audio but exclude HLS
            format = formatSelector + "+bestaudio[protocol!=http_hls]/best[protocol!=http_hls]";
        } else {
            format = formatSelector;
        }

        List<String> command = new ArrayList<>();
        command.add(ytDlpCommand);
        command.add("-g");  // Get direct URL
        command.add("-f");
        command.add(format);
        command.add(originalUrl);

        String result = executeCommand(command, 60);
        if (result == null || result.trim().isEmpty()) {
            throw new Exception("Failed to resolve download URL");
        }

        // yt-dlp may return multiple URLs (video+audio), take the first one
        String[] urls = result.trim().split("\n");
        return urls[0].trim();
    }

    /**
     * Execute yt-dlp command with given arguments
     */
    private String executeCommand(List<String> args, int timeout) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(args);
        processBuilder.redirectErrorStream(false);

        Process process = processBuilder.start();

        // Read stdout (JSON output)
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        // Read stderr (warnings)
        StringBuilder errorOutput = new StringBuilder();
        try (BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
                logger.warn("yt-dlp: {}", line);
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
            String errorMsg = output.toString() + errorOutput.toString();
            if (errorMsg.contains("HTTP Error 429")) {
                throw new Exception("Rate limited by server. Please try again later.");
            } else if (errorMsg.contains("Unable to extract")) {
                throw new Exception("Unable to parse media. The URL may be private or unavailable.");
            } else if (errorMsg.contains("Video unavailable")) {
                throw new Exception("Video is unavailable.");
            } else if (errorMsg.contains("login required") || errorMsg.contains("Login")) {
                throw new Exception("Login required. Please provide Instagram cookies.");
            }
            throw new Exception("Command failed with exit code: " + exitCode + " - " + errorMsg.substring(0, Math.min(200, errorMsg.length())));
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
