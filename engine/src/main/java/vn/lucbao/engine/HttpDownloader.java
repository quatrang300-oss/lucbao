package vn.lucbao.engine;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Minimal {@link Downloader} built on HttpURLConnection (no extra libraries needed). */
final class HttpDownloader extends Downloader {
    static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";

    @Override
    public Response execute(final Request request) throws IOException, ReCaptchaException {
        final String url = request.url();
        final HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(30_000);
            c.setReadTimeout(30_000);
            c.setInstanceFollowRedirects(true);
            c.setRequestMethod(request.httpMethod());
            c.setRequestProperty("User-Agent", USER_AGENT);

            final Map<String, List<String>> headers = request.headers();
            if (headers != null) {
                for (final Map.Entry<String, List<String>> h : headers.entrySet()) {
                    if (h.getKey() == null || h.getValue() == null || h.getValue().isEmpty()) {
                        continue;
                    }
                    boolean first = true;
                    for (final String v : h.getValue()) {
                        if (first) {
                            c.setRequestProperty(h.getKey(), v);
                            first = false;
                        } else {
                            c.addRequestProperty(h.getKey(), v);
                        }
                    }
                }
            }

            final byte[] body = request.dataToSend();
            if (body != null) {
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(body.length);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body);
                }
            }

            final int code = c.getResponseCode();
            if (code == 429) {
                throw new ReCaptchaException("reCaptcha Challenge requested", url);
            }

            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = "";
            if (in != null) {
                if ("gzip".equalsIgnoreCase(c.getContentEncoding())) {
                    in = new GZIPInputStream(in);
                }
                try (InputStream stream = in) {
                    text = readAll(stream);
                }
            }

            final Map<String, List<String>> responseHeaders = new HashMap<>();
            for (final Map.Entry<String, List<String>> h : c.getHeaderFields().entrySet()) {
                if (h.getKey() != null) {
                    responseHeaders.put(h.getKey(), new ArrayList<>(h.getValue()));
                }
            }
            return new Response(code, c.getResponseMessage(), responseHeaders, text,
                    c.getURL().toString());
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(final InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
