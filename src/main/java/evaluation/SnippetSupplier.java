package evaluation;

import org.eclipse.jdt.core.dom.ASTNode;

import java.util.function.Supplier;

@FunctionalInterface
public interface SnippetSupplier {

    String getSnippet(ASTNode node);

}
