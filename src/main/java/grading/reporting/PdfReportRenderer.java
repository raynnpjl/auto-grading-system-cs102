package grading.reporting;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Lays out a report written in the small Markdown subset produced by the report formatters as an
 * A4 PDF: headings, paragraphs, bullet lists, tables with wrapped cells, code blocks, and bold,
 * italic and code text. Characters the standard PDF fonts cannot show are replaced, so unusual
 * student output never stops a report from being written.
 */
final class PdfReportRenderer {

    private static final float MARGIN = 50;
    private static final float WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;
    private static final float TOP = PDRectangle.A4.getHeight() - MARGIN;
    private static final float BOTTOM = MARGIN + 20;
    private static final float BODY_SIZE = 9.5f;
    private static final float TABLE_SIZE = 8;
    private static final float CODE_SIZE = 7.5f;
    private static final float CELL_PADDING = 3;
    private static final float BULLET_INDENT = 12;
    private static final float MIN_COLUMN = 36;
    private static final float GREY_FILL = 0.93f;
    private static final float GRID_GREY = 0.6f;
    private static final float GRID_LINE_WIDTH = 0.4f;
    private static final float FOOTER_GREY = 0.45f;
    private static final float FOOTER_SIZE = 7.5f;
    private static final float LEADING = 1.3f;
    private static final float KEEP_WITH_HEADING = 60;
    // Font sizes and the space above each heading level (#, ## and ###).
    private static final float TITLE_SIZE = 16;
    private static final float TITLE_SPACE = 4;
    private static final float SECTION_SIZE = 13;
    private static final float SECTION_SPACE = 10;
    private static final float SUBSECTION_SIZE = 11;
    private static final float SUBSECTION_SPACE = 6;
    // Vertical gaps, as fractions of the text size: a blank line or code block, below a heading, below a table.
    private static final float HALF_LINE = 0.5f;
    private static final float HEADING_GAP = 0.3f;
    private static final float TABLE_GAP = 0.6f;
    // The bullet sits this far into the indent before the bullet's text.
    private static final float BULLET_OFFSET = 0.75f;
    // PDFBox measures glyph widths in thousandths of the font size.
    private static final float GLYPH_UNITS = 1000;
    // PDFBox indexes the system fonts the first time it creates a font and logs a warning for every
    // font it cannot read; those warnings say nothing about the report, so only errors are shown.
    private static final Logger PDFBOX_LOG = Logger.getLogger("org.apache.pdfbox");

    static {
        PDFBOX_LOG.setLevel(Level.SEVERE);
    }

    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDFont italic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
    private final PDFont mono = new PDType1Font(Standard14Fonts.FontName.COURIER);
    private final Map<Character, Boolean> printable = new HashMap<>();

    private PDDocument document;
    private PDPageContentStream content;
    private float y;

    /** Writes the report to {@code pdf}; the footer of every page shows {@code footer} and the page number. */
    void render(String markdown, Path pdf, String footer) throws IOException {
        try (PDDocument output = new PDDocument()) {
            document = output;
            newPage();
            layOut(markdown.replace("\r", "").split("\n", -1));
            content.close();
            addFooters(footer);
            output.getDocumentInformation().setTitle(footer);
            output.save(pdf.toFile());
        } finally {
            document = null;
            content = null;
        }
    }

    private void layOut(String[] lines) throws IOException {
        int index = 0;
        StringBuilder paragraph = new StringBuilder();
        while (index < lines.length) {
            String line = lines[index];
            if (line.startsWith("|") || line.startsWith("```") || line.startsWith("#") || line.isBlank()
                    || line.startsWith("- ") || line.startsWith("  - ")) {
                flushParagraph(paragraph);
            }
            if (line.startsWith("```")) {
                List<String> code = new ArrayList<>();
                index++;
                while (index < lines.length && !lines[index].startsWith("```")) {
                    code.add(lines[index++]);
                }
                codeBlock(code);
            } else if (line.startsWith("|")) {
                List<String> rows = new ArrayList<>();
                while (index < lines.length && lines[index].startsWith("|")) {
                    rows.add(lines[index++]);
                }
                table(rows);
                continue;
            } else if (line.startsWith("### ")) {
                heading(line.substring(4), SUBSECTION_SIZE, SUBSECTION_SPACE);
            } else if (line.startsWith("## ")) {
                heading(line.substring(3), SECTION_SIZE, SECTION_SPACE);
            } else if (line.startsWith("# ")) {
                heading(line.substring(2), TITLE_SIZE, TITLE_SPACE);
            } else if (line.startsWith("  - ")) {
                bullet(line.substring(4), 2 * BULLET_INDENT);
            } else if (line.startsWith("- ")) {
                bullet(line.substring(2), BULLET_INDENT);
            } else if (line.isBlank()) {
                y -= BODY_SIZE * HALF_LINE;
            } else {
                paragraph.append(paragraph.isEmpty() ? "" : " ").append(line.strip());
            }
            index++;
        }
        flushParagraph(paragraph);
    }

    private void flushParagraph(StringBuilder paragraph) throws IOException {
        if (!paragraph.isEmpty()) {
            for (List<Run> line : wrap(runs(paragraph.toString(), regular), WIDTH, BODY_SIZE)) {
                drawLine(line, MARGIN, BODY_SIZE);
            }
            paragraph.setLength(0);
        }
    }

    private void heading(String text, float size, float spaceBefore) throws IOException {
        y -= spaceBefore;
        List<List<Run>> lines = wrap(runs(text, bold), WIDTH, size);
        // keep a heading with the start of its section, such as a table's header and first row
        ensureSpace(lines.size() * size * LEADING + KEEP_WITH_HEADING);
        for (List<Run> line : lines) {
            drawLine(line, MARGIN, size);
        }
        y -= size * HEADING_GAP;
    }

    private void bullet(String text, float indent) throws IOException {
        List<List<Run>> lines = wrap(runs(text, regular), WIDTH - indent, BODY_SIZE);
        ensureSpace(BODY_SIZE * LEADING);
        drawText(List.of(new Run("•", regular)), MARGIN + indent - BULLET_INDENT * BULLET_OFFSET, y - BODY_SIZE,
                BODY_SIZE);
        for (List<Run> line : lines) {
            drawLine(line, MARGIN + indent, BODY_SIZE);
        }
    }

    private void codeBlock(List<String> code) throws IOException {
        List<String> wrapped = new ArrayList<>();
        float charWidth = mono.getStringWidth("M") / GLYPH_UNITS * CODE_SIZE;
        int perLine = Math.max(1, (int) ((WIDTH - 2 * CELL_PADDING) / charWidth));
        for (String line : code) {
            String text = printableText(line.replace("\t", "    "), mono);
            do {
                wrapped.add(text.substring(0, Math.min(perLine, text.length())));
                text = text.length() > perLine ? text.substring(perLine) : "";
            } while (!text.isEmpty());
        }
        float lineHeight = CODE_SIZE * LEADING;
        for (String line : wrapped) {
            ensureSpace(lineHeight + CELL_PADDING);
            fill(MARGIN, y - lineHeight, WIDTH, lineHeight, GREY_FILL);
            drawText(List.of(new Run(line, mono)), MARGIN + CELL_PADDING, y - CODE_SIZE, CODE_SIZE);
            y -= lineHeight;
        }
        y -= BODY_SIZE * HALF_LINE;
    }

    private void table(List<String> rows) throws IOException {
        List<List<String>> cells = new ArrayList<>();
        for (String row : rows) {
            if (!row.replaceAll("[|\\-\\s:]", "").isEmpty() || !row.contains("---")) {
                cells.add(splitRow(row));
            }
        }
        if (cells.isEmpty()) {
            return;
        }
        int columns = cells.stream().mapToInt(List::size).max().orElse(1);
        float[] widths = columnWidths(cells, columns);
        List<String> header = cells.getFirst();
        // keep the header with the first row instead of leaving it alone at the foot of a page
        float firstRow = cells.size() > 1 ? rowHeight(cells.get(1), widths) : 0;
        ensureSpace(rowHeight(header, widths) + firstRow);
        drawRow(header, widths, true);
        for (List<String> row : cells.subList(1, cells.size())) {
            float height = rowHeight(row, widths);
            if (y - height < BOTTOM) {
                newPage();
                drawRow(header, widths, true);
            }
            drawRow(row, widths, false);
        }
        y -= BODY_SIZE * TABLE_GAP;
    }

    private void drawRow(List<String> row, float[] widths, boolean header) throws IOException {
        float height = rowHeight(row, widths);
        ensureSpace(height);
        float x = MARGIN;
        if (header) {
            fill(MARGIN, y - height, sum(widths), height, GREY_FILL);
        }
        for (int column = 0; column < widths.length; column++) {
            String text = column < row.size() ? row.get(column) : "";
            float lineY = y - CELL_PADDING - TABLE_SIZE;
            for (List<Run> line : cellLines(text, widths[column], header)) {
                drawText(line, x + CELL_PADDING, lineY, TABLE_SIZE);
                lineY -= TABLE_SIZE * LEADING;
            }
            x += widths[column];
        }
        grid(widths, height);
        y -= height;
    }

    private float rowHeight(List<String> row, float[] widths) throws IOException {
        int lines = 1;
        for (int column = 0; column < widths.length; column++) {
            String text = column < row.size() ? row.get(column) : "";
            lines = Math.max(lines, cellLines(text, widths[column], false).size());
        }
        return lines * TABLE_SIZE * LEADING + 2 * CELL_PADDING;
    }

    private List<List<Run>> cellLines(String text, float width, boolean header) throws IOException {
        List<List<Run>> lines = new ArrayList<>();
        for (String part : text.split("<br>", -1)) {
            lines.addAll(wrap(runs(part, header ? bold : regular), width - 2 * CELL_PADDING, TABLE_SIZE));
        }
        return lines;
    }

    // Wide columns get the space their content needs; the rest is shared in proportion to content.
    private float[] columnWidths(List<List<String>> rows, int columns) throws IOException {
        float[] natural = new float[columns];
        float[] minimum = new float[columns];
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            // the header row is drawn in bold, which is wider
            PDFont font = rowIndex == 0 ? bold : regular;
            for (int column = 0; column < row.size(); column++) {
                for (String part : row.get(column).split("<br>", -1)) {
                    List<Run> runs = runs(part, font);
                    natural[column] = Math.max(natural[column], width(runs, TABLE_SIZE) + 2 * CELL_PADDING);
                    for (Run run : runs) {
                        for (String word : run.text().split(" ")) {
                            float wordWidth = run.font().getStringWidth(word) / GLYPH_UNITS * TABLE_SIZE + 2 * CELL_PADDING;
                            minimum[column] = Math.max(minimum[column], Math.min(wordWidth, WIDTH / 4));
                        }
                    }
                }
            }
        }
        float[] widths = new float[columns];
        float naturalTotal = 0;
        float minimumTotal = 0;
        for (int column = 0; column < columns; column++) {
            minimum[column] = Math.max(MIN_COLUMN, minimum[column]);
            natural[column] = Math.max(minimum[column], natural[column]);
            naturalTotal += natural[column];
            minimumTotal += minimum[column];
        }
        for (int column = 0; column < columns; column++) {
            if (naturalTotal <= WIDTH) {
                widths[column] = natural[column] * WIDTH / naturalTotal;
            } else if (minimumTotal >= WIDTH) {
                widths[column] = minimum[column] * WIDTH / minimumTotal;
            } else {
                float extra = (natural[column] - minimum[column]) / (naturalTotal - minimumTotal);
                widths[column] = minimum[column] + extra * (WIDTH - minimumTotal);
            }
        }
        return widths;
    }

    private static List<String> splitRow(String row) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        String inner = row.strip();
        inner = inner.substring(1, inner.endsWith("|") && inner.length() > 1 ? inner.length() - 1 : inner.length());
        for (int index = 0; index < inner.length(); index++) {
            char current = inner.charAt(index);
            if (current == '\\' && index + 1 < inner.length()) {
                cell.append(current).append(inner.charAt(++index));
            } else if (current == '|') {
                cells.add(cell.toString().strip());
                cell.setLength(0);
            } else {
                cell.append(current);
            }
        }
        cells.add(cell.toString().strip());
        return cells;
    }

    /** Splits Markdown inline text into runs of one font: **bold**, *italic*, `code` and [links](...). */
    private List<Run> runs(String text, PDFont base) throws IOException {
        List<Run> runs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean strong = base == bold;
        boolean emphasis = false;
        int index = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character == '\\' && index + 1 < text.length()) {
                current.append(text.charAt(index + 1));
                index += 2;
            } else if (character == '`') {
                int end = text.indexOf('`', index + 1);
                if (end < 0) {
                    current.append(character);
                    index++;
                    continue;
                }
                addRun(runs, current, strong, emphasis);
                runs.add(new Run(printableText(unescape(text.substring(index + 1, end)), mono), mono));
                index = end + 1;
            } else if (text.startsWith("**", index)) {
                addRun(runs, current, strong, emphasis);
                strong = !strong;
                index += 2;
            } else if (character == '*') {
                addRun(runs, current, strong, emphasis);
                emphasis = !emphasis;
                index++;
            } else if (character == '[' && text.indexOf("](", index) > index && text.indexOf(')', index) > index) {
                int labelEnd = text.indexOf("](", index);
                current.append(text, index + 1, labelEnd);
                index = text.indexOf(')', labelEnd) + 1;
            } else {
                current.append(character);
                index++;
            }
        }
        addRun(runs, current, strong, emphasis);
        return runs;
    }

    private void addRun(List<Run> runs, StringBuilder text, boolean strong, boolean emphasis) {
        if (!text.isEmpty()) {
            PDFont font = strong ? bold : emphasis ? italic : regular;
            runs.add(new Run(printableText(text.toString(), font), font));
            text.setLength(0);
        }
    }

    private static String unescape(String text) {
        return text.replace("\\|", "|").replace("\\\\", "\\");
    }

    /** Breaks runs into lines no wider than {@code width}, splitting a word only when it is longer than a line. */
    private List<List<Run>> wrap(List<Run> runs, float width, float size) throws IOException {
        List<List<Run>> lines = new ArrayList<>();
        List<Run> line = new ArrayList<>();
        float used = 0;
        for (Run run : runs) {
            for (String piece : run.text().split("(?<= )")) {
                float pieceWidth = run.font().getStringWidth(piece) / GLYPH_UNITS * size;
                if (used + pieceWidth > width && used > 0) {
                    lines.add(line);
                    line = new ArrayList<>();
                    used = 0;
                    piece = piece.stripLeading();
                    pieceWidth = run.font().getStringWidth(piece) / GLYPH_UNITS * size;
                }
                while (pieceWidth > width && piece.length() > 1) {
                    int fit = fitting(piece, run.font(), size, width);
                    lines.add(List.of(new Run(piece.substring(0, fit), run.font())));
                    piece = piece.substring(fit);
                    pieceWidth = run.font().getStringWidth(piece) / GLYPH_UNITS * size;
                }
                line.add(new Run(piece, run.font()));
                used += pieceWidth;
            }
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(line);
        }
        return lines;
    }

    private static int fitting(String text, PDFont font, float size, float width) throws IOException {
        int fit = 1;
        while (fit < text.length() && font.getStringWidth(text.substring(0, fit + 1)) / GLYPH_UNITS * size <= width) {
            fit++;
        }
        return fit;
    }

    private static float width(List<Run> runs, float size) throws IOException {
        float width = 0;
        for (Run run : runs) {
            width += run.font().getStringWidth(run.text()) / GLYPH_UNITS * size;
        }
        return width;
    }

    private void drawLine(List<Run> line, float x, float size) throws IOException {
        ensureSpace(size * LEADING);
        drawText(line, x, y - size, size);
        y -= size * LEADING;
    }

    private void drawText(List<Run> line, float x, float baseline, float size) throws IOException {
        float cursor = x;
        for (Run run : line) {
            if (run.text().isEmpty()) {
                continue;
            }
            content.beginText();
            content.setFont(run.font(), size);
            content.newLineAtOffset(cursor, baseline);
            content.showText(run.text());
            content.endText();
            cursor += run.font().getStringWidth(run.text()) / GLYPH_UNITS * size;
        }
    }

    private void fill(float x, float bottom, float width, float height, float grey) throws IOException {
        content.setNonStrokingColor(grey, grey, grey);
        content.addRect(x, bottom, width, height);
        content.fill();
        content.setNonStrokingColor(0f, 0f, 0f);
    }

    private void grid(float[] widths, float height) throws IOException {
        content.setStrokingColor(GRID_GREY, GRID_GREY, GRID_GREY);
        content.setLineWidth(GRID_LINE_WIDTH);
        content.addRect(MARGIN, y - height, sum(widths), height);
        float x = MARGIN;
        for (int column = 0; column < widths.length - 1; column++) {
            x += widths[column];
            content.moveTo(x, y);
            content.lineTo(x, y - height);
        }
        content.stroke();
        content.setStrokingColor(0f, 0f, 0f);
    }

    private static float sum(float[] values) {
        float total = 0;
        for (float value : values) {
            total += value;
        }
        return total;
    }

    private void ensureSpace(float height) throws IOException {
        if (y - height < BOTTOM) {
            newPage();
        }
    }

    private void newPage() throws IOException {
        if (content != null) {
            content.close();
        }
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        content = new PDPageContentStream(document, page);
        y = TOP;
    }

    private void addFooters(String footer) throws IOException {
        int pages = document.getNumberOfPages();
        for (int number = 1; number <= pages; number++) {
            PDPage page = document.getPage(number - 1);
            try (PDPageContentStream stream = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                stream.setNonStrokingColor(FOOTER_GREY, FOOTER_GREY, FOOTER_GREY);
                stream.beginText();
                stream.setFont(regular, FOOTER_SIZE);
                stream.newLineAtOffset(MARGIN, MARGIN / 2);
                stream.showText(printableText(footer + "   |   Page " + number + " of " + pages, regular));
                stream.endText();
            }
        }
    }

    // The standard PDF fonts cover Western European text only; anything else is replaced.
    private String printableText(String text, PDFont font) {
        StringBuilder printableText = new StringBuilder(text.length());
        // by code point, so a character outside the basic plane (such as an emoji) becomes one '?'
        for (int character : text.codePoints().toArray()) {
            if (character == '\t') {
                printableText.append("    ");
            } else if (Character.isISOControl(character)) {
                continue;
            } else if (character <= Character.MAX_VALUE && canShow((char) character, font)) {
                printableText.append((char) character);
            } else {
                printableText.append(switch (character) {
                    case '→' -> "->";
                    case '←' -> "<-";
                    case '≤' -> "<=";
                    case '≥' -> ">=";
                    case '✓' -> "OK";
                    default -> "?";
                });
            }
        }
        return printableText.toString();
    }

    private boolean canShow(char character, PDFont font) {
        return printable.computeIfAbsent(character, key -> {
            try {
                font.encode(String.valueOf(key));
                return true;
            } catch (IOException | IllegalArgumentException exception) {
                return false;
            }
        });
    }

    /** Text drawn in one font. */
    private record Run(String text, PDFont font) {
    }
}
