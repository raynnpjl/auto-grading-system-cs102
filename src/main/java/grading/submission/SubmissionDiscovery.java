package grading.submission;

import grading.assessment.AssessmentDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Works out whether an imported path is one student's submission or a folder of many, so a marker
 * can import either.
 */
final class SubmissionDiscovery {

    private final UsernameResolver usernames;

    SubmissionDiscovery(UsernameResolver usernames) {
        this.usernames = usernames;
    }

    List<Path> discover(Path input, Path workspace) throws IOException {
        if (Files.isRegularFile(input) || hasSubmissionContentAtTopLevel(input)) {
            return List.of(input);
        }

        Optional<String> inputName = usernames.candidate(input);
        if (inputName.isPresent() && !usernames.isGeneric(inputName.get())
                && hasRedundantWrapper(input, inputName.get())) {
            return List.of(input);
        }

        Path normalizedWorkspace = workspace.toAbsolutePath().normalize();
        try (Stream<Path> children = Files.list(input)) {
            List<Path> candidates = children
                    .filter(path -> !path.toAbsolutePath().normalize().startsWith(normalizedWorkspace))
                    .filter(path -> Files.isDirectory(path) || FileSearch.isZip(path))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
            return candidates.isEmpty() ? List.of(input) : candidates;
        }
    }

    // Question folders or Java files directly inside mean the input is a single submission.
    private boolean hasSubmissionContentAtTopLevel(Path directory) throws IOException {
        for (String folder : AssessmentDefinition.questionFolders()) {
            if (FileSearch.hasChildFolder(directory, folder)) {
                return true;
            }
        }
        try (Stream<Path> children = Files.list(directory)) {
            return children.anyMatch(path -> Files.isRegularFile(path) && FileSearch.isJavaSource(path));
        }
    }

    // A student folder that wraps a second folder with the same name, or the template folder.
    private boolean hasRedundantWrapper(Path directory, String username) throws IOException {
        return FileSearch.hasChildFolder(directory, username)
                || (UsernameResolver.TRAILING_YEAR.matcher(username).find()
                        && FileSearch.hasChildFolder(directory, AssessmentDefinition.templateFolder()));
    }
}
