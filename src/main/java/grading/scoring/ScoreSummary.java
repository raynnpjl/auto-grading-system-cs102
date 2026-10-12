package grading.scoring;

import grading.execution.StudentGradingResult;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The scores of one grading run, kept in grading order so they can be reported and exported. */
public record ScoreSummary(List<StudentScore> students) {

    public ScoreSummary {
        students = List.copyOf(students);
    }

    public static ScoreSummary from(List<StudentGradingResult> results) {
        return new ScoreSummary(results.stream().map(StudentScore::from).toList());
    }

    /** Usernames are matched without regard to case, as LMS exports and folder names may differ. */
    public Optional<StudentScore> find(String username) {
        String key = normalize(username);
        return students.stream().filter(student -> normalize(student.username()).equals(key)).findFirst();
    }

    public static String normalize(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }
}
