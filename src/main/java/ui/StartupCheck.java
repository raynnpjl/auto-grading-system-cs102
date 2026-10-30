package ui;

import grading.assessment.AssessmentDefinition;
import grading.config.GradingSettings;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Loads the settings before the window is built, so a mistake in {@code config/grading.properties} or
 * {@code config/assessment.properties} is shown to the marker instead of stopping with a stack trace.
 */
final class StartupCheck {

    private static final String UNKNOWN_PROBLEM =
            "The settings could not be loaded. Check the files in the config folder.";

    private StartupCheck() {
    }

    /** Why the settings cannot be used, or empty when they load. */
    static Optional<String> settingsProblem() {
        try {
            GradingSettings.current();
            AssessmentDefinition.QUESTIONS.size();
            return Optional.empty();
        } catch (ExceptionInInitializerError error) {
            return Optional.of(messageOf(error.getCause()));
        } catch (IllegalStateException | IllegalArgumentException | UncheckedIOException exception) {
            return Optional.of(messageOf(exception));
        } catch (NoClassDefFoundError error) {
            // Thrown on later use of a class whose settings already failed to load.
            return Optional.of(UNKNOWN_PROBLEM);
        }
    }

    private static String messageOf(Throwable problem) {
        return problem == null || problem.getMessage() == null ? UNKNOWN_PROBLEM : problem.getMessage();
    }
}
