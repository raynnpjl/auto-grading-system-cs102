package grading.submission;

import grading.assessment.AssessmentDefinition;
import grading.assessment.QuestionFiles;
import grading.common.FileTrees;
import grading.common.Messages;
import grading.config.GradingSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validates and normalizes student submissions without changing their originals. Each submission
 * is opened, its owner resolved, its arrangement checked, and its required files copied into a
 * normalized copy laid out as the assessment expects.
 */
public final class SubmissionPreparationService {

    // Unmodified copies of the submitted sources, kept beside the normalized copies for style checks.
    private static final String ORIGINALS_FOLDER = ".originals";

    private final ZipExtractor zipExtractor;
    private final UsernameResolver usernames = new UsernameResolver();
    private final SubmissionDiscovery discovery = new SubmissionDiscovery(usernames);
    private final StudentHeaders headers;
    private final List<SubmissionCheck> checks;
    private final long maxJavaFileBytes;

    public SubmissionPreparationService() {
        this(GradingSettings.current());
    }

    public SubmissionPreparationService(GradingSettings settings) {
        this(settings, List.of(new UsernameFolderCheck(), new QuestionFolderCheck(), new ExtraJavaFilesCheck()));
    }

    /** Uses the given checks of each submission's arrangement, in order. */
    public SubmissionPreparationService(GradingSettings settings, List<SubmissionCheck> checks) {
        this.zipExtractor = new ZipExtractor(settings.maxZipBytes(), settings.maxZipEntries());
        this.headers = new StudentHeaders(new IdentitySuggestions(settings.emailDomain()), settings.maxJavaFileBytes());
        this.checks = List.copyOf(checks);
        this.maxJavaFileBytes = settings.maxJavaFileBytes();
    }

    /**
     * Processes either a single ZIP/folder or a folder containing multiple submissions.
     * An unexpected failure in one submission is converted into a result and does not
     * stop the remaining batch.
     */
    public List<SubmissionValidationResult> prepareBatch(Path input, Path workspace) throws IOException {
        requireReadableInput(input);
        Files.createDirectories(workspace);

        List<SubmissionValidationResult> results = new ArrayList<>();
        Set<String> outputNames = new HashSet<>();
        for (Path submission : discovery.discover(input, workspace)) {
            try {
                results.add(prepareOne(SubmissionSource.of(submission, zipExtractor), workspace, outputNames));
            } catch (Exception exception) {
                String fallbackUsername = usernames.candidate(submission)
                        .orElse(IdentitySuggestions.PLACEHOLDER_PREFIX + "submission");
                results.add(new SubmissionValidationResult(submission, fallbackUsername, null, Map.of(),
                        List.of(new SubmissionFinding(
                                FindingSeverity.ERROR,
                                "SUBMISSION_UNREADABLE",
                                "Could not prepare this submission: " + Messages.describe(exception),
                                false))));
            }
        }
        return List.copyOf(results);
    }

    private SubmissionValidationResult prepareOne(SubmissionSource source, Path workspace, Set<String> outputNames)
            throws IOException {
        List<SubmissionFinding> findings = new ArrayList<>();
        Path staging = Files.createDirectories(workspace.resolve(".staging").resolve(UUID.randomUUID().toString()));
        try {
            Path content = source.open(staging, findings);
            List<Path> javaFiles = FileSearch.javaFiles(content);
            HeaderIdentity identity = headers.discover(javaFiles, findings);
            String username = usernames.resolve(source.path(), identity, findings);
            String outputName = usernames.outputFolderName(username, outputNames, workspace);
            if (!outputName.equals(username)) {
                findings.add(new SubmissionFinding(
                        FindingSeverity.WARNING,
                        "DUPLICATE_USERNAME",
                        "A duplicate username was found; normalized this copy as " + outputName + ".",
                        true));
            }

            Path normalizedRoot = workspace.resolve(outputName).normalize();
            ensureChildOf(workspace, normalizedRoot);
            Files.createDirectories(normalizedRoot);
            Normalization normalization = new Normalization(source, username, outputName,
                    workspace.resolve(ORIGINALS_FOLDER).resolve(outputName));
            if (!usernames.candidate(source.path()).orElse("").equals(outputName)) {
                normalization.corrections().add(new FileCorrection(normalization.label(), outputName + "/",
                        "The normalized copy is named after the student's username."));
            }

            SubmissionContext context = new SubmissionContext(source, content, username, javaFiles);
            for (SubmissionCheck check : checks) {
                check.inspect(context, findings);
            }
            Map<String, Path> sources = normalizeSources(context, normalizedRoot, identity, normalization, findings);
            Map<String, Path> dependencies = normalizeDependencies(content, normalizedRoot, normalization, findings);

            Map<String, QuestionFiles> questions = new LinkedHashMap<>();
            for (var question : AssessmentDefinition.QUESTIONS) {
                questions.put(question.id(), new QuestionFiles(
                        selectedFiles(question.sources(), sources),
                        selectedFiles(question.supportClasses(), dependencies),
                        selectedFiles(question.runtimeFiles(), dependencies)));
            }

            if (sources.isEmpty()) {
                FileTrees.delete(normalizedRoot);
                FileTrees.delete(normalization.originalsRoot());
                normalizedRoot = null;
                questions.clear();
            }
            return new SubmissionValidationResult(source.path(), username, normalizedRoot, questions, findings,
                    identity, normalization.corrections(), normalization.originals());
        } finally {
            try {
                FileTrees.delete(staging);
            } catch (IOException ignored) {
                // A cleanup problem must not turn a usable submission into a failed batch item.
            }
        }
    }

    // Copies each required source file into its question folder, keeps an unmodified copy of it,
    // and checks its header.
    private Map<String, Path> normalizeSources(SubmissionContext submission, Path normalizedRoot,
            HeaderIdentity identity, Normalization normalization, List<SubmissionFinding> findings)
            throws IOException {
        Map<String, Path> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> requirement : AssessmentDefinition.sourceFiles().entrySet()) {
            String fileName = requirement.getKey();
            String folder = requirement.getValue();
            List<Path> candidates = FileSearch.filesNamed(submission.content(), fileName);
            if (candidates.isEmpty()) {
                findings.add(new SubmissionFinding(
                        FindingSeverity.ERROR,
                        "MISSING_JAVA_FILE",
                        "Required file " + folder + "/" + fileName + " is missing.",
                        false));
                continue;
            }

            candidates.sort(FileSearch.preferringFolder(folder));
            Path selected = candidates.getFirst();
            if (candidates.size() > 1) {
                findings.add(new SubmissionFinding(
                        FindingSeverity.WARNING,
                        "DUPLICATE_JAVA_FILE",
                        "Found multiple copies of " + fileName + "; used "
                                + submission.content().relativize(selected) + ".",
                        false));
            }

            Path target = Files.createDirectories(normalizedRoot.resolve(folder)).resolve(fileName);
            try {
                Files.copy(selected, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                findings.add(new SubmissionFinding(
                        FindingSeverity.ERROR,
                        "JAVA_FILE_UNREADABLE",
                        "Could not copy " + fileName + ": " + Messages.describe(exception),
                        false));
                continue;
            }

            String actualLocation = submission.content().relativize(selected).toString().replace('\\', '/');
            String correctedLocation = normalization.outputName() + "/" + folder + "/" + fileName;
            if (!actualLocation.equals(submission.source().expectedLocation(submission.username(), folder, fileName))) {
                findings.add(new SubmissionFinding(
                        FindingSeverity.WARNING,
                        "JAVA_FILE_RELOCATED",
                        fileName + " was moved into " + folder + " in the normalized copy.",
                        true));
                normalization.corrections().add(new FileCorrection(normalization.label() + "/" + actualLocation,
                        correctedLocation, "Moved to where the assessment expects it."));
            }

            if (Files.size(target) > maxJavaFileBytes) {
                findings.add(new SubmissionFinding(
                        FindingSeverity.ERROR,
                        "JAVA_FILE_TOO_LARGE",
                        fileName + " is too large to inspect or run safely; its question scores 0.",
                        false));
                Files.delete(target);
                continue;
            }
            normalization.keepOriginal(folder, target);
            try {
                List<String> added = headers.checkAndRepair(target, submission.username(), identity, findings);
                if (!added.isEmpty()) {
                    normalization.corrections().add(new FileCorrection(normalization.label() + "/" + actualLocation,
                            correctedLocation,
                            "Added to the header: " + String.join("; ", added) + "."));
                }
            } catch (IOException exception) {
                findings.add(new SubmissionFinding(
                        FindingSeverity.ERROR,
                        "HEADER_UNREADABLE",
                        "Could not inspect the header of " + fileName + ": " + Messages.describe(exception),
                        false));
            }
            normalized.put(fileName, target);
        }
        return Map.copyOf(normalized);
    }

    // Copies the support classes and test data the testers need from the student's submission.
    private Map<String, Path> normalizeDependencies(Path content, Path normalizedRoot, Normalization normalization,
            List<SubmissionFinding> findings) throws IOException {
        Map<String, String> requirements = new LinkedHashMap<>(AssessmentDefinition.supportClasses());
        requirements.putAll(AssessmentDefinition.runtimeFiles());
        Map<String, Path> normalized = new LinkedHashMap<>();
        for (var entry : requirements.entrySet()) {
            List<Path> candidates = FileSearch.filesNamed(content, entry.getKey());
            String relative = entry.getValue() + "/" + entry.getKey();
            if (candidates.isEmpty()) {
                findings.add(new SubmissionFinding(FindingSeverity.ERROR, "MISSING_DEPENDENCY",
                        "Required grading dependency " + relative + " is missing.", false));
                continue;
            }
            candidates.sort(FileSearch.preferringFolder(entry.getValue()));
            if (candidates.size() > 1) {
                findings.add(new SubmissionFinding(FindingSeverity.WARNING, "DUPLICATE_DEPENDENCY",
                        "Found multiple copies of " + entry.getKey() + "; used "
                                + content.relativize(candidates.getFirst()) + ".", false));
            }
            try {
                Path folder = Files.createDirectories(normalizedRoot.resolve(entry.getValue()));
                Path copy = Files.copy(candidates.getFirst(), folder.resolve(entry.getKey()));
                normalized.put(entry.getKey(), copy);
                String actualLocation = content.relativize(candidates.getFirst()).toString().replace('\\', '/');
                if (!actualLocation.equals(normalization.expectedLocation(entry.getValue(), entry.getKey()))) {
                    normalization.corrections().add(new FileCorrection(normalization.label() + "/" + actualLocation,
                            normalization.outputName() + "/" + relative, "Moved to where the assessment expects it."));
                }
            } catch (IOException exception) {
                findings.add(new SubmissionFinding(FindingSeverity.ERROR, "DEPENDENCY_UNREADABLE",
                        "Could not copy " + relative + ": " + Messages.describe(exception), false));
            }
        }
        return normalized;
    }

    private static List<Path> selectedFiles(List<String> names, Map<String, Path> files) {
        return names.stream().filter(files::containsKey).map(files::get).toList();
    }

    private static void requireReadableInput(Path input) throws IOException {
        if (input == null || !Files.exists(input)) {
            throw new IOException("Submission path does not exist");
        }
        if (!Files.isReadable(input)) {
            throw new IOException("Submission path is not readable: " + input);
        }
        if (Files.isRegularFile(input) && !FileSearch.isZip(input)) {
            throw new IOException("Expected a submission folder or ZIP file: " + input);
        }
    }

    private static void ensureChildOf(Path parent, Path child) throws IOException {
        Path normalizedParent = parent.toAbsolutePath().normalize();
        Path normalizedChild = child.toAbsolutePath().normalize();
        if (!normalizedChild.startsWith(normalizedParent) || normalizedChild.equals(normalizedParent)) {
            throw new IOException("Refusing to write outside the normalization workspace");
        }
    }

    /** What normalizing one submission changed, and where its unmodified sources are kept. */
    private static final class Normalization {

        private final SubmissionSource source;
        private final String username;
        private final String outputName;
        private final Path originalsRoot;
        private final List<FileCorrection> corrections = new ArrayList<>();
        private final Map<String, Path> originals = new LinkedHashMap<>();

        private Normalization(SubmissionSource source, String username, String outputName, Path originalsRoot) {
            this.source = source;
            this.username = username;
            this.outputName = outputName;
            this.originalsRoot = originalsRoot;
        }

        /** The submission as the marker sees it: the ZIP or folder name. */
        String label() {
            return source.path().getFileName().toString();
        }

        String outputName() {
            return outputName;
        }

        Path originalsRoot() {
            return originalsRoot;
        }

        List<FileCorrection> corrections() {
            return corrections;
        }

        Map<String, Path> originals() {
            return originals;
        }

        String expectedLocation(String folder, String fileName) {
            return source.expectedLocation(username, folder, fileName);
        }

        // Copies a source before its header is repaired, so style checks see what the student wrote.
        void keepOriginal(String folder, Path normalizedFile) throws IOException {
            Path copy = Files.createDirectories(originalsRoot.resolve(folder)).resolve(normalizedFile.getFileName());
            Files.copy(normalizedFile, copy, StandardCopyOption.REPLACE_EXISTING);
            originals.put(normalizedFile.getFileName().toString(), copy);
        }
    }
}
