package grading.submission;

import grading.common.Messages;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the student name and email that each Java file should declare in its leading comment,
 * reports headers that disagree with the submission's owner, and adds missing details.
 */
final class StudentHeaders {

    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    // "Name:" must start its comment line, so a line such as "File name: Q1a.java" is not a name.
    private static final Pattern NAME = Pattern.compile(
            "(?im)^[\\s*/]*(?:student\\s+name|name)\\s*[:=-]\\s*([^\\r\\n*/]{2,80})");
    private static final String BYTE_ORDER_MARK = "﻿";

    private final IdentitySuggestions suggestions;
    private final long maxFileBytes;

    StudentHeaders(IdentitySuggestions suggestions, long maxFileBytes) {
        this.suggestions = suggestions;
        this.maxFileBytes = maxFileBytes;
    }

    /** The one name and email shared by the submission's headers; empty where they disagree. */
    HeaderIdentity discover(List<Path> javaFiles, List<SubmissionFinding> findings) {
        Set<String> names = new HashSet<>();
        Set<String> emails = new HashSet<>();
        for (Path javaFile : javaFiles) {
            try {
                if (Files.size(javaFile) > maxFileBytes) {
                    continue;
                }
                HeaderIdentity identity = read(Files.readString(javaFile, StandardCharsets.UTF_8));
                identity.name().map(String::trim).filter(Predicate.not(String::isBlank)).ifPresent(names::add);
                identity.email().map(value -> value.toLowerCase(Locale.ROOT)).ifPresent(emails::add);
            } catch (IOException exception) {
                findings.add(SubmissionFinding.error("HEADER_UNREADABLE",
                        "Could not inspect " + javaFile.getFileName() + ": " + Messages.describe(exception)));
            }
        }

        if (names.size() > 1 || emails.size() > 1) {
            findings.add(SubmissionFinding.error("CONFLICTING_HEADER_IDENTITIES",
                    "Java headers contain conflicting student identities; existing values were not replaced."));
        }
        Optional<String> name = names.size() == 1 ? names.stream().findFirst() : Optional.empty();
        Optional<String> email = emails.size() == 1 ? emails.stream().findFirst() : Optional.empty();
        return new HeaderIdentity(name, email);
    }

    /**
     * Reports an email that belongs to someone else, and adds a missing name or email to the
     * normalized copy using the submission's shared header identity or its username.
     *
     * @return the header lines that were added, empty when the header was complete
     */
    List<String> checkAndRepair(Path javaFile, String username, HeaderIdentity submissionIdentity,
            List<SubmissionFinding> findings) throws IOException {
        String source = Files.readString(javaFile, StandardCharsets.UTF_8);
        HeaderIdentity fileIdentity = read(source);
        boolean missingName = fileIdentity.name().isEmpty();
        boolean missingEmail = fileIdentity.email().isEmpty();
        Optional<String> repairEmail = submissionIdentity.email().or(() -> suggestions.email(username));

        if (fileIdentity.email().isPresent() && !emailMatchesUsername(fileIdentity.email().get(), username)) {
            findings.add(SubmissionFinding.error("HEADER_IDENTITY_MISMATCH",
                    javaFile.getFileName() + " contains an email that does not match username " + username + "."));
        }

        List<String> additions = new ArrayList<>();
        if (missingName) {
            additions.add(" * Student name: "
                    + submissionIdentity.name().orElseGet(() -> suggestions.displayName(username)));
        }
        if (missingEmail) {
            additions.add(repairEmail.map(email -> " * Student email: " + email)
                    .orElse(" * Student username: " + username));
        }
        if (!additions.isEmpty()) {
            String repairedHeader = "/*\n" + String.join("\n", additions) + "\n */\n";
            Files.writeString(javaFile, repairedHeader + source, StandardCharsets.UTF_8);
        }
        List<String> added = additions.stream().map(line -> line.replaceFirst("^ \\* ", "")).toList();

        if (missingName) {
            findings.add(SubmissionFinding.corrected("MISSING_HEADER_NAME",
                    javaFile.getFileName() + " had no student name in its header; one was added."));
        }
        if (missingEmail && repairEmail.isPresent()) {
            findings.add(SubmissionFinding.corrected("MISSING_HEADER_EMAIL",
                    javaFile.getFileName() + " had no email in its header; " + repairEmail.get()
                            + " was added automatically from the trusted submission identity."));
        } else if (missingEmail) {
            findings.add(SubmissionFinding.error("MISSING_HEADER_EMAIL",
                    javaFile.getFileName() + " has no email in its header and no trustworthy email "
                            + "was available for automatic repair."));
        }
        return added;
    }

    private HeaderIdentity read(String source) {
        String header = leadingComment(source);
        Matcher email = EMAIL.matcher(header);
        Matcher name = NAME.matcher(header);
        // A "Name:" line followed only by spaces has no name.
        return new HeaderIdentity(
                name.find() && !name.group(1).isBlank() ? Optional.of(name.group(1).trim()) : Optional.empty(),
                email.find() ? Optional.of(email.group().toLowerCase(Locale.ROOT)) : Optional.empty());
    }

    // The comments before the first line of code, where students write their name and email.
    private String leadingComment(String source) {
        int index = source.startsWith(BYTE_ORDER_MARK) ? BYTE_ORDER_MARK.length() : 0;
        StringBuilder comments = new StringBuilder();
        while (index < source.length()) {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
            if (source.startsWith("//", index)) {
                int end = source.indexOf('\n', index);
                end = end < 0 ? source.length() : end + 1;
                comments.append(source, index, end);
                index = end;
            } else if (source.startsWith("/*", index)) {
                int end = source.indexOf("*/", index + 2);
                if (end < 0) {
                    return comments.toString();
                }
                end += 2;
                comments.append(source, index, end).append('\n');
                index = end;
            } else {
                break;
            }
        }
        return comments.toString();
    }

    private static boolean emailMatchesUsername(String email, String username) {
        String localPart = email.substring(0, email.indexOf('@'));
        return UsernameResolver.normalizeIdentifier(localPart).equals(UsernameResolver.normalizeIdentifier(username));
    }
}
