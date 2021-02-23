package codemining.java.codeutils.scopes;

import codemining.java.tokenizers.AstFlattenerWithoutClass;
import org.eclipse.jdt.core.dom.ASTNode;

public interface ScopeCodeSnippetExtractor {

    String extractCodeSnippet(ASTNode node);

    ScopeCodeSnippetExtractor standard = ASTNode::toString;

    class ObfuscatedScopeSnippetExtractor implements  ScopeCodeSnippetExtractor {

        @Override
        public String extractCodeSnippet(ASTNode node) {
            AstFlattenerWithoutClass astFlattenerWithoutClass = new AstFlattenerWithoutClass();
            node.accept(astFlattenerWithoutClass);
            return astFlattenerWithoutClass.getResult();
        }
    }
}
