package vn.lucbao.api;

public final class AudioOption {
    public final String id;
    public final int bitrate;
    public final String codec;
    public final String mime;
    /** display name of the audio track, may be null */
    public final String trackName;
    /** true for the original (not dubbed / descriptive) track */
    public final boolean original;

    public AudioOption(String id, int bitrate, String codec, String mime, String trackName,
                       boolean original) {
        this.id = id;
        this.bitrate = bitrate;
        this.codec = codec;
        this.mime = mime;
        this.trackName = trackName;
        this.original = original;
    }
}
