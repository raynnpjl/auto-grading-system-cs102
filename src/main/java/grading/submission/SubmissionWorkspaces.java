package grading.submission;

import grading.common.FileTrees;
import grading.config.GradingSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The temporary folders that hold normalized submissions. Each records the process that created
 * it, so folders left behind by a closed or crashed session can be found and removed, while those
 * of a session that is still running are kept.
 */
public final class SubmissionWorkspaces {

    private static final String PREFIX = "normalized-";
    private static final String OWNER_FILE = ".owner-pid";

    private final Path root;

    public SubmissionWorkspaces(Path root) {
        this.root = root;
    }

    /** Workspaces under the configured folder in the system temporary directory. */
    public static SubmissionWorkspaces fromSettings() {
        return new SubmissionWorkspaces(Path.of(System.getProperty("java.io.tmpdir"),
                GradingSettings.current().workspaceFolder()));
    }

    /** Creates an empty workspace owned by this process. */
    public Path create() throws IOException {
        Path workspace = Files.createDirectories(root.resolve(PREFIX + UUID.randomUUID()));
        Files.writeString(workspace.resolve(OWNER_FILE), Long.toString(ProcessHandle.current().pid()),
                StandardCharsets.UTF_8);
        return workspace;
    }

    /** Deletes a workspace; a failure is ignored because the next start-up removes it anyway. */
    public void delete(Path workspace) {
        if (workspace == null) {
            return;
        }
        try {
            FileTrees.delete(workspace);
        } catch (IOException ignored) {
            // removeAbandoned() deletes it once this process has ended
        }
    }

    /** Deletes every workspace whose owning process is no longer running; returns how many. */
    public int removeAbandoned() {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        List<Path> abandoned;
        try (Stream<Path> children = Files.list(root)) {
            abandoned = children
                    .filter(path -> Files.isDirectory(path) && path.getFileName().toString().startsWith(PREFIX))
                    .filter(path -> !ownerIsRunning(path))
                    .toList();
        } catch (IOException exception) {
            return 0;
        }
        abandoned.forEach(this::delete);
        return (int) abandoned.stream().filter(path -> !Files.exists(path)).count();
    }

    // A workspace without a readable owner predates owner tracking and is treated as abandoned.
    private static boolean ownerIsRunning(Path workspace) {
        return owner(workspace).flatMap(ProcessHandle::of).map(ProcessHandle::isAlive).orElse(false);
    }

    private static Optional<Long> owner(Path workspace) {
        try {
            return Optional.of(Long.parseLong(Files.readString(workspace.resolve(OWNER_FILE)).strip()));
        } catch (IOException | NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
