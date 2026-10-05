package vn.lucbao.engine;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs;
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException;
import org.schabi.newpipe.extractor.feed.FeedExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException;
import org.schabi.newpipe.extractor.exceptions.PaidContentException;
import org.schabi.newpipe.extractor.exceptions.PrivateContentException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubePostLiveStreamDvrDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import vn.lucbao.api.AudioOption;
import vn.lucbao.api.Engine;
import vn.lucbao.api.ErrorKind;
import vn.lucbao.api.Feed;
import vn.lucbao.api.HttpPlan;
import vn.lucbao.api.Kiosk;
import vn.lucbao.api.Playback;
import vn.lucbao.api.Track;
import vn.lucbao.api.VideoDetails;
import vn.lucbao.api.VideoItem;
import vn.lucbao.api.VideoOption;

/**
 * The YouTube engine, backed by NewPipeExtractor. Loaded by the app through a
 * DexClassLoader using the class name {@code vn.lucbao.engine.EngineImpl}.
 *
 * Only stable extractor APIs are referenced directly; parts that change between
 * extractor versions are accessed defensively so the automatic update pipeline
 * keeps compiling.
 */
public final class EngineImpl implements Engine {
    private static final String WEB_ORIGIN = "https://www.youtube.com";
    private static final byte[] POST_BODY = new byte[]{0x78, 0};
    private static final String DEPRECATED_TRENDING = "Trending";

    private final StreamingService yt = ServiceList.YouTube;
    private final AtomicLong requestNumber = new AtomicLong();
    private Localization localization = Localization.DEFAULT;

    /** pageToken -> extractor Page */
    private final Map<String, Page> pages = Collections.synchronizedMap(
            new LinkedHashMap<String, Page>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, Page> e) {
                    return size() > 300;
                }
            });

    /** url -> StreamInfo */
    private final Map<String, StreamInfo> infos = Collections.synchronizedMap(
            new LinkedHashMap<String, StreamInfo>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, StreamInfo> e) {
                    return size() > 24;
                }
            });

    public EngineImpl() {
    }

    @Override
    public int apiVersion() {
        return API_VERSION;
    }

    @Override
    public String describe() {
        return "NewPipeExtractor " + BuildConfig.EXTRACTOR_VERSION;
    }

    @Override
    public void init(final Object androidContext, final String languageTag,
                     final String countryCode) {
        final String lang = languageTag == null || languageTag.isEmpty() ? "vi" : languageTag;
        final String country = countryCode == null || countryCode.isEmpty() ? "VN" : countryCode;
        localization = new Localization(lang, country);
        NewPipe.init(new HttpDownloader(), localization, new ContentCountry(country));
    }

    // ------------------------------------------------------------------ browse

    @Override
    public List<Kiosk> kiosks() throws Exception {
        final KioskList list = yt.getKioskList();
        final String[] preferred = {"trending_music", "trending_gaming",
                "trending_movies_and_shows", "trending_podcasts_episodes", "live"};
        final List<Kiosk> out = new ArrayList<>();
        final List<String> available = new ArrayList<>(list.getAvailableKiosks());
        for (final String id : preferred) {
            if (available.remove(id)) {
                out.add(new Kiosk(id, kioskName(id)));
            }
        }
        for (final String id : available) {
            if (!DEPRECATED_TRENDING.equals(id)) {
                out.add(new Kiosk(id, kioskName(id)));
            }
        }
        return out;
    }

    private static String kioskName(final String id) {
        switch (id) {
            case "trending_music":
                return "Âm nhạc";
            case "trending_gaming":
                return "Trò chơi";
            case "trending_movies_and_shows":
                return "Phim & Trailer";
            case "trending_podcasts_episodes":
                return "Podcast";
            case "live":
                return "Trực tiếp";
            case DEPRECATED_TRENDING:
                return "Thịnh hành";
            default:
                return id.replace('_', ' ');
        }
    }

    /** Prefix for {@link #kiosk} ids that ask for a channel's latest uploads. */
    private static final String FEED_PREFIX = "feed:";

    @Override
    public Feed kiosk(final String kioskId, final String pageToken) throws Exception {
        if (kioskId.startsWith(FEED_PREFIX)) {
            return channelFeed(kioskId.substring(FEED_PREFIX.length()));
        }
        final KioskList list = yt.getKioskList();
        final ListExtractor.InfoItemsPage<StreamInfoItem> page;
        if (pageToken == null) {
            final KioskExtractor<StreamInfoItem> ex = list.getExtractorById(kioskId, null);
            ex.fetchPage();
            page = ex.getInitialPage();
        } else {
            final Page p = pages.get(pageToken);
            if (p == null) {
                return new Feed(Collections.emptyList(), null, null);
            }
            final KioskExtractor<StreamInfoItem> ex = list.getExtractorById(kioskId, p);
            page = ex.getPage(p);
        }
        return toFeed(page.getItems(), page.getNextPage(), null);
    }

    /**
     * Latest uploads of a channel (used for following channels without an account).
     * Upload dates are returned as ISO-8601 instants in {@link VideoItem#uploaded} so the
     * app can sort videos from many channels by date.
     */
    private Feed channelFeed(final String channelUrl) throws Exception {
        final List<StreamInfoItem> items = new ArrayList<>();
        try {
            final FeedExtractor feed = yt.getFeedExtractor(channelUrl);
            if (feed != null) {
                feed.fetchPage();
                items.addAll(feed.getInitialPage().getItems());
            }
        } catch (final Exception e) {
            items.clear();
        }
        if (items.isEmpty()) {
            final ChannelInfo channel = ChannelInfo.getInfo(yt, channelUrl);
            for (final ListLinkHandler tab : channel.getTabs()) {
                if (tab.getContentFilters().contains(ChannelTabs.VIDEOS)) {
                    final ChannelTabInfo tabInfo = ChannelTabInfo.getInfo(yt, tab);
                    for (final InfoItem item : tabInfo.getRelatedItems()) {
                        if (item instanceof StreamInfoItem) {
                            items.add((StreamInfoItem) item);
                        }
                    }
                    break;
                }
            }
        }
        final List<VideoItem> out = new ArrayList<>();
        for (final StreamInfoItem s : items) {
            final VideoItem v = toItem(s);
            String uploaded = v.uploaded;
            try {
                if (s.getUploadDate() != null) {
                    uploaded = s.getUploadDate().getInstant().toString();
                }
            } catch (final Throwable ignored) {
                // keep the textual date
            }
            out.add(new VideoItem(v.url, v.title, v.channel, v.channelUrl, v.thumbnail,
                    v.duration, v.views, uploaded, v.live, v.shortVideo));
        }
        return new Feed(out, null, null);
    }

    @Override
    public Feed search(final String query, final String pageToken) throws Exception {
        final SearchQueryHandler qh = yt.getSearchQHFactory().fromQuery(query,
                Collections.singletonList("videos"), "");
        if (pageToken == null) {
            final SearchExtractor ex = yt.getSearchExtractor(qh);
            ex.fetchPage();
            final ListExtractor.InfoItemsPage<InfoItem> page = ex.getInitialPage();
            String suggestion = null;
            try {
                suggestion = ex.getSearchSuggestion();
            } catch (final Exception ignored) {
                // optional
            }
            return toFeed(page.getItems(), page.getNextPage(), suggestion);
        }
        final Page p = pages.get(pageToken);
        if (p == null) {
            return new Feed(Collections.emptyList(), null, null);
        }
        final ListExtractor.InfoItemsPage<InfoItem> page = SearchInfo.getMoreItems(yt, qh, p);
        return toFeed(page.getItems(), page.getNextPage(), null);
    }

    @Override
    public List<String> suggestions(final String query) throws Exception {
        return new ArrayList<>(yt.getSuggestionExtractor().suggestionList(query));
    }

    private Feed toFeed(final List<? extends InfoItem> items, final Page next,
                        final String suggestion) {
        final List<VideoItem> out = new ArrayList<>();
        for (final InfoItem item : items) {
            if (item instanceof StreamInfoItem) {
                out.add(toItem((StreamInfoItem) item));
            }
        }
        String token = null;
        if (Page.isValid(next)) {
            token = UUID.randomUUID().toString();
            pages.put(token, next);
        }
        return new Feed(out, token, suggestion == null || suggestion.isEmpty() ? null : suggestion);
    }

    private static VideoItem toItem(final StreamInfoItem s) {
        final StreamType type = s.getStreamType();
        final boolean live = type == StreamType.LIVE_STREAM || type == StreamType.AUDIO_LIVE_STREAM;
        boolean isShort = false;
        try {
            isShort = s.isShortFormContent();
        } catch (final Throwable ignored) {
            // optional
        }
        return new VideoItem(s.getUrl(), s.getName(), s.getUploaderName(), s.getUploaderUrl(),
                pickImage(s.getThumbnails(), 360), s.getDuration(), s.getViewCount(),
                s.getTextualUploadDate(), live, isShort);
    }

    // ------------------------------------------------------------------ video

    @Override
    public VideoDetails details(final String url) throws Exception {
        final StreamInfo info = StreamInfo.getInfo(yt, url);
        infos.put(url, info);
        infos.put(info.getUrl(), info);

        final StreamType type = info.getStreamType();
        final boolean live = type == StreamType.LIVE_STREAM || type == StreamType.AUDIO_LIVE_STREAM;

        final List<VideoOption> videos = new ArrayList<>();
        for (final VideoStream v : allVideoStreams(info)) {
            if (!isPlayableDelivery(v)) {
                continue;
            }
            videos.add(new VideoOption(videoId(v), v.getHeight(), v.getWidth(), v.getFps(),
                    v.getCodec(), mime(v, "video/mp4"), v.getBitrate(), v.isVideoOnly()));
        }

        final List<AudioOption> audios = new ArrayList<>();
        for (final AudioStream a : info.getAudioStreams()) {
            if (!isPlayableDelivery(a)) {
                continue;
            }
            final AudioTrackType tt = a.getAudioTrackType();
            final int bitrate = a.getAverageBitrate() > 0 ? a.getAverageBitrate() * 1000
                    : a.getBitrate();
            audios.add(new AudioOption(audioId(a), bitrate, a.getCodec(), mime(a, "audio/mp4"),
                    a.getAudioTrackName(), tt == null || tt == AudioTrackType.ORIGINAL));
        }

        final List<VideoItem> related = new ArrayList<>();
        for (final InfoItem item : info.getRelatedItems()) {
            if (item instanceof StreamInfoItem) {
                related.add(toItem((StreamInfoItem) item));
            }
        }

        return new VideoDetails(info.getUrl(), info.getName(), info.getUploaderName(),
                info.getUploaderUrl(), pickImage(info.getUploaderAvatars(), 88),
                info.getUploaderSubscriberCount(), info.getViewCount(), info.getLikeCount(),
                info.getTextualUploadDate(), plainDescription(info.getDescription()),
                info.getDuration(), pickImage(info.getThumbnails(), 720), live,
                videos, audios, related);
    }

    @Override
    public Playback resolve(final String url, final String videoOptionId,
                            final String audioOptionId) throws Exception {
        StreamInfo info = infos.get(url);
        if (info == null) {
            details(url);
            info = infos.get(url);
        }
        if (info == null) {
            throw new ExtractionException("Could not load stream info");
        }

        final StreamType type = info.getStreamType();
        final boolean live = type == StreamType.LIVE_STREAM || type == StreamType.AUDIO_LIVE_STREAM;
        if (live) {
            final String hls = info.getHlsUrl();
            if (hls != null && !hls.isEmpty()) {
                return new Playback(Collections.singletonList(
                        new Track(Track.KIND_HLS, hls, null, "application/x-mpegURL")));
            }
            final String dash = info.getDashMpdUrl();
            if (dash != null && !dash.isEmpty()) {
                throw new ExtractionException("Live DASH is not supported");
            }
        }

        final List<Track> tracks = new ArrayList<>();
        VideoStream video = null;
        if (videoOptionId != null) {
            for (final VideoStream v : allVideoStreams(info)) {
                if (videoOptionId.equals(videoId(v))) {
                    video = v;
                    break;
                }
            }
            if (video == null) {
                throw new ExtractionException("Video option not found: " + videoOptionId);
            }
            tracks.add(toTrack(info, video));
            if (!video.isVideoOnly()) {
                return new Playback(tracks);
            }
        }

        AudioStream audio = null;
        if (audioOptionId != null) {
            for (final AudioStream a : info.getAudioStreams()) {
                if (audioOptionId.equals(audioId(a))) {
                    audio = a;
                    break;
                }
            }
        }
        if (audio == null) {
            audio = bestAudio(info.getAudioStreams());
        }
        if (audio == null) {
            throw new ExtractionException("No audio stream");
        }
        tracks.add(toTrack(info, audio));
        return new Playback(tracks);
    }

    private static AudioStream bestAudio(final List<AudioStream> list) {
        AudioStream best = null;
        int bestScore = Integer.MIN_VALUE;
        for (final AudioStream a : list) {
            if (!isPlayableDelivery(a)) {
                continue;
            }
            final AudioTrackType tt = a.getAudioTrackType();
            int score = Math.max(a.getAverageBitrate(), a.getBitrate() / 1000);
            if (tt == null || tt == AudioTrackType.ORIGINAL) {
                score += 100_000;
            }
            if (score > bestScore) {
                bestScore = score;
                best = a;
            }
        }
        return best;
    }

    private static List<VideoStream> allVideoStreams(final StreamInfo info) {
        final List<VideoStream> all = new ArrayList<>(info.getVideoOnlyStreams());
        all.addAll(info.getVideoStreams());
        return all;
    }

    private static boolean isPlayableDelivery(final Stream s) {
        final DeliveryMethod m = s.getDeliveryMethod();
        return m == DeliveryMethod.PROGRESSIVE_HTTP || m == DeliveryMethod.DASH
                || m == DeliveryMethod.HLS;
    }

    private static String videoId(final VideoStream v) {
        return (v.isVideoOnly() ? "o" : "m") + v.getItag() + "-" + v.getResolution()
                + "-" + v.getDeliveryMethod().name();
    }

    private static String audioId(final AudioStream a) {
        final String track = a.getAudioTrackId() == null ? "" : a.getAudioTrackId();
        return "a" + a.getItag() + "-" + track + "-" + a.getDeliveryMethod().name();
    }

    private static String mime(final Stream s, final String fallback) {
        return s.getFormat() != null && s.getFormat().getMimeType() != null
                ? s.getFormat().getMimeType() : fallback;
    }

    private static Track toTrack(final StreamInfo info, final Stream s) throws Exception {
        final String content = s.getContent();
        final String mime = mime(s, s instanceof AudioStream ? "audio/mp4" : "video/mp4");
        final ItagItem itag = s.getItagItem();
        final long duration = info.getDuration();
        switch (s.getDeliveryMethod()) {
            case HLS:
                return new Track(Track.KIND_HLS, content, null, mime);
            case DASH:
                if (itag == null) {
                    throw new ExtractionException("Missing itag for DASH stream");
                }
                if (info.getStreamType() == StreamType.POST_LIVE_STREAM) {
                    return new Track(Track.KIND_DASH, content,
                            YoutubePostLiveStreamDvrDashManifestCreator
                                    .fromPostLiveStreamDvrStreamingUrl(content, itag,
                                            itag.getTargetDurationSec(), duration), mime);
                }
                return new Track(Track.KIND_DASH, content,
                        YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(content, itag,
                                duration), mime);
            case PROGRESSIVE_HTTP:
            default:
                final boolean separate = s instanceof AudioStream
                        || (s instanceof VideoStream && ((VideoStream) s).isVideoOnly());
                if (separate && itag != null) {
                    try {
                        return new Track(Track.KIND_DASH, content,
                                YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(
                                        content, itag, duration), mime);
                    } catch (final RuntimeException ignored) {
                        // fall back to plain progressive playback
                    }
                }
                return new Track(Track.KIND_PROGRESSIVE, content, null, mime);
        }
    }

    // ------------------------------------------------------------------ http shaping

    @Override
    public HttpPlan shapeRequest(final String url, final long position, final long length,
                                 final int kind) {
        String requestUrl = url;
        final boolean playback = isVideoPlayback(url);
        final boolean rangeParam = kind == Track.KIND_DASH;
        final boolean rnParam = kind == Track.KIND_DASH || kind == Track.KIND_PROGRESSIVE;

        if (playback && rnParam && !requestUrl.contains("&rn=")) {
            requestUrl += "&rn=" + requestNumber.getAndIncrement();
        }
        boolean rangeInUrl = false;
        if (playback && rangeParam && !(position == 0 && length < 0)) {
            requestUrl += "&range=" + position + "-" + (length >= 0 ? (position + length - 1) : "");
            rangeInUrl = true;
        }

        final Map<String, String> headers = new HashMap<>();
        final String client = clientOf(requestUrl);
        if (client.startsWith("WEB") || client.equals("MWEB")) {
            headers.put("Origin", WEB_ORIGIN);
            headers.put("Referer", WEB_ORIGIN);
            headers.put("Sec-Fetch-Dest", "empty");
            headers.put("Sec-Fetch-Mode", "cors");
            headers.put("Sec-Fetch-Site", "cross-site");
        }
        headers.put("TE", "trailers");
        headers.put("User-Agent", userAgentFor(client));

        if (playback) {
            return new HttpPlan(requestUrl, "POST", headers, POST_BODY, rangeInUrl);
        }
        return new HttpPlan(requestUrl, "GET", headers, null, false);
    }

    private static boolean isVideoPlayback(final String url) {
        try {
            final String path = new URI(url).getPath();
            return path != null && path.startsWith("/videoplayback");
        } catch (final Exception e) {
            return url.contains("/videoplayback");
        }
    }

    /** Value of the {@code c=} query parameter (the YouTube client), upper case. */
    private static String clientOf(final String url) {
        final int q = url.indexOf('?');
        if (q < 0) {
            return "";
        }
        for (final String part : url.substring(q + 1).split("&")) {
            if (part.startsWith("c=")) {
                return part.substring(2).toUpperCase(Locale.ROOT);
            }
        }
        return "";
    }

    /** Uses the extractor's own user agent for the client when it offers one. */
    private String userAgentFor(final String client) {
        final String method;
        switch (client) {
            case "ANDROID":
            case "ANDROID_VR":
                method = "getAndroidUserAgent";
                break;
            case "IOS":
                method = "getIosUserAgent";
                break;
            case "VISIONOS":
                method = "getVisionOsUserAgent";
                break;
            default:
                return HttpDownloader.USER_AGENT;
        }
        try {
            final Class<?> helper = Class.forName(
                    "org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper");
            final Method m = helper.getMethod(method, Localization.class);
            final Object ua = m.invoke(null, localization);
            if (ua instanceof String) {
                return (String) ua;
            }
        } catch (final Throwable ignored) {
            // helper not present in this extractor version
        }
        return HttpDownloader.USER_AGENT;
    }

    // ------------------------------------------------------------------ errors

    @Override
    public int classifyError(final Throwable error) {
        Throwable t = error;
        for (int depth = 0; t != null && depth < 8; depth++, t = t.getCause()) {
            if (t instanceof AgeRestrictedContentException) {
                return ErrorKind.AGE_RESTRICTED;
            }
            if (t instanceof GeographicRestrictionException
                    || t instanceof UnsupportedContentInCountryException) {
                return ErrorKind.GEO_BLOCKED;
            }
            if (t instanceof PrivateContentException) {
                return ErrorKind.PRIVATE;
            }
            if (t instanceof PaidContentException) {
                return ErrorKind.PAID;
            }
            if (t instanceof ReCaptchaException
                    || t.getClass().getSimpleName().equals("SignInConfirmNotBotException")) {
                return ErrorKind.BOT_CHECK;
            }
            if (t instanceof ContentNotAvailableException) {
                final String msg = String.valueOf(t.getMessage()).toLowerCase(Locale.ROOT);
                if (msg.contains("premiere") || msg.contains("will begin")
                        || msg.contains("upcoming")) {
                    return ErrorKind.NOT_STARTED_YET;
                }
                return ErrorKind.UNAVAILABLE;
            }
            if (t instanceof java.net.UnknownHostException
                    || t instanceof java.net.SocketTimeoutException
                    || t instanceof java.net.ConnectException
                    || t instanceof javax.net.ssl.SSLException) {
                return ErrorKind.NETWORK;
            }
            if (t instanceof ExtractionException) {
                return ErrorKind.BROKEN;
            }
        }
        if (error instanceof IOException) {
            return ErrorKind.NETWORK;
        }
        return error instanceof RuntimeException ? ErrorKind.BROKEN : ErrorKind.UNKNOWN;
    }

    // ------------------------------------------------------------------ helpers

    /** Picks the smallest image at least {@code targetHeight} tall, else the largest. */
    private static String pickImage(final List<Image> images, final int targetHeight) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        Image best = null;
        Image largest = null;
        for (final Image img : images) {
            final int h = img.getHeight();
            if (largest == null || h > largest.getHeight()) {
                largest = img;
            }
            if (h >= targetHeight && (best == null || h < best.getHeight())) {
                best = img;
            }
        }
        if (best != null) {
            return best.getUrl();
        }
        if (largest != null && largest.getHeight() > 0) {
            return largest.getUrl();
        }
        return images.get(images.size() - 1).getUrl();
    }

    /** Works with both the old (getContent) and new (record content()) Description API. */
    private static String plainDescription(final Object description) {
        if (description == null) {
            return "";
        }
        String raw = null;
        for (final String name : new String[]{"getContent", "content"}) {
            try {
                final Object v = description.getClass().getMethod(name).invoke(description);
                if (v instanceof String) {
                    raw = (String) v;
                    break;
                }
            } catch (final Throwable ignored) {
                // try next
            }
        }
        if (raw == null) {
            return "";
        }
        String s = raw.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("<[^>]+>", "");
        s = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");
        return s.trim();
    }
}
