package grading.scoring;

import java.util.Locale;

/** Formats marks the same way everywhere they are shown: one decimal place, such as 8.5. */
public final class Marks {

    private Marks() {
    }

    public static String format(double marks) {
        return String.format(Locale.ROOT, "%.1f", marks);
    }
}
