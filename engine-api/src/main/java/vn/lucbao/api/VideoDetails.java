package vn.lucbao.api;

import java.util.List;

public final class VideoDetails {
    public final String url;
    public final String title;
    public final String channel;
    public final String channelUrl;
    public final String channelAvatar;
    /** -1 when unknown */
    public final long subscribers;
    public final long views;
    public final long likes;
    public final String uploaded;
    /** plain text */
    public final String description;
    public final long duration;
    public final String thumbnail;
    public final boolean live;
    public final List<VideoOption> videoOptions;
    public final List<AudioOption> audioOptions;
    public final List<VideoItem> related;

    public VideoDetails(String url, String title, String channel, String channelUrl,
                        String channelAvatar, long subscribers, long views, long likes,
                        String uploaded, String description, long duration, String thumbnail,
                        boolean live, List<VideoOption> videoOptions,
                        List<AudioOption> audioOptions, List<VideoItem> related) {
        this.url = url;
        this.title = title;
        this.channel = channel;
        this.channelUrl = channelUrl;
        this.channelAvatar = channelAvatar;
        this.subscribers = subscribers;
        this.views = views;
        this.likes = likes;
        this.uploaded = uploaded;
        this.description = description;
        this.duration = duration;
        this.thumbnail = thumbnail;
        this.live = live;
        this.videoOptions = videoOptions;
        this.audioOptions = audioOptions;
        this.related = related;
    }
}
