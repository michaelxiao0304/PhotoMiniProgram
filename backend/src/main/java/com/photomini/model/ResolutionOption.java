package com.photomini.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResolutionOption {
    private String id;
    private String label;
    private String size;
    private String downloadUrl;
    private String formatId;  // yt-dlp format ID for specific resolution
}
