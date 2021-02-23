package evaluation;

import codemining.java.codeutils.IdentifierPerType;
import codemining.java.codeutils.JavaASTExtractor;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.Scope;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.core.RenameElementsOperation;
import org.eclipse.jdt.internal.core.RenameResourceElementsOperation;
import org.python.pydev.shared_core.structure.Tuple;
import renaming.renamers.AbstractIdentifierRenamings;
import renaming.renamers.BaseIdentifierRenamings;
import renaming.renamers.INGramIdentifierRenamer;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ProjectEvaluator {

    public static final RegexFileFilter javaCodeFileFilter = new RegexFileFilter(
            ".*\\.java$");

    public static void main(String[] args) throws IOException {
        if (args.length < 3 || args.length > 4) {
            throw new IllegalArgumentException("params: directory threshold searchInFirstK (file)");
        }
        if(args.length==4){
            final Collection<File> allFiles = FileUtils.listFiles(new File(args[0]), javaCodeFileFilter, DirectoryFileFilter.DIRECTORY);
            ProjectEvaluator projectEvaluator = new ProjectEvaluator();
            System.out.println(projectEvaluator.
                    averageAccuracyMap(projectEvaluator.
                            getSummedUpAccuraciesForFile(new File(args[3]),allFiles,Double.parseDouble(args[1]), Integer.parseInt(args[2]))));
        }else {
            System.out.println(new ProjectEvaluator().getAverageAccuracies(args[0], Double.parseDouble(args[1]), Integer.parseInt(args[2])));
        }
    }

    private Map<IdentifierTypes, Double> getAverageAccuracies(String directoryPath, double frequency, int searchInFirstK) {

        final Collection<File> allFiles = FileUtils.listFiles(new File(directoryPath), javaCodeFileFilter, DirectoryFileFilter.DIRECTORY);

        Map<IdentifierTypes, Tuple<Double, Integer>> summedUpAccuracyMap = allFiles.stream()
                .map(file -> getSummedUpAccuraciesForFileExceptionWrapper(file, allFiles, frequency, searchInFirstK))
                .reduce(getDefaultSummedUpAccuracyMap(),this::sumUpAccuracyMaps);
        return averageAccuracyMap(summedUpAccuracyMap);
    }

    private Map<IdentifierTypes, Double> averageAccuracyMap(Map<IdentifierTypes, Tuple<Double, Integer>> summedUpAccuracyMap) {
        Map<IdentifierTypes, Double> averageAccuracyMap = new EnumMap<>(IdentifierTypes.class);
        summedUpAccuracyMap.forEach((k, v)->averageAccuracyMap.put(k,v.o1/v.o2));
        return averageAccuracyMap;
    }

    private EnumMap<IdentifierTypes, Tuple<Double, Integer>> getDefaultSummedUpAccuracyMap() {
        EnumMap<IdentifierTypes, Tuple<Double, Integer>> summedUpAccuracyMap = new EnumMap<>(IdentifierTypes.class);
        summedUpAccuracyMap.put(IdentifierTypes.METHOD, new Tuple<>(0d, 0));
        summedUpAccuracyMap.put(IdentifierTypes.VARIABLE, new Tuple<>(0d, 0));
        summedUpAccuracyMap.put(IdentifierTypes.TYPE, new Tuple<>(0d, 0));
        return summedUpAccuracyMap;
    }

    private Map<IdentifierTypes, Tuple<Double, Integer>> sumUpAccuracyMaps(Map<IdentifierTypes, Tuple<Double, Integer>> m1, Map<IdentifierTypes, Tuple<Double, Integer>> m2) {
        m1.replaceAll((k,v)->{
            Tuple<Double, Integer> other = m2.get(k);
            v.o1+= other.o1;
            v.o2+=other.o2;
            return v;
        });
        return m1;
    }

    public Map<IdentifierTypes, Tuple<Double, Integer>> getSummedUpAccuraciesForFileExceptionWrapper(File file, Collection<File> allFiles, double threshold, int searchInFirstK) {
        try {
            return getSummedUpAccuraciesForFile(file, allFiles, threshold, searchInFirstK);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<IdentifierTypes, Tuple<Double, Integer>> getSummedUpAccuraciesForFile(File file, Collection<File> allFiles, double threshold, int searchInFirstK) throws IOException {
        System.out.println("-----------------------------------------------------");
        System.out.println("inspecting file: ---- " + file.getName());
        Set<String> methodIdentifiers = IdentifierPerType.getMethodIdentifiers(file);
        Set<String> typeIdentifiers = IdentifierPerType.getTypeIdentifiers(file);
        Set<String> variableIdentifiers = IdentifierPerType.getVariableIdentifiers(file);
        System.out.println("Set of methodIdentifiers:");
        System.out.println(methodIdentifiers);
        System.out.println("Set of typeIdentifiers:");
        System.out.println(typeIdentifiers);
        System.out.println("Set of variableIdentifiers:");
        System.out.println(variableIdentifiers);
        final String snippet = FileUtils.readFileToString(file);
        AbstractIdentifierRenamings renamer = createRenamerWithoutFile(file, allFiles);

        Map<IdentifierTypes, Tuple<Double, Integer>> accuracies = new EnumMap<>(IdentifierTypes.class);

        accuracies.put(IdentifierTypes.VARIABLE, new Tuple<>(
                getSummedUpAccuracyForIdentifierSet(variableIdentifiers, snippet, renamer,threshold, searchInFirstK),
                variableIdentifiers.size()));
        accuracies.put(IdentifierTypes.TYPE, new Tuple<>(
                getSummedUpAccuracyForIdentifierSet(typeIdentifiers, snippet, renamer,threshold, searchInFirstK),
                typeIdentifiers.size()));
        accuracies.put(IdentifierTypes.METHOD, new Tuple<>(
                getSummedUpAccuracyForIdentifierSet(methodIdentifiers, snippet, renamer,threshold, searchInFirstK),
                methodIdentifiers.size()));
        System.out.println(accuracies);
        System.out.println("-----------------------------------------------------");
        System.out.println("-----------------------------------------------------");


        return accuracies;
    }

    private AbstractIdentifierRenamings createRenamerWithoutFile(File file, Collection<File> allFiles) throws IOException {
        final AbstractIdentifierRenamings renamer = new BaseIdentifierRenamings(
                new JavaTokenizer());
        HashSet<File> trainingFiles = new HashSet<>(allFiles);
        trainingFiles.remove(file);
        renamer.buildRenamingModel(trainingFiles);
        return renamer;
    }

    private Double getSummedUpAccuracyForIdentifierSet(Set<String> identifiers, String snippet, AbstractIdentifierRenamings renamer, double threshold, int searchInFirstK) {
        double summedUpAccuracies = identifiers.stream()
                .map(s -> getAccuracyForIdentifier(s, snippet, renamer,threshold, searchInFirstK))
                .reduce(0d, Double::sum);
        return summedUpAccuracies;
    }

    public Double getAccuracyForIdentifier(String identifier, String snippet, AbstractIdentifierRenamings renamer,double threshold, int searchInFirstK) {
        final SortedSet<AbstractIdentifierRenamings.Renaming> renamings = renamer
                .getRenamings(new Scope(snippet,
                                Scope.ScopeType.SCOPE_LOCAL, null, -1, -1),
                        identifier);
        AbstractIdentifierRenamings.Renaming originalRenaming = renamings.stream().filter(r->r.name.equals(identifier)).findAny().orElseThrow(IllegalStateException::new);
        INGramIdentifierRenamer.Renaming UNKRenaming = renamings.stream().filter(r->r.name.equals("UNK_SYMBOL")).findAny().orElse(null);
        SortedSet<INGramIdentifierRenamer.Renaming> filteredRenamings = renamings.stream()
                .filter(r->r==originalRenaming || r==UNKRenaming || (r.score-originalRenaming.score) > threshold && (UNKRenaming == null || r.score - UNKRenaming.score > threshold))
                .limit(searchInFirstK)
                .collect(Collectors.toCollection(TreeSet::new));
        if(filteredRenamings.isEmpty()){
            throw new IllegalStateException("At least original renaming should be in here.");
        }
        return filteredRenamings.stream()
        .anyMatch(n -> n.name.equals(identifier) || n.name.equals("UNK_SYMBOL")) ? 1d : 0d;
    }

    private enum IdentifierTypes {
        TYPE, METHOD, VARIABLE
    }


}
