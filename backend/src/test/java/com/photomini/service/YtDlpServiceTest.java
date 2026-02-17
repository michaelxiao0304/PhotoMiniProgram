package com.photomini.service;

import com.photomini.model.MediaInfo;
import com.photomini.model.ParseResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for YtDlpService
 */
@ExtendWith(MockitoExtension.class)
class YtDlpServiceTest {

    @InjectMocks
    private YtDlpService ytDlpService;

    @Test
    void testParseUrl_Success() {
        // Set up test values
        ReflectionTestUtils.setField(ytDlpService, "ytDlpCommand", "yt-dlp");
        ReflectionTestUtils.setField(ytDlpService, "tempDir", "/tmp");
        ReflectionTestUtils.setField(ytDlpService, "timeoutSeconds", 300);

        // Test parsing a valid URL
        String testUrl = "https://www.youtube.com/watch?v=test123";
        ParseResult result = ytDlpService.parseUrl(testUrl);

        // Verify results
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("YouTube", result.getPlatform());
        assertNotNull(result.getSessionId());
        assertNotNull(result.getMediaList());
        assertFalse(result.getMediaList().isEmpty());
    }

    @Test
    void testParseUrl_Instagram() {
        ReflectionTestUtils.setField(ytDlpService, "ytDlpCommand", "yt-dlp");
        ReflectionTestUtils.setField(ytDlpService, "tempDir", "/tmp");
        ReflectionTestUtils.setField(ytDlpService, "timeoutSeconds", 300);

        String testUrl = "https://www.instagram.com/p/test123/";
        ParseResult result = ytDlpService.parseUrl(testUrl);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("Instagram", result.getPlatform());
    }

    @Test
    void testParseUrl_Twitter() {
        ReflectionTestUtils.setField(ytDlpService, "ytDlpCommand", "yt-dlp");
        ReflectionTestUtils.setField(ytDlpService, "tempDir", "/tmp");
        ReflectionTestUtils.setField(ytDlpService, "timeoutSeconds", 300);

        String testUrl = "https://twitter.com/user/status/123456789";
        ParseResult result = ytDlpService.parseUrl(testUrl);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("Twitter", result.getPlatform());
    }

    @Test
    void testParseUrl_TikTok() {
        ReflectionTestUtils.setField(ytDlpService, "ytDlpCommand", "yt-dlp");
        ReflectionTestUtils.setField(ytDlpService, "tempDir", "/tmp");
        ReflectionTestUtils.setField(ytDlpService, "timeoutSeconds", 300);

        String testUrl = "https://www.tiktok.com/@user/video/123456789";
        ParseResult result = ytDlpService.parseUrl(testUrl);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("TikTok", result.getPlatform());
    }

    @Test
    void testParseUrl_Bilibili() {
        ReflectionTestUtils.setField(ytDlpService, "ytDlpCommand", "yt-dlp");
        ReflectionTestUtils.setField(ytDlpService, "tempDir", "/tmp");
        ReflectionTestUtils.setField(ytDlpService, "timeoutSeconds", 300);

        String testUrl = "https://www.bilibili.com/video/BV123456789";
        ParseResult result = ytDlpService.parseUrl(testUrl);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("Bilibili", result.getPlatform());
    }

    @Test
    void testParseUrl_UnknownPlatform() {
        ReflectionTestUtils.setField(ytDlpService, "ytDlpCommand", "yt-dlp");
        ReflectionTestUtils.setField(ytDlpService, "tempDir", "/tmp");
        ReflectionTestUtils.setField(ytDlpService, "timeoutSeconds", 300);

        String testUrl = "https://example.com/video/123";
        ParseResult result = ytDlpService.parseUrl(testUrl);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("Unknown", result.getPlatform());
    }

    @Test
    void testGeneratePreview() {
        ReflectionTestUtils.setField(ytDlpService, "tempDir", "/tmp");

        MediaInfo media = new MediaInfo();
        media.setId("test-id-123");

        String previewPath = ytDlpService.generatePreview(media);

        assertNotNull(previewPath);
        assertTrue(previewPath.contains("preview_test-id-123.jpg"));
    }

    @Test
    void testGetMediaStream() {
        MediaInfo media = new MediaInfo();
        media.setId("test-id-123");

        // Currently returns null as per specification
        java.io.InputStream stream = ytDlpService.getMediaStream(media);

        assertNull(stream);
    }

    @Test
    void testMediaInfoHasRequiredFields() {
        ReflectionTestUtils.setField(ytDlpService, "ytDlpCommand", "yt-dlp");
        ReflectionTestUtils.setField(ytDlpService, "tempDir", "/tmp");
        ReflectionTestUtils.setField(ytDlpService, "timeoutSeconds", 300);

        ParseResult result = ytDlpService.parseUrl("https://www.youtube.com/watch?v=test");

        MediaInfo media = result.getMediaList().get(0);

        assertNotNull(media.getId());
        assertNotNull(media.getType());
        assertNotNull(media.getThumbnailUrl());
        assertNotNull(media.getDownloadUrl());
        assertNotNull(media.getFilename());
        assertNotNull(media.getWidth());
        assertNotNull(media.getHeight());
        assertNotNull(media.getResolution());
    }
}
