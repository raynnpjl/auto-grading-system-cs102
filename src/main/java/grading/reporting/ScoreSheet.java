package grading.reporting;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

// Generated with Claude Code (Claude Opus 5.5); reviewed and adapted by the team.
/**
 * An imported LMS score sheet. Rows are kept as the exact original text so that an update changes
 * only the score cells it fills and leaves every other byte of the file as the instructor provided it.
 */
public final class ScoreSheet {

    public static final String USERNAME_COLUMN = "Username";
    public static final String NUMERATOR_COLUMN = "Calculated Final Grade Numerator";
    private static final String BYTE_ORDER_MARK = "﻿";
    // the LMS export prefixes identifiers with '#' so spreadsheet tools keep them as text
    private static final String LMS_TEXT_PREFIX = "#";

    private final Path path;
    private final byte[] originalBytes;
    private final boolean hasByteOrderMark;
    private final List<String> header;
    private final List<Row> rows;
    private final String headerText;
    private final int usernameColumn;
    private final int numeratorColumn;

    private ScoreSheet(Path path, byte[] originalBytes, boolean hasByteOrderMark, List<String> header,
            String headerText, List<Row> rows, int usernameColumn, int numeratorColumn) {
        this.path = path;
        this.originalBytes = originalBytes;
        this.hasByteOrderMark = hasByteOrderMark;
        this.header = List.copyOf(header);
        this.headerText = headerText;
        this.rows = List.copyOf(rows);
        this.usernameColumn = usernameColumn;
        this.numeratorColumn = numeratorColumn;
    }

    public static ScoreSheet load(Path path) throws IOException {
        Path file = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            throw new ScoreSheetException("The score sheet was not found: " + file);
        }
        if (!Files.isReadable(file)) {
            throw new ScoreSheetException("The score sheet cannot be read: " + file
                    + ". Check the file permissions, then import it again.");
        }
        if (!Files.isWritable(file)) {
            throw new ScoreSheetException("The score sheet is read-only: " + file
                    + ". Make it writable (or save a writable copy), then import it again.");
        }
        byte[] bytes = Files.readAllBytes(file);
        String text = decode(bytes, file);
        boolean byteOrderMark = text.startsWith(BYTE_ORDER_MARK);
        if (byteOrderMark) {
            text = text.substring(BYTE_ORDER_MARK.length());
        }

        List<CSVRecord> records;
        try (CSVParser parser = CSVParser.parse(text, CSVFormat.DEFAULT)) {
            records = parser.getRecords();
        } catch (IOException | UncheckedIOException | IllegalStateException exception) {
            throw new ScoreSheetException("The score sheet is not a valid CSV file: " + exception.getMessage(),
                    exception);
        }
        if (records.isEmpty()) {
            throw new ScoreSheetException("The score sheet is empty. Import the CSV exported from the LMS.");
        }

        List<String> header = records.getFirst().toList();
        int usernameColumn = requireColumn(header, USERNAME_COLUMN);
        int numeratorColumn = requireColumn(header, NUMERATOR_COLUMN);

        List<String> texts = recordTexts(text, records);
        List<Row> rows = new ArrayList<>();
        Set<String> usernames = new HashSet<>();
        for (int index = 1; index < records.size(); index++) {
            CSVRecord record = records.get(index);
            if (record.size() != header.size()) {
                throw new ScoreSheetException("Line " + record.getRecordNumber() + " has " + record.size()
                        + " values, but the header has " + header.size()
                        + ". Repair the row in the LMS export, then import it again.");
            }
            Row row = new Row(record.toList(), texts.get(index));
            String username = normalizeUsername(row.values().get(usernameColumn));
            if (!username.isEmpty() && !usernames.add(username)) {
                throw new ScoreSheetException("The username " + username + " appears more than once. "
                        + "Remove the duplicate row so each score has exactly one destination.");
            }
            rows.add(row);
        }
        return new ScoreSheet(file, bytes, byteOrderMark, header, texts.getFirst(), rows,
                usernameColumn, numeratorColumn);
    }

    public Path path() {
        return path;
    }

    public List<String> header() {
        return header;
    }

    public int rowCount() {
        return rows.size();
    }

    /** The row's username without the LMS '#' prefix. */
    public String username(int row) {
        return stripTextPrefix(rows.get(row).values().get(usernameColumn).strip());
    }

    public String numerator(int row) {
        return rows.get(row).values().get(numeratorColumn);
    }

    boolean matchesFileOnDisk() throws IOException {
        return Files.isRegularFile(path) && Arrays.equals(originalBytes, Files.readAllBytes(path));
    }

    /** Returns the file content with only the given rows' score cells replaced. */
    byte[] withNumerators(Map<Integer, String> numeratorsByRow) {
        StringBuilder content = new StringBuilder(hasByteOrderMark ? BYTE_ORDER_MARK : "");
        content.append(headerText);
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            String numerator = numeratorsByRow.get(index);
            if (numerator == null) {
                content.append(row.text());
            } else {
                List<String> values = new ArrayList<>(row.values());
                values.set(numeratorColumn, numerator);
                content.append(formatRecord(values)).append(lineEnding(row.text()));
            }
        }
        return content.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String normalizeUsername(String value) {
        return stripTextPrefix(value == null ? "" : value.strip()).toLowerCase(Locale.ROOT);
    }

    private static String stripTextPrefix(String value) {
        return value.startsWith(LMS_TEXT_PREFIX) ? value.substring(LMS_TEXT_PREFIX.length()) : value;
    }

    private static String decode(byte[] bytes, Path file) throws ScoreSheetException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new ScoreSheetException("The score sheet is not UTF-8 text: " + file
                    + ". Save it as \"CSV UTF-8\", then import it again.", exception);
        }
    }

    private static int requireColumn(List<String> header, String name) throws ScoreSheetException {
        for (int index = 0; index < header.size(); index++) {
            if (header.get(index).strip().equalsIgnoreCase(name)) {
                return index;
            }
        }
        throw new ScoreSheetException("The score sheet has no \"" + name + "\" column. "
                + "Import the score sheet CSV exported from the LMS.");
    }

    // Each record's original text, including its line ending and any blank lines that follow it.
    private static List<String> recordTexts(String text, List<CSVRecord> records) {
        List<String> texts = new ArrayList<>();
        for (int index = 0; index < records.size(); index++) {
            int start = index == 0 ? 0 : (int) records.get(index).getCharacterPosition();
            int end = index + 1 < records.size() ? (int) records.get(index + 1).getCharacterPosition() : text.length();
            texts.add(text.substring(start, end));
        }
        return texts;
    }

    private static String lineEnding(String recordText) {
        int end = recordText.length();
        while (end > 0 && (recordText.charAt(end - 1) == '\n' || recordText.charAt(end - 1) == '\r')) {
            end--;
        }
        return recordText.substring(end);
    }

    // Quotes only values that need it, so unchanged cells keep their original spelling.
    private static String formatRecord(List<String> values) {
        List<String> cells = new ArrayList<>();
        for (String value : values) {
            boolean needsQuotes = value.contains(",") || value.contains("\"")
                    || value.contains("\n") || value.contains("\r");
            cells.add(needsQuotes ? "\"" + value.replace("\"", "\"\"") + "\"" : value);
        }
        return String.join(",", cells);
    }

    private record Row(List<String> values, String text) {
    }
}
