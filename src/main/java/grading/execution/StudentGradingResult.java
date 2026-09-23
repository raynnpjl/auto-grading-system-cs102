package grading.execution;

import java.util.List;

public record StudentGradingResult(String username, List<QuestionGradingResult> questions) {

    public StudentGradingResult {
        questions = List.copyOf(questions);
    }

    public double totalScore() {
        return questions.stream().mapToDouble(QuestionGradingResult::score).sum();
    }
}
