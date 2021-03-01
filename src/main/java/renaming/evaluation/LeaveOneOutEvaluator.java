/**
 *
 */
package renaming.evaluation;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import codemining.java.codeutils.scopes.ScopeCodeSnippetExtractor;
import codemining.util.SettingsLoader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;

import renaming.evaluation.NamingEvaluator.ResultObject;
import renaming.ngram.IdentifierNeighborsNGramLM;
import renaming.renamers.AbstractIdentifierRenamings;
import codemining.java.codeutils.scopes.ScopesTUI;
import codemining.languagetools.IScopeExtractor;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.Scope.ScopeType;
import codemining.lm.ngram.AbstractNGramLM;
import codemining.util.parallel.ParallelThreadPool;
import codemining.util.serialization.ISerializationStrategy.SerializationException;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import renaming.renamers.BaseIdentifierRenamings;

/**
 * Evaluate renaming by leaving one file out.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class LeaveOneOutEvaluator {


    /**
     * Test a specific file.
     *
     */

    public class ModelEvaluator implements Runnable {

        final IScopeExtractor scopeExtractor;

        final File testedFile;

        final IdentifierNeighborsNGramLM model;


        public ModelEvaluator(final File fileToRetain,  IdentifierNeighborsNGramLM model,
                              final IScopeExtractor extractor, final String renamerClass,
                              final String renamerConstructorParams) {
            this.model = model;
            testedFile = fileToRetain;
            scopeExtractor = extractor;

        }

        @Override
        public void run() {
            try {
                final Collection<File> testFiles = Lists.newArrayList();
                testFiles.add(testedFile);


                final NamingEvaluator ve = new NamingEvaluator(model,
                        data);
                ve.performEvaluation(testFiles, scopeExtractor);
            } catch (Exception e) {
                LOGGER.warning("Error in file " + testedFile.getAbsolutePath()
                        + " " + ExceptionUtils.getFullStackTrace(e));
            }
        }
    }

    /**
     * Print the data.
     *
     */
    private class Printer implements Runnable {
        @Override
        public void run() {
            for (int i = 0; i < data.length; i++) {
                System.out.println("==============" + ScopeType.values()[i]
                        + "===========");
                data[i].printStats();
            }
        }

    }

    private static final Logger LOGGER = Logger
            .getLogger(LeaveOneOutEvaluator.class.getName());

    /**
     * @param args
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws ClassNotFoundException
     */
    public static void main(String[] args) throws InstantiationException,
            IllegalAccessException, ClassNotFoundException,
            SerializationException, NoSuchMethodException, InvocationTargetException, InterruptedException, IOException {
        if (args.length < 4) {
            System.err
                    .println("Usage <outputFile> <tokenizerClass> variable|method <renamingClass> files...");
            return;
        }
        File outputFile = new File(args[0] + "out1.csv");
        int index = 1;
        while (outputFile.exists()) {
            outputFile = new File(args[0] + "out" + ++index + ".csv");
        }

        final Class<? extends ITokenizer> tokenizerName = (Class<? extends ITokenizer>) Class
                .forName(args[1]);
        final ITokenizer tokenizer = tokenizerName.getDeclaredConstructor().newInstance();

        final Class<? extends AbstractNGramLM> smoothedNgramClass = null;//(Class<? extends AbstractNGramLM>) Class
        //.forName(args[2]);

        File[] directories = new File[args.length - 5];
        for (int i = 0; i < directories.length; i++) {
            directories[i] = new File(args[5 + i]);
        }


        final LeaveOneOutEvaluator eval = new LeaveOneOutEvaluator(outputFile, directories,
                tokenizer, smoothedNgramClass);

        ScopeCodeSnippetExtractor codeSnippetExtractor;
        if(args[3].equals("StandardScopeExtractor")){
            codeSnippetExtractor = ScopeCodeSnippetExtractor.standard;
        }else{
            codeSnippetExtractor = new ScopeCodeSnippetExtractor.ObfuscatedScopeSnippetExtractor();
        }

        final IScopeExtractor scopeExtractor = ScopesTUI
                .getScopeExtractorByName(args[2], codeSnippetExtractor);

        eval.performEvaluation(scopeExtractor, args[4], null);
    }

    final ResultObject[] data = new ResultObject[ScopeType.values().length];

    final ITokenizer tokenizer;

    final File[] directories;

    final File outputFile;

    public LeaveOneOutEvaluator(File outputFile, final File[] directories,
                                final ITokenizer tokenizer,
                                final Class<? extends AbstractNGramLM> smoother) {
        this.directories = directories;
        this.tokenizer = tokenizer;
        for (int i = 0; i < data.length; i++) {
            data[i] = new ResultObject();
        }
        this.outputFile = outputFile;
    }

    public void performEvaluation(final IScopeExtractor scopeExtractor,
                                  final String renamerClass, final String additionalParams) throws InterruptedException, IOException {
        final ParallelThreadPool threadPool = new ParallelThreadPool();
        writeHeader();
        for (File directory : directories) {
            Collection<File> allFiles = FileUtils.listFiles(directory, tokenizer.getFileFilter(),
                    DirectoryFileFilter.DIRECTORY);
            IdentifierNeighborsNGramLM model = new IdentifierNeighborsNGramLM((int) SettingsLoader
                    .getNumericSetting("ngramSize", 5),tokenizer);
            model.trainModel(allFiles);
            List<Callable<Object>> calls = new ArrayList<Callable<Object>>();
            for (final File fi : allFiles) {
                //IdentifierNeighborsNGramLM modelOrig = new IdentifierNeighborsNGramLM((int) SettingsLoader
                //        .getNumericSetting("ngramSize", 5),tokenizer);
                //Collection<File> trainFiles = new HashSet<>(allFiles);
                //trainFiles.remove(fi);
                //modelOrig.trainModel(trainFiles);
                //IdentifierNeighborsNGramLM modelNew = new IdentifierNeighborsNGramLM(model);
                //modelNew.removeNGramsFromFile(fi);
                //CompareTries.compareTries(modelOrig,modelNew);
                calls.add(Executors.callable(new ModelEvaluator(fi, model, scopeExtractor,
                       renamerClass, additionalParams)));
                //new ModelEvaluator(fi, model, scopeExtractor, renamerClass, additionalParams).run();

            }
            threadPool.threadPool.invokeAll(calls);
            writeMeasurements(directory);
            for (int i = 0; i < data.length; i++) {
                data[i] = new ResultObject();
            }
        }
        threadPool.waitForTermination();

        System.out.println("||||||||||||||||||||||| FINAL ||||||||||||||||||||||||||");
    }

    private void writeHeader() {
        appendToFile(getHeader());
    }

    private String getHeader() {
        StringBuilder builder = new StringBuilder();
        builder.append("Scopetype:");
        for (int i = 0; i < ScopeType.values().length; i++) {
            builder.append(StringUtils.repeat(" "+ScopeType.values()[i], 76));
        }
        builder.append("\nk");
        for (int i = 0; i < ScopeType.values().length; i++) {
            builder.append(StringUtils.repeat(" 1", 38)).append(StringUtils.repeat(" 5", 38));
        }
        builder.append("\nt");
        for (int i = 0; i < ScopeType.values().length; i++) {
            for (int t = 0; t < ResultObject.THRESHOLD_VALUES.length; t++) {
                builder.append(" ").append(ResultObject.THRESHOLD_VALUES[t])
                        .append(" ").append(ResultObject.THRESHOLD_VALUES[t]);
            }
            for (int t = 0; t < ResultObject.THRESHOLD_VALUES.length; t++) {
                builder.append(" ").append(ResultObject.THRESHOLD_VALUES[t])
                        .append(" ").append(ResultObject.THRESHOLD_VALUES[t]);
            }
        }
        builder.append("\nvalue");
        for (int i = 0; i < ScopeType.values().length; i++) {
            for (int t = 0; t < ResultObject.THRESHOLD_VALUES.length; t++) {
                builder.append(" frequency accuracy");
            }
            for (int t = 0; t < ResultObject.THRESHOLD_VALUES.length; t++) {
                builder.append(" frequency accuracy");
            }
        }
        builder.append("\n");
        return builder.toString();
    }

    private void writeMeasurements(File currentProjectDir) {
        appendToFile(getMeasurements(currentProjectDir));
    }

    private String getMeasurements(File currentProjectDir){
        StringBuilder builder = new StringBuilder();
        builder.append(currentProjectDir.getAbsolutePath());
        getMeasurementValues(builder);
        builder.append("\n");
        return builder.toString();
    }

    private void getMeasurementValues(StringBuilder builder) {
        for (int i = 0; i < ScopeType.values().length; i++) {
            getMeasurementValues(builder, data[i]);
        }
    }


    private void getMeasurementValues(StringBuilder builder, ResultObject res) {
        getMeasurementValues(builder, res, 1);
        getMeasurementValues(builder, res, 5);
    }


    private void getMeasurementValues(StringBuilder builder, ResultObject res, int k) {
        for (int t = 0; t < ResultObject.THRESHOLD_VALUES.length; t++) {
            builder.append(' ')
                    .append(res.nGaveSuggestions[t] / (double) res.count)
                    .append(' ')
                    .append(res.recallAtRank[t][k] / (double) res.nGaveSuggestions[t]);
        }
    }

    private void appendToFile(String content){
        FileWriter writer = null;
        try {
            writer = new FileWriter(outputFile, true);
            writer.write(content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

}
