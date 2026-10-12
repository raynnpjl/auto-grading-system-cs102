package grading.style.checks;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

/**
 * CS102 style guide section 3: use declaration and initialisation together, and always
 * initialise variables. Reports a local variable declared without a value. Fields, parameters,
 * for-each variables and resources are not local declarations and are not checked.
 */
public final class InitializeAtDeclarationCheck extends AbstractCheck {

    static final String MESSAGE_KEY = "initialise.at.declaration";

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
        return new int[] {TokenTypes.VARIABLE_DEF};
    }

    @Override
    public void visitToken(DetailAST declaration) {
        boolean local = declaration.getParent().getType() == TokenTypes.SLIST;
        if (local && declaration.findFirstToken(TokenTypes.ASSIGN) == null) {
            log(declaration, MESSAGE_KEY, declaration.findFirstToken(TokenTypes.IDENT).getText());
        }
    }
}
