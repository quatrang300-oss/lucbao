package vn.lucbao.api;

import java.util.List;

/**
 * Contract between the Lục Bảo app and its hot-swappable YouTube engine.
 * The engine is shipped as a separate signed APK that the app loads with a
 * DexClassLoader, so it can be updated silently when YouTube changes.
 *
 * Never change existing methods: bump {@link #API_VERSION} instead.
 */
public interface Engine {
    int API_VERSION = 1;

    /** Must return {@link #API_VERSION} of the api the engine was compiled against. */
    int apiVersion();

    /** Human readable description, e.g. "NewPipeExtractor v0.26.5". */
    String describe();

    /**
     * Called once on a background thread before any other call.
     *
     * @param androidContext an android.content.Context (application context)
     * @param languageTag    e.g. "vi"
     * @param countryCode    e.g. "VN"
     */
    void init(Object androidContext, String languageTag, String countryCode) throws Exception;

    /** Content categories offered on the home screen. */
    List<Kiosk> kiosks() throws Exception;

    /** @param pageToken null for the first page, else {@link Feed#nextPageToken}. */
    Feed kiosk(String kioskId, String pageToken) throws Exception;

    Feed search(String query, String pageToken) throws Exception;

    List<String> suggestions(String query) throws Exception;

    VideoDetails details(String url) throws Exception;

    /**
     * Builds what the player needs for the chosen options.
     *
     * @param videoOptionId null for audio only
     * @param audioOptionId null to let the engine choose
     */
    Playback resolve(String url, String videoOptionId, String audioOptionId) throws Exception;

    /**
     * Tells the app's HTTP layer how to request a byte range of a media URL
     * (headers, method, query parameters YouTube expects).
     *
     * @param length -1 when unbounded
     * @param kind   one of the Track.KIND_* constants
     */
    HttpPlan shapeRequest(String url, long position, long length, int kind);

    /** Maps an exception thrown by this engine to one of the ErrorKind constants. */
    int classifyError(Throwable error);
}
