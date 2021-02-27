/**
 *
 */
package codemining.lm.util;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;

import codemining.languagetools.ITokenizer;
import codemining.util.parallel.ParallelThreadPool;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset.Entry;

/**
 * A utility class allowing to build in parallel a token vocabulary from a given
 * corpus.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class TokenVocabularyBuilder {

    private static class VocabularyExtractorRunnable implements Runnable {
        final File codeFile;
        final ConcurrentHashMultiset<String> vocabularySet;
        final ITokenizer tokenizer;

        public VocabularyExtractorRunnable(final File file,
                                           final ConcurrentHashMultiset<String> vocabulary,
                                           final ITokenizer tokenizerModule) {
            codeFile = file;
            vocabularySet = vocabulary;
            tokenizer = tokenizerModule;
        }

        @Override
        public void run() {
            LOGGER.finer("Reading file " + codeFile.getAbsolutePath());
            try {
                vocabularySet.addAll(tokenizer.tokenListFromCode(codeFile));
            } catch (final IOException e) {
                LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
            }
        }
    }

    private static final Logger LOGGER = Logger
            .getLogger(TokenVocabularyBuilder.class.getName());
    ;

    /**
     * Build a set of words in the vocabulary from a collection of files.
     *
     * @param files
     * @return
     */
    public static Set<String> buildVocabulary(final Collection<File> files,
                                              final ITokenizer tokenizer, final int threshold) {
        return buildVocabularyPerFile(files, tokenizer, threshold).values().stream()
                .reduce(new HashSet<>(), (s1, s2) -> {
                    s1.addAll(s2);
                    return s1;
                });
    }

    /**
     * Build a set of words in the vocabulary from a collection of files.
     *
     * @param files
     * @return
     */
    public static Map<File, Set<String>> buildVocabularyPerFile(final Collection<File> files,
                                                                final ITokenizer tokenizer, final int threshold) {
        Map<File,ConcurrentHashMultiset<String>> globalVocabulary = new HashMap<>();

        // add everything
        //final ParallelThreadPool threadPool = new ParallelThreadPool();

        for (final File fi : files) {
            //threadPool.pushTask(new VocabularyExtractorRunnable(fi, vocabulary,
            //tokenizer));
            final ConcurrentHashMultiset<String> vocabulary = ConcurrentHashMultiset
                    .create();
            new VocabularyExtractorRunnable(fi, vocabulary, tokenizer).run();
            globalVocabulary.put(fi, vocabulary);
        }
        pruneElementsFromMultiset(threshold,globalVocabulary);

        //threadPool.waitForTermination();

        // Remove rare


         return globalVocabulary.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e->e.getValue().elementSet()));
    }

    /**
     * @param threshold
     * @param vocabulary
     */
    public static void pruneElementsFromMultiset(final int threshold,
                                                 final Map<File,ConcurrentHashMultiset<String>> vocabulary) {
        List<String> words = vocabulary.values().stream().flatMap(cchm->cchm.elementSet().stream()).collect(Collectors.toList());
        final ArrayDeque<String> toBeRemoved = new ArrayDeque<String>();

        for (final String word : words) {
            int sum = vocabulary.values().stream().mapToInt(cchm->cchm.count(word)).sum();
            if (sum <= threshold) {
                toBeRemoved.add(word);
            }
        }

        for (final String remove : toBeRemoved) {
            vocabulary.values().forEach(cchm->cchm.remove(remove,cchm.count(remove)));
        }
    }

    private TokenVocabularyBuilder() {
    }
}
