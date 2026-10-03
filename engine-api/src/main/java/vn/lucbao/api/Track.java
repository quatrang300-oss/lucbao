package vn.lucbao.api;

public final class Track {
    /** {@link #manifest} holds a DASH MPD document, {@link #uri} is its base URL. */
    public static final int KIND_DASH = 1;
    /** {@link #uri} is a plain media file. */
    public static final int KIND_PROGRESSIVE = 2;
    /** {@link #uri} is an HLS playlist. */
    public static final int KIND_HLS = 3;

    public final int kind;
    public final String uri;
    /** only for KIND_DASH */
    public final String manifest;
    public final String mime;

    public Track(int kind, String uri, String manifest, String mime) {
        this.kind = kind;
        this.uri = uri;
        this.manifest = manifest;
        this.mime = mime;
    }
}
