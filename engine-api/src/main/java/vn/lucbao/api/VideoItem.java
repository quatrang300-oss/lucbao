package vn.lucbao.api;

public final class VideoItem {
    public final String url;
    public final String title;
    public final String channel;
    public final String channelUrl;
    public final String thumbnail;
    /** seconds, -1 when unknown */
    public final long duration;
    /** -1 when unknown */
    public final long views;
    /** e.g. "3 ngày trước", may be null */
    public final String uploaded;
    public final boolean live;
    public final boolean shortVideo;

    public VideoItem(String url, String title, String channel, String channelUrl,
                     String thumbnail, long duration, long views, String uploaded,
                     boolean live, boolean shortVideo) {
        this.url = url;
        this.title = title;
        this.channel = channel;
        this.channelUrl = channelUrl;
        this.thumbnail = thumbnail;
        this.duration = duration;
        this.views = views;
        this.uploaded = uploaded;
        this.live = live;
        this.shortVideo = shortVideo;
    }
}
