package grading.submission;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Validates and normalizes student submissions without changing their originals.
 */
public final class SubmissionPreparationService {

    private static final String TEMPLATE_FOLDER = "RenameToYourUsername";
    private static final long MAX_UNCOMPRESSED_ZIP_BYTES = 500L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 10_000;
    private static final long MAX_JAVA_FILE_BYTES = 2L * 1024 * 1024;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern NAME_PATTERN = Pattern.compile(
            "(?im)\\b(?:student\\s+name|name)\\s*[:=-]\\s*([^\\r\\n*/]{2,80})");
    private static final Pattern TRAILING_YEAR_PATTERN = Pattern.compile("[._-](?:19|20)\\d{2}$");
    private static final Map<String, String> REQUIRED_JAVA_FILES = createRequirements();

    public static Map<String, String> requiredJavaFiles() {
        return REQUIRED_JAVA_FILES;
    }

    /**
     * Processes either a single ZIP/folder or a folder containing multiple submissions.
     * An unexpected failure in one submission is converted into a result and does not
     * stop the remaining batch.
     */
    public List<SubmissionValidationResult> prepareBatch(Path input, Path workspace) throws IOException {
        requireReadableInput(input);
        Files.createDirectories(workspace);

        List<Path> submissions = discoverSubmissions(input, workspace);
        List<SubmissionValidationResult> results = new ArrayList<>();
        Set<String> outputNames = new HashSet<>();

        for (Path submission : submissions) {
            try {
                SubmissionValidationResult result = prepareOne(submission, workspace, outputNames);
                results.add(result);
            } catch (Exception exception) {
                String fallbackUsername = candidateUsername(submission).orElse("unknown-submission");
                results.add(new SubmissionValidationResult(
                        submission,
                        fallbackUsername,
                        null,
                        Map.of(),
                        List.of(new SubmissionFinding(
                                FindingSeverity.ERROR,
                                "SUBMISSION_UNREADABLE",
                                "Could not prepare this submission: " + safeMessage(exception),
                                false))));
            }
        }

        return List.copyOf(results);
    }

    private SubmissionValidationResult prepareOne(
            Path submission,
            Path workspace,
            Set<String> outputNames) throws IOException {
        List<SubmissionFinding> findings = new ArrayList<>();
        Path stagingRoot = workspace.resolve(".staging").resolve(UUID.randomUUID().toString());
        Files.createDirectories(stagingRoot);

        try {
            boolean zipped = isZip(submission);
            Path searchableRoot;
            if (zipped) {
                searchableRoot = stagingRoot.resolve("content");
                Files.createDirectories(searchableRoot);
                extractZipSafely(submission, searchableRoot, findings);
            } else {
                searchableRoot = submission;
            }

            List<Path> javaFiles = findJavaFiles(searchableRoot);
            HeaderIdentity discoveredIdentity = discoverIdentity(javaFiles, findings);
            String username = resolveUsername(submission, discoveredIdentity, findings);
            String outputName = uniqueOutputName(safePathSegment(username), outputNames, workspace);
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

            inspectHierarchy(submission, searchableRoot, zipped, username, findings);
            Map<String, Path> normalizedFiles = normalizeRequiredFiles(
                    searchableRoot, normalizedRoot, zipped, username, discoveredIdentity, findings);
            reportUnexpectedJavaFiles(javaFiles, normalizedFiles.keySet(), findings);

            if (normalizedFiles.isEmpty()) {
                deleteTree(normalizedRoot);
                normalizedRoot = null;
            }

            return new SubmissionValidationResult(
                    submission, username, normalizedRoot, normalizedFiles, findings);
        } finally {
            try {
                deleteTree(stagingRoot);
            } catch (IOException ignored) {
                // A cleanup problem must not turn a usable submission into a failed batch item.
            }
        }
    }

    private List<Path> discoverSubmissions(Path input, Path workspace) throws IOException {
        if (Files.isRegularFile(input)) {
            return List.of(input);
        }

        if (hasSubmissionContentAtTopLevel(input)) {
            return List.of(input);
        }

        Optional<String> inputName = candidateUsername(input);
        if (inputName.isPresent() && !isGenericName(inputName.get())
                && hasRedundantWrapper(input, inputName.get())) {
            return List.of(input);
        }

        Path normalizedWorkspace = workspace.toAbsolutePath().normalize();
        try (Stream<Path> children = Files.list(input)) {
            List<Path> candidates = children
                    .filter(path -> !path.toAbsolutePath().normalize().startsWith(normalizedWorkspace))
                    .filter(path -> Files.isDirectory(path) || isZip(path))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
            return candidates.isEmpty() ? List.of(input) : candidates;
        }
    }

    private boolean hasSubmissionContentAtTopLevel(Path directory) throws IOException {
        for (String question : Set.copyOf(REQUIRED_JAVA_FILES.values())) {
            if (findChildIgnoreCase(directory, question).filter(Files::isDirectory).isPresent()) {
                return true;
            }
        }
        try (Stream<Path> children = Files.list(directory)) {
            return children.anyMatch(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java"));
        }
    }

    private boolean hasRedundantWrapper(Path directory, String username) throws IOException {
        return findChildIgnoreCase(directory, username).filter(Files::isDirectory).isPresent()
                || (TRAILING_YEAR_PATTERN.matcher(username).find()
                        && findChildIgnoreCase(directory, TEMPLATE_FOLDER).filter(Files::isDirectory).isPresent());
    }

    private void inspectHierarchy(
            Path submission,
            Path searchableRoot,
            boolean zipped,
            String username,
            List<SubmissionFinding> findings) throws IOException {
        if (zipped) {
            Optional<Path> expectedWrapper = findChildIgnoreCase(searchableRoot, username)
                    .filter(Files::isDirectory);
            Optional<Path> templateWrapper = findChildIgnoreCase(searchableRoot, TEMPLATE_FOLDER)
                    .filter(Files::isDirectory);
            if (expectedWrapper.isEmpty()) {
                String message = templateWrapper.isPresent()
                        ? "The ZIP still contained the template folder name."
                        : "The ZIP did not contain a top-level folder named " + username + ".";
                findings.add(new SubmissionFinding(
                        FindingSeverity.WARNING,
                        "WRONG_USERNAME_FOLDER",
                        message + " The normalized copy uses the inferred username.",
                        true));
            }
        } else if (submission.getFileName() != null
                && !submission.getFileName().toString().equalsIgnoreCase(username)) {
            findings.add(new SubmissionFinding(
                    FindingSeverity.WARNING,
                    "WRONG_USERNAME_FOLDER",
                    "Folder " + submission.getFileName() + " was normalized as " + username + ".",
                    true));
        }

        for (String question : Set.copyOf(REQUIRED_JAVA_FILES.values())) {
            boolean exists = findDirectoriesIgnoreCase(searchableRoot, question).stream().findAny().isPresent();
            if (!exists) {
                findings.add(new SubmissionFinding(
                        FindingSeverity.WARNING,
                        "MISSING_QUESTION_FOLDER",
                        question + " was missing; it will be created if its Java file can be found.",
                        true));
            }
        }
    }

    private Map<String, Path> normalizeRequiredFiles(
            Path searchableRoot,
            Path normalizedRoot,
            boolean zipped,
            String username,
            HeaderIdentity identity,
            List<SubmissionFinding> findings) throws IOException {
        Map<String, Path> normalizedFiles = new LinkedHashMap<>();

        for (Map.Entry<String, String> requirement : REQUIRED_JAVA_FILES.entrySet()) {
            String fileName = requirement.getKey();
            String question = requirement.getValue();
            List<Path> candidates = findFilesIgnoreCase(searchableRoot, fileName);
            if (candidates.isEmpty()) {
                findings.add(new SubmissionFinding(
                        FindingSeverity.ERROR,
                        "MISSING_JAVA_FILE",
                        "Required file " + question + "/" + fileName + " is missing.",
                        false));
                continue;
            }

            candidates.sort(preferredCandidateOrder(question));
            Path selected = candidates.getFirst();
            if (candidates.size() > 1) {
                findings.add(new SubmissionFinding(
                        FindingSeverity.WARNING,
                        "DUPLICATE_JAVA_FILE",
                        "Found multiple copies of " + fileName + "; used "
                                + searchableRoot.relativize(selected) + ".",
                        false));
            }

            Path targetDirectory = normalizedRoot.resolve(question);
            Files.createDirectories(targetDirectory);
            Path target = targetDirectory.resolve(fileName);
            try {
                Files.copy(selected, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                findings.add(new SubmissionFinding(
                        FindingSeverity.ERROR,
                        "JAVA_FILE_UNREADABLE",
                        "Could not copy " + fileName + ": " + safeMessage(exception),
                        false));
                continue;
            }

            String actualRelativePath = searchableRoot.relativize(selected).toString().replace('\\', '/');
            String expectedRelativePath = (zipped ? username + "/" : "") + question + "/" + fileName;
            boolean alreadyInRightFolder = actualRelativePath.equals(expectedRelativePath);
            if (!alreadyInRightFolder) {
                findings.add(new SubmissionFinding(
                        FindingSeverity.WARNING,
                        "JAVA_FILE_RELOCATED",
                        fileName + " was moved into " + question + " in the normalized copy.",
                        true));
            }

            try {
                validateAndRepairHeader(target, username, identity, findings);
            } catch (IOException exception) {
                findings.add(new SubmissionFinding(
                        FindingSeverity.ERROR,
                        "HEADER_UNREADABLE",
                        "Could not inspect the header of " + fileName + ": " + safeMessage(exception),
                        false));
            }
            normalizedFiles.put(fileName, target);
        }

        return Map.copyOf(normalizedFiles);
    }

    private void validateAndRepairHeader(
            Path javaFile,
            String username,
            HeaderIdentity batchIdentity,
            List<SubmissionFinding> findings) throws IOException {
        if (Files.size(javaFile) > MAX_JAVA_FILE_BYTES) {
            findings.add(new SubmissionFinding(
                    FindingSeverity.ERROR,
                    "JAVA_FILE_TOO_LARGE",
                    javaFile.getFileName() + " is too large to inspect safely.",
                    false));
            return;
        }

        String source = Files.readString(javaFile, StandardCharsets.UTF_8);
        String header = leadingComment(source);
        HeaderIdentity fileIdentity = identityFromHeader(header);
        boolean missingName = fileIdentity.name().isEmpty();
        boolean missingEmail = fileIdentity.email().isEmpty();

        if (fileIdentity.email().isPresent()
                && !emailMatchesUsername(fileIdentity.email().get(), username)) {
            findings.add(new SubmissionFinding(
                    FindingSeverity.ERROR,
                    "HEADER_IDENTITY_MISMATCH",
                    javaFile.getFileName() + " contains an email that does not match username "
                            + username + ".",
                    false));
        }

        List<String> additions = new ArrayList<>();
        if (missingName) {
            String name = batchIdentity.name().orElseGet(() -> displayNameFromUsername(username));
            additions.add(" * Student name: " + name);
        }
        if (missingEmail && batchIdentity.email().isPresent()) {
            additions.add(" * Student email: " + batchIdentity.email().get());
        }
        if (missingEmail && batchIdentity.email().isEmpty()) {
            additions.add(" * Student username: " + username);
        }

        if (!additions.isEmpty()) {
            String repairedHeader = "/*\n" + String.join("\n", additions) + "\n */\n";
            Files.writeString(javaFile, repairedHeader + source, StandardCharsets.UTF_8);
        }

        if (missingName) {
            findings.add(new SubmissionFinding(
                    FindingSeverity.WARNING,
                    "MISSING_HEADER_NAME",
                    javaFile.getFileName() + " had no student name in its header; one was added.",
                    true));
        }
        if (missingEmail && batchIdentity.email().isPresent()) {
            findings.add(new SubmissionFinding(
                    FindingSeverity.WARNING,
                    "MISSING_HEADER_EMAIL",
                    javaFile.getFileName() + " had no email in its header; the email found elsewhere "
                            + "in the submission was added.",
                    true));
        } else if (missingEmail) {
            findings.add(new SubmissionFinding(
                    FindingSeverity.ERROR,
                    "MISSING_HEADER_EMAIL",
                    javaFile.getFileName() + " has no email in its header and no trustworthy email "
                            + "was available for automatic repair.",
                    false));
        }
    }

    private HeaderIdentity discoverIdentity(
            List<Path> javaFiles,
            List<SubmissionFinding> findings) throws IOException {
        Set<String> names = new HashSet<>();
        Set<String> emails = new HashSet<>();

        for (Path javaFile : javaFiles) {
            try {
                if (Files.size(javaFile) > MAX_JAVA_FILE_BYTES) {
                    continue;
                }
                String source = Files.readString(javaFile, StandardCharsets.UTF_8);
                HeaderIdentity identity = identityFromHeader(leadingComment(source));
                identity.name().map(String::trim).filter(Predicate.not(String::isBlank)).ifPresent(names::add);
                identity.email().map(value -> value.toLowerCase(Locale.ROOT)).ifPresent(emails::add);
            } catch (IOException exception) {
                findings.add(new SubmissionFinding(
                        FindingSeverity.ERROR,
                        "HEADER_UNREADABLE",
                        "Could not inspect " + javaFile.getFileName() + ": " + safeMessage(exception),
                        false));
            }
        }

        if (names.size() > 1 || emails.size() > 1) {
            findings.add(new SubmissionFinding(
                    FindingSeverity.ERROR,
                    "CONFLICTING_HEADER_IDENTITIES",
                    "Java headers contain conflicting student identities; existing values were not replaced.",
                    false));
        }

        Optional<String> name = names.size() == 1 ? names.stream().findFirst() : Optional.empty();
        Optional<String> email = emails.size() == 1 ? emails.stream().findFirst() : Optional.empty();
        return new HeaderIdentity(name, email);
    }

    private String resolveUsername(
            Path submission,
            HeaderIdentity identity,
            List<SubmissionFinding> findings) {
        Optional<String> fileCandidate = candidateUsername(submission);
        if (fileCandidate.isPresent() && !isGenericName(fileCandidate.get())) {
            if (identity.email().isPresent()) {
                String emailUsername = identity.email().get().substring(0, identity.email().get().indexOf('@'));
                if (!normalizeIdentifier(fileCandidate.get()).equals(normalizeIdentifier(emailUsername))) {
                    findings.add(new SubmissionFinding(
                            FindingSeverity.WARNING,
                            "WRONG_SUBMISSION_NAME",
                            "Submission name " + fileCandidate.get() + " did not match the consistent header "
                                    + "email; the normalized copy uses " + emailUsername + ".",
                            true));
                    return emailUsername;
                }
            }
            return fileCandidate.get();
        }

        if (identity.email().isPresent()) {
            String inferred = identity.email().get().substring(0, identity.email().get().indexOf('@'));
            findings.add(new SubmissionFinding(
                    FindingSeverity.WARNING,
                    "WRONG_SUBMISSION_NAME",
                    "The ZIP/folder still used a generic or template name; the normalized copy uses "
                            + inferred + " from a consistent Java header email.",
                    true));
            return inferred;
        }

        String fallback = "unknown-" + Integer.toUnsignedString(
                submission.toAbsolutePath().normalize().toString().hashCode(), 36);
        findings.add(new SubmissionFinding(
                FindingSeverity.ERROR,
                "USERNAME_UNKNOWN",
                "The submission still uses a generic/template name and no header email was available "
                        + "to identify the student. It was isolated as " + fallback + ".",
                false));
        return fallback;
    }

    private void reportUnexpectedJavaFiles(
            List<Path> javaFiles,
            Set<String> normalizedNames,
            List<SubmissionFinding> findings) {
        Set<String> expectedLowercase = new HashSet<>();
        normalizedNames.forEach(name -> expectedLowercase.add(name.toLowerCase(Locale.ROOT)));
        long unexpected = javaFiles.stream()
                .map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                .filter(name -> !expectedLowercase.contains(name))
                .count();
        if (unexpected > 0) {
            findings.add(new SubmissionFinding(
                    FindingSeverity.INFO,
                    "EXTRA_JAVA_FILES",
                    "Found " + unexpected + " additional Java file(s); they were left out of the normalized copy.",
                    false));
        }
    }

    private void extractZipSafely(
            Path zipFile,
            Path destination,
            List<SubmissionFinding> findings) throws IOException {
        long totalBytes = 0;
        int entries = 0;
        Path normalizedDestination = destination.toAbsolutePath().normalize();

        try (InputStream input = new BufferedInputStream(Files.newInputStream(zipFile));
                ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ZIP_ENTRIES) {
                    throw new IOException("ZIP contains too many entries");
                }

                Path target = destination.resolve(entry.getName()).normalize();
                if (!target.toAbsolutePath().normalize().startsWith(normalizedDestination)) {
                    findings.add(new SubmissionFinding(
                            FindingSeverity.ERROR,
                            "UNSAFE_ZIP_ENTRY",
                            "Skipped unsafe ZIP entry " + entry.getName() + ".",
                            false));
                    zip.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    try (var output = Files.newOutputStream(target)) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            totalBytes += read;
                            if (totalBytes > MAX_UNCOMPRESSED_ZIP_BYTES) {
                                throw new IOException("ZIP expands beyond the safe size limit");
                            }
                            output.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private List<Path> findJavaFiles(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private List<Path> findFilesIgnoreCase(Path root, String fileName) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return new ArrayList<>(paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(fileName))
                    .toList());
        }
    }

    private List<Path> findDirectoriesIgnoreCase(Path root, String name) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName() != null
                            && path.getFileName().toString().equalsIgnoreCase(name))
                    .toList();
        }
    }

    private Optional<Path> findChildIgnoreCase(Path root, String name) throws IOException {
        if (!Files.isDirectory(root)) {
            return Optional.empty();
        }
        try (Stream<Path> children = Files.list(root)) {
            return children
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(name))
                    .findFirst();
        }
    }

    private Comparator<Path> preferredCandidateOrder(String question) {
        return Comparator
                .comparing((Path path) -> path.getParent() == null
                        || path.getParent().getFileName() == null
                        || !path.getParent().getFileName().toString().equalsIgnoreCase(question))
                .thenComparingInt(Path::getNameCount)
                .thenComparing(path -> path.toString().toLowerCase(Locale.ROOT));
    }

    private HeaderIdentity identityFromHeader(String header) {
        Matcher emailMatcher = EMAIL_PATTERN.matcher(header);
        Optional<String> email = emailMatcher.find()
                ? Optional.of(emailMatcher.group().toLowerCase(Locale.ROOT))
                : Optional.empty();
        Matcher nameMatcher = NAME_PATTERN.matcher(header);
        Optional<String> name = nameMatcher.find()
                ? Optional.of(nameMatcher.group(1).trim())
                : Optional.empty();
        return new HeaderIdentity(name, email);
    }

    private String leadingComment(String source) {
        int index = source.startsWith("\uFEFF") ? 1 : 0;
        StringBuilder comments = new StringBuilder();

        while (index < source.length()) {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
            if (source.startsWith("//", index)) {
                int end = source.indexOf('\n', index);
                end = end < 0 ? source.length() : end + 1;
                comments.append(source, index, end);
                index = end;
            } else if (source.startsWith("/*", index)) {
                int end = source.indexOf("*/", index + 2);
                if (end < 0) {
                    return comments.toString();
                }
                end += 2;
                comments.append(source, index, end).append('\n');
                index = end;
            } else {
                break;
            }
        }
        return comments.toString();
    }

    private boolean emailMatchesUsername(String email, String username) {
        String localPart = email.substring(0, email.indexOf('@'));
        return normalizeIdentifier(localPart).equals(normalizeIdentifier(username));
    }

    private String normalizeIdentifier(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String displayNameFromUsername(String username) {
        String withoutYear = TRAILING_YEAR_PATTERN.matcher(username).replaceFirst("");
        String[] parts = withoutYear.split("[._-]+");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                words.add(Character.toUpperCase(part.charAt(0)) + part.substring(1));
            }
        }
        return words.isEmpty() ? username : String.join(" ", words);
    }

    private Optional<String> candidateUsername(Path submission) {
        if (submission.getFileName() == null) {
            return Optional.empty();
        }
        String name = submission.getFileName().toString();
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            name = name.substring(0, name.length() - 4);
        }
        return name.isBlank() ? Optional.empty() : Optional.of(name);
    }

    private boolean isGenericName(String name) {
        String normalized = normalizeIdentifier(name);
        return normalized.equals(normalizeIdentifier(TEMPLATE_FOLDER))
                || normalized.equals("submission")
                || normalized.equals("submissions")
                || normalized.equals("student")
                || normalized.equals("students");
    }

    private String safePathSegment(String username) {
        String normalized = Normalizer.normalize(username, Normalizer.Form.NFKC)
                .replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "_")
                .replaceAll("[. ]+$", "")
                .trim();
        return normalized.isBlank() ? "unknown-submission" : normalized;
    }

    private String uniqueOutputName(String base, Set<String> usedNames, Path workspace) {
        String candidate = base;
        int suffix = 2;
        while (!usedNames.add(candidate.toLowerCase(Locale.ROOT))
                || Files.exists(workspace.resolve(candidate))) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private boolean isZip(Path path) {
        return Files.isRegularFile(path)
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private void requireReadableInput(Path input) throws IOException {
        if (input == null || !Files.exists(input)) {
            throw new IOException("Submission path does not exist");
        }
        if (!Files.isReadable(input)) {
            throw new IOException("Submission path is not readable: " + input);
        }
        if (Files.isRegularFile(input) && !isZip(input)) {
            throw new IOException("Expected a submission folder or ZIP file: " + input);
        }
    }

    private void ensureChildOf(Path parent, Path child) throws IOException {
        Path normalizedParent = parent.toAbsolutePath().normalize();
        Path normalizedChild = child.toAbsolutePath().normalize();
        if (!normalizedChild.startsWith(normalizedParent) || normalizedChild.equals(normalizedParent)) {
            throw new IOException("Refusing to write outside the normalization workspace");
        }
    }

    private void deleteTree(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private String safeMessage(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static Map<String, String> createRequirements() {
        Map<String, String> requirements = new LinkedHashMap<>();
        requirements.put("Q1a.java", "Q1");
        requirements.put("Q1b.java", "Q1");
        requirements.put("Q2a.java", "Q2");
        requirements.put("Q2b.java", "Q2");
        requirements.put("Q3.java", "Q3");
        requirements.put("ShapeComparator.java", "Q3");
        return Map.copyOf(requirements);
    }

    private record HeaderIdentity(Optional<String> name, Optional<String> email) {
    }
}
