package com.photomini.model;

import lombok.Data;
import java.util.List;

@Data
public class MediaInfo {
    private String id;
    private MediaType type;
    private String thumbnailUrl;
    private String downloadUrl;
    private String filename;
    private Integer width;
    private Integer height;
    private String resolution;
    private List<ResolutionOption> resolutions;
    private String defaultResolution;
}

@Data
class ResolutionOption {
    private String id;
    private String label;
    private String size;
}
