package grading.style.checks;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

/**
 * CS102 style guide section 10: avoid break and continue in an infinite loop. Reports a
 * {@code while (true)}, {@code do ... while (true)} or {@code for} loop without a condition that
 * is left or continued with break or continue. A break that belongs to an inner loop or switch,
 * or a continue that belongs to an inner loop, is not counted.
 */
public final class InfiniteLoopExitCheck extends AbstractCheck {

    static final String MESSAGE_KEY = "infinite.loop.exit";

    @Override
    public int[] getDefaultTokens() {
        return getRequiredTokens();
    }

    @Override
    public int[] getAcceptableTokens() {
        return getRequiredTokens();
    }

    @Override
    public int[] getRequiredTokens() {
        return new int[] {TokenTypes.LITERAL_WHILE, TokenTypes.LITERAL_DO, TokenTypes.LITERAL_FOR};
    }

    @Override
    public void visitToken(DetailAST loop) {
        if (isInfinite(loop) && exits(body(loop), SyntaxTrees.label(loop), false, false)) {
            log(loop, MESSAGE_KEY, loop.getText());
        }
    }

    private static boolean isInfinite(DetailAST loop) {
        if (loop.getType() == TokenTypes.LITERAL_FOR) {
            DetailAST condition = loop.findFirstToken(TokenTypes.FOR_CONDITION);
            return condition != null && condition.getChildCount() == 0;
        }
        // while and do-while both hold their condition in an EXPR child
        DetailAST condition = loop.findFirstToken(TokenTypes.EXPR);
        return condition != null && condition.getChildCount() == 1
                && condition.getFirstChild().getType() == TokenTypes.LITERAL_TRUE;
    }

    // The loop's statement: the block or single statement it repeats, without its condition.
    private static DetailAST body(DetailAST loop) {
        if (loop.getType() == TokenTypes.LITERAL_DO) {
            return loop.getFirstChild();
        }
        return loop.getLastChild();
    }

    private static boolean exits(DetailAST node, String label, boolean inInnerLoop, boolean inSwitch) {
        if (node == null || SyntaxTrees.isSeparateScope(node)) {
            return false;
        }
        int type = node.getType();
        if (type == TokenTypes.LITERAL_BREAK || type == TokenTypes.LITERAL_CONTINUE) {
            DetailAST target = node.findFirstToken(TokenTypes.IDENT);
            if (target != null) {
                return target.getText().equals(label);
            }
            return type == TokenTypes.LITERAL_BREAK ? !inInnerLoop && !inSwitch : !inInnerLoop;
        }
        boolean innerLoop = inInnerLoop || SyntaxTrees.isLoop(node);
        boolean innerSwitch = inSwitch || type == TokenTypes.LITERAL_SWITCH;
        for (DetailAST child : SyntaxTrees.children(node)) {
            if (exits(child, label, innerLoop, innerSwitch)) {
                return true;
            }
        }
        return false;
    }
}
