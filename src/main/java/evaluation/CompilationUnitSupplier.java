package evaluation;

import codemining.java.codeutils.JavaASTExtractor;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.io.File;
import java.io.IOException;

public class CompilationUnitSupplier {

    public CompilationUnit getCompilationUnit(File f) throws IOException {
            JavaASTExtractor astExtractor = new JavaASTExtractor(true);
          return  astExtractor.getAST(f);
    }

    public static void main(String[] args) throws IOException {
        CompilationUnit compilationUnit = new CompilationUnitSupplier().getCompilationUnit(new File("/home/zuchn/Documents/Universitaet/Seminar_Deep_Learning_for_SE/naturalize/src/main/java/renaming/tools/CodeReviewAssistant.java"));
        System.out.println(compilationUnit);
    }
}
