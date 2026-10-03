package vn.lucbao.api;

public final class VideoOption {
    public final String id;
    public final int height;
    public final int width;
    public final int fps;
    /** e.g. "avc1.640028", "vp09.00.40.08", "av01.0.08M.08" */
    public final String codec;
    /** e.g. "video/mp4", "video/webm" */
    public final String mime;
    public final int bitrate;
    /** true when the stream has no audio and must be merged with an AudioOption */
    public final boolean videoOnly;

    public VideoOption(String id, int height, int width, int fps, String codec, String mime,
                       int bitrate, boolean videoOnly) {
        this.id = id;
        this.height = height;
        this.width = width;
        this.fps = fps;
        this.codec = codec;
        this.mime = mime;
        this.bitrate = bitrate;
        this.videoOnly = videoOnly;
    }
}
