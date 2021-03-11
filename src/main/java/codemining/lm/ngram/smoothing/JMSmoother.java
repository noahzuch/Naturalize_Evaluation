package codemining.lm.ngram.smoothing;

import codemining.lm.ILanguageModel;
import codemining.lm.ngram.AbstractNGramLM;
import codemining.lm.ngram.NGram;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The stupid backoff as in the following paper:
 * <p>
 * inproceedings{brants2007large, title={Large language models in machine
 * translation}, author={Brants, T. and Popat, A.C. and Xu, P. and Och, F.J. and
 * Dean, J.}, booktitle={In EMNLP}, year={2007}, organization={Citeseer} }
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 */
public class JMSmoother extends AbstractNGramLM {

    private static final long serialVersionUID = -4632284391688356590L;

    private static final Logger LOGGER = Logger.getLogger(JMSmoother.class
            .getName());

    public JMSmoother(final AbstractNGramLM original) {
        super(original);
        LOGGER.info("Starting JM smoother");
    }

    @Override
    public void addFromSentence(final List<String> sentence,
                                final boolean addNewVoc) {
        throw new UnsupportedOperationException(
                "JMSmoother is an immutable Language Model");
    }

    @Override
    protected void addNgram(final NGram<String> ngram, final boolean addNewVoc) {
        throw new UnsupportedOperationException(
                "JMSmoother is an immutable Language Model");
    }

    @Override
    public void addSentences(final Collection<List<String>> sentenceSet,
                             final boolean addNewVocabulary) {
        throw new UnsupportedOperationException(
                "JMSmoother is an immutable Language Model");
    }

    @Override
    public void cutoffRare(final int threshold) {
        throw new UnsupportedOperationException(
                "JMSmoother is an immutable Language Model");
    }

    @Override
    public ILanguageModel getImmutableVersion() {
        return this;
    }

    /**
     * Get the probability using the ngram. It assumes that the last word w_i
     * exists in the dictionary (or has been converted to UNK).
     *
     * @param ngram
     * @return
     */
    @Override
    public double getProbabilityFor(NGram<String> ngram) {
        double probabilityCurrentOrder = getProbabilityOfCurrentOrder(ngram);
        if(ngram.size()> 1) {
            return 0.5d * (probabilityCurrentOrder + getProbabilityFor(ngram.getSuffix()));
        }else{
            return probabilityCurrentOrder;
        }
    }

    private Double getProbabilityOfCurrentOrder(NGram<String> ngram) {
        if (ngram.size() == 1) {
            ngram = trie.substituteWordsToUNK(ngram);
        }
        boolean usesUnk = ngram.size() == 1 || "UNK_SYMBOL".equals(ngram.get(ngram.size() - 1));
        final long thisNgramCount = trie.getCount(ngram, usesUnk,
                true);

        if (thisNgramCount > 0) {
            final long productionCount = trie.getCount(ngram.getPrefix(),
                    usesUnk, false);
            if (productionCount < thisNgramCount) {
                checkArgument(productionCount >= thisNgramCount);
            }

            double probabilityCurrentOrder = ((double) thisNgramCount)
                    / ((double) productionCount);
            checkArgument(!Double.isInfinite(probabilityCurrentOrder));
            return probabilityCurrentOrder;
        } else {
            return 0d;
        }
    }

    @Override
    public void removeNgram(final NGram<String> ngram) {
        throw new UnsupportedOperationException(
                "JMSmoother is an immutable Language Model");
    }

    @Override
    public void removeNGramsFromFile(File f) {
        throw new UnsupportedOperationException(
                "JMSmoother is an immutable Language Model");

    }

    @Override
    public void trainIncrementalModel(final Collection<File> files)
            throws IOException {
        throw new UnsupportedOperationException(
                "JMSmoother is an immutable Language Model");

    }

    @Override
    public void trainModel(final Collection<File> files) throws IOException {
        throw new UnsupportedOperationException(
                "JMSmoother is an immutable Language Model");
    }
}