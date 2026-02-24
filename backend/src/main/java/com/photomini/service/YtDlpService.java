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
import java.util.LinkedHashMap;
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

    @Value("${yt-dlp.youtube-cookies-file:}")
    private String youtubeCookiesFile;

    // Store parsed data from FxTwitter API for fallback results
    private String lastThumbnailUrl;
    private List<ResolutionOption> lastResolutions;

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

            // Add cookies if configured - use platform-specific cookies
            String platform = detectPlatform(url);
            String cookiesToUse = null;

            if ("YouTube".equals(platform) && youtubeCookiesFile != null && !youtubeCookiesFile.isEmpty()) {
                cookiesToUse = youtubeCookiesFile;
            } else if (cookiesFile != null && !cookiesFile.isEmpty()) {
                cookiesToUse = cookiesFile;
            }

            if (cookiesToUse != null) {
                command.add("--cookies");
                command.add(cookiesToUse);
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

            // Fallback: try to extract embedded media for Twitter URLs
            if (isTwitterUrl(url)) {
                String embeddedUrl = extractEmbeddedMedia(url);
                if (embeddedUrl != null) {
                    // Check if it's already a direct video URL (from FxTwitter)
                    if (embeddedUrl.contains("video.twimg.com")) {
                        logger.info("Found direct video URL from FxTwitter: {}", embeddedUrl);
                        // Create a result with the direct URL
                        result = new ParseResult();
                        result.setSuccess(true);
                        result.setPlatform("Twitter");
                        result.setSessionId(UUID.randomUUID().toString());

                        MediaInfo media = new MediaInfo();
                        media.setId(result.getSessionId() + "_0");
                        media.setType(MediaType.VIDEO);
                        media.setThumbnailUrl(lastThumbnailUrl); // Use thumbnail from FxTwitter API
                        media.setResolutions(lastResolutions); // Use formats from FxTwitter

                        // Set default resolution to highest quality
                        if (lastResolutions != null && !lastResolutions.isEmpty()) {
                            media.setDefaultResolution(lastResolutions.get(0).getLabel());
                            // Use the best format URL as download URL
                            media.setDownloadUrl(lastResolutions.get(0).getFormatId());
                            // Set width/height from resolution
                            String label = lastResolutions.get(0).getLabel();
                            int height = parseHeight(label);
                            media.setHeight(height);
                            media.setWidth((int) (height * 16.0 / 9)); // Assume 16:9 aspect ratio
                            media.setResolution(media.getWidth() + "x" + height);
                        } else {
                            media.setDownloadUrl(embeddedUrl);
                        }

                        result.setTitle("[Twitter Video]");

                        List<MediaInfo> mediaList = new ArrayList<>();
                        mediaList.add(media);
                        result.setMediaList(mediaList);

                        return result;
                    } else {
                        // It's an external URL (YouTube/TikTok) - parse it
                        logger.info("Found external embed URL, trying to parse: {}", embeddedUrl);
                        ParseResult embeddedResult = parseUrl(embeddedUrl);
                        if (embeddedResult.isSuccess()) {
                            embeddedResult.setPlatform("Twitter");
                            if (embeddedResult.getTitle() != null) {
                                embeddedResult.setTitle("[Twitter嵌入] " + embeddedResult.getTitle());
                            }
                            return embeddedResult;
                        } else {
                            logger.warn("Failed to parse embedded media: {}", embeddedResult.getErrorMessage());
                        }
                    }
                }
            }

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
                        } else if (formatInfo.has("tbr") && !formatInfo.get("tbr").isNull() && entry.has("duration")) {
                            // Estimate from bitrate: size = bitrate (kbps) * duration (seconds) / 8
                            double tbr = formatInfo.get("tbr").asDouble();
                            double duration = entry.get("duration").asDouble();
                            totalSize = (long)(tbr * 1000 * duration / 8);
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

                        // For now, add all options - we'll deduplicate after
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

            // Deduplicate resolutions - keep only the best quality (largest size) for each resolution
            if (!resolutions.isEmpty()) {
                java.util.Map<String, ResolutionOption> bestByResolution = new java.util.LinkedHashMap<>();
                for (ResolutionOption opt : resolutions) {
                    String label = opt.getLabel();
                    String existingSize = bestByResolution.get(label) != null ? bestByResolution.get(label).getSize() : null;
                    // Compare sizes - larger size = better quality
                    if (!bestByResolution.containsKey(label) ||
                        (opt.getSize() != null && (existingSize == null || opt.getSize().compareTo(existingSize) > 0))) {
                        bestByResolution.put(label, opt);
                    }
                }
                // Convert back to list sorted by resolution
                java.util.List<ResolutionOption> deduped = new java.util.ArrayList<>(bestByResolution.values());
                // Sort by resolution (height)
                deduped.sort((a, b) -> {
                    int heightA = Integer.parseInt(a.getLabel().replace("p", ""));
                    int heightB = Integer.parseInt(b.getLabel().replace("p", ""));
                    return heightB - heightA; // Descending
                });
                resolutions = deduped;
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
     * Check if URL is a Twitter/X URL
     */
    private boolean isTwitterUrl(String url) {
        return url != null && (url.contains("twitter.com") || url.contains("x.com"));
    }

    /**
     * Extract embedded external media URLs from Twitter page
     * Uses FxTwitter API as fallback when yt-dlp fails
     *
     * @param twitterUrl the Twitter URL to extract from
     * @return the extracted external URL (YouTube/TikTok) or null if not found
     */
    private String extractEmbeddedMedia(String twitterUrl) {
        lastThumbnailUrl = null; // Reset
        lastResolutions = null;
        try {
            // Extract tweet ID from URL
            String tweetId = extractTweetId(twitterUrl);
            if (tweetId == null) {
                logger.warn("Could not extract tweet ID from URL: {}", twitterUrl);
                return null;
            }

            // Use FxTwitter API to get tweet data
            String apiUrl = "https://api.fxtwitter.com/status/" + tweetId;
            logger.info("Trying FxTwitter API: {}", apiUrl);

            List<String> command = new ArrayList<>();
            command.add("/usr/bin/curl");
            command.add("-s");
            command.add("-L");
            command.add("--max-time");
            command.add("30");
            command.add(apiUrl);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }

            String jsonResponse = output.toString();

            // Use Jackson ObjectMapper to parse the JSON properly
            try {
                JsonNode rootNode = objectMapper.readTree(jsonResponse);
                JsonNode tweetNode = rootNode.path("tweet");
                JsonNode mediaNode = tweetNode.path("media").path("all").get(0);

                if (mediaNode.isMissingNode()) {
                    logger.warn("No media found in FxTwitter response");
                    return null;
                }

                // Extract thumbnail URL
                JsonNode thumbNode = mediaNode.path("thumbnail_url");
                if (!thumbNode.isMissingNode()) {
                    lastThumbnailUrl = thumbNode.asText();
                    logger.info("Found thumbnail URL: {}", lastThumbnailUrl);
                }

                // Extract formats from the media node
                JsonNode formatsNode = mediaNode.path("formats");
                JsonNode durationNode = mediaNode.path("duration");
                double videoDuration = durationNode.isMissingNode() ? 0 : durationNode.asDouble();

                lastResolutions = new ArrayList<>();
                java.util.Map<String, ResolutionOption> resolutionMap = new java.util.LinkedHashMap<>();
                String bestFormatId = null;
                int bestBitrate = 0;

                if (formatsNode.isArray()) {
                    for (JsonNode formatNode : formatsNode) {
                        JsonNode urlNode = formatNode.path("url");
                        JsonNode bitrateNode = formatNode.path("bitrate");

                        if (urlNode.isMissingNode() || bitrateNode.isMissingNode()) {
                            continue;
                        }

                        String formatUrl = urlNode.asText();
                        int bitrate = bitrateNode.asInt();

                        // Skip m3u8 streams
                        if (formatUrl.contains(".m3u8")) {
                            continue;
                        }

                        // Extract resolution from URL
                        String resolution = "unknown";
                        java.util.regex.Pattern resPattern = java.util.regex.Pattern.compile("/(\\d+)x(\\d+)/");
                        java.util.regex.Matcher resMatcher = resPattern.matcher(formatUrl);
                        if (resMatcher.find()) {
                            resolution = resMatcher.group(2) + "p";
                        }

                        // Calculate file size: bitrate (bps) * duration (seconds) / 8 = bytes
                        // FxTwitter bitrate is in bps, duration is in seconds
                        long estimatedSize = 0;
                        if (videoDuration > 0 && bitrate > 0) {
                            estimatedSize = (long) ((long) bitrate * videoDuration / 8.0);
                        }
                        String size = estimatedSize > 0 ? formatFileSize(estimatedSize) : "Unknown";

                        // Only keep best bitrate for each resolution
                        ResolutionOption existing = resolutionMap.get(resolution);
                        if (existing == null) {
                            ResolutionOption option = new ResolutionOption();
                            option.setId("fxtwitter_" + resolution);
                            option.setFormatId(formatUrl);
                            option.setLabel(resolution);
                            option.setSize(size);
                            resolutionMap.put(resolution, option);
                            logger.info("Found format: {} {} {} (duration: {}s, bitrate: {})", resolution, size, formatUrl, videoDuration, bitrate);
                        }

                        // Track best quality overall
                        if (bitrate > bestBitrate) {
                            bestBitrate = bitrate;
                            bestFormatId = formatUrl;
                        }
                    }
                }

                // Convert map to list and sort by resolution
                lastResolutions = new ArrayList<>(resolutionMap.values());
                if (!lastResolutions.isEmpty()) {
                    lastResolutions.sort((a, b) -> {
                        int heightA = parseHeight(a.getLabel());
                        int heightB = parseHeight(b.getLabel());
                        return heightB - heightA;
                    });

                    // Try to get actual file size from the video URL
                    for (ResolutionOption option : lastResolutions) {
                        try {
                            String actualSize = getActualFileSize(option.getFormatId());
                            if (actualSize != null) {
                                option.setSize(actualSize);
                                logger.info("Got actual file size for {}: {}", option.getLabel(), actualSize);
                            }
                        } catch (Exception e) {
                            logger.warn("Could not get file size for {}: {}", option.getLabel(), e.getMessage());
                        }
                    }

                    String videoUrl = bestFormatId;
                    logger.info("Found Twitter video via FxTwitter: {} with {} formats", videoUrl, lastResolutions.size());
                    return videoUrl;
                }

            } catch (Exception e) {
                logger.error("Error parsing FxTwitter JSON: {}", e.getMessage());
            }

            // Fallback: try regex for any video URL
            if (jsonResponse.contains("\"url\":\"https://video.twimg.com")) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "\"url\":\"(https://video.twimg.com[^\"]+)\"");
                java.util.regex.Matcher m = p.matcher(jsonResponse);
                if (m.find()) {
                    String videoUrl = m.group(1);
                    logger.info("Found Twitter video via FxTwitter: {}", videoUrl);
                    return videoUrl;
                }
            }

            // Check for external embeds (YouTube, TikTok)
            String[] externalPatterns = {
                "youtube\\.com/watch",
                "youtu\\.be/",
                "tiktok\\.com/@"
            };

            for (String pattern : externalPatterns) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "(https?://[^\"]*" + pattern + "[^\"]*)");
                java.util.regex.Matcher m = p.matcher(jsonResponse);
                if (m.find()) {
                    String externalUrl = m.group(1);
                    logger.info("Found external embed via FxTwitter: {}", externalUrl);
                    return externalUrl;
                }
            }

            logger.warn("No media found in FxTwitter response for tweet: {}", tweetId);
            return null;

        } catch (Exception e) {
            logger.error("Error extracting embedded media from Twitter: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract tweet ID from Twitter URL
     */
    private String extractTweetId(String url) {
        try {
            // Match patterns like /status/1234567890123456789
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "/status/(\\d+)");
            java.util.regex.Matcher m = p.matcher(url);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            logger.error("Error extracting tweet ID: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Extract URL matching a pattern from HTML content
     */
    private String extractUrl(String html, String pattern) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(https?://[^\"']*" + pattern + "[^\"']*)",
                java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher m = p.matcher(html);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            logger.error("Error matching pattern {}: {}", pattern, e.getMessage());
        }
        return null;
    }

    /**
     * Normalize YouTube URL to standard format
     */
    private String normalizeYoutubeUrl(String url) {
        // Handle youtu.be short URLs
        if (url.contains("youtu.be/")) {
            String videoId = url.substring(url.lastIndexOf("/") + 1);
            // Remove any query parameters
            if (videoId.contains("?")) {
                videoId = videoId.substring(0, videoId.indexOf("?"));
            }
            return "https://www.youtube.com/watch?v=" + videoId;
        }
        // Handle youtube.com/shorts/ URLs
        if (url.contains("youtube.com/shorts/")) {
            String videoId = url.substring(url.lastIndexOf("/") + 1);
            return "https://www.youtube.com/watch?v=" + videoId;
        }
        // Already in watch format
        return url;
    }

    /**
     * Get actual file size from URL by checking Content-Length header
     */
    private String getActualFileSize(String url) {
        try {
            List<String> command = new ArrayList<>();
            command.add("/usr/bin/curl");
            command.add("-sI");
            command.add("--max-time");
            command.add("30");
            command.add(url);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }

            String response = output.toString();
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?i)content-length:\\s*(\\d+)");
            java.util.regex.Matcher m = p.matcher(response);
            if (m.find()) {
                long size = Long.parseLong(m.group(1));
                return formatFileSize(size);
            }
        } catch (Exception e) {
            logger.debug("Error getting file size: {}", e.getMessage());
        }
        return null;
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
     * Parse height from resolution label (e.g., "720p" -> 720)
     */
    private int parseHeight(String label) {
        if (label == null || label.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(label.replace("p", ""));
        } catch (NumberFormatException e) {
            return 0;
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
