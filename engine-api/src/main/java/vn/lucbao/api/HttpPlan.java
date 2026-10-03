package vn.lucbao.api;

import java.util.Map;

public final class HttpPlan {
    public final String url;
    /** "GET" or "POST" */
    public final String method;
    public final Map<String, String> headers;
    /** may be null */
    public final byte[] body;
    /**
     * true when the byte range is already encoded in {@link #url}; the app must
     * then request from position 0 of the response instead of sending a Range header.
     */
    public final boolean rangeInUrl;

    public HttpPlan(String url, String method, Map<String, String> headers, byte[] body,
                    boolean rangeInUrl) {
        this.url = url;
        this.method = method;
        this.headers = headers;
        this.body = body;
        this.rangeInUrl = rangeInUrl;
    }
}
