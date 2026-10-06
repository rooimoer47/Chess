package pvt.phgg.chess.server.analysis;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The prompt for one move's comment, and the check its answer has to pass.
//
// The model gets facts only, never a board (not even a FEN), so it has nothing to work out for
// itself: small models misread positions and invent tactics. The check holds it to that: any square
// in the answer must appear in the facts.
final class CommentaryPrompt {

    static final String SYSTEM = """
            You are a chess coach writing short notes for a game review. You are given facts about one \
            move, worked out by a chess engine, and you explain them to a club player in plain English.

            Rules:
            - Use only the facts given. Do not mention any move, square or piece that is not in them, \
            and do not add tactics or plans of your own.
            - Say what concretely happens: what gets captured, attacked or mated, naming the key move \
            from the facts. Do not just repeat the evaluation numbers.
            - Write at most two sentences and under 45 words.
            - Plain text only: no lists, headings or quotation marks.
            - Refer to the players as White and Black, never as "you".""";

    // Longer than this isn't two short sentences.
    static final int MAX_LENGTH = 400;

    private static final Pattern SQUARE = Pattern.compile("[a-h][1-8]");
    private static final Pattern THINKING = Pattern.compile("(?s)<think>.*?</think>");
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?](\\s|$)");

    private CommentaryPrompt() {
    }

    static String user(MoveContext move) {
        String verdict = move.classification().name().toLowerCase(Locale.ROOT);
        StringBuilder prompt = new StringBuilder()
                .append("Move: ").append(move.move()).append(", played by ").append(move.mover()).append(".\n")
                .append("Engine verdict: ").append(verdict).append(".\n")
                .append("Evaluation in pawns, from White's side: before the move ").append(move.evalBefore())
                .append("; after it ").append(move.evalAfter()).append(".\n")
                .append("Facts:\n");
        move.facts().forEach(fact -> prompt.append("- ").append(fact).append('\n'));
        prompt.append('\n');
        if (move.decisive()) {
            prompt.append("This is the move after which the game was decided. Explain why ")
                    .append(move.move()).append(" decided the game.");
        } else {
            prompt.append("Explain why ").append(move.move()).append(" was ")
                    .append(verdict.equals("inaccuracy") ? "an inaccuracy" : "a " + verdict).append('.');
        }
        return prompt.toString();
    }

    // The answer cleaned up, or empty if it fails the check: blank, too long, more than three
    // sentences, or naming a square that the prompt doesn't.
    static Optional<String> check(String answer, String userPrompt) {
        String text = THINKING.matcher(answer).replaceAll("").strip().replaceAll("\\s+", " ");
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1).strip();
        }
        if (text.isEmpty() || text.length() > MAX_LENGTH) return Optional.empty();
        if (SENTENCE_END.matcher(text).results().count() > 3) return Optional.empty();
        if (!squares(userPrompt).containsAll(squares(text))) return Optional.empty();
        return Optional.of(text);
    }

    static Set<String> squares(String text) {
        Set<String> squares = new HashSet<>();
        Matcher matcher = SQUARE.matcher(text);
        while (matcher.find()) squares.add(matcher.group());
        return squares;
    }
}
