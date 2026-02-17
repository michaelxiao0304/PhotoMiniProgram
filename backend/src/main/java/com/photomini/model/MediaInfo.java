package com.photomini.model;

import lombok.Data;
import java.util.List;

@Data
public class MediaInfo {
    private String id;
    private MediaType type;
    private String thumbnailUrl;
    private String downloadUrl;
    private String sourceUrl;  // Original URL for resolving download URL
    private String filename;
    private Integer width;
    private Integer height;
    private String resolution;
    private List<ResolutionOption> resolutions;
    private String defaultResolution;
}
