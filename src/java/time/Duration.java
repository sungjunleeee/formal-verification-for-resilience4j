package java.time;

public class Duration {
    private final long millis;

    private Duration(long millis) {
        this.millis = millis;
    }

    public static Duration ofSeconds(long seconds) {
        return new Duration(seconds * 1000);
    }

    public static Duration ofMillis(long millis) {
        return new Duration(millis);
    }

    public long toMillis() {
        return millis;
    }
}
