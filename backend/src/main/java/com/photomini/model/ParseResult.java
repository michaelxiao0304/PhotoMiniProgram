package com.photomini.model;

import lombok.Data;
import java.util.List;

@Data
public class ParseResult {
    private boolean success;
    private String platform;
    private List<MediaInfo> mediaList;
    private String title;
    private String errorMessage;
    private String sessionId;
}
