package grading.similarity;

/**
 * One normalized token of a student's code, such as {@code while}, {@code ID} or {@code (}.
 *
 * @param kind the token after normalization; renamed variables and changed values give the same kind
 * @param file the file it came from, such as Q2a.java
 * @param line the 1-based line it is on
 */
record Token(String kind, String file, int line) {
}
