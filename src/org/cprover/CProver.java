package org.cprover;

public class CProver {
    public static boolean nondetBoolean() {
        return false;
    }

    public static int nondetInt() {
        return 0;
    }

    public static void assume(boolean condition) {
        // no-op for compilation
    }
}
