package grading.style;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.PropertiesExpander;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AuditListener;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.api.SeverityLevel;
import grading.assessment.AssessmentDefinition;
import grading.common.Messages;
import grading.config.StyleSettings;
import grading.submission.SubmissionValidationResult;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks each student's source files against the CS102 style guide with Checkstyle and scores
 * them. The files are checked as the student submitted them, before any header repair, and lines
 * kept from the instructor's template are left out. A file that cannot be analysed is recorded
 * with the reason; checking never stops because of one submission.
 */
public final class StyleChecker {

    private static final Pattern PARSE_POSITION = Pattern.compile("(\\d+:\\d+: [^\\r\\n]+)");
    private static final int MAX_CODE_LENGTH = 100;
    private static final String GENERAL_EXCEPTION_KEY = "general.exception";

    private final StyleSettings settings;
    private final StyleRules rules;
    private final Map<String, Optional<Path>> templates = new HashMap<>();
    private Configuration configuration;
    private String configurationProblem;

    public StyleChecker(StyleSettings settings) {
        this.settings = settings;
        this.rules = StyleRules.load(settings.rules());
    }

    public StyleRules rules() {
        return rules;
    }

    /** The Checkstyle release in use, for the attempt summary. */
    public static String checkstyleVersion() {
        String version = Checker.class.getPackage().getImplementationVersion();
        return version == null ? "unknown" : version;
    }

    /** Never throws: anything that goes wrong is recorded in the result. */
    public StudentStyleResult check(SubmissionValidationResult submission) {
        try {
            return checkSources(submission.username(), orderedSources(submission));
        } catch (RuntimeException exception) {
            return StudentStyleResult.notChecked(submission.username(), settings.maxScore(),
                    "Style could not be checked: " + Messages.describe(exception));
        }
    }

    private StudentStyleResult checkSources(String username, Map<String, Path> sources) {
        if (sources.isEmpty()) {
            return StudentStyleResult.notChecked(username, settings.maxScore(),
                    "No source files were available to check.");
        }
        Optional<Configuration> loaded = configuration();
        if (loaded.isEmpty()) {
            List<FileStyleResult> files = sources.keySet().stream()
                    .map(name -> FileStyleResult.couldNotAnalyse(name, configurationProblem))
                    .toList();
            return new StudentStyleResult(username, files, OptionalDouble.empty(), settings.maxScore(),
                    List.of(configurationProblem));
        }

        Audit audit = new Audit();
        List<String> notes = new ArrayList<>();
        try {
            Checker checker = new Checker();
            checker.setModuleClassLoader(StyleChecker.class.getClassLoader());
            checker.configure(loaded.get());
            checker.addListener(audit);
            try {
                checker.process(sources.values().stream().map(Path::toFile).toList());
            } finally {
                checker.destroy();
            }
        } catch (CheckstyleException | RuntimeException exception) {
            notes.add("Checkstyle stopped early: " + Messages.describe(exception));
        }

        List<FileStyleResult> files = new ArrayList<>();
        for (Map.Entry<String, Path> source : sources.entrySet()) {
            files.add(fileResult(source.getKey(), source.getValue(), audit));
        }
        if (rules.problem().isPresent()) {
            notes.add(rules.problem().get());
        }
        return new StudentStyleResult(username, files, score(files), settings.maxScore(), notes);
    }

    private FileStyleResult fileResult(String fileName, Path file, Audit audit) {
        String key = file.toAbsolutePath().normalize().toString();
        if (audit.failures.containsKey(key)) {
            return FileStyleResult.couldNotAnalyse(fileName, "Could not analyse this file: "
                    + audit.failures.get(key));
        }
        if (!audit.started.contains(key)) {
            return FileStyleResult.couldNotAnalyse(fileName, "Could not analyse this file: Checkstyle did not "
                    + "reach it.");
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return FileStyleResult.couldNotAnalyse(fileName, "Could not analyse this file: it could not be read ("
                    + Messages.describe(exception) + ").");
        }
        Optional<Path> template = template(fileName);
        Set<Integer> keptLines = Set.of();
        String note = "No template file was found for " + fileName + ", so every line was checked.";
        if (template.isPresent()) {
            try {
                keptLines = TemplateLines.kept(lines, Files.readAllLines(template.get(), StandardCharsets.UTF_8));
                note = "";
            } catch (IOException exception) {
                note = "The template file could not be read, so every line was checked.";
            }
        }
        List<StyleViolation> violations = new ArrayList<>();
        for (AuditEvent event : audit.events.getOrDefault(key, List.of())) {
            if (event.getLine() > 0 && keptLines.contains(event.getLine())) {
                continue;
            }
            violations.add(new StyleViolation(event.getLine(), event.getColumn(), rules.rule(event.getModuleId()),
                    event.getMessage(), codeAt(lines, event.getLine())));
        }
        return new FileStyleResult(fileName, true, violations, keptLines.size(), note);
    }

    private OptionalDouble score(List<FileStyleResult> files) {
        if (files.stream().noneMatch(FileStyleResult::analysed)) {
            return OptionalDouble.empty();
        }
        double deducted = files.stream()
                .flatMap(file -> file.countedViolationList().stream())
                .mapToDouble(violation -> violation.rule().weight())
                .sum();
        return OptionalDouble.of(Math.max(0, settings.maxScore() - deducted));
    }

    // The unmodified sources, in the order the assessment lists them.
    private static Map<String, Path> orderedSources(SubmissionValidationResult submission) {
        Map<String, Path> ordered = new LinkedHashMap<>();
        for (String fileName : AssessmentDefinition.sourceFiles().keySet()) {
            Path source = submission.originalSources().get(fileName);
            if (source != null) {
                ordered.put(fileName, source);
            }
        }
        return ordered;
    }

    private synchronized Optional<Configuration> configuration() {
        if (configuration == null && configurationProblem == null) {
            Path file = settings.checkstyleConfig();
            if (!Files.isRegularFile(file)) {
                configurationProblem = "Style could not be checked: the Checkstyle configuration " + file
                        + " was not found.";
            } else {
                try {
                    configuration = ConfigurationLoader.loadConfiguration(file.toString(),
                            new PropertiesExpander(new Properties()));
                } catch (CheckstyleException | RuntimeException exception) {
                    configurationProblem = "Style could not be checked: the Checkstyle configuration " + file
                            + " is invalid (" + Messages.describe(exception) + ").";
                }
            }
        }
        return Optional.ofNullable(configuration);
    }

    private synchronized Optional<Path> template(String fileName) {
        return templates.computeIfAbsent(fileName.toLowerCase(Locale.ROOT), name -> {
            if (!Files.isDirectory(settings.templateFolder())) {
                return Optional.empty();
            }
            try (Stream<Path> files = Files.walk(settings.templateFolder())) {
                return files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).equals(name))
                        .findFirst();
            } catch (IOException exception) {
                return Optional.empty();
            }
        });
    }

    private static String codeAt(List<String> lines, int line) {
        if (line < 1 || line > lines.size()) {
            return "";
        }
        String code = lines.get(line - 1).strip();
        return code.length() > MAX_CODE_LENGTH ? code.substring(0, MAX_CODE_LENGTH) + "..." : code;
    }

    /** Collects Checkstyle's events by file, separating violations from files it could not parse. */
    private static final class Audit implements AuditListener {

        private final Map<String, List<AuditEvent>> events = new HashMap<>();
        private final Map<String, String> failures = new HashMap<>();
        private final Set<String> started = new HashSet<>();

        @Override
        public void auditStarted(AuditEvent event) {
        }

        @Override
        public void auditFinished(AuditEvent event) {
        }

        @Override
        public void fileStarted(AuditEvent event) {
            started.add(key(event));
        }

        @Override
        public void fileFinished(AuditEvent event) {
        }

        @Override
        public void addError(AuditEvent event) {
            boolean parseFailure = event.getSeverityLevel() == SeverityLevel.ERROR
                    || GENERAL_EXCEPTION_KEY.equals(event.getViolation().getKey());
            if (parseFailure) {
                failures.putIfAbsent(key(event), reason(event.getMessage()));
            } else {
                events.computeIfAbsent(key(event), file -> new ArrayList<>()).add(event);
            }
        }

        @Override
        public void addException(AuditEvent event, Throwable throwable) {
            failures.putIfAbsent(key(event), reason(Messages.describe(throwable)));
        }

        private static String key(AuditEvent event) {
            return new File(event.getFileName()).toPath().toAbsolutePath().normalize().toString();
        }

        // Checkstyle reports a parse failure with a stack trace; keep only the position and problem.
        private static String reason(String message) {
            Matcher position = PARSE_POSITION.matcher(message == null ? "" : message);
            String detail = position.find() ? "line " + position.group(1) : "the Java code could not be parsed";
            return "it does not parse as Java (" + detail + "). Fix the syntax error and grade again "
                    + "to check its style.";
        }
    }
}
