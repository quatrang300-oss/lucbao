package vn.lucbao.api;

import java.util.List;

public final class Feed {
    public final List<VideoItem> items;
    /** null when there is no further page. */
    public final String nextPageToken;
    /** "Did you mean" suggestion for searches, may be null. */
    public final String suggestion;

    public Feed(List<VideoItem> items, String nextPageToken, String suggestion) {
        this.items = items;
        this.nextPageToken = nextPageToken;
        this.suggestion = suggestion;
    }
}
