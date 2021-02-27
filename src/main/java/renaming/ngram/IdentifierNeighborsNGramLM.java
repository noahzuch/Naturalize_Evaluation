package renaming.ngram;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

import codemining.util.ObjectCloner;
import org.apache.commons.lang.exception.ExceptionUtils;

import codemining.languagetools.ITokenizer;
import codemining.languagetools.ITokenizer.FullToken;
import codemining.lm.ILanguageModel;
import codemining.lm.ngram.AbstractNGramLM;
import codemining.lm.ngram.ImmutableNGramLM;
import codemining.lm.ngram.NGram;
import codemining.lm.util.TokenVocabularyBuilder;
import codemining.util.SettingsLoader;

import com.google.common.collect.Lists;

/**
 * An n-gram LM that is specific to identifiers.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class IdentifierNeighborsNGramLM extends AbstractNGramLM {

	/**
	 * Extract ngrams from a specific file.
	 * 
	 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
	 * 
	 */
	private class NGramExtractorRunnable implements Runnable {

		final File codeFile;


		final ITokenizer tokenizer;

		final Consumer<NGram<String>> consumer;

		public NGramExtractorRunnable(final File file,
									  final ITokenizer tokenizerModule, Consumer<NGram<String>> consumer) {
			codeFile = file;
			tokenizer = tokenizerModule;
			this.consumer = consumer;
		}

		public void addRelevantNGrams(final List<FullToken> lst) {

			final SortedSet<Integer> identifierPositions = new TreeSet<Integer>();
			final List<String> sentence = Lists.newArrayList();

			for (int i = 0; i < lst.size(); i++) {
				final FullToken fullToken = lst.get(i);
				sentence.add(fullToken.token);
				if (fullToken.tokenType.equals(tokenizer.getIdentifierType())) {
					identifierPositions.add(i);
				}
			}

			// Construct the rest
			for (int i = 0; i < sentence.size(); i++) {
				// Filter n-grams with no identifiers
				if (identifierPositions.subSet(i - getN() + 1, i + 1).isEmpty()) {
					continue;
				}
				final NGram<String> ngram = NGram.constructNgramAt(i, sentence,
						getN());
				if (ngram.size() > 1) {
					consumer.accept(ngram);
				}
			}

		}

		@Override
		public void run() {
			LOGGER.finer("Reading file " + codeFile.getAbsolutePath());
			try {
				final List<FullToken> tokens = tokenizer
						.getTokenListFromCode(codeFile);

				addRelevantNGrams(tokens);
			} catch (final IOException e) {
				LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
			}
		}
	}

	private static final Logger LOGGER = Logger
			.getLogger(IdentifierNeighborsNGramLM.class.getName());

	private static final long serialVersionUID = 2765488075402402353L;

	public static final int CLEAN_NGRAM_THRESHOLD = (int) SettingsLoader
			.getNumericSetting("CleanNgramCountThreshold", 1);

	public static final int CLEAN_VOCABULARY_THRESHOLD = (int) SettingsLoader
			.getNumericSetting("CleanVocabularyThreshold", 1);

	private Map<File, Set<String>> trainedVocabularyMap;
	/**
	 * Constructor.
	 * 
	 * @param size
	 *            the max-size of the n-grams. The n.
	 */
	public IdentifierNeighborsNGramLM(final int size,
			final ITokenizer tokenizerModule) {
		super(size, tokenizerModule);
	}

	public IdentifierNeighborsNGramLM(IdentifierNeighborsNGramLM original){
		super(original);
		this.trainedVocabularyMap=original.trainedVocabularyMap;
		try {
			trie = ObjectCloner.deepCopy(trie); // deep copy the trie so that we can change it
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Given a sentence (i.e. a list of strings) add all appropriate ngrams.
	 * 
	 * @param sentence
	 *            an (ordered) list of tokens belonging to a sentence.
	 */
	@Override
	public void addFromSentence(final List<String> sentence,
			final boolean addNewToks) {
		for (int i = getN() - 1; i < sentence.size(); ++i) {
			final NGram<String> ngram = NGram.constructNgramAt(i, sentence,
					getN());
			if (ngram.size() > 1) {
				addNgram(ngram, addNewToks);
			}
		}

		// Construct for the last parts
		for (int i = getN() - 1; i > 0; i--) {
			final NGram<String> ngram = NGram.constructNgramAt(
					sentence.size() - 1, sentence, i);
			addNgram(ngram, addNewToks);
		}
	}

	/**
	 * Given an ngram (a list of strings with size <= n) add it to the trie and
	 * update the counts of counts.
	 * 
	 * @param ngram
	 */
	@Override
	protected void addNgram(final NGram<String> ngram, final boolean addNewVoc) {

		trie.add(ngram, addNewVoc);
	}

	/**
	 * Add a set of sentences to the dictionary.
	 * 
	 * @param sentenceSet
	 */
	@Override
	public void addSentences(final Collection<List<String>> sentenceSet,
			final boolean addNewVocabulary) {
		for (final List<String> sent : sentenceSet) {
			addFromSentence(sent, addNewVocabulary);
		}
	}

	/**
	 * Cut-off rare ngrams by removing rare tokens.
	 * 
	 * @param threshold
	 */
	@Override
	public void cutoffRare(final int threshold) {
		trie.cutoffRare(threshold);
	}

	@Override
	public ILanguageModel getImmutableVersion() {
		return new ImmutableNGramLM(this);
	}

	@Override
	public double getProbabilityFor(final NGram<String> ngram) {
		return getMLProbabilityFor(ngram, false);
	}

	@Override
	public void removeNgram(final NGram<String> ngram) {
		trie.remove(ngram);
	}

	@Override
	public void removeNGramsFromFile(File f) {
		new NGramExtractorRunnable(f,getTokenizer(),this::removeNgram).run();
		Set<String> vocabularyInFile = trainedVocabularyMap.get(f);
		Set<String> uniqueVocabInFile = new HashSet<>(vocabularyInFile);
		for (Set<String> vocab : trainedVocabularyMap.values()) {
			if(vocab != vocabularyInFile){
				uniqueVocabInFile.removeAll(vocab);
			}
		}
		trie.alphabet.keySet().removeAll(uniqueVocabInFile);
	}

	@Override
	public void trainIncrementalModel(final Collection<File> files)
			throws IOException {
		trainModel(files);

	}

	@Override
	public void trainModel(final Collection<File> files) throws IOException {
		LOGGER.info("Building vocabulary...");
		trainedVocabularyMap = TokenVocabularyBuilder.buildVocabularyPerFile(files,getTokenizer(),CLEAN_VOCABULARY_THRESHOLD);
		trie.buildVocabularySymbols(trainedVocabularyMap.values().stream().reduce(new HashSet<>(),(s1,s2)->{
			s1.addAll(s2);
			return s1;
		}));

		LOGGER.info("Vocabulary Built. Counting n-grams");
		trainModel(files, false, false);
		LOGGER.info("Counting n-grams finished.");
	}

	/**
	 * @param files
	 * @param performCleanups
	 */
	private void trainModel(final Collection<File> files,
			final boolean performCleanups, final boolean addNewToksToVocabulary) {
		//final ParallelThreadPool threadPool = new ParallelThreadPool();

		for (final File fi : files) {
		//	threadPool.pushTask(new NGramExtractorRunnable(fi, getTokenizer()));
            new NGramExtractorRunnable(fi,getTokenizer(), nGram -> addNgram(nGram,false)).run();
		}

		//threadPool.waitForTermination();
	}

}
