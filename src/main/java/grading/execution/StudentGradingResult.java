package grading.execution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record StudentGradingResult(String username, List<QuestionGradingResult> questions) {

    public StudentGradingResult {
        questions = List.copyOf(questions);
    }

    public double totalScore() {
        return questions.stream().mapToDouble(QuestionGradingResult::score).sum();
    }

    public Map<String, Double> questionScores() {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (QuestionGradingResult question : questions) {
            String group = question.questionId().replaceFirst("(?<=\\d)[a-z]+$", "");
            scores.merge(group, question.score(), Double::sum);
        }
        return Collections.unmodifiableMap(scores);
    }
}
