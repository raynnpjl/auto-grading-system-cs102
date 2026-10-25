package web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import grading.AssessmentFixtures;
import grading.assessment.AssessmentDefinition;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebApplicationTest {

    @TempDir
    Path temporary;

    private WebApplication app;
    private String base;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void startServer() throws IOException {
        app = new WebApplication();
        base = "http://127.0.0.1:" + app.start(0);
    }

    @AfterEach
    void stopServer() {
        app.stop();
    }

    @Test
    void servesThePageAndItsBundledLibraries() throws Exception {
        HttpResponse<String> page = get("/");
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("CS102 Auto Grading System"));
        assertEquals(200, get("/lib/vue.global.prod.js").statusCode());
        assertEquals(404, get("/lib/nope.js").statusCode());
    }

    @Test
    void rejectsAnUploadThatIsNotAZip() throws Exception {
        for (String path : List.of("/api/testers", "/api/submissions")) {
            HttpResponse<String> response = post(path, "garbage".getBytes());
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("not a ZIP file"));
        }
    }

    @Test
    void refusesToGradeBeforeTestersAreUploaded() throws Exception {
        HttpResponse<String> response = post("/api/grade", new byte[0]);
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Upload valid tester files first."));
    }

    @Test
    void offersNoDownloadsBeforeGrading() throws Exception {
        assertEquals(404, get("/api/reports/zip").statusCode());
        HttpResponse<String> sheet = get("/api/scoresheet/download");
        assertEquals(404, sheet.statusCode());
        assertTrue(sheet.body().contains("Upload a score sheet first."));
    }

    @Test
    void gradesABatchThenRegradesOneStudent() throws Exception {
        Path testers = createTesters();
        Path batch = temporary.resolve("batch");
        createSubmission(batch, "alice", 1.0);
        createSubmission(batch, "bob", 2.0);

        HttpResponse<String> testerReply = post("/api/testers", zipFolder(testers));
        assertEquals(200, testerReply.statusCode());
        assertTrue(testerReply.body().contains("\"valid\":true"), testerReply.body());

        HttpResponse<String> submissionReply = post("/api/submissions", zipFolder(batch));
        assertEquals(200, submissionReply.statusCode());
        assertEquals("{\"total\":2,\"ready\":2,\"skipped\":0}", submissionReply.body());

        assertEquals(202, post("/api/grade", new byte[0]).statusCode());
        String progress = waitForGradingToFinish();
        assertEquals(5.0, totalOf(progress, "alice"));
        assertEquals(10.0, totalOf(progress, "bob"));

        HttpResponse<byte[]> pdf = getBytes("/api/reports/student?username=alice");
        assertEquals(200, pdf.statusCode());
        assertTrue(pdf.headers().firstValue("Content-Type").orElse("").startsWith("application/pdf"));
        assertTrue(new String(pdf.body(), 0, 4).equals("%PDF"));

        HttpResponse<byte[]> zip = getBytes("/api/reports/zip");
        assertEquals(200, zip.statusCode());
        assertTrue(zip.headers().firstValue("Content-Type").orElse("").startsWith("application/zip"));

        assertEquals(202, post("/api/grade/student?username=alice", new byte[0]).statusCode());
        progress = waitForGradingToFinish();
        assertEquals(10.0, totalOf(progress, "bob"));
        assertEquals(5.0, totalOf(progress, "alice"));

        HttpResponse<String> unknown = post("/api/grade/student?username=nobody", new byte[0]);
        assertEquals(404, unknown.statusCode());
        assertTrue(unknown.body().contains("Unknown student."));
    }

    @Test
    void refusesUploadsWhileGradingRuns() throws Exception {
        Path testers = createTesters();
        write("testers/Q1aTester.java", "public class Q1aTester extends Q1a { public static void main(String[] args)"
                + " throws InterruptedException { Thread.sleep(3000); System.out.println(score()); } }");
        Path batch = temporary.resolve("batch");
        createSubmission(batch, "alice", 1.0);
        byte[] testerZip = zipFolder(testers);
        byte[] batchZip = zipFolder(batch);

        assertEquals(200, post("/api/testers", testerZip).statusCode());
        assertEquals(200, post("/api/submissions", batchZip).statusCode());
        assertEquals(202, post("/api/grade", new byte[0]).statusCode());

        HttpResponse<String> testerReply = post("/api/testers", testerZip);
        assertEquals(409, testerReply.statusCode());
        assertTrue(testerReply.body().contains("Wait until grading has finished."), testerReply.body());
        HttpResponse<String> submissionReply = post("/api/submissions", batchZip);
        assertEquals(409, submissionReply.statusCode());
        assertTrue(submissionReply.body().contains("Wait until grading has finished."), submissionReply.body());

        assertEquals(5.0, totalOf(waitForGradingToFinish(), "alice"));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).build(), BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> getBytes(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).build(), BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> post(String path, byte[] body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        return http.send(request, BodyHandlers.ofString());
    }

    private byte[] zipFolder(Path folder) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes); Stream<Path> files = Files.walk(folder)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String name = folder.getFileName() + "/" + folder.relativize(file).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(name));
                zip.write(Files.readAllBytes(file));
                zip.closeEntry();
            }
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
        return bytes.toByteArray();
    }

    private String waitForGradingToFinish() throws Exception {
        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline) {
            String body = get("/api/progress").body();
            if (body.contains("\"running\":false") && body.contains("\"reportsReady\":true")) {
                return body;
            }
            Thread.sleep(250);
        }
        return fail("Grading did not finish within 120 seconds");
    }

    private double totalOf(String progressJson, String username) {
        Matcher matcher = Pattern.compile(
                "\"username\":\"" + Pattern.quote(username) + "\"[^\\]]*?\"total\":([0-9.]+)").matcher(progressJson);
        if (!matcher.find()) {
            fail("No total for " + username + " in " + progressJson);
        }
        return Double.parseDouble(matcher.group(1));
    }

    private Path createTesters() throws IOException {
        Path testersFolder = temporary.resolve("testers");
        for (String testerFile : AssessmentDefinition.testerFiles()) {
            String question = testerFile.replace("Tester.java", "");
            write("testers/" + testerFile, "public class " + question + "Tester extends " + question
                    + " { public static void main(String[] args) { System.out.println(score()); } }");
        }
        return testersFolder;
    }

    private void createSubmission(Path batch, String username, double score) throws IOException {
        AssessmentFixtures.writeDependencies(batch.resolve(username));
        for (var entry : AssessmentDefinition.sourceFiles().entrySet()) {
            String type = entry.getKey().replace(".java", "");
            Path file = batch.resolve(username).resolve(entry.getValue()).resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            String expression = type.equals("Q3") ? "ShapeComparator.score()" : Double.toString(score);
            Files.writeString(file, "/* Name: " + username + " Student\n * Email ID: " + username + "@smu.edu.sg */\n"
                    + "public class " + type + " { public static double score() { return " + expression + "; } }");
        }
    }

    private Path write(String relativePath, String content) throws IOException {
        Path file = temporary.resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }
}
