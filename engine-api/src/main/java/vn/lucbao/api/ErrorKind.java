package vn.lucbao.api;

public final class ErrorKind {
    public static final int UNKNOWN = 0;
    public static final int NETWORK = 1;
    /** YouTube changed something: the engine needs an update. */
    public static final int BROKEN = 2;
    public static final int UNAVAILABLE = 3;
    public static final int AGE_RESTRICTED = 4;
    public static final int GEO_BLOCKED = 5;
    public static final int PRIVATE = 6;
    public static final int BOT_CHECK = 7;
    public static final int PAID = 8;
    public static final int NOT_STARTED_YET = 9;

    private ErrorKind() {
    }
}
