package codemining.java.tokenizers;

import codemining.java.codeutils.JavaASTExtractor;
import codemining.languagetools.ITokenizer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AbstractFileFilter;
import org.eclipse.cdt.core.parser.util.ASTPrinter;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.IDocument;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.SortedMap;

public class JavaObfuscatedTokenizer implements ITokenizer {

    private final JavaTokenizer javaTokenizer;

    public JavaObfuscatedTokenizer(boolean tokenizeComments) {
        javaTokenizer = new JavaTokenizer(tokenizeComments);
    }

    private String getObfuscatedString(String originalCode){
        CompilationUnit compilationUnit = new JavaASTExtractor(true, true).getAST(originalCode);
        AstFlattenerWithoutClass flattener = new AstFlattenerWithoutClass();
        flattener.visit(compilationUnit);
       return flattener.getResult();
    }

    @Override
    public SortedMap<Integer, FullToken> fullTokenListWithPos(char[] code) {
        return javaTokenizer.fullTokenListWithPos(getObfuscatedString(new String(code)).toCharArray());
    }

    @Override
    public AbstractFileFilter getFileFilter() {
        return javaTokenizer.getFileFilter();
    }

    @Override
    public String getIdentifierType() {
        return javaTokenizer.getIdentifierType();
    }

    @Override
    public Collection<String> getKeywordTypes() {
        return javaTokenizer.getKeywordTypes();
    }

    @Override
    public Collection<String> getLiteralTypes() {
        return javaTokenizer.getLiteralTypes();
    }

    @Override
    public FullToken getTokenFromString(String token) {
        return javaTokenizer.getTokenFromString(token);
    }

    @Override
    public List<FullToken> getTokenListFromCode(char[] code) {
        return javaTokenizer.getTokenListFromCode(getObfuscatedString(new String(code)).toCharArray());
    }

    @Override
    public List<FullToken> getTokenListFromCode(File codeFile) throws IOException {
        return javaTokenizer.getTokenListFromCode(getObfuscatedString(FileUtils.readFileToString(codeFile)).toCharArray());
    }

    @Override
    public List<String> tokenListFromCode(char[] code) {
        return javaTokenizer.tokenListFromCode(getObfuscatedString(new String(code)).toCharArray());
    }

    @Override
    public List<String> tokenListFromCode(File codeFile) throws IOException {
        return javaTokenizer.tokenListFromCode(getObfuscatedString(FileUtils.readFileToString(codeFile)).toCharArray());
    }

    @Override
    public SortedMap<Integer, String> tokenListWithPos(char[] code) {
        return tokenListWithPos(getObfuscatedString(new String(code)).toCharArray());
    }

    @Override
    public SortedMap<Integer, FullToken> tokenListWithPos(File file) throws IOException {
        return fullTokenListWithPos(FileUtils.readFileToString(file)
                .toCharArray());
    }
}
