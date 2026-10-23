package grading.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubmissionWorkspacesTest {

    @TempDir
    Path root;

    @Test
    void removesOnlyWorkspacesWhoseSessionHasEnded() throws Exception {
        SubmissionWorkspaces workspaces = new SubmissionWorkspaces(root);
        Path current = workspaces.create();
        Files.writeString(current.resolve("alice.txt"), "normalized copy");
        Path crashed = workspaces.create();
        // no running process has this id
        Files.writeString(crashed.resolve(".owner-pid"), Long.toString(Long.MAX_VALUE));
        Path legacy = Files.createDirectories(root.resolve("normalized-from-an-older-version"));
        Path unrelated = Files.createDirectories(root.resolve("not-a-workspace"));

        assertEquals(2, workspaces.removeAbandoned());

        assertTrue(Files.exists(current.resolve("alice.txt")), "this session's workspace is kept");
        assertFalse(Files.exists(crashed));
        assertFalse(Files.exists(legacy));
        assertTrue(Files.exists(unrelated), "only workspaces are ever removed");
    }

    @Test
    void deleteRemovesTheWholeWorkspaceAndToleratesMissingOnes() throws Exception {
        SubmissionWorkspaces workspaces = new SubmissionWorkspaces(root);
        Path workspace = workspaces.create();
        Files.createDirectories(workspace.resolve("alice/Q1"));
        Files.writeString(workspace.resolve("alice/Q1/Q1a.java"), "class Q1a {}");

        workspaces.delete(workspace);
        workspaces.delete(workspace);
        workspaces.delete(null);

        assertFalse(Files.exists(workspace));
    }
}
