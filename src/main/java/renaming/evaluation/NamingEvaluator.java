/**
 * 
 */
package renaming.evaluation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.logging.Logger;

import codemining.java.codeutils.scopes.MethodScopeExtractor;
import codemining.java.codeutils.scopes.TypenameScopeExtractor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang.math.RandomUtils;

import renaming.ngram.IdentifierNeighborsNGramLM;
import renaming.renamers.AbstractIdentifierRenamings;
import renaming.renamers.BaseIdentifierRenamings;
import renaming.renamers.INGramIdentifierRenamer.Renaming;
import codemining.java.codeutils.scopes.ScopesTUI;
import codemining.languagetools.IScopeExtractor;
import codemining.languagetools.Scope;
import codemining.languagetools.Scope.ScopeType;
import codemining.lm.ngram.AbstractNGramLM;
import codemining.util.SettingsLoader;
import codemining.util.parallel.ParallelThreadPool;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

import com.google.common.collect.Multimap;

/**
 * Evaluate variable renamings.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class NamingEvaluator {

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

	private class RenamingEvaluator implements Runnable {

		final IScopeExtractor scopeExtractor;

		final File fi;

		public RenamingEvaluator(final File file,
				final IScopeExtractor extractor) {
			fi = file;
			scopeExtractor = extractor;
		}

		@Override
		public void run() {
			final Multimap<Scope, String> m;
			try {
				m = scopeExtractor.getFromFile(fi);
				evaluateRenamings(m, fi);
			} catch (final IOException e) {
				LOGGER.warning("Failed to open file " + fi.getAbsolutePath()
						+ ExceptionUtils.getFullStackTrace(e));
			}
		}

	}

	public static class ResultObject {
		static double[] THRESHOLD_VALUES = { .5, 1, 1.5, 2, 2.5, 3, 3.5, 4, 5,
				6, 7, 8, 10, 12, 15, 20, 50, 100, Double.MAX_VALUE };
		static int MAX_RANK = 20;
		final long[][] recallAtRank = new long[THRESHOLD_VALUES.length][MAX_RANK];
		long count = 0;
		final long[] nGaveSuggestions = new long[THRESHOLD_VALUES.length];
		final double[] reciprocalSum = new double[THRESHOLD_VALUES.length];

		public synchronized void accumulate(final ResultObject ro) {
			this.count += ro.count;
			for (int i = 0; i < THRESHOLD_VALUES.length; i++) {
				this.nGaveSuggestions[i] += ro.nGaveSuggestions[i];
				this.reciprocalSum[i] += ro.reciprocalSum[i];
				for (int j = 0; j < this.recallAtRank[i].length; j++) {
					this.recallAtRank[i][j] += ro.recallAtRank[i][j];
				}
			}
		}

		public void printStats() {
			System.out.println("t=" + Arrays.toString(THRESHOLD_VALUES));
			System.out.println("count=" + count);
			System.out.print("recallAtRank=[");
			for (int i = 0; i < recallAtRank.length; i++) {
				System.out.print(Arrays.toString(recallAtRank[i]) + ";");
			}
			System.out.println("]");
			System.out.println("nGaveSuggestions="
					+ Arrays.toString(nGaveSuggestions));
			System.out.println("reciprocalSum="
					+ Arrays.toString(reciprocalSum));

			final double[][] pRecallAtRank = new double[THRESHOLD_VALUES.length][MAX_RANK];
			for (int i = 0; i < THRESHOLD_VALUES.length; i++) {
				final long suggestionsGiven = nGaveSuggestions[i];
				for (int j = 0; j < MAX_RANK; j++) {
					pRecallAtRank[i][j] = ((double) recallAtRank[i][j])
							/ suggestionsGiven;
				}
			}
			System.out.print("pRecallAtRank=[");
			for (int i = 0; i < pRecallAtRank.length; i++) {
				System.out.print(Arrays.toString(pRecallAtRank[i]) + ";");
			}
			System.out.println("]");

			final double[] pGaveSuggestions = new double[THRESHOLD_VALUES.length];
			for (int i = 0; i < THRESHOLD_VALUES.length; i++) {
				pGaveSuggestions[i] = ((double) nGaveSuggestions[i]) / count;
			}
			System.out.println("pGaveSuggestions="
					+ Arrays.toString(pGaveSuggestions));

		}
	}

	public static final Logger LOGGER = Logger.getLogger(NamingEvaluator.class
			.getName());

	private static final boolean DEBUG_RENAMINGS = SettingsLoader
			.getBooleanSetting("DEBUG", false);

	private static final boolean PER_FILE_STATS = SettingsLoader
			.getBooleanSetting("OutputFileStats", false);

	private static FileOutputStream debugFile;
	private static FileOutputStream fileStatsOutput;

	static {
		if (DEBUG_RENAMINGS) {
			try {
				debugFile = new FileOutputStream(new File(
						SettingsLoader.getStringSetting("debug_file",
								"renamings.debug")));
			} catch (final FileNotFoundException e) {
				LOGGER.warning("Error creating debug output file: " + e);
			}
		}

		if (PER_FILE_STATS) {
			try {
				fileStatsOutput = new FileOutputStream(new File(
						SettingsLoader.getStringSetting("OutputFileStats_file",
								"renamingsFileStats.out")));
			} catch (final FileNotFoundException e) {
				LOGGER.warning("Error creating debug output file: " + e);
			}
		}
	}

	private synchronized static void appendToDebugFile(final StringBuffer buf) {
		try {
			debugFile.write(buf.toString().getBytes());
			if (RandomUtils.nextDouble() < 0.01) {
				debugFile.flush();
			}
		} catch (final IOException e) {
			LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
		}
	}

	private synchronized static void appendToStatsFile(final StringBuffer buf) {
		try {
			fileStatsOutput.write(buf.toString().getBytes());
			if (RandomUtils.nextDouble() < 0.01) {
				fileStatsOutput.flush();
			}
		} catch (final IOException e) {
			LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
		}
	}


	final IdentifierNeighborsNGramLM model;

	/**
	 * An array of result objects, one for each scope type.
	 */
	private final ResultObject[] data;

	public NamingEvaluator(IdentifierNeighborsNGramLM model,
			final ResultObject[] dataObject) {
		this.model = model;
		data = dataObject;
	}


	final void evaluateRenamings(final Multimap<Scope, String> m,
			final File file) {
		BaseIdentifierRenamings singleFileRenamings = new BaseIdentifierRenamings(model.getTokenizer());
		singleFileRenamings.buildFromExistingModelExcludingFile(model,file);
		final ResultObject[] fileResults = new ResultObject[ScopeType.values().length];
		for (int i = 0; i < fileResults.length; i++) {
			fileResults[i] = new ResultObject();
		}

		for (final Entry<Scope, String> variable : m.entries()) {
			try {
				evaluateSingleRenaming(singleFileRenamings, fileResults, variable);
			} catch (final Throwable e) {
				LOGGER.warning("Failed to evaluate renaming " + variable + " "
						+ ExceptionUtils.getFullStackTrace(e));
			}
		}

		for (int i = 0; i < fileResults.length; i++) {
			data[i].accumulate(fileResults[i]);
		}

		if (PER_FILE_STATS) {
			final StringBuffer buf = new StringBuffer();
			buf.append(file.getAbsolutePath());
			buf.append("\t");
			long tp0 = 0;
			long allSum = 0;

			for (int i = 0; i < ScopeType.values().length; i++) {
				final long[] tpScope = data[i].recallAtRank[i];
				tp0 += tpScope[0];

				final long allScope = data[i].count;
				allSum += allScope;

				buf.append(data[i].nGaveSuggestions[0]);
				buf.append("\t");
			}

			buf.append(((double) tp0) / allSum);

			buf.append("\n");
			appendToStatsFile(buf);
		}

	}

	/**
	 */
	public void evaluateSingleRenaming(final AbstractIdentifierRenamings renamer, final ResultObject[] results,
			final Entry<Scope, String> identifier) {
		final SortedSet<Renaming> renamings = renamer.getRenamings(
				identifier.getKey(), identifier.getValue());

		if (DEBUG_RENAMINGS) {
			outputResult(identifier, renamings);
		}

		int scopeIndex;
		switch(identifier.getKey().type){
			case MethodScopeExtractor.METHOD_CALL:
				scopeIndex=2; //method;
				break;
			case TypenameScopeExtractor.TYPENAME:
				scopeIndex=0;
				break;
			default:
				scopeIndex=1;
				break;
		}
		results[scopeIndex].count++;

		if (renamings.first().name.equals("UNK_SYMBOL")) {
			return;
		}

		int pos = 1;
		Renaming expectedRenaming = new Renaming("INVALID!!!",
				Double.POSITIVE_INFINITY, 0, identifier.getKey());
		for (final Renaming renaming : renamings) {
			if (renaming.name.equals(identifier.getValue())
					|| (renaming.name.equals("UNK_SYMBOL") && renamer
							.isTrueUNK(identifier.getValue()))) {
				expectedRenaming = renaming;
				break;
			}
			pos++;
		}

		// Find threshold t after which the renaming was suggested
		int tLimitIndex = ResultObject.THRESHOLD_VALUES.length + 1;
		for (int i = 0; i < ResultObject.THRESHOLD_VALUES.length; i++) {
			if (ResultObject.THRESHOLD_VALUES[i] >= expectedRenaming.score) {
				tLimitIndex = i;
				break;
			}
		}

		for (int i = tLimitIndex; i < ResultObject.THRESHOLD_VALUES.length; i++) {
			for (int j = pos; j < ResultObject.MAX_RANK + 1; j++) {
				results[scopeIndex].recallAtRank[i][j - 1]++;
			}
			results[scopeIndex].reciprocalSum[i] += 1. / pos;
			results[scopeIndex].nGaveSuggestions[i]++;
		}
	}

	private void outputResult(final Entry<Scope, String> variable,
			final SortedSet<Renaming> renamings) {
		final StringBuffer buf = new StringBuffer();
		buf.append("=================================================\n");
		buf.append("Renaming " + variable.getValue() + " in the snippet ("
				+ variable.getKey().scopeType + ") \n");
		buf.append(variable.getKey().code);
		buf.append("----------------------------------------------------\n");
		int i = 1;

		double sum = 0;
		for (final Renaming r : renamings) {
			sum += Math.pow(2, -r.score);
		}
		sum /= 100.;

		for (final Renaming r : renamings) {
			buf.append(i + "\t" + r.name + "\t" + r.score + "\t"
					+ Math.pow(2, -r.score) / sum + "%\n");
			i++;
			if (i > 21) {
				break;
			}
		}
		buf.append("\n\n");

		appendToDebugFile(buf);
	}

	public void performEvaluation(final Collection<File> files,
			final IScopeExtractor scopeExtractor) {
		final ParallelThreadPool threadPool = new ParallelThreadPool();
		int fileNo = 0;
		for (final File fi : files) {
			threadPool.pushTask(new RenamingEvaluator(fi, scopeExtractor));
			fileNo++;
			if (fileNo % 50 == 0) {
				threadPool.pushTask(new Printer());
			}
		}
		threadPool.waitForTermination();
		(new Printer()).run();
	}
}
