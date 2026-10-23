package web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import grading.assessment.AssessmentDefinition;
import grading.config.GradingSettings;
import grading.execution.JavaGradingService;
import grading.execution.QuestionGradingResult;
import grading.execution.StudentGradingResult;
import grading.progress.ProgressTracker;
import grading.reporting.AttemptReportWriter;
import grading.reporting.GradingAttempt;
import grading.style.StyleChecker;
import grading.submission.StudentIdentity;
import grading.submission.SubmissionCorrectionService;
import grading.submission.SubmissionFinding;
import grading.submission.SubmissionPreparationService;
import grading.submission.SubmissionValidationResult;
import grading.tester.TesterFileValidationService;
import grading.tester.TesterValidationResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The whole application as a web app: uploads, identity confirmation, grading progress and the
 * CSV download, all served on http://127.0.0.1:8080.
 */
public final class WebApplication {

    private static final int PORT = 8080;

    private final GradingSettings settings = GradingSettings.current();
    private final ProgressTracker progress = new ProgressTracker();
    private final TesterFileValidationService testerService = new TesterFileValidationService(settings);
    private final SubmissionPreparationService submissionService = new SubmissionPreparationService(settings);
    private final SubmissionCorrectionService correctionService = new SubmissionCorrectionService(submissionService);
    private final AtomicBoolean grading = new AtomicBoolean(false);
    private final Path uploads;

    private volatile TesterValidationResult testers;
    private volatile List<SubmissionValidationResult> submissions = List.of();
    private volatile Path submissionWorkspace;
    private volatile List<StudentGradingResult> latestResults = List.of();

    public WebApplication() throws IOException {
        uploads = Files.createTempDirectory("auto-grader-web-");
    }

    public static void main(String[] args) throws IOException {
        new WebApplication().start(PORT);
        System.out.println("Auto-grader running at http://127.0.0.1:" + PORT);
    }

    /** Starts the web server. Only this computer can connect to it. */
    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        // A thread pool, so a slow upload does not block the progress updates.
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::route);
        server.start();
    }

    /** Sends each request to the right handler based on its method and path. */
    private void route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        try {
            if (method.equals("GET") && path.equals("/")) {
                send(exchange, 200, "text/html; charset=UTF-8", page(), Map.of());
            } else if (method.equals("GET") && path.equals("/api/progress")) {
                sendJson(exchange, 200, progressJson());
            } else if (method.equals("GET") && path.equals("/api/export.csv")) {
                exportCsv(exchange);
            } else if (method.equals("POST") && path.equals("/api/testers")) {
                importTesters(exchange);
            } else if (method.equals("POST") && path.equals("/api/submissions")) {
                importSubmissions(exchange);
            } else if (method.equals("POST") && path.equals("/api/identity")) {
                confirmIdentity(exchange);
            } else if (method.equals("POST") && path.equals("/api/grade")) {
                startGrading(exchange);
            } else {
                sendJson(exchange, 404, "{\"error\":\"Not found\"}");
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            sendJson(exchange, 500, "{\"error\":" + quote(String.valueOf(exception.getMessage())) + "}");
        }
    }

    /** POST /api/testers: the body is a zip of the Tester-Files folder. */
    private void importTesters(HttpExchange exchange) throws IOException {
        Path folder = Files.createDirectories(uploads.resolve("testers-" + UUID.randomUUID()));
        unzip(exchange.getRequestBody().readAllBytes(), folder);
        
        // Unwrap root folder if JSZip archived a single outer folder
        Path targetFolder = folder;
        try (var stream = Files.list(folder)) {
            List<Path> list = stream.toList();
            if (list.size() == 1 && Files.isDirectory(list.get(0))) {
                targetFolder = list.get(0);
            }
        }

        TesterValidationResult result = testerService.validate(targetFolder);
        testers = result;
        sendJson(exchange, 200, "{\"valid\":" + result.isValid()
                + ",\"messages\":" + messages(result.findings()) + "}");
    }

    /**
     * POST /api/submissions: the body is a zip whose top level holds the student ZIPs or folders
     * (or one student's folder on its own).
     */
    /**
     * POST /api/submissions: accepts any uploaded archive or file bundle containing student submissions.
     */
    private void importSubmissions(HttpExchange exchange) throws IOException {
        Path input = Files.createDirectories(uploads.resolve("submissions-" + UUID.randomUUID()));
        unzip(exchange.getRequestBody().readAllBytes(), input);

        // Unwrap root folder if JSZip archived a single outer folder
        Path targetInput = input;
        try (var stream = Files.list(input)) {
            List<Path> list = stream.toList();
            if (list.size() == 1 && Files.isDirectory(list.get(0))) {
                targetInput = list.get(0);
            }
        }

        Path workspace = Files.createDirectories(uploads.resolve("normalized-" + UUID.randomUUID()));

        List<SubmissionValidationResult> prepared = submissionService.prepareBatch(targetInput, workspace);
        submissions = List.copyOf(prepared);
        submissionWorkspace = workspace;
        latestResults = List.of();
        progress.prepare(prepared);

        long ready = prepared.stream().filter(SubmissionValidationResult::isReadyForGrading).count();
        sendJson(exchange, 200, "{\"total\":" + prepared.size()
                + ",\"ready\":" + ready
                + ",\"skipped\":" + (prepared.size() - ready) + "}");
    }

    /** POST /api/identity: a form with target (the current username), username, name and email. */
    private synchronized void confirmIdentity(HttpExchange exchange) throws IOException {
        if (grading.get()) {
            sendJson(exchange, 409, "{\"error\":\"Wait until grading has finished.\"}");
            return;
        }
        Map<String, String> form = parseForm(exchange.getRequestBody().readAllBytes());
        String target = form.getOrDefault("target", "");
        SubmissionValidationResult current = submissions.stream()
                .filter(submission -> submission.username().equals(target))
                .findFirst()
                .orElse(null);
        if (current == null) {
            sendJson(exchange, 404, "{\"error\":\"Unknown student.\"}");
            return;
        }

        StudentIdentity identity;
        try {
            identity = new StudentIdentity(form.getOrDefault("username", ""),
                    form.getOrDefault("name", ""), form.getOrDefault("email", ""));
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, "{\"error\":" + quote(exception.getMessage()) + "}");
            return; 
        }

        Path correctionFolder = Files.createDirectories(
                submissionWorkspace.resolve("guided-" + UUID.randomUUID()));
        SubmissionValidationResult updated = correctionService.applyIdentity(current, identity, correctionFolder);

        List<SubmissionValidationResult> list = new ArrayList<>(submissions);
        list.replaceAll(submission -> submission == current ? updated : submission);
        submissions = List.copyOf(list);
        progress.prepare(submissions);
        sendJson(exchange, 200, "{\"ok\":true}");
    }

    /** POST /api/grade: starts grading in the background and returns straight away. */
    private void startGrading(HttpExchange exchange) throws IOException {
        TesterValidationResult testerResult = testers;
        List<SubmissionValidationResult> batch = submissions;
        if (testerResult == null || !testerResult.isValid()) {
            sendJson(exchange, 400, "{\"error\":\"Upload valid tester files first.\"}");
            return;
        }
        if (batch.stream().noneMatch(SubmissionValidationResult::isReadyForGrading)) {
            sendJson(exchange, 400, "{\"error\":\"No submission is ready for grading.\"}");
            return;
        }
        if (!grading.compareAndSet(false, true)) {
            sendJson(exchange, 409, "{\"error\":\"Grading is already running.\"}");
            return;
        }

        progress.prepare(batch);
        new Thread(() -> runGrading(batch, testerResult), "grading-runner").start();
        sendJson(exchange, 202, "{\"started\":true}");
    }

    /** Does the grading. Runs in its own thread so the page can keep asking for progress. */
    private void runGrading(List<SubmissionValidationResult> batch, TesterValidationResult testerResult) {
        Consumer<String> log = message -> {
            System.out.println(message);
            progress.log(message);
        };
        try {
            LocalDateTime started = LocalDateTime.now();
            List<StudentGradingResult> results =
                    new JavaGradingService(log, progress).gradeBatch(batch, testerResult);
            GradingAttempt attempt = GradingAttempt.assemble(started, testerResult.source(), uploads,
                    settings, batch, results, new StyleChecker(settings.style()));
            AttemptReportWriter.Written written = new AttemptReportWriter(settings.reportsFolder()).write(attempt);
            latestResults = List.copyOf(results);
            log.accept("Grading complete. Reports: " + written.folder().map(Path::toString).orElse("not written"));
        } catch (Exception exception) {
            log.accept("Grading failed: " + exception.getMessage());
            exception.printStackTrace();
        } finally {
            progress.finish();
            grading.set(false);
        }
    }

    /** GET /api/export.csv: one row per graded student, one column per question part. */
    private void exportCsv(HttpExchange exchange) throws IOException {
        List<StudentGradingResult> results = latestResults;
        if (results.isEmpty()) {
            sendJson(exchange, 404, "{\"error\":\"No grading results yet. Run grading first.\"}");
            return;
        }
        StringBuilder csv = new StringBuilder("Username");
        for (AssessmentDefinition.Question question : AssessmentDefinition.QUESTIONS) {
            csv.append(',').append(question.id());
        }
        csv.append(",Total\n");

        for (StudentGradingResult student : results) {
            csv.append(student.username());
            for (AssessmentDefinition.Question question : AssessmentDefinition.QUESTIONS) {
                csv.append(',').append(scoreOf(student, question.id()));
            }
            csv.append(',').append(student.totalScore()).append('\n');
        }
        send(exchange, 200, "text/csv; charset=UTF-8", csv.toString(),
                Map.of("Content-Disposition", "attachment; filename=grades.csv"));
    }

    /** The progress JSON, plus two flags the page needs to know what is ready. */
    private String progressJson() {
        boolean testersReady = testers != null && testers.isValid();
        boolean submissionsReady = submissions.stream().anyMatch(SubmissionValidationResult::isReadyForGrading);
        String base = progress.toJson();          // starts with "{"
        return "{\"testersReady\":" + testersReady
                + ",\"submissionsReady\":" + submissionsReady
                + "," + base.substring(1);
    }

    // ----- helpers -----

    private static double scoreOf(StudentGradingResult student, String questionId) {
        return student.questions().stream()
                .filter(question -> question.questionId().equals(questionId))
                .mapToDouble(QuestionGradingResult::score)
                .sum();
    }

    private static String messages(List<SubmissionFinding> findings) {
        return findings.stream()
                .map(finding -> quote(finding.message()))
                .collect(Collectors.joining(",", "[", "]"));
    }

    /** Reads "a=1&b=2" into a map, decoding the %20-style escapes. */
    private static Map<String, String> parseForm(byte[] body) {
        Map<String, String> values = new HashMap<>();
        for (String pair : new String(body, StandardCharsets.UTF_8).split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                String key = URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
                values.put(key, value);
            }
        }
        return values;
    }

    /** Extracts a zip into a folder. Refuses entries that would be written outside that folder. */
    private static void unzip(byte[] zipBytes, Path destination) throws IOException {
        Path root = destination.toAbsolutePath().normalize();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root)) {
                    throw new IOException("Unsafe path in the ZIP: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    private static String page() throws IOException {
        try (InputStream input = WebApplication.class.getResourceAsStream("/web/index.html")) {
            if (input == null) {
                throw new IOException("web/index.html is missing from the build");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        send(exchange, status, "application/json; charset=UTF-8", json, Map.of());
    }

    private static void send(HttpExchange exchange, int status, String type, String text,
            Map<String, String> headers) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        headers.forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Wraps text in quotes and escapes characters that are not allowed raw in JSON. */
    static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}