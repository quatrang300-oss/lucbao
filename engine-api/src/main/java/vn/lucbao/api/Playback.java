package vn.lucbao.api;

import java.util.List;

/** One or more tracks that must be played together (e.g. video-only + audio). */
public final class Playback {
    public final List<Track> tracks;

    public Playback(List<Track> tracks) {
        this.tracks = tracks;
    }
}
