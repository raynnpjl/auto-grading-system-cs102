package grading.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import grading.AssessmentFixtures;
import grading.assessment.AssessmentDefinition;

import grading.config.GradingSettings;
import grading.progress.ProgressTracker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradingSessionTest {

    @TempDir
    Path workFolder;

    @TempDir
    Path submissions;

    @Test
    void outcomeIsNoneBeforeAnyRun() {
        GradingSession session = new GradingSession(GradingSettings.current(), new ProgressTracker(), workFolder);
        assertEquals(RunOutcome.NONE, session.lastOutcome());
    }

    @Test
    void reimportDeletesThePreviousCopy() throws Exception {
        Path student = Files.createDirectory(submissions.resolve("alice"));
        for (var entry : AssessmentDefinition.sourceFiles().entrySet()) {
            String type = entry.getKey().replace(".java", "");
            Path folder = Files.createDirectories(student.resolve(entry.getValue()));
            Files.writeString(folder.resolve(entry.getKey()),
                    "// Name: Alice Student\n// Email: alice@smu.edu.sg\npublic class " + type + " {}");
        }
        AssessmentFixtures.writeDependencies(student);
        GradingSession session = new GradingSession(GradingSettings.current(), new ProgressTracker(), workFolder);

        session.importSubmissions(student);
        session.importSubmissions(student);

        try (Stream<Path> copies = Files.list(workFolder)) {
            assertEquals(1, copies.filter(path -> path.getFileName().toString().startsWith("normalized-")).count());
        }
    }
}
