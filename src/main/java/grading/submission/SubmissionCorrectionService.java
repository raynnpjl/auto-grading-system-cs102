package grading.submission;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** Applies marker-confirmed information to normalized copies and revalidates them. */
public final class SubmissionCorrectionService {

    private final SubmissionPreparationService validationService;

    public SubmissionCorrectionService(SubmissionPreparationService validationService) {
        this.validationService = Objects.requireNonNull(validationService, "validationService");
    }

    public SubmissionValidationResult applyIdentity(
            SubmissionValidationResult existing,
            StudentIdentity identity,
            Path correctionWorkspace) throws IOException {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(correctionWorkspace, "correctionWorkspace");
        if (existing.normalizedRoot() == null || !Files.isDirectory(existing.normalizedRoot())) {
            throw new IOException("No normalized submission is available to correct");
        }

        Path inputRoot = correctionWorkspace.resolve("input").resolve(identity.username());
        copyTree(existing.normalizedRoot(), inputRoot);
        addVerifiedIdentityHeaders(inputRoot, identity);

        List<SubmissionValidationResult> revalidated = validationService.prepareBatch(
                inputRoot, correctionWorkspace.resolve("validated"));
        if (revalidated.size() != 1) {
            throw new IOException("Corrected submission could not be revalidated as one student submission");
        }

        SubmissionValidationResult result = revalidated.getFirst();
        List<SubmissionFinding> findings = new ArrayList<>(result.findings());
        findings.add(new SubmissionFinding(
                FindingSeverity.INFO,
                "IDENTITY_CONFIRMED",
                "The marker supplied and confirmed the student identity; all normalized Java headers were updated.",
                true));
        // Revalidation sees the already corrected copy, so earlier corrections and the unmodified
        // sources are carried over from the first import.
        List<FileCorrection> corrections = new ArrayList<>(existing.corrections());
        corrections.add(new FileCorrection(existing.username(), identity.username(),
                "The marker confirmed the student's identity; every Java header now names " + identity.name()
                        + " <" + identity.email() + ">."));
        return new SubmissionValidationResult(
                existing.source(),
                identity.username(),
                result.normalizedRoot(),
                result.questionFiles(),
                findings,
                new HeaderIdentity(Optional.of(identity.name()), Optional.of(identity.email())),
                corrections,
                existing.originalSources());
    }

    private void copyTree(Path sourceRoot, Path targetRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path source : paths.toList()) {
                Path relative = sourceRoot.relativize(source);
                Path target = targetRoot.resolve(relative).normalize();
                if (!target.startsWith(targetRoot.normalize())) {
                    throw new IOException("Refusing to copy outside the correction workspace");
                }
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else if (Files.isRegularFile(source)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void addVerifiedIdentityHeaders(Path root, StudentIdentity identity) throws IOException {
        String verifiedHeader = """
                /*
                 * Verified student username: %s
                 * Verified student name: %s
                 * Verified student email: %s
                 */
                """.formatted(identity.username(), identity.name(), identity.email());

        try (Stream<Path> paths = Files.walk(root)) {
            for (Path javaFile : paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(javaFile, StandardCharsets.UTF_8);
                Files.writeString(javaFile, verifiedHeader + source, StandardCharsets.UTF_8);
            }
        }
    }
}
