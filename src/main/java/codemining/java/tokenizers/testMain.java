package codemining.java.tokenizers;

import codemining.java.codeutils.JavaASTExtractor;
import org.apache.commons.io.FileUtils;
import org.eclipse.cdt.core.parser.util.ASTPrinter;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.internal.core.dom.NaiveASTFlattener;
import org.eclipse.jdt.internal.core.dom.rewrite.ASTRewriteFlattener;
import org.eclipse.jdt.internal.core.dom.rewrite.RewriteEventStore;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class testMain {

    public static void main(String[] args) throws IOException {

        testMain[] test = new testMain[3];
        File f = new File(args[0]);
        final String sourceFile = FileUtils.readFileToString(f);
        CompilationUnit compilationUnit = new JavaASTExtractor(true, true).getAST(sourceFile);
        AstFlattenerWithoutClass flattener = new AstFlattenerWithoutClass();
        flattener.visit(compilationUnit);
        String flattenerResult = flattener.getResult();
        System.out.println(flattenerResult);
        System.out.println(new JavaTokenizer(false).tokenListFromCode(flattenerResult.toCharArray()));
    }


}