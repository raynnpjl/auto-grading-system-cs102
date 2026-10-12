package grading.submission;

import java.nio.file.Path;
import java.util.List;

/**
 * What a {@link SubmissionCheck} can inspect.
 *
 * @param source    the original submission
 * @param content   the opened folder holding the submitted files
 * @param username  the username the submission was resolved to
 * @param javaFiles every Java source file in the content
 */
public record SubmissionContext(SubmissionSource source, Path content, String username, List<Path> javaFiles) {

    public SubmissionContext {
        javaFiles = List.copyOf(javaFiles);
    }
}
