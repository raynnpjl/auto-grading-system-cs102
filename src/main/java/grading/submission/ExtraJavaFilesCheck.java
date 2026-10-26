package grading.submission;

import grading.assessment.AssessmentDefinition;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Reports Java files the assessment does not ask for; they are left out of grading. */
public final class ExtraJavaFilesCheck implements SubmissionCheck {

    @Override
    public void inspect(SubmissionContext submission, List<SubmissionFinding> findings) {
        Set<String> required = AssessmentDefinition.sourceFiles().keySet().stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        long extra = submission.javaFiles().stream()
                .map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                .filter(name -> !required.contains(name))
                .count();
        if (extra > 0) {
            findings.add(SubmissionFinding.info("EXTRA_JAVA_FILES",
                    "Found " + extra + " additional Java file(s); they were left out of the normalized copy."));
        }
    }
}
