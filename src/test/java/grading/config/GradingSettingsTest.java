package grading.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import grading.assessment.AssessmentDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradingSettingsTest {

    @TempDir
    Path temporary;

    // Loads the shared settings before any test sets an override, so overrides stay local to a test.
    @BeforeAll
    static void loadDefaults() {
        GradingSettings.current();
    }

    @AfterEach
    void clearOverride() {
        System.clearProperty("grading.config");
    }

    @Test
    void packagedDefaultsDescribeTheSampleAssessment() {
        GradingSettings settings = GradingSettings.current();
        assertEquals(Duration.ofSeconds(30), settings.compileTimeout());
        assertEquals(Duration.ofSeconds(5), settings.executionTimeout());
        assertEquals("smu.edu.sg", settings.emailDomain());

        assertEquals(List.of("Q1a", "Q1b", "Q2a", "Q2b", "Q3"),
                AssessmentDefinition.QUESTIONS.stream().map(AssessmentDefinition.Question::id).toList());
        assertEquals(List.of("Q1", "Q2", "Q3"), AssessmentDefinition.questionFolders());
        assertEquals("RenameToYourUsername", AssessmentDefinition.templateFolder());
        assertEquals(List.of("Shape.class", "Circle.class", "Rectangle.class"),
                AssessmentDefinition.QUESTIONS.getLast().supportClasses());
    }

    @Test
    void anExternalFileOverridesOnlyTheSettingsItNames() throws Exception {
        Path override = Files.writeString(temporary.resolve("grading.properties"), """
                grading.execution-timeout-seconds=12
                submission.email-domain=computing.smu.edu.sg
                """);
        System.setProperty("grading.config", override.toString());

        GradingSettings settings = GradingSettings.load(ConfigFile.load("grading"));

        assertEquals(Duration.ofSeconds(12), settings.executionTimeout());
        assertEquals("computing.smu.edu.sg", settings.emailDomain());
        assertEquals(Duration.ofSeconds(30), settings.compileTimeout(), "unnamed settings keep their default");
    }

    @Test
    void rejectsInvalidValuesWithTheSettingName() throws Exception {
        Path override = Files.writeString(temporary.resolve("grading.properties"),
                "grading.compile-timeout-seconds=soon\n");
        System.setProperty("grading.config", override.toString());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> GradingSettings.load(ConfigFile.load("grading")));
        assertTrue(failure.getMessage().contains("grading.compile-timeout-seconds"), failure.getMessage());
    }
}
