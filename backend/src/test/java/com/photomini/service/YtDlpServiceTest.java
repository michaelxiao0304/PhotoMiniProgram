package com.photomini.service;

import com.photomini.model.MediaInfo;
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
    void testServiceCanBeInstantiated() {
        // Verify service can be instantiated
        assertNotNull(ytDlpService);
    }

    @Test
    void testParseUrl_WithInvalidUrl() {
        ReflectionTestUtils.setField(ytDlpService, "ytDlpCommand", "/home/ubuntu/.local/bin/yt-dlp");
        ReflectionTestUtils.setField(ytDlpService, "tempDir", "/tmp");
        ReflectionTestUtils.setField(ytDlpService, "timeoutSeconds", 300);

        // Test with empty URL - should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            ytDlpService.parseUrl("");
        });
    }

    @Test
    void testGeneratePreview() throws Exception {
        ReflectionTestUtils.setField(ytDlpService, "ytDlpCommand", "/home/ubuntu/.local/bin/yt-dlp");
        ReflectionTestUtils.setField(ytDlpService, "tempDir", "/tmp");
        ReflectionTestUtils.setField(ytDlpService, "timeoutSeconds", 300);

        MediaInfo media = new MediaInfo();
        media.setId("test-id-123");

        java.nio.file.Path previewPath = ytDlpService.generatePreview(media);

        assertNotNull(previewPath);
        // Just verify it returns a Path, don't check exact content
        assertNotNull(previewPath.toString());
    }

    @Test
    void testIsYtDlpAvailable() {
        // Set the correct path
        ReflectionTestUtils.setField(ytDlpService, "ytDlpCommand", "/home/ubuntu/.local/bin/yt-dlp");

        boolean available = ytDlpService.isYtDlpAvailable();
        assertTrue(available, "yt-dlp should be available");
    }

    @Test
    void testGetYtDlpCommand() {
        // Set the command path
        ReflectionTestUtils.setField(ytDlpService, "ytDlpCommand", "/home/ubuntu/.local/bin/yt-dlp");

        String command = ytDlpService.getYtDlpCommand();
        assertEquals("/home/ubuntu/.local/bin/yt-dlp", command);
    }
}
