package grading.reporting;

import static grading.reporting.ScoreSheetTest.HEADER;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import grading.scoring.ScoreSummary;
import grading.scoring.StudentScore;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScoreSheetUpdaterTest {

    private static final String SHEET = HEADER
            + "#01400001,#ping.lee.2023,_,PING LEE,ping.lee.2023@computing.smu.edu.sg,,20,#\r\n"
            + "#01400002,#chee.teo.2022,_,TEO CHEE,chee.teo.2022@computing.smu.edu.sg,,20,#\r\n"
            + "#01400005,#jing.lim.2021,_,JING LIM,jing.lim.2021@computing.smu.edu.sg,,20,#\r\n";

    @TempDir
    Path temporary;

    private final ScoreSheetUpdater updater = new ScoreSheetUpdater();

    @Test
    void writesTotalsIntoTheImportedFileChangingOnlyScoreCells() throws Exception {
        Path csv = write(SHEET);
        ScoreSheetPreview preview = updater.preview(ScoreSheet.load(csv),
                scores(Map.of("ping.lee.2023", 20.0, "chee.teo.2022", 12.0, "jing.lim.2021", 0.0)));

        ScoreSheetUpdater.Result result = updater.write(preview);

        assertEquals(csv.toAbsolutePath().normalize(), result.sheet());
        assertEquals(3, result.scoresWritten());
        assertEquals(HEADER
                + "#01400001,#ping.lee.2023,_,PING LEE,ping.lee.2023@computing.smu.edu.sg,20.0,20,#\r\n"
                + "#01400002,#chee.teo.2022,_,TEO CHEE,chee.teo.2022@computing.smu.edu.sg,12.0,20,#\r\n"
                + "#01400005,#jing.lim.2021,_,JING LIM,jing.lim.2021@computing.smu.edu.sg,0.0,20,#\r\n",
                Files.readString(csv));
        assertEquals(SHEET, Files.readString(result.backup()));
        assertTrue(result.backup().getFileName().toString().matches("score-sheet\\.backup-\\d{8}-\\d{6}\\.csv"));
        assertEquals("20.0", result.updatedSheet().numerator(0));
    }

    @Test
    void reportsStudentsMissingFromEitherSideAndLeavesTheirRowsUntouched() throws Exception {
        Path csv = write(SHEET);
        ScoreSheetPreview preview = updater.preview(ScoreSheet.load(csv),
                scores(Map.of("PING.LEE.2023", 20.0, "new.student.2025", 7.5)));

        assertEquals(1, preview.entries().size());
        assertEquals("ping.lee.2023", preview.entries().getFirst().username());
        assertEquals(List.of("new.student.2025"), preview.notInSheet());
        assertEquals(List.of("chee.teo.2022", "jing.lim.2021"), preview.noSubmission());
        assertTrue(preview.describe().contains("Scores to write: 1 of 3 students in the sheet"));
        assertTrue(preview.describe().contains("new.student.2025"));

        updater.write(preview);

        List<String> lines = Files.readString(csv).lines().toList();
        assertTrue(lines.get(1).contains(",20.0,20,#"));
        assertEquals(SHEET.lines().toList().subList(2, 4), lines.subList(2, 4));
    }

    @Test
    void replacesEarlierScoresOnARegradeAndKeepsEachVersionAsABackup() throws Exception {
        Path csv = write(SHEET);
        ScoreSheetUpdater.Result first = updater.write(updater.preview(ScoreSheet.load(csv),
                scores(Map.of("chee.teo.2022", 10.0))));

        ScoreSheetPreview regrade = updater.preview(first.updatedSheet(), scores(Map.of("chee.teo.2022", 12.0)));
        assertEquals(1, regrade.overwrittenCount());
        assertTrue(regrade.describe().contains("chee.teo.2022: 10.0 -> 12.0"));
        ScoreSheetUpdater.Result second = updater.write(regrade);

        assertTrue(Files.readString(csv).contains("chee.teo.2022@computing.smu.edu.sg,12.0,20,#"));
        assertTrue(Files.readString(second.backup()).contains("chee.teo.2022@computing.smu.edu.sg,10.0,20,#"));
        assertEquals(2, backups().size());
    }

    @Test
    void keepsByteOrderMarkLineEndingsAndQuotedCells() throws Exception {
        String sheet = "﻿Username,Name,Calculated Final Grade Numerator\n"
                + "#amy.tan.2024,\"TAN, AMY\",\n"
                + "#ben.ong.2023,\"BEN\",\n";
        Path csv = write(sheet);

        updater.write(updater.preview(ScoreSheet.load(csv), scores(Map.of("amy.tan.2024", 8.5))));

        assertEquals("﻿Username,Name,Calculated Final Grade Numerator\n"
                + "#amy.tan.2024,\"TAN, AMY\",8.5\n"
                + "#ben.ong.2023,\"BEN\",\n", Files.readString(csv));
    }

    @Test
    void refusesToOverwriteASheetEditedAfterImport() throws Exception {
        Path csv = write(SHEET);
        ScoreSheetPreview preview = updater.preview(ScoreSheet.load(csv), scores(Map.of("ping.lee.2023", 20.0)));
        String edited = SHEET.replace("PING LEE", "PING LEE (edited in Excel)");
        Files.writeString(csv, edited);

        ScoreSheetException failure = assertThrows(ScoreSheetException.class, () -> updater.write(preview));

        assertTrue(failure.getMessage().contains("changed after it was imported"), failure.getMessage());
        assertEquals(edited, Files.readString(csv));
        assertTrue(backups().isEmpty());
    }

    @Test
    void refusesWhenNoGradedStudentIsInTheSheet() throws Exception {
        Path csv = write(SHEET);
        ScoreSheetPreview preview = updater.preview(ScoreSheet.load(csv), scores(Map.of("someone.else", 3.0)));

        assertThrows(ScoreSheetException.class, () -> updater.write(preview));
        assertEquals(SHEET, Files.readString(csv));
    }

    @Test
    void leavesTheOriginalUnchangedWhenTheFolderCannotBeWritten() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path folder = Files.createDirectory(temporary.resolve("locked"));
        Path csv = Files.writeString(folder.resolve("score-sheet.csv"), SHEET);
        byte[] original = Files.readAllBytes(csv);
        ScoreSheetPreview preview = updater.preview(ScoreSheet.load(csv), scores(Map.of("ping.lee.2023", 20.0)));
        Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            assumeTrue(!Files.isWritable(folder), "running with permissions that ignore read-only folders");
            assertThrows(ScoreSheetException.class, () -> updater.write(preview));
            assertArrayEquals(original, Files.readAllBytes(csv));
        } finally {
            Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        try (Stream<Path> files = Files.list(folder)) {
            assertEquals(List.of(csv), files.toList());
        }
    }

    @Test
    void formatsTotalsLikeTheGradingReport() {
        assertEquals("12.0", ScoreSheetUpdater.formatScore(12));
        assertEquals("8.5", ScoreSheetUpdater.formatScore(8.5));
        assertEquals("0.0", ScoreSheetUpdater.formatScore(0));
    }

    private Path write(String content) throws Exception {
        return Files.write(temporary.resolve("score-sheet.csv"), content.getBytes(StandardCharsets.UTF_8));
    }

    private List<Path> backups() throws Exception {
        try (Stream<Path> files = Files.list(temporary)) {
            return files.filter(file -> file.getFileName().toString().contains(".backup-")).toList();
        }
    }

    private static ScoreSummary scores(Map<String, Double> totals) {
        return new ScoreSummary(totals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new StudentScore(entry.getKey(), Map.of(), entry.getValue()))
                .toList());
    }
}
