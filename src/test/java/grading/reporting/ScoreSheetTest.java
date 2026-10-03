package grading.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScoreSheetTest {

    static final String HEADER = "OrgDefinedId,Username,Last Name,First Name,Email,"
            + "Calculated Final Grade Numerator,Calculated Final Grade Denominator,End-of-Line Indicator\r\n";

    @TempDir
    Path temporary;

    @Test
    void readsLmsUsernamesWithoutTheirTextPrefix() throws Exception {
        Path csv = write("sheet.csv", HEADER
                + "#01400001,#amy.tan.2024,_,AMY TAN,amy.tan.2024@smu.edu.sg,,20,#\r\n"
                + "#01400002,#ben.ong.2023,_,BEN ONG,ben.ong.2023@smu.edu.sg,15.0,20,#\r\n");

        ScoreSheet sheet = ScoreSheet.load(csv);

        assertEquals(2, sheet.rowCount());
        assertEquals("amy.tan.2024", sheet.username(0));
        assertEquals("", sheet.numerator(0));
        assertEquals("15.0", sheet.numerator(1));
        assertEquals(csv.toAbsolutePath().normalize(), sheet.path());
    }

    @Test
    void rejectsSheetsWithoutTheRequiredColumns() throws Exception {
        Path noUsername = write("no-username.csv", "OrgDefinedId,Calculated Final Grade Numerator\r\n#1,\r\n");
        Path noNumerator = write("no-numerator.csv", "OrgDefinedId,Username\r\n#1,#amy\r\n");

        assertMessage(noUsername, "no \"Username\" column");
        assertMessage(noNumerator, "no \"Calculated Final Grade Numerator\" column");
    }

    @Test
    void rejectsEmptyMalformedDuplicateAndNonUtf8Sheets() throws Exception {
        assertMessage(write("empty.csv", ""), "empty");
        assertMessage(write("short-row.csv", HEADER + "#1,#amy.tan.2024,_,AMY\r\n"),
                "Line 2 has 4 values, but the header has 8");
        assertMessage(write("duplicate.csv", HEADER
                + "#1,#amy.tan.2024,_,AMY,a@smu.edu.sg,,20,#\r\n"
                + "#2,#AMY.TAN.2024,_,AMY,a@smu.edu.sg,,20,#\r\n"), "appears more than once");
        Path latin1 = temporary.resolve("latin1.csv");
        Files.write(latin1, (HEADER + "#1,#amy,_,ZOË,a@smu.edu.sg,,20,#\r\n")
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        assertMessage(latin1, "not UTF-8");
        assertMessage(temporary.resolve("missing.csv"), "not found");
    }

    @Test
    void rejectsReadOnlySheetsBeforeGradingFinishes() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path csv = write("read-only.csv", HEADER + "#1,#amy.tan.2024,_,AMY,a@smu.edu.sg,,20,#\r\n");
        Files.setPosixFilePermissions(csv, PosixFilePermissions.fromString("r--r--r--"));
        assumeTrue(!Files.isWritable(csv), "running with permissions that ignore read-only files");

        assertMessage(csv, "read-only");
    }

    private void assertMessage(Path csv, String expected) {
        ScoreSheetException failure = assertThrows(ScoreSheetException.class, () -> ScoreSheet.load(csv));
        assertTrue(failure.getMessage().contains(expected), failure.getMessage());
    }

    private Path write(String name, String content) throws Exception {
        return Files.writeString(temporary.resolve(name), content);
    }
}
