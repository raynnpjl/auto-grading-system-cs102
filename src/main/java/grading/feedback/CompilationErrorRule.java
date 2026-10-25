package grading.feedback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Explains why the code did not compile, at the line of the student's file where javac found each
 * problem. Errors javac reports many times for the same reason are given once, listing the lines.
 */
final class CompilationErrorRule implements FeedbackRule {

    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+([\\w.]+)\\.(\\w+)\\s*;");
    private static final Pattern CALL = Pattern.compile("\\b([a-z][\\w$]*)\\s*\\(");
    private static final String TESTER_SUFFIX = "Tester.java";
    private static final int MAX_TESTER_ERRORS = 3;

    @Override
    public List<Feedback> apply(FeedbackContext context) {
        var compilation = context.result().compilation();
        if (compilation == null || compilation.succeeded()) {
            return List.of();
        }
        List<Feedback> feedback = new ArrayList<>();
        Set<String> hiddenClasses = importClashes(context, feedback);
        List<JavacError> errors = JavacError.parse(compilation.stderr()).stream()
                .filter(error -> hiddenClasses.stream().noneMatch(name -> mentions(error, name)))
                .toList();
        List<JavacError> inStudentFiles = errors.stream().filter(error -> !isTester(error)).toList();
        if (!inStudentFiles.isEmpty()) {
            feedback.addAll(studentErrors(context, inStudentFiles));
        } else if (hiddenClasses.isEmpty()) {
            feedback.addAll(testerErrors(context, errors));
        }
        if (feedback.isEmpty()) {
            feedback.add(new Feedback(context.questionId(), "Compile error", "Your code did not compile. "
                    + "Compile it yourself to see javac's messages.", Optional.empty()));
        }
        context.explain(context.cases());
        return feedback;
    }

    // An import of another class with the same name as a class given with the question hides the given one.
    private static Set<String> importClashes(FeedbackContext context, List<Feedback> feedback) {
        Set<String> hidden = new TreeSet<>();
        for (SourceFile file : context.sources()) {
            for (int line = 1; line <= file.lines().size(); line++) {
                Matcher importLine = IMPORT.matcher(file.lines().get(line - 1));
                if (importLine.find() && context.givenClasses().contains(importLine.group(2))) {
                    String name = importLine.group(2);
                    hidden.add(name);
                    feedback.add(Feedback.at(context.questionId(), "Import hides a given class", "This import "
                            + "replaces the `" + name + "` class given with the question by `" + importLine.group(1)
                            + "." + name + "`, so the given class's methods and subclasses no longer fit. Remove "
                            + "this import.", file.at(line)));
                }
            }
        }
        return hidden;
    }

    // Errors with the same explanation are reported once, at the first line, listing the other lines.
    private static List<Feedback> studentErrors(FeedbackContext context, List<JavacError> errors) {
        Map<CompilerHints.Hint, List<SourceLocation>> byHint = new LinkedHashMap<>();
        for (JavacError error : errors) {
            SourceLocation location = context.submittedLocation(error.file(), error.line())
                    .orElse(new SourceLocation(error.file(), error.line(), error.code()));
            List<SourceLocation> locations = byHint.computeIfAbsent(CompilerHints.explain(error),
                    hint -> new ArrayList<>());
            if (locations.stream().noneMatch(seen -> seen.equals(location))) {
                locations.add(location);
            }
        }
        List<Feedback> feedback = new ArrayList<>();
        byHint.forEach((hint, locations) -> {
            String message = hint.message();
            if (locations.size() > 1) {
                message += " The same problem is also on line(s) " + locations.stream().skip(1)
                        .map(location -> String.valueOf(location.line())).distinct()
                        .collect(Collectors.joining(", ")) + ".";
            }
            feedback.add(Feedback.at(context.questionId(), hint.kind(), message, locations.getFirst()));
        });
        return feedback;
    }

    // The student's files compiled, but the tester could not use them: names or types differ from the question.
    private static List<Feedback> testerErrors(FeedbackContext context, List<JavacError> errors) {
        return errors.stream()
                .map(JavacError::message)
                .distinct()
                .limit(MAX_TESTER_ERRORS)
                .map(message -> {
                    JavacError error = errors.stream().filter(candidate -> candidate.message().equals(message))
                            .findFirst().orElseThrow();
                    return Feedback.at(context.questionId(), "Does not match the tester", "The tester could not "
                            + "use your code (javac: " + message + "). The method's name, parameters and return "
                            + "type must be exactly as given in the question.", calledMethod(context, error));
                })
                .toList();
    }

    // The declaration of the student's method that the tester's failing line calls, if any.
    private static SourceLocation calledMethod(FeedbackContext context, JavacError error) {
        Matcher call = CALL.matcher(error.code());
        while (call.find()) {
            Optional<FeedbackContext.Located> method = context.method(call.group(1));
            if (method.isPresent()) {
                return method.get().file().at(method.get().method().start());
            }
        }
        return null;
    }

    private static boolean isTester(JavacError error) {
        return error.file().endsWith(TESTER_SUFFIX);
    }

    private static boolean mentions(JavacError error, String className) {
        return Pattern.compile("\\b" + Pattern.quote(className) + "\\b").matcher(error.fullText()).find();
    }
}
