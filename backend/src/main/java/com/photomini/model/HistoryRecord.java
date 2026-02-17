package com.photomini.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HistoryRecord {
    private String id;
    private String url;
    private String platform;
    private String title;
    private String thumbnailUrl;
    private int mediaCount;
    private List<String> mediaTypes;
    private long timestamp;

    // Formatted time for display
    public String getTimestamp() {
        if (timestamp <= 0) return "";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd HH:mm");
        return sdf.format(new java.util.Date(timestamp));
    }
}
