package web;

import static java.net.HttpURLConnection.HTTP_ACCEPTED;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static web.HttpResponses.sendError;
import static web.HttpResponses.sendFile;
import static web.HttpResponses.sendJson;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import grading.common.Json;
import grading.common.Messages;
import grading.config.GradingSettings;
import grading.execution.JavaGradingService;
import grading.execution.StudentGradingResult;
import grading.progress.ProgressTracker;
import grading.reporting.AttemptReportWriter;
import grading.reporting.GradingAttempt;
import grading.reporting.ReportArchive;
import grading.reporting.ScoreSheetUpdater;
import grading.scoring.ScoreBreakdown;
import grading.scoring.ScoreSummary;
import grading.style.StyleChecker;
import grading.submission.StudentIdentity;
import grading.submission.SubmissionCorrectionService;
import grading.submission.SubmissionFinding;
import grading.submission.SubmissionPreparationService;
import grading.submission.SubmissionValidationResult;
import grading.tester.TesterFileValidationService;
import grading.tester.TesterValidationResult;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The whole application as a web app: uploads, identity confirmation, grading progress, report
 * downloads and the filled score sheet, served on this computer at the port set by {@code ui.port}.
 */
public final class WebApplication {

    private static final String HOST = "127.0.0.1";
    private static final String EMPTY_UPLOAD =
            "The upload is empty or is not a ZIP file. Choose a folder that contains files.";
    private static final String NO_SUBMISSIONS = "No student submissions were found. Choose the folder "
            + "that holds the student ZIPs or folders, or one student's folder.";
    private static final String GRADING_RUNNING = "Wait until grading has finished.";

    /** One request handler; the route table maps each method and path to one. */
    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private final GradingSettings settings = GradingSettings.current();
    private final ProgressTracker progress = new ProgressTracker();
    private final TesterFileValidationService testerService = new TesterFileValidationService(settings);
    private final SubmissionPreparationService submissionService = new SubmissionPreparationService(settings);
    private final SubmissionCorrectionService correctionService = new SubmissionCorrectionService(submissionService);
    private final ScoreSheetUpdater scoreSheetUpdater = new ScoreSheetUpdater();
    private final AtomicBoolean grading = new AtomicBoolean(false);
    private final Map<String, Handler> routes = Map.ofEntries(
            Map.entry("GET /", HttpResponses::sendPage),
            Map.entry("GET /api/progress", exchange -> sendJson(exchange, progressJson())),
            Map.entry("GET /api/reports/zip", this::zipReports),
            Map.entry("GET /api/reports/student", this::studentReport),
            Map.entry("GET /api/scoresheet/preview", this::scoreSheetPreview),
            Map.entry("GET /api/scoresheet/download", this::downloadScoreSheet),
            Map.entry("POST /api/testers", this::importTesters),
            Map.entry("POST /api/submissions", this::importSubmissions),
            Map.entry("POST /api/identity", this::confirmIdentity),
            Map.entry("POST /api/grade", this::startGrading),
            Map.entry("POST /api/scoresheet", this::uploadScoreSheet));
    private final Path uploads;

    private volatile TesterValidationResult testers;
    private volatile List<SubmissionValidationResult> submissions = List.of();
    private volatile Path submissionWorkspace;
    // The latest finished attempt. Cleared whenever the submissions change, so links never show stale results.
    private volatile GradingAttempt latestAttempt;
    private volatile String latestAttemptId;
    // The folder AttemptReportWriter wrote for the latest attempt; the ZIP and PDF links read from it.
    private volatile Path latestAttemptFolder;
    // The score sheet the user uploaded, or null before one is uploaded.
    private volatile UploadedScoreSheet scoreSheet;

    public WebApplication() throws IOException {
        uploads = Files.createTempDirectory("auto-grader-web-");
    }

    public static void main(String[] args) throws IOException {
        int port = GradingSettings.current().port();
        new WebApplication().start(port);
        System.out.println("Auto-grader running at http://" + HOST + ":" + port);
    }

    /** Starts the web server. Only this computer can connect to it. */
    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(HOST, port), 0);
        // A thread pool, so a slow upload does not block the progress updates.
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::route);
        server.start();
    }

    /** Sends each request to the handler registered for its method and path. */
    private void route(HttpExchange exchange) throws IOException {
        String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
        Handler handler = routes.get(key);
        try {
            if (handler == null) {
                sendError(exchange, HTTP_NOT_FOUND, "Not found");
            } else {
                handler.handle(exchange);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            sendError(exchange, HTTP_INTERNAL_ERROR, Messages.describe(exception));
        }
    }

    /** POST /api/testers: the body is a zip of the Tester-Files folder. */
    private void importTesters(HttpExchange exchange) throws IOException {
        Optional<Path> folder = extractUpload(exchange, "testers-");
        if (folder.isEmpty()) {
            sendError(exchange, HTTP_BAD_REQUEST, EMPTY_UPLOAD);
            return;
        }
        TesterValidationResult result = testerService.validate(folder.get());
        testers = result;
        sendJson(exchange, "{\"valid\":" + result.isValid()
                + ",\"messages\":" + Json.strings(result.findings().stream().map(SubmissionFinding::message).toList())
                + "}");
    }

    /**
     * POST /api/submissions: the body is a zip whose top level holds the student ZIPs or folders
     * (or one student's folder on its own).
     */
    private void importSubmissions(HttpExchange exchange) throws IOException {
        Optional<Path> input = extractUpload(exchange, "submissions-");
        if (input.isEmpty()) {
            sendError(exchange, HTTP_BAD_REQUEST, EMPTY_UPLOAD);
            return;
        }
        Path workspace = Files.createDirectories(uploads.resolve("normalized-" + UUID.randomUUID()));
        List<SubmissionValidationResult> prepared = submissionService.prepareBatch(input.get(), workspace);
        // Keep the previous import when nothing usable was uploaded.
        if (prepared.isEmpty()) {
            sendError(exchange, HTTP_BAD_REQUEST, NO_SUBMISSIONS);
            return;
        }
        submissions = List.copyOf(prepared);
        submissionWorkspace = workspace;
        clearAttempt();
        progress.prepare(prepared);

        long ready = prepared.stream().filter(SubmissionValidationResult::isReadyForGrading).count();
        sendJson(exchange, "{\"total\":" + prepared.size()
                + ",\"ready\":" + ready
                + ",\"skipped\":" + (prepared.size() - ready) + "}");
    }

    /** Extracts the uploaded ZIP into a new folder; empty when the upload was empty or not a ZIP. */
    private Optional<Path> extractUpload(HttpExchange exchange, String prefix) throws IOException {
        Path folder = Files.createDirectories(uploads.resolve(prefix + UUID.randomUUID()));
        return UploadedFolder.extract(exchange.getRequestBody().readAllBytes(), folder);
    }

    /** POST /api/identity: a form with target (the current username), username, name and email. */
    private synchronized void confirmIdentity(HttpExchange exchange) throws IOException {
        if (grading.get()) {
            sendError(exchange, HTTP_CONFLICT, GRADING_RUNNING);
            return;
        }
        Map<String, String> form = HttpResponses.form(exchange);
        String target = form.getOrDefault("target", "");
        Optional<SubmissionValidationResult> current = submissions.stream()
                .filter(submission -> submission.username().equals(target))
                .findFirst();
        if (current.isEmpty()) {
            sendError(exchange, HTTP_NOT_FOUND, "Unknown student.");
            return;
        }

        StudentIdentity identity;
        try {
            identity = new StudentIdentity(form.getOrDefault("username", ""),
                    form.getOrDefault("name", ""), form.getOrDefault("email", ""));
        } catch (IllegalArgumentException exception) {
            sendError(exchange, HTTP_BAD_REQUEST, exception.getMessage());
            return;
        }
        replaceSubmission(current.get(), identity);
        sendJson(exchange, "{\"ok\":true}");
    }

    /** Re-validates one submission with the identity the marker confirmed and swaps it into the batch. */
    private void replaceSubmission(SubmissionValidationResult current, StudentIdentity identity) throws IOException {
        Path correctionFolder = Files.createDirectories(
                submissionWorkspace.resolve("guided-" + UUID.randomUUID()));
        SubmissionValidationResult updated = correctionService.applyIdentity(current, identity, correctionFolder);

        List<SubmissionValidationResult> list = new ArrayList<>(submissions);
        list.replaceAll(submission -> submission == current ? updated : submission);
        submissions = List.copyOf(list);
        clearAttempt();
        progress.prepare(submissions);
    }

    /** POST /api/grade: starts grading in the background and returns straight away. */
    private void startGrading(HttpExchange exchange) throws IOException {
        TesterValidationResult testerResult = testers;
        List<SubmissionValidationResult> batch = submissions;
        if (testerResult == null || !testerResult.isValid()) {
            sendError(exchange, HTTP_BAD_REQUEST, "Upload valid tester files first.");
            return;
        }
        if (batch.stream().noneMatch(SubmissionValidationResult::isReadyForGrading)) {
            sendError(exchange, HTTP_BAD_REQUEST, "No submission is ready for grading.");
            return;
        }
        if (!grading.compareAndSet(false, true)) {
            sendError(exchange, HTTP_CONFLICT, "Grading is already running.");
            return;
        }

        clearAttempt();
        progress.prepare(batch);
        new Thread(() -> runGrading(batch, testerResult), "grading-runner").start();
        sendJson(exchange, HTTP_ACCEPTED, "{\"started\":true}");
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
            attempt.students().forEach(student -> progress.feedback(student.username(), student.feedback()));
            progress.similarity(attempt.similarity().pairs());
            AttemptReportWriter.Written written = new AttemptReportWriter(settings.reportsFolder()).write(attempt);
            log.accept("Grading complete. Reports: " + written.folder().map(Path::toString).orElse("not written"));

            // Set last, so download links appear only once the reports exist.
            latestAttemptId = written.attemptId();
            latestAttemptFolder = written.folder().orElse(null);
            latestAttempt = attempt;
        } catch (Exception exception) {
            log.accept("Grading failed: " + exception.getMessage());
            exception.printStackTrace();
        } finally {
            progress.finish();
            grading.set(false);
        }
    }

    /** GET /api/reports/zip: the attempt folder as one ZIP, the same file the JavaFX "Save reports" button writes. */
    private void zipReports(HttpExchange exchange) throws IOException {
        Path folder = latestAttemptFolder;
        if (folder == null || !Files.isDirectory(folder)) {
            sendError(exchange, HTTP_NOT_FOUND, "No grading results yet. Run grading first.");
            return;
        }
        Path zip = Files.createTempFile(uploads, "reports-", ".zip");
        try {
            ReportArchive.save(folder, zip);
            sendFile(exchange, "application/zip", Files.readAllBytes(zip),
                    "grading-reports-" + latestAttemptId + ".zip");
        } finally {
            Files.deleteIfExists(zip);
        }
    }

    /** GET /api/reports/student?username=...: one student's report, read from the attempt folder. */
    private void studentReport(HttpExchange exchange) throws IOException {
        Path folder = latestAttemptFolder;
        String username = HttpResponses.query(exchange).getOrDefault("username", "");
        Optional<Path> report = folder == null
                ? Optional.empty()
                : AttemptReportWriter.studentReportOf(folder, username);
        if (report.isEmpty()) {
            sendError(exchange, HTTP_NOT_FOUND, "No report for this student yet. Run grading first.");
            return;
        }
        String fileName = report.get().getFileName().toString();
        String type = fileName.endsWith(".pdf") ? "application/pdf" : "text/plain; charset=UTF-8";
        sendFile(exchange, type, Files.readAllBytes(report.get()), fileName);
    }

    /** POST /api/scoresheet?name=...: the body is the score sheet CSV the user chose. */
    private synchronized void uploadScoreSheet(HttpExchange exchange) throws IOException {
        if (grading.get()) {
            sendError(exchange, HTTP_CONFLICT, GRADING_RUNNING);
            return;
        }
        String name = HttpResponses.query(exchange).getOrDefault("name", "score-sheet.csv");
        try {
            scoreSheet = UploadedScoreSheet.load(exchange.getRequestBody().readAllBytes(), name, uploads);
        } catch (IOException exception) {
            scoreSheet = null;
            sendError(exchange, HTTP_BAD_REQUEST, Messages.describe(exception));
            return;
        }
        sendJson(exchange, "{\"ok\":true}");
    }

    /** GET /api/scoresheet/preview: what filling the uploaded sheet would change, as JSON. */
    private void scoreSheetPreview(HttpExchange exchange) throws IOException {
        UploadedScoreSheet sheet = scoreSheet;
        sendJson(exchange, sheet == null
                ? "{\"loaded\":false}"
                : sheet.previewJson(scoreSheetUpdater, currentScores(), latestAttempt != null));
    }

    /** GET /api/scoresheet/download: the uploaded sheet with this run's grades filled in. */
    private void downloadScoreSheet(HttpExchange exchange) throws IOException {
        UploadedScoreSheet sheet = scoreSheet;
        if (grading.get()) {
            sendError(exchange, HTTP_CONFLICT, GRADING_RUNNING);
        } else if (sheet == null) {
            sendError(exchange, HTTP_NOT_FOUND, "Upload a score sheet first.");
        } else if (latestAttempt == null) {
            sendError(exchange, HTTP_CONFLICT, "Run grading first, then download the filled score sheet.");
        } else {
            sendFile(exchange, "text/csv; charset=UTF-8", sheet.filledCsv(scoreSheetUpdater, currentScores()),
                    sheet.downloadName());
        }
    }

    /** The progress JSON, plus the flags the page needs to know what is ready. */
    private String progressJson() {
        boolean testersReady = testers != null && testers.isValid();
        boolean submissionsReady = submissions.stream().anyMatch(SubmissionValidationResult::isReadyForGrading);
        String base = progress.toJson(); // starts with "{"
        return "{\"testersReady\":" + testersReady
                + ",\"submissionsReady\":" + submissionsReady
                + ",\"reportsReady\":" + (latestAttemptFolder != null)
                + ",\"scoreSheetName\":" + Json.quote(scoreSheet == null ? "" : scoreSheet.name())
                + "," + base.substring(1);
    }

    /** Forgets the latest attempt, so no link points at results from an older run. */
    private void clearAttempt() {
        latestAttempt = null;
        latestAttemptId = null;
        latestAttemptFolder = null;
    }

    /** The totals of the latest attempt, as they are written to a score sheet. */
    private ScoreSummary currentScores() {
        GradingAttempt attempt = latestAttempt;
        if (attempt == null) {
            return new ScoreSummary(List.of());
        }
        return new ScoreSummary(attempt.scores().stream().map(ScoreBreakdown::toStudentScore).toList());
    }
}
