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
import grading.common.FileTrees;
import grading.common.Json;
import grading.common.Messages;
import grading.config.GradingSettings;
import grading.progress.ProgressTracker;
import grading.reporting.AttemptReportWriter;
import grading.reporting.ReportArchive;
import grading.reporting.ScoreSheetUpdater;
import grading.session.GradingSession;
import grading.session.SessionRefusal;
import grading.submission.StudentIdentity;
import grading.submission.SubmissionFinding;
import grading.submission.SubmissionValidationResult;
import grading.tester.TesterValidationResult;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The whole application as a web app: uploads, identity confirmation, grading progress, report
 * downloads and the filled score sheet, served on this computer at the port set by {@code ui.port}.
 * It only speaks HTTP; what is loaded and graded lives in the {@link GradingSession}.
 */
public final class WebApplication {

    private static final String HOST = "127.0.0.1";
    private static final String EMPTY_UPLOAD =
            "The upload is empty or is not a ZIP file. Choose a folder that contains files.";

    /** One request handler; the route table maps each method and path to one. */
    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private final GradingSettings settings = GradingSettings.current();
    private final ProgressTracker progress = new ProgressTracker();
    private final ScoreSheetUpdater scoreSheetUpdater = new ScoreSheetUpdater();
    private final Map<String, Handler> routes = Map.ofEntries(
            Map.entry("GET /", HttpResponses::sendPage),
            // The page's libraries are packaged with the application, so no internet connection is needed.
            Map.entry("GET /lib/bootstrap.min.css", library("bootstrap.min.css", HttpResponses.CSS)),
            Map.entry("GET /lib/vue.global.prod.js", library("vue.global.prod.js", HttpResponses.JAVASCRIPT)),
            Map.entry("GET /lib/jszip.min.js", library("jszip.min.js", HttpResponses.JAVASCRIPT)),
            Map.entry("GET /api/progress", exchange -> sendJson(exchange, progressJson())),
            Map.entry("GET /api/reports/zip", this::zipReports),
            Map.entry("GET /api/reports/student", this::studentReport),
            Map.entry("GET /api/scoresheet/preview", this::scoreSheetPreview),
            Map.entry("GET /api/scoresheet/download", this::downloadScoreSheet),
            Map.entry("POST /api/testers", this::importTesters),
            Map.entry("POST /api/submissions", this::importSubmissions),
            Map.entry("POST /api/identity", this::confirmIdentity),
            Map.entry("POST /api/grade", this::startGrading),
            Map.entry("POST /api/grade/student", this::regradeStudent),
            Map.entry("POST /api/scoresheet", this::uploadScoreSheet));
    private final Path uploads;
    private final GradingSession session;
    private HttpServer server;
    private ExecutorService requestThreads;

    // The score sheet the user uploaded, or null before one is uploaded.
    private volatile UploadedScoreSheet scoreSheet;

    public WebApplication() throws IOException {
        uploads = Files.createTempDirectory("auto-grader-web-");
        session = new GradingSession(settings, progress, uploads);
        // Uploads and normalized copies are only needed while the app runs; remove them when it stops.
        Runtime.getRuntime().addShutdownHook(new Thread(this::deleteUploads, "delete-uploads"));
    }

    private void deleteUploads() {
        try {
            FileTrees.delete(uploads);
        } catch (IOException exception) {
            System.err.println("Could not delete the temporary uploads in " + uploads + ": "
                    + Messages.describe(exception));
        }
    }

    public static void main(String[] args) throws IOException {
        int port = GradingSettings.current().port();
        new WebApplication().start(port);
        System.out.println("Auto-grader running at http://" + HOST + ":" + port);
    }

    /**
     * Starts the web server and returns the port it listens on. Pass 0 to let the system choose a free
     * port, as the tests do. Only this computer can connect to it.
     */
    public int start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(HOST, port), 0);
        // A thread pool, so a slow upload does not block the progress updates.
        requestThreads = Executors.newCachedThreadPool();
        server.setExecutor(requestThreads);
        server.createContext("/", this::route);
        server.start();
        return server.getAddress().getPort();
    }

    /** Stops the web server. Used by the tests; the application itself runs until the process ends. */
    public void stop() {
        if (server != null) {
            server.stop(0);
            requestThreads.shutdownNow();
        }
    }

    private static Handler library(String fileName, String type) {
        return exchange -> HttpResponses.sendResource(exchange, "/web/lib/" + fileName, type);
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
        } catch (SessionRefusal refusal) {
            sendError(exchange, statusOf(refusal.reason()), refusal.getMessage());
        } catch (Exception exception) {
            exception.printStackTrace();
            sendError(exchange, HTTP_INTERNAL_ERROR, Messages.describe(exception));
        }
    }

    private static int statusOf(SessionRefusal.Reason reason) {
        return switch (reason) {
            case INVALID -> HTTP_BAD_REQUEST;
            case NOT_FOUND -> HTTP_NOT_FOUND;
            case BUSY -> HTTP_CONFLICT;
        };
    }

    /** Refuses before an upload is read, so a busy session does not unpack a large body for nothing. */
    private void refuseWhileGrading() {
        if (session.grading()) {
            throw new SessionRefusal(SessionRefusal.Reason.BUSY, GradingSession.GRADING_RUNNING);
        }
    }

    /** POST /api/testers: the body is a zip of the Tester-Files folder. Refused while grading is running. */
    private void importTesters(HttpExchange exchange) throws IOException {
        refuseWhileGrading();
        Optional<Path> folder = extractUpload(exchange, "testers-");
        if (folder.isEmpty()) {
            sendError(exchange, HTTP_BAD_REQUEST, EMPTY_UPLOAD);
            return;
        }
        TesterValidationResult result = session.importTesters(folder.get());
        sendJson(exchange, "{\"valid\":" + result.isValid()
                + ",\"messages\":" + Json.strings(result.findings().stream().map(SubmissionFinding::message).toList())
                + "}");
    }

    /**
     * POST /api/submissions: the body is a zip whose top level holds the student ZIPs or folders
     * (or one student's folder on its own). Refused while grading is running.
     */
    private void importSubmissions(HttpExchange exchange) throws IOException {
        refuseWhileGrading();
        Optional<Path> input = extractUpload(exchange, "submissions-");
        if (input.isEmpty()) {
            sendError(exchange, HTTP_BAD_REQUEST, EMPTY_UPLOAD);
            return;
        }
        List<SubmissionValidationResult> prepared = session.importSubmissions(input.get());
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
    private void confirmIdentity(HttpExchange exchange) throws IOException {
        refuseWhileGrading();
        Map<String, String> form = HttpResponses.form(exchange);
        StudentIdentity identity;
        try {
            identity = new StudentIdentity(form.getOrDefault("username", ""),
                    form.getOrDefault("name", ""), form.getOrDefault("email", ""));
        } catch (IllegalArgumentException exception) {
            sendError(exchange, HTTP_BAD_REQUEST, exception.getMessage());
            return;
        }
        session.confirmIdentity(form.getOrDefault("target", ""), identity);
        sendJson(exchange, "{\"ok\":true}");
    }

    /** POST /api/grade: starts grading in the background and returns straight away. */
    private void startGrading(HttpExchange exchange) throws IOException {
        session.startGrading();
        sendJson(exchange, HTTP_ACCEPTED, "{\"started\":true}");
    }

    /** POST /api/grade/student?username=...: grades one student again; everyone else keeps their result. */
    private void regradeStudent(HttpExchange exchange) throws IOException {
        session.regrade(HttpResponses.query(exchange).getOrDefault("username", ""));
        sendJson(exchange, HTTP_ACCEPTED, "{\"started\":true}");
    }

    /** GET /api/reports/zip: the attempt folder, with summary.pdf and every student report, as one ZIP. */
    private void zipReports(HttpExchange exchange) throws IOException {
        Path folder = session.reportsFolder().orElse(null);
        if (folder == null || !Files.isDirectory(folder)) {
            sendError(exchange, HTTP_NOT_FOUND, "No grading results yet. Run grading first.");
            return;
        }
        Path zip = Files.createTempFile(uploads, "reports-", ".zip");
        try {
            ReportArchive.save(folder, zip);
            sendFile(exchange, "application/zip", Files.readAllBytes(zip),
                    "grading-reports-" + session.latestAttemptId() + ".zip");
        } finally {
            Files.deleteIfExists(zip);
        }
    }

    /** GET /api/reports/student?username=...: one student's report, read from the attempt folder. */
    private void studentReport(HttpExchange exchange) throws IOException {
        String username = HttpResponses.query(exchange).getOrDefault("username", "");
        Optional<Path> report = session.reportsFolder()
                .flatMap(folder -> AttemptReportWriter.studentReportOf(folder, username));
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
        if (session.grading()) {
            sendError(exchange, HTTP_CONFLICT, GradingSession.GRADING_RUNNING);
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
                : sheet.previewJson(scoreSheetUpdater, session.currentScores(), session.hasResults()));
    }

    /** GET /api/scoresheet/download: the uploaded sheet with this run's grades filled in. */
    private void downloadScoreSheet(HttpExchange exchange) throws IOException {
        UploadedScoreSheet sheet = scoreSheet;
        if (session.grading()) {
            sendError(exchange, HTTP_CONFLICT, GradingSession.GRADING_RUNNING);
        } else if (sheet == null) {
            sendError(exchange, HTTP_NOT_FOUND, "Upload a score sheet first.");
        } else if (!session.hasResults()) {
            sendError(exchange, HTTP_CONFLICT, "Run grading first, then download the filled score sheet.");
        } else {
            sendFile(exchange, "text/csv; charset=UTF-8",
                    sheet.filledCsv(scoreSheetUpdater, session.currentScores()), sheet.downloadName());
        }
    }

    /** The progress JSON, plus the flags the page needs to know what is ready. */
    private String progressJson() {
        String base = progress.toJson(); // starts with "{"
        return "{\"testersReady\":" + session.testersReady()
                + ",\"submissionsReady\":" + session.submissionsReady()
                + ",\"reportsReady\":" + session.reportsFolder().isPresent()
                + ",\"scoreSheetName\":" + Json.quote(scoreSheet == null ? "" : scoreSheet.name())
                + "," + base.substring(1);
    }
}
