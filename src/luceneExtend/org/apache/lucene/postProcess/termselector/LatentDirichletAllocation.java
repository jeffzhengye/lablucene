/*
 * LingPipe v. 3.8
 * Copyright (C) 2003-2009 Alias-i
 *
 * This program is licensed under the Alias-i Royalty Free License
 * Version 1 WITHOUT ANY WARRANTY, without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Alias-i
 * Royalty Free License Version 1 for more details.
 *
 * You should have received a copy of the Alias-i Royalty Free License
 * Version 1 along with this program; if not, visit
 * http://alias-i.com/lingpipe/licenses/lingpipe-license-1.txt or contact
 * Alias-i, Inc. at 181 North 11th Street, Suite 401, Brooklyn, NY 11211,
 * +1 (718) 290-9170.
 */

package org.apache.lucene.postProcess.termselector;

import com.aliasi.corpus.ObjectHandler;

import com.aliasi.symbol.SymbolTable;

import com.aliasi.tokenizer.Tokenizer;
import com.aliasi.tokenizer.TokenizerFactory;

import com.aliasi.stats.Statistics;

import com.aliasi.util.Math;
import com.aliasi.util.Iterators;
import com.aliasi.util.ObjectToCounterMap;
import com.aliasi.util.Strings;

// import java.util.Arrays;
import gnu.trove.TIntHashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.log4j.Logger;
import org.dutir.lucene.util.Rounding;

public class LatentDirichletAllocation {

	protected static Logger logger = Logger
			.getLogger(LatentDirichletAllocation.class);
	private final double mDocTopicPrior;
	private final double[][] mTopicWordProbs;

	public LatentDirichletAllocation(double docTopicPrior,
			double[][] topicWordProbs) {

		if (docTopicPrior <= 0.0 || Double.isNaN(docTopicPrior)
				|| Double.isInfinite(docTopicPrior)) {
			String msg = "Document-topic prior must be finite and positive."
					+ " Found docTopicPrior=" + docTopicPrior;
			throw new IllegalArgumentException(msg);
		}
		int numTopics = topicWordProbs.length;
		if (numTopics < 1) {
			String msg = "Require non-empty topic-word probabilities.";
			throw new IllegalArgumentException(msg);
		}

		int numWords = topicWordProbs[0].length;
		for (int topic = 1; topic < numTopics; ++topic) {
			if (topicWordProbs[topic].length != numWords) {
				String msg = "All topics must have the same number of words."
						+ " topicWordProbs[0].length="
						+ topicWordProbs[0].length + " topicWordProbs[" + topic
						+ "]=" + topicWordProbs[topic].length;
				throw new IllegalArgumentException(msg);
			}
		}

		for (int topic = 0; topic < numTopics; ++topic) {
			for (int word = 0; word < numWords; ++word) {
				if (topicWordProbs[topic][word] < 0.0
						|| topicWordProbs[topic][word] > 1.0) {
					String msg = "All probabilities must be between 0.0 and 1.0"
							+ " Found topicWordProbs["
							+ topic
							+ "]["
							+ word
							+ "]=" + topicWordProbs[topic][word];
					throw new IllegalArgumentException(msg);
				}
			}
		}

		mDocTopicPrior = docTopicPrior;
		mTopicWordProbs = topicWordProbs;
	}

	/**
	 * Returns the number of topics in this LDA model.
	 * 
	 * @return The number of topics in this model.
	 */
	public int numTopics() {
		return mTopicWordProbs.length;
	}

	/**
	 * Returns the number of words on which this LDA model is based.
	 * 
	 * @return The numbe of words in this model.
	 */
	public int numWords() {
		return mTopicWordProbs[0].length;
	}

	/**
	 * Returns the concentration value of the uniform Dirichlet prior over topic
	 * distributions for documents. This value is effectively a prior count for
	 * topics used for additive smoothing during estimation.
	 * 
	 * @return The prior count of topics in documents.
	 */
	public double documentTopicPrior() {
		return mDocTopicPrior;
	}

	/**
	 * Returns the probability of the specified word in the specified topic. The
	 * values returned should be non-negative and finite, and should sum to 1.0
	 * over all words for a specifed topic.
	 * 
	 * @param topic
	 *            Topic identifier.
	 * @param word
	 *            Word identifier.
	 * @return Probability of the specified word in the specified topic.
	 */
	public double wordProbability(int topic, int word) {
		return mTopicWordProbs[topic][word];
	}

	/**
	 * Returns an array representing of probabilities of words in the specified
	 * topic. The probabilities are indexed by word identifier.
	 * 
	 * <p>
	 * The returned result is a copy of the underlying data in the model so that
	 * changing it will not change the model.
	 * 
	 * @param topic
	 *            Topic identifier.
	 * @return Array of probabilities of words in the specified topic.
	 */
	public double[] wordProbabilities(int topic) {
		double[] xs = new double[mTopicWordProbs[topic].length];
		for (int i = 0; i < xs.length; ++i)
			xs[i] = mTopicWordProbs[topic][i];
		return xs;
	}

	/**
	 * Returns the specified number of Gibbs samples of topics for the specified
	 * tokens using the specified number of burnin epochs, the specified lag
	 * between samples, and the specified randomizer. The array returned is an
	 * array of samples, each sample consisting of a topic assignment to each
	 * token in the specified list of tokens. The tokens must all be in the
	 * appropriate range for this class
	 * 
	 * <p>
	 * See the class documentation for more information on how the samples are
	 * computed.
	 * 
	 * @param tokens
	 *            The tokens making up the document.
	 * @param numSamples
	 *            Number of Gibbs samples to return.
	 * @param burnin
	 *            The number of samples to take and throw away during the burnin
	 *            period.
	 * @param sampleLag
	 *            The interval between samples after burnin.
	 * @param random
	 *            The random number generator to use for this sampling process.
	 * @return The selection of topic samples generated by this sampler.
	 * @throws IndexOutOfBoundsException
	 *             If there are tokens whose value is less than zero, or whose
	 *             value is greater than the number of tokens in this model.
	 * @throws IllegalArgumentException
	 *             If the number of samples is not positive, the sample lag is
	 *             not positive, or if the burnin period is negative. if the
	 *             number of samples, burnin, and lag are not positive numbers.
	 */
	public short[][] sampleTopics(int[] tokens, int numSamples, int burnin,
			int sampleLag, Random random) {

		if (burnin < 0) {
			String msg = "Burnin period must be non-negative."
					+ " Found burnin=" + burnin;
			throw new IllegalArgumentException(msg);
		}

		if (numSamples < 1) {
			String msg = "Number of samples must be at least 1."
					+ " Found numSamples=" + numSamples;
			throw new IllegalArgumentException(msg);
		}

		if (sampleLag < 1) {
			String msg = "Sample lag must be at least 1." + " Found sampleLag="
					+ sampleLag;
			throw new IllegalArgumentException(msg);
		}

		double docTopicPrior = documentTopicPrior();
		int numTokens = tokens.length;

		int numTopics = numTopics();

		int[] topicCount = new int[numTopics];

		short[][] samples = new short[numSamples][numTokens];
		int sample = 0;
		short[] currentSample = samples[0];
		for (int token = 0; token < numTokens; ++token) {
			int randomTopic = random.nextInt(numTopics);
			++topicCount[randomTopic];
			currentSample[token] = (short) randomTopic;
		}

		double[] topicDistro = new double[numTopics];

		int numEpochs = burnin + sampleLag * (numSamples - 1);
		for (int epoch = 0; epoch < numEpochs; ++epoch) {
			for (int token = 0; token < numTokens; ++token) {
				int word = tokens[token];

				int currentTopic = currentSample[token];
				--topicCount[currentTopic];
				if (topicCount[currentTopic] < 0) {
					throw new IllegalArgumentException("bomb");
				}
				for (int topic = 0; topic < numTopics; ++topic) {
					topicDistro[topic] = (topicCount[topic] + docTopicPrior)
							* wordProbability(topic, word)
							+ (topic == 0 ? 0.0 : topicDistro[topic - 1]);
				}
				int sampledTopic = Statistics.sample(topicDistro, random);
				++topicCount[sampledTopic];
				currentSample[token] = (short) sampledTopic;
			}
			if ((epoch >= burnin) && (((epoch - burnin) % sampleLag) == 0)) {
				short[] pastSample = currentSample;
				++sample;
				currentSample = samples[sample];
				for (int token = 0; token < numTokens; ++token)
					currentSample[token] = pastSample[token];
			}
		}
		return samples;
	}

	/**
	 * Return the maximum a posteriori (MAP) estimate of the topic distribution
	 * for a document consisting of the specified tokens, using Gibbs sampling
	 * with the specified parameters. The Gibbs topic samples are simply
	 * averaged to produce the MAP estimate.
	 * 
	 * <p>
	 * See the method {@link #sampleTopics(int[],int,int,int,Random)} and the
	 * class documentation for more information on the sampling procedure.
	 * 
	 * @param tokens
	 *            The tokens making up the document.
	 * @param numSamples
	 *            Number of Gibbs samples to return.
	 * @param burnin
	 *            The number of samples to take and throw away during the burnin
	 *            period.
	 * @param sampleLag
	 *            The interval between samples after burnin.
	 * @param random
	 *            The random number generator to use for this sampling process.
	 * @return The selection of topic samples generated by this sampler.
	 * @throws IndexOutOfBoundsException
	 *             If there are tokens whose value is less than zero, or whose
	 *             value is greater than the number of tokens in this model.
	 * @throws IllegalArgumentException
	 *             If the number of samples is not positive, the sample lag is
	 *             not positive, or if the burnin period is negative. if the
	 *             number of samples, burnin, and lag are not positive numbers.
	 */
	public double[] mapTopicEstimate(int[] tokens, int numSamples, int burnin,
			int sampleLag, Random random) {
		short[][] sampleTopics = sampleTopics(tokens, numSamples, burnin,
				sampleLag, random);
		int numTopics = numTopics();
		int[] counts = new int[numTopics];
		for (short[] topics : sampleTopics) {
			for (int tok = 0; tok < topics.length; ++tok)
				++counts[topics[tok]];
		}
		double totalCount = 0;
		for (int topic = 0; topic < numTopics; ++topic)
			totalCount += counts[topic];
		double[] result = new double[numTopics];
		for (int topic = 0; topic < numTopics; ++topic)
			result[topic] = counts[topic] / totalCount;
		return result;

	}

	/**
	 * Run Gibbs sampling for the specified multinomial data, number of topics,
	 * priors, search parameters, randomization and callback sample handler.
	 * Gibbs sampling provides samples from the posterior distribution of topic
	 * assignments given the corpus and prior hyperparameters. A sample is
	 * encapsulated as an instance of class {@link GibbsSample}. This method
	 * will return the final sample and also send intermediate samples to an
	 * optional handler.
	 * 
	 * <p>
	 * The class documentation above explains Gibbs sampling for LDA as used in
	 * this method.
	 * 
	 * <p>
	 * The primary input is an array of documents, where each document is
	 * represented as an array of integers representing the tokens that appear
	 * in it. These tokens should be numbered contiguously from 0 for space
	 * efficiency. The topic assignments in the Gibbs sample are aligned as
	 * parallel arrays to the array of documents.
	 * 
	 * <p>
	 * The next three parameters are the hyperparameters of the model,
	 * specifically the number of topics, the prior count assigned to topics in
	 * a document, and the prior count assigned to words in topics. A rule of
	 * thumb for the document-topic prior is to set it to 5 divided by the
	 * number of topics (or less if there are very few topics; 0.1 is typically
	 * the maximum value used). A good general value for the topic-word prior is
	 * 0.01. Both of these priors will be diffuse and tend to lead to skewed
	 * posterior distributions.
	 * 
	 * <p>
	 * The following three parameters specify how the sampling is to be done.
	 * First, the sampler is &quot;burned in&quot; for a number of epochs
	 * specified by the burnin parameter. After burn in, samples are taken after
	 * fixed numbers of documents to avoid correlation in the samples; the
	 * sampling frequency is specified by the sample lag. Finally, the number of
	 * samples to be taken is specified. For instance, if the burnin is 1000,
	 * the sample lag is 250, and the number of samples is 5, then samples are
	 * taken after 1000, 1250, 1500, 1750 and 2000 epochs. If a non-null handler
	 * object is specified in the method call, its
	 * <code>handle(GibbsSample)</code> method is called with each the samples
	 * produced as above.
	 * 
	 * <p>
	 * The final sample in the chain of samples is returned as the result. Note
	 * that this sample will also have been passed to the specified handler as
	 * the last sample for the handler.
	 * 
	 * <p>
	 * A random number generator must be supplied as an argument. This may just
	 * be a new instance of {@link java.util.Random} or a custom extension. It
	 * is used for all randomization in this method.
	 * 
	 * @param docWords
	 *            Corpus of documents to be processed.
	 * @param numTopics
	 *            Number of latent topics to generate.
	 * @param docTopicPrior
	 *            Prior count of topics in a document.
	 * @param topicWordPrior
	 *            Prior count of words in a topic.
	 * @param burninEpochs
	 *            Number of epochs to run before taking a sample.
	 * @param sampleLag
	 *            Frequency between samples.
	 * @param numSamples
	 *            Number of samples to take before exiting.
	 * @param random
	 *            Random number generator.
	 * @param handler
	 *            Handler to which the samples are sent.
	 * @return The final Gibbs sample.
	 */
	public static GibbsSample gibbsSampler(int[][] docWords, short numTopics,
			double docTopicPrior, double topicWordPrior,

			int burninEpochs, int sampleLag, int numSamples,

			Random random,

			ObjectHandler<GibbsSample> handler) {

		validateInputs(docWords, numTopics, docTopicPrior, topicWordPrior,
				burninEpochs, sampleLag, numSamples);

		int numDocs = docWords.length;
		int numWords = max(docWords) + 1;

		int numTokens = 0;
		for (int doc = 0; doc < numDocs; ++doc)
			numTokens += docWords[doc].length;

		// should inputs be permuted?
		// for (int doc = 0; doc < numDocs; ++doc)
		// Arrays.permute(docWords[doc]);

		short[][] currentSample = new short[numDocs][];
		for (int doc = 0; doc < numDocs; ++doc)
			currentSample[doc] = new short[docWords[doc].length];

		int[][] docTopicCount = new int[numDocs][numTopics];
		int[][] wordTopicCount = new int[numWords][numTopics];
		int[] topicTotalCount = new int[numTopics];

		for (int doc = 0; doc < numDocs; ++doc) {
			for (int tok = 0; tok < docWords[doc].length; ++tok) {
				int word = docWords[doc][tok];
				int topic = random.nextInt(numTopics);
				currentSample[doc][tok] = (short) topic;
				++docTopicCount[doc][topic];
				++wordTopicCount[word][topic];
				++topicTotalCount[topic];
			}
		}

		double numWordsTimesTopicWordPrior = numWords * topicWordPrior;
		double[] topicDistro = new double[numTopics];
		int numEpochs = burninEpochs + sampleLag * (numSamples - 1);
		for (int epoch = 0; epoch <= numEpochs; ++epoch) {
			double corpusLog2Prob = 0.0;
			int numChangedTopics = 0;
			for (int doc = 0; doc < numDocs; ++doc) {
				int[] docWordsDoc = docWords[doc];
				short[] currentSampleDoc = currentSample[doc];
				int[] docTopicCountDoc = docTopicCount[doc];
				for (int tok = 0; tok < docWordsDoc.length; ++tok) {
					int word = docWordsDoc[tok];
					int[] wordTopicCountWord = wordTopicCount[word];
					int currentTopic = currentSampleDoc[tok];
					if (currentTopic == 0) {
						topicDistro[0] = (docTopicCountDoc[0] - 1.0 + docTopicPrior)
								* (wordTopicCountWord[0] - 1.0 + topicWordPrior)
								/ (topicTotalCount[0] - 1.0 + numWordsTimesTopicWordPrior);
					} else {
						topicDistro[0] = (docTopicCountDoc[0] + docTopicPrior)
								* (wordTopicCountWord[0] + topicWordPrior)
								/ (topicTotalCount[0] + numWordsTimesTopicWordPrior);
						for (int topic = 1; topic < currentTopic; ++topic) {
							topicDistro[topic] = (docTopicCountDoc[topic] + docTopicPrior)
									* (wordTopicCountWord[topic] + topicWordPrior)
									/ (topicTotalCount[topic] + numWordsTimesTopicWordPrior)
									+ topicDistro[topic - 1];
						}
						topicDistro[currentTopic] = (docTopicCountDoc[currentTopic] - 1.0 + docTopicPrior)
								* (wordTopicCountWord[currentTopic] - 1.0 + topicWordPrior)
								/ (topicTotalCount[currentTopic] - 1.0 + numWordsTimesTopicWordPrior)
								+ topicDistro[currentTopic - 1];
					}
					for (int topic = currentTopic + 1; topic < numTopics; ++topic) {
						topicDistro[topic] = (docTopicCountDoc[topic] + docTopicPrior)
								* (wordTopicCountWord[topic] + topicWordPrior)
								/ (topicTotalCount[topic] + numWordsTimesTopicWordPrior)
								+ topicDistro[topic - 1];
					}
					int sampledTopic = Statistics.sample(topicDistro, random);

					// compute probs before updates
					if (sampledTopic != currentTopic) {
						currentSampleDoc[tok] = (short) sampledTopic;
						--docTopicCountDoc[currentTopic];
						--wordTopicCountWord[currentTopic];
						--topicTotalCount[currentTopic];
						++docTopicCountDoc[sampledTopic];
						++wordTopicCountWord[sampledTopic];
						++topicTotalCount[sampledTopic];
					}

					if (sampledTopic != currentTopic)
						++numChangedTopics;
					double topicProbGivenDoc = docTopicCountDoc[sampledTopic]
							/ (double) docWordsDoc.length;
					double wordProbGivenTopic = wordTopicCountWord[sampledTopic]
							/ (double) topicTotalCount[sampledTopic];
					double tokenLog2Prob = Math.log2(topicProbGivenDoc
							* wordProbGivenTopic);
					corpusLog2Prob += tokenLog2Prob;
				}
			}
			// double crossEntropyRate = -corpusLog2Prob / numTokens;
			if ((epoch >= burninEpochs)
					&& (((epoch - burninEpochs) % sampleLag) == 0)) {
				GibbsSample sample = new GibbsSample(epoch, currentSample,
						docWords, docTopicPrior, topicWordPrior, docTopicCount,
						wordTopicCount, topicTotalCount, numChangedTopics,
						numWords, numTokens);
				if (handler != null)
					handler.handle(sample);
				if (epoch == numEpochs)
					return sample;
			}
		}
		throw new IllegalStateException(
				"unreachable in practice because of return if epoch==numEpochs");
	}

	public static GibbsSample gibbsSampler(int[][] docWords, short numTopics,
			double docTopicPrior, double topicWordPrior1,

			int burninEpochs, int sampleLag, int numSamples, Random random,
			int queryids[], int backgroundids[],
			ObjectHandler<GibbsSample> handler) {

		validateInputs(docWords, numTopics, docTopicPrior, topicWordPrior1,
				burninEpochs, sampleLag, numSamples);

		double ratio = 2;

		TIntHashSet querySet = new TIntHashSet();
		TIntHashSet backSet = new TIntHashSet();
		querySet.addAll(queryids);
		backSet.addAll(backgroundids);

		int numDocs = docWords.length;
		int numWords = max(docWords) + 1;

		int numTokens = 0;
		for (int doc = 0; doc < numDocs; ++doc)
			numTokens += docWords[doc].length;

		// should inputs be permuted?
		// for (int doc = 0; doc < numDocs; ++doc)
		// Arrays.permute(docWords[doc]);

		short[][] currentSample = new short[numDocs][];
		for (int doc = 0; doc < numDocs; ++doc)
			currentSample[doc] = new short[docWords[doc].length];

		int[][] docTopicCount = new int[numDocs][numTopics];
		int[][] wordTopicCount = new int[numWords][numTopics];
		int[] topicTotalCount = new int[numTopics];

		for (int doc = 0; doc < numDocs; ++doc) {
			for (int tok = 0; tok < docWords[doc].length; ++tok) {
				int word = docWords[doc][tok];
				int topic = random.nextInt(numTopics);
				if (querySet.contains(word)) {
					topic = 0;
				} else if (backSet.contains(word)) {
					topic = numTopics - 1;
				}
				currentSample[doc][tok] = (short) topic;
				++docTopicCount[doc][topic];
				++wordTopicCount[word][topic];
				++topicTotalCount[topic];
			}
		}

		double numWordsTimesTopicWordPrior = numWords * topicWordPrior1;
		double[] topicDistro = new double[numTopics]; // is cumulative
														// probability
		int numEpochs = burninEpochs + sampleLag * (numSamples - 1);

		for (int epoch = 0; epoch <= numEpochs; ++epoch) {
			double corpusLog2Prob = 0.0;
			int numChangedTopics = 0;
			for (int doc = 0; doc < numDocs; ++doc) {
				int[] docWordsDoc = docWords[doc];
				short[] currentSampleDoc = currentSample[doc];
				int[] docTopicCountDoc = docTopicCount[doc];
				for (int tok = 0; tok < docWordsDoc.length; ++tok) {
					int word = docWordsDoc[tok];
					double topicWordPrior = topicWordPrior1;
					numWordsTimesTopicWordPrior = numWords
							* topicWordPrior1
							+ (topicTotalCount[0] + (backSet.size() > 0 ? 1 : 0)
									* topicTotalCount[numTopics - 1])
							* (ratio - 1) * topicWordPrior1;
					if (querySet.contains(word) || backSet.contains(word)) {
						topicWordPrior = ratio * topicWordPrior1;
					}

					int[] wordTopicCountWord = wordTopicCount[word];
					int currentTopic = currentSampleDoc[tok];
					if (currentTopic == 0) {
						topicDistro[0] = (docTopicCountDoc[0] - 1.0 + docTopicPrior)
								* (wordTopicCountWord[0] - 1.0 + topicWordPrior)
								/ (topicTotalCount[0] - 1.0 + numWordsTimesTopicWordPrior);
					} else {
						topicDistro[0] = (docTopicCountDoc[0] + docTopicPrior)
								* (wordTopicCountWord[0] + topicWordPrior)
								/ (topicTotalCount[0] + numWordsTimesTopicWordPrior);
						for (int topic = 1; topic < currentTopic; ++topic) {
							topicDistro[topic] = (docTopicCountDoc[topic] + docTopicPrior)
									* (wordTopicCountWord[topic] + topicWordPrior)
									/ (topicTotalCount[topic] + numWordsTimesTopicWordPrior)
									+ topicDistro[topic - 1];
						}
						topicDistro[currentTopic] = (docTopicCountDoc[currentTopic] - 1.0 + docTopicPrior)
								* (wordTopicCountWord[currentTopic] - 1.0 + topicWordPrior)
								/ (topicTotalCount[currentTopic] - 1.0 + numWordsTimesTopicWordPrior)
								+ topicDistro[currentTopic - 1];
					}
					for (int topic = currentTopic + 1; topic < numTopics; ++topic) {
						topicDistro[topic] = (docTopicCountDoc[topic] + docTopicPrior)
								* (wordTopicCountWord[topic] + topicWordPrior)
								/ (topicTotalCount[topic] + numWordsTimesTopicWordPrior)
								+ topicDistro[topic - 1];
					}
					int sampledTopic = Statistics.sample(topicDistro, random);

					// if(logger.isDebugEnabled()){
					// if(querySet.contains(word) && sampledTopic !=
					// currentTopic){
					// StringBuilder buf = new StringBuilder();
					// for(int i=0; i < topicDistro.length; i++){
					// buf.append(Rounding.round(topicDistro[i], 4) +",");
					// }
					// buf.append("cur:" + currentTopic +", sample:" +
					// sampledTopic);
					// logger.debug(buf.toString());
					// }
					//                	
					// }
					// if(sampledTopic != currentTopic &&
					// querySet.contains(word)){
					// sampledTopic = currentTopic;
					// }

					// compute probs before updates
					if (sampledTopic != currentTopic) {
						currentSampleDoc[tok] = (short) sampledTopic;
						--docTopicCountDoc[currentTopic];
						--wordTopicCountWord[currentTopic];
						--topicTotalCount[currentTopic];
						++docTopicCountDoc[sampledTopic];
						++wordTopicCountWord[sampledTopic];
						++topicTotalCount[sampledTopic];
					}

					if (sampledTopic != currentTopic)
						++numChangedTopics;
					double topicProbGivenDoc = docTopicCountDoc[sampledTopic]
							/ (double) docWordsDoc.length;
					double wordProbGivenTopic = wordTopicCountWord[sampledTopic]
							/ (double) topicTotalCount[sampledTopic];
					double tokenLog2Prob = Math.log2(topicProbGivenDoc
							* wordProbGivenTopic);
					corpusLog2Prob += tokenLog2Prob;
				}

			}
			if (epoch % 500 == 0) {
				if(logger.isDebugEnabled()) logger.debug("epoch " + epoch + " -corpusLog2Prob:"
						+ corpusLog2Prob);
			}
			// double crossEntropyRate = -corpusLog2Prob / numTokens;
			if ((epoch >= burninEpochs)
					&& (((epoch - burninEpochs) % sampleLag) == 0)) {
				GibbsSample sample = new GibbsSample(epoch, currentSample,
						docWords, docTopicPrior, topicWordPrior1,
						docTopicCount, wordTopicCount, topicTotalCount,
						numChangedTopics, numWords, numTokens, querySet,
						backSet);
				if (handler != null)
					handler.handle(sample);
				if (epoch == numEpochs)
					return sample;
			}
		}
		throw new IllegalStateException(
				"unreachable in practice because of return if epoch==numEpochs");
	}

	
	public static GibbsSample gibbsSampler(int[][] docWords, short numTopics,
			double docTopicPrior, double topicWordPrior1,
			int burninEpochs, int sampleLag, int numSamples, Random random,
			int queryids[], int backgroundids[],
			ObjectHandler<GibbsSample> handler, 
			TermAssociation tAss) {

		validateInputs(docWords, numTopics, docTopicPrior, topicWordPrior1,
				burninEpochs, sampleLag, numSamples);

		double ratio = 2;

		TIntHashSet querySet = new TIntHashSet();
		TIntHashSet backSet = new TIntHashSet();
		querySet.addAll(queryids);
		backSet.addAll(backgroundids);

		int numDocs = docWords.length;
		int numWords = max(docWords) + 1;

		int numTokens = 0;
		for (int doc = 0; doc < numDocs; ++doc)
			numTokens += docWords[doc].length;

		// should inputs be permuted?
		// for (int doc = 0; doc < numDocs; ++doc)
		// Arrays.permute(docWords[doc]);

		short[][] currentSample = new short[numDocs][];
		for (int doc = 0; doc < numDocs; ++doc)
			currentSample[doc] = new short[docWords[doc].length];

		int[][] docTopicCount = new int[numDocs][numTopics];
		int[][] wordTopicCount = new int[numWords][numTopics];
		int[] topicTotalCount = new int[numTopics];

		for (int doc = 0; doc < numDocs; ++doc) {
			for (int tok = 0; tok < docWords[doc].length; ++tok) {
				int word = docWords[doc][tok];
				int topic = random.nextInt(numTopics);
				if (querySet.contains(word)) {
					topic = 0;
				} else if (backSet.contains(word)) {
					topic = numTopics - 1;
				}
				currentSample[doc][tok] = (short) topic;
				++docTopicCount[doc][topic];
				++wordTopicCount[word][topic];
				++topicTotalCount[topic];
			}
		}

		double numWordsTimesTopicWordPrior = numWords * topicWordPrior1;
		double[] topicDistro = new double[numTopics]; // is cumulative probability
		int numEpochs = burninEpochs + sampleLag * (numSamples - 1);

		for (int epoch = 0; epoch <= numEpochs; ++epoch) {
			double corpusLog2Prob = 0.0;
			int numChangedTopics = 0;
			for (int doc = 0; doc < numDocs; ++doc) {
				int[] docWordsDoc = docWords[doc];
				short[] currentSampleDoc = currentSample[doc];
				int[] docTopicCountDoc = docTopicCount[doc];
				for (int tok = 0; tok < docWordsDoc.length; ++tok) {
					int word = docWordsDoc[tok];
					double topicWordPrior = topicWordPrior1;
					numWordsTimesTopicWordPrior = numWords
							* topicWordPrior1
							+ (topicTotalCount[0] + (backSet.size() > 0 ? 1 : 0)
									* topicTotalCount[numTopics - 1])
							* (ratio - 1) * topicWordPrior1;
					if (querySet.contains(word) || backSet.contains(word)) {
						topicWordPrior = ratio * topicWordPrior1;
					}

					int[] wordTopicCountWord = wordTopicCount[word];
					int currentTopic = currentSampleDoc[tok];
					
					
					//**revise the sample probabilities using term associations**//
					double topicPriors[] = new double[numTopics];
					int topicWordCounts[] = new int[numTopics];
					Arrays.fill(topicPriors, 0);
					for (int doci = 0; doci < currentSample.length; ++doci){
						for(int wordj =0; wordj < currentSample[doci].length; wordj++){
							int tCurrentSample = currentSample[doci][wordj];
							if(docWords[doci][wordj] == word){
								continue;
							}
							topicPriors[tCurrentSample] +=  tAss.conditionProb(docWords[doci][wordj], word);
							topicWordCounts[tCurrentSample]++;
						}
					}
					for (int i = 0; i < numTopics; i++) {
						topicPriors[i] = topicPriors[i] / topicWordCounts[i];
					}
					///**********************************************************//
					
					if (currentTopic == 0) {
						topicDistro[0] = (docTopicCountDoc[0] - 1.0 + docTopicPrior)
								* (wordTopicCountWord[0] - 1.0 + topicWordPrior)
								/ (topicTotalCount[0] - 1.0 + numWordsTimesTopicWordPrior);
						topicDistro[0] *= topicPriors[0];
					} else {
						topicDistro[0] = (docTopicCountDoc[0] + docTopicPrior)
								* (wordTopicCountWord[0] + topicWordPrior)
								/ (topicTotalCount[0] + numWordsTimesTopicWordPrior);
						topicDistro[0] *= topicPriors[0];
						for (int topic = 1; topic < currentTopic; ++topic) {
							topicDistro[topic] = topicPriors[topic]*  // multiple the prior for each topic
									(docTopicCountDoc[topic] + docTopicPrior)
									* (wordTopicCountWord[topic] + topicWordPrior) 
									/ (topicTotalCount[topic] + numWordsTimesTopicWordPrior)
									+ topicDistro[topic - 1];
						}
						topicDistro[currentTopic] = topicPriors[currentTopic]*
								(docTopicCountDoc[currentTopic] - 1.0 + docTopicPrior)
								* (wordTopicCountWord[currentTopic] - 1.0 + topicWordPrior)
								/ (topicTotalCount[currentTopic] - 1.0 + numWordsTimesTopicWordPrior)
								+ topicDistro[currentTopic - 1];
					}
					for (int topic = currentTopic + 1; topic < numTopics; ++topic) {
						topicDistro[topic] = topicPriors[topic]*
								(docTopicCountDoc[topic] + docTopicPrior)
								* (wordTopicCountWord[topic] + topicWordPrior)
								/ (topicTotalCount[topic] + numWordsTimesTopicWordPrior)
								+ topicDistro[topic - 1];
					}
					
					//**revise the sample probabilities using term associations**//


//					if (logger.isDebugEnabled()) {
//						StringBuilder buf = new StringBuilder();
//						buf.append("\n");
//						for (int i = 0; i < numTopics; i++) {
//							buf.append("" + Rounding.round(topicDistro[i], 4) + "\t");
//						}
//						buf.append("\n");
//						for (int i = 0; i < numTopics; i++) {
//							buf.append("" + Rounding.round(topicPriors[i]/topicWordCounts[i], 4) + "\t");
//						}
//						logger.debug(buf.toString());
//					}

					///*********************************************************///
					
					
					int sampledTopic = Statistics.sample(topicDistro, random);

					// if(logger.isDebugEnabled()){
					// if(querySet.contains(word) && sampledTopic !=
					// currentTopic){
					// StringBuilder buf = new StringBuilder();
					// for(int i=0; i < topicDistro.length; i++){
					// buf.append(Rounding.round(topicDistro[i], 4) +",");
					// }
					// buf.append("cur:" + currentTopic +", sample:" +
					// sampledTopic);
					// logger.debug(buf.toString());
					// }
					//                	
					// }
					// if(sampledTopic != currentTopic &&
					// querySet.contains(word)){
					// sampledTopic = currentTopic;
					// }

					// compute probs before updates
					if (sampledTopic != currentTopic) {
						currentSampleDoc[tok] = (short) sampledTopic;
						--docTopicCountDoc[currentTopic];
						--wordTopicCountWord[currentTopic];
						--topicTotalCount[currentTopic];
						++docTopicCountDoc[sampledTopic];
						++wordTopicCountWord[sampledTopic];
						++topicTotalCount[sampledTopic];
					}

					if (sampledTopic != currentTopic)
						++numChangedTopics;
					double topicProbGivenDoc = docTopicCountDoc[sampledTopic]
							/ (double) docWordsDoc.length;
					double wordProbGivenTopic = wordTopicCountWord[sampledTopic]
							/ (double) topicTotalCount[sampledTopic];
					double tokenLog2Prob = Math.log2(topicProbGivenDoc
							* wordProbGivenTopic);
					corpusLog2Prob += tokenLog2Prob;
				}

			}
			if (epoch % 500 == 0) {
				if(logger.isDebugEnabled()) logger.debug("epoch " + epoch + " -corpusLog2Prob:"
						+ corpusLog2Prob);
			}
			// double crossEntropyRate = -corpusLog2Prob / numTokens;
			if ((epoch >= burninEpochs)
					&& (((epoch - burninEpochs) % sampleLag) == 0)) {
				GibbsSample sample = new GibbsSample(epoch, currentSample,
						docWords, docTopicPrior, topicWordPrior1,
						docTopicCount, wordTopicCount, topicTotalCount,
						numChangedTopics, numWords, numTokens, querySet,
						backSet);
				if (handler != null)
					handler.handle(sample);
				if (epoch == numEpochs){
					if(logger.isDebugEnabled()) logger.debug("epoch " + epoch + ", corpusLog2Prob:"
							+ corpusLog2Prob);
					return sample;
				}
					
			}
		}
		throw new IllegalStateException(
				"unreachable in practice because of return if epoch==numEpochs");
	}
	/**
	 * Return an iterator over Gibbs samples for the specified document-word
	 * corpus, number of topics, priors and randomizer. These are the same Gibbs
	 * samples as wold be produced by the method
	 * {@link #gibbsSampler(int[][],short,double,double,int,int,int,Random,ObjectHandler)}
	 * . See that method and the class documentation for more details.
	 * 
	 * @param docWords
	 *            Corpus of documents to be processed.
	 * @param numTopics
	 *            Number of latent topics to generate.
	 * @param docTopicPrior
	 *            Prior count of topics in a document.
	 * @param topicWordPrior
	 *            Prior count of words in a topic.
	 * @param random
	 *            Random number generator.
	 */
	public static Iterator<GibbsSample> gibbsSample(int[][] docWords,
			short numTopics, double docTopicPrior, double topicWordPrior,
			Random random) {
		validateInputs(docWords, numTopics, docTopicPrior, topicWordPrior);

		return new SampleIterator(docWords, numTopics, docTopicPrior,
				topicWordPrior, random);
	}

	static class SampleIterator extends Iterators.Buffered<GibbsSample> {
		private final int[][] mDocWords;
		private final short mNumTopics;
		private final double mDocTopicPrior;
		private final double mTopicWordPrior;
		private final Random mRandom;

		private final int mNumDocs;
		private final int mNumWords;
		private final int mNumTokens;

		private final short[][] mCurrentSample;
		private final int[][] mDocTopicCount;
		private final int[][] mWordTopicCount;
		private final int[] mTopicTotalCount;

		private int mNumChangedTopics;
		private int mEpoch = 0;

		SampleIterator(int[][] docWords, short numTopics, double docTopicPrior,
				double topicWordPrior, Random random) {
			mDocWords = docWords;
			mNumTopics = numTopics;
			mDocTopicPrior = docTopicPrior;
			mTopicWordPrior = topicWordPrior;
			mRandom = random;

			mNumDocs = mDocWords.length;
			mNumWords = max(mDocWords) + 1;

			int numTokens = 0;
			for (int doc = 0; doc < mNumDocs; ++doc)
				numTokens += mDocWords[doc].length;
			mNumTokens = numTokens;

			mNumChangedTopics = numTokens;

			mCurrentSample = new short[mNumDocs][];
			for (int doc = 0; doc < mNumDocs; ++doc)
				mCurrentSample[doc] = new short[mDocWords[doc].length];

			mDocTopicCount = new int[mNumDocs][numTopics];
			mWordTopicCount = new int[mNumWords][numTopics];
			mTopicTotalCount = new int[numTopics];

			// random initialization
			for (int doc = 0; doc < mNumDocs; ++doc) {
				for (int tok = 0; tok < docWords[doc].length; ++tok) {
					int word = docWords[doc][tok];
					int topic = mRandom.nextInt(numTopics);
					mCurrentSample[doc][tok] = (short) topic;
					++mDocTopicCount[doc][topic];
					++mWordTopicCount[word][topic];
					++mTopicTotalCount[topic];
				}
			}
		}

		@Override
		protected GibbsSample bufferNext() {

			// create existing sample; then compute next one by setting all vars
			GibbsSample sample = new GibbsSample(mEpoch, mCurrentSample,
					mDocWords, mDocTopicPrior, mTopicWordPrior, mDocTopicCount,
					mWordTopicCount, mTopicTotalCount, mNumChangedTopics,
					mNumWords, mNumTokens);
			++mEpoch;
			double numWordsTimesTopicWordPrior = mNumWords * mTopicWordPrior;
			double[] topicDistro = new double[mNumTopics];
			int numChangedTopics = 0;
			for (int doc = 0; doc < mNumDocs; ++doc) {
				int[] docWordsDoc = mDocWords[doc];
				short[] currentSampleDoc = mCurrentSample[doc];
				int[] docTopicCountDoc = mDocTopicCount[doc];
				for (int tok = 0; tok < docWordsDoc.length; ++tok) {
					int word = docWordsDoc[tok];
					int[] wordTopicCountWord = mWordTopicCount[word];
					int currentTopic = currentSampleDoc[tok];
					if (currentTopic == 0) {
						topicDistro[0] = (docTopicCountDoc[0] - 1.0 + mDocTopicPrior)
								* (wordTopicCountWord[0] - 1.0 + mTopicWordPrior)
								/ (mTopicTotalCount[0] - 1.0 + numWordsTimesTopicWordPrior);
					} else {
						topicDistro[0] = (docTopicCountDoc[0] + mDocTopicPrior)
								* (wordTopicCountWord[0] + mTopicWordPrior)
								/ (mTopicTotalCount[0] + numWordsTimesTopicWordPrior);
						for (int topic = 1; topic < currentTopic; ++topic) {
							topicDistro[topic] = (docTopicCountDoc[topic] + mDocTopicPrior)
									* (wordTopicCountWord[topic] + mTopicWordPrior)
									/ (mTopicTotalCount[topic] + numWordsTimesTopicWordPrior)
									+ topicDistro[topic - 1];
						}
						topicDistro[currentTopic] = (docTopicCountDoc[currentTopic] - 1.0 + mDocTopicPrior)
								* (wordTopicCountWord[currentTopic] - 1.0 + mTopicWordPrior)
								/ (mTopicTotalCount[currentTopic] - 1.0 + numWordsTimesTopicWordPrior)
								+ topicDistro[currentTopic - 1];
					}
					for (int topic = currentTopic + 1; topic < mNumTopics; ++topic) {
						topicDistro[topic] = (docTopicCountDoc[topic] + mDocTopicPrior)
								* (wordTopicCountWord[topic] + mTopicWordPrior)
								/ (mTopicTotalCount[topic] + numWordsTimesTopicWordPrior)
								+ topicDistro[topic - 1];
					}
					int sampledTopic = Statistics.sample(topicDistro, mRandom);
					if (sampledTopic != currentTopic) {
						currentSampleDoc[tok] = (short) sampledTopic;
						--docTopicCountDoc[currentTopic];
						--wordTopicCountWord[currentTopic];
						--mTopicTotalCount[currentTopic];
						++docTopicCountDoc[sampledTopic];
						++wordTopicCountWord[sampledTopic];
						++mTopicTotalCount[sampledTopic];
						++numChangedTopics;
					}
				}
			}
			mNumChangedTopics = numChangedTopics;
			return sample;
		}

	}

	/**
	 * Tokenize an array of text documents represented as character sequences
	 * into a form usable by LDA, using the specified tokenizer factory and
	 * symbol table. The symbol table should be constructed fresh for this
	 * application, but may be used after this method is called for further
	 * token to symbol conversions. Only tokens whose count is equal to or
	 * larger the specified minimum count are included. Only tokens whose count
	 * exceeds the minimum are added to the symbol table, thus producing a
	 * compact set of symbol assignments to tokens for downstream processing.
	 * 
	 * <p>
	 * <i>Warning</i>: With some tokenizer factories and or minimum count
	 * thresholds, there may be documents with no tokens in them.
	 * 
	 * @param texts
	 *            The text corpus.
	 * @param tokenizerFactory
	 *            A tokenizer factory for tokenizing the texts.
	 * @param symbolTable
	 *            Symbol table used to convert tokens to identifiers.
	 * @param minCount
	 *            Minimum count for a token to be included in a document's
	 *            representation.
	 * @return The tokenized form of a document suitable for input to LDA.
	 */
	public static int[][] tokenizeDocuments(CharSequence[] texts,
			TokenizerFactory tokenizerFactory, SymbolTable symbolTable,
			int minCount) {
		ObjectToCounterMap<String> tokenCounter = new ObjectToCounterMap<String>();
		for (CharSequence text : texts) {
			char[] cs = Strings.toCharArray(text);
			Tokenizer tokenizer = tokenizerFactory.tokenizer(cs, 0, cs.length);
			for (String token : tokenizer)
				tokenCounter.increment(token);
		}
		tokenCounter.prune(minCount);
		Set<String> tokenSet = tokenCounter.keySet();
		for (String token : tokenSet)
			symbolTable.getOrAddSymbol(token);

		int[][] docTokenId = new int[texts.length][];
		for (int i = 0; i < docTokenId.length; ++i) {
			docTokenId[i] = tokenizeDocument(texts[i], tokenizerFactory,
					symbolTable);
		}
		return docTokenId;
	}

	/**
	 * Tokenizes the specified text document using the specified tokenizer
	 * factory returning only tokens that exist in the symbol table. This method
	 * is useful within a given LDA model for tokenizing new documents into
	 * lists of words.
	 * 
	 * @param text
	 *            Character sequence to tokenize.
	 * @param tokenizerFactory
	 *            Tokenizer factory for tokenization.
	 * @param symbolTable
	 *            Symbol table to use for converting tokens to symbols.
	 * @return The array of integer symbols for tokens that exist in the symbol
	 *         table.
	 */
	public static int[] tokenizeDocument(CharSequence text,
			TokenizerFactory tokenizerFactory, SymbolTable symbolTable) {
		char[] cs = Strings.toCharArray(text);
		Tokenizer tokenizer = tokenizerFactory.tokenizer(cs, 0, cs.length);
		List<Integer> idList = new ArrayList<Integer>();
		for (String token : tokenizer) {
			int id = symbolTable.symbolToID(token);
			if (id >= 0)
				idList.add(id);
		}
		int[] tokenIds = new int[idList.size()];
		for (int i = 0; i < tokenIds.length; ++i)
			tokenIds[i] = idList.get(i);

		return tokenIds;
	}

	static int max(int[][] xs) {
		int max = 0;
		for (int i = 0; i < xs.length; ++i) {
			int[] xsI = xs[i];
			for (int j = 0; j < xsI.length; ++j) {
				if (xsI[j] > max)
					max = xsI[j];
			}
		}
		return max;
	}

	static double relativeDifference(double x, double y) {
		return java.lang.Math.abs(x - y)
				/ (java.lang.Math.abs(x) + java.lang.Math.abs(y));
	}

	static void validateInputs(int[][] docWords, short numTopics,
			double docTopicPrior, double topicWordPrior, int burninEpochs,
			int sampleLag, int numSamples) {

		validateInputs(docWords, numTopics, docTopicPrior, topicWordPrior);

		if (burninEpochs < 0) {
			String msg = "Number of burnin epochs must be non-negative."
					+ " Found burninEpochs=" + burninEpochs;
			throw new IllegalArgumentException(msg);
		}

		if (sampleLag < 1) {
			String msg = "Sample lag must be positive." + " Found sampleLag="
					+ sampleLag;
			throw new IllegalArgumentException(msg);
		}

		if (numSamples < 1) {
			String msg = "Number of samples must be positive."
					+ " Found numSamples=" + numSamples;
			throw new IllegalArgumentException(msg);
		}
	}

	static void validateInputs(int[][] docWords, short numTopics,
			double docTopicPrior, double topicWordPrior) {

		for (int doc = 0; doc < docWords.length; ++doc) {
			for (int tok = 0; tok < docWords[doc].length; ++tok) {
				if (docWords[doc][tok] >= 0)
					continue;
				String msg = "All tokens must have IDs greater than 0."
						+ " Found docWords[" + doc + "][" + tok + "]="
						+ docWords[doc][tok];
				throw new IllegalArgumentException(msg);
			}
		}

		if (numTopics < 1) {
			String msg = "Num topics must be positive." + " Found numTopics="
					+ numTopics;
			throw new IllegalArgumentException(msg);
		}

		if (Double.isInfinite(docTopicPrior) || Double.isNaN(docTopicPrior)
				|| docTopicPrior < 0.0) {
			String msg = "Document-topic prior must be finite and positive."
					+ " Found docTopicPrior=" + docTopicPrior;
			throw new IllegalArgumentException(msg);
		}

		if (Double.isInfinite(topicWordPrior) || Double.isNaN(topicWordPrior)
				|| topicWordPrior < 0.0) {
			String msg = "Topic-word prior must be finite and positive."
					+ " Found topicWordPrior=" + topicWordPrior;
			throw new IllegalArgumentException(msg);
		}
	}

	/**
	 * The <code>LatentDirichletAllocation.GibbsSample</code> class encapsulates
	 * all of the information related to a single Gibbs sample for latent
	 * Dirichlet allocation (LDA). A sample consists of the assignment of a
	 * topic identifier to each token in the corpus. Other methods in this class
	 * are derived from either the topic samples, the data being estimated, and
	 * the LDA parameters such as priors.
	 * 
	 * <p>
	 * Instances of this class are created by the sampling method in the
	 * containing class, {@link LatentDirichletAllocation}. For convenience, the
	 * sample includes all of the data used to construct the sample, as well as
	 * the hyperparameters used for sampling.
	 * 
	 * <p>
	 * As described in the class documentation for the containing class
	 * {@link LatentDirichletAllocation}, the primary content in a Gibbs sample
	 * for LDA is the assignment of a single topic to each token in the corpus.
	 * Cumulative counts for topics in documents and words in topics as well as
	 * total counts are also available; they do not entail any additional
	 * computation costs as the sampler maintains them as part of the sample.
	 * 
	 * <p>
	 * The sample also contains meta information about the state of the sampling
	 * procedure. The epoch at which the sample was produced is provided, as
	 * well as an indication of how many topic assignments changed between this
	 * sample and the previous sample (note that this is the previous sample in
	 * the chain, not necessarily the previous sample handled by the LDA
	 * handler; the handler only gets the samples separated by the specified
	 * lag.
	 * 
	 * <p>
	 * The sample may be used to generate an LDA model. The resulting model may
	 * then be used for estimation of unseen documents. Typically, models
	 * derived from several samples are used for Bayesian computations, as
	 * described in the class documentation above.
	 * 
	 * @author Bob Carpenter
	 * @version 3.3.0
	 * @since LingPipe3.3
	 */
	public static class GibbsSample {
		private final int mEpoch;
		private final short[][] mTopicSample;
		private final int[][] mDocWords;
		private final double mDocTopicPrior;
		private final double mTopicWordPrior;
		private final int[][] mDocTopicCount;
		private final int[][] mWordTopicCount;
		private final int[] mTopicCount;
		private final int mNumChangedTopics;
		private TIntHashSet querySet = null;
		private TIntHashSet backSet = null;
		private final int mNumWords;
		private final int mNumTokens;
		private double[][] mtopicWordProbs;

		GibbsSample(int epoch, short[][] topicSample, int[][] docWords,
				double docTopicPrior, double topicWordPrior,
				int[][] docTopicCount, int[][] wordTopicCount,
				int[] topicCount, int numChangedTopics, int numWords,
				int numTokens) {

			mEpoch = epoch;
			mTopicSample = topicSample;
			mDocWords = docWords;
			mDocTopicPrior = docTopicPrior;
			mTopicWordPrior = topicWordPrior;
			mDocTopicCount = docTopicCount;
			mWordTopicCount = wordTopicCount;
			mTopicCount = topicCount;
			mNumChangedTopics = numChangedTopics;
			mNumWords = numWords;
			mNumTokens = numTokens;
		}

		public GibbsSample(int epoch, short[][] currentSample,
				int[][] docWords, double docTopicPrior, double topicWordPrior,
				int[][] docTopicCount, int[][] wordTopicCount,
				int[] topicTotalCount, int numChangedTopics, int numWords,
				int numTokens, TIntHashSet querySet2, TIntHashSet backSet2) {
			this(epoch, currentSample, docWords, docTopicPrior, topicWordPrior,
					docTopicCount, wordTopicCount, topicTotalCount,
					numChangedTopics, numWords, numTokens);
			querySet = querySet2;
			backSet = backSet2;
		}

		/**
		 * Returns the epoch in which this sample was generated.
		 * 
		 * @return The epoch for this sample.
		 */
		public int epoch() {
			return mEpoch;
		}

		/**
		 * Returns the number of documents on which the sample was based.
		 * 
		 * @return The number of documents for the sample.
		 */
		public int numDocuments() {
			return mDocWords.length;
		}

		/**
		 * Returns the number of distinct words in the documents on which the
		 * sample was based.
		 * 
		 * @return The number of words underlying the model.
		 */
		public int numWords() {
			return mNumWords;
		}

		/**
		 * Returns the number of tokens in documents on which the sample was
		 * based. Each token is an instance of a particular word.
		 */
		public int numTokens() {
			return mNumTokens;
		}

		/**
		 * Returns the number of topics for this sample.
		 * 
		 * @return The number of topics for this sample.
		 */
		public int numTopics() {
			return mTopicCount.length;
		}

		/**
		 * Returns the topic identifier sampled for the specified token position
		 * in the specified document.
		 * 
		 * @param doc
		 *            Identifier for a document.
		 * @param token
		 *            Token position in the specified document.
		 * @return The topic assigned to the specified token in this sample.
		 * @throws IndexOutOfBoundsException
		 *             If the document identifier is not between 0 (inclusive)
		 *             and the number of documents (exclusive), or if the token
		 *             is not between 0 (inclusive) and the number of tokens
		 *             (exclusive) in the specified document.
		 */
		public short topicSample(int doc, int token) {
			return mTopicSample[doc][token];
		}

		/**
		 * Returns the word identifier for the specified token position in the
		 * specified document.
		 * 
		 * @param doc
		 *            Identifier for a document.
		 * @param token
		 *            Token position in the specified document.
		 * @return The word found at the specified position in the specified
		 *         document.
		 * @throws IndexOutOfBoundsException
		 *             If the document identifier is not between 0 (inclusive)
		 *             and the number of documents (exclusive), or if the token
		 *             is not between 0 (inclusive) and the number of tokens
		 *             (exclusive) in the specified document.
		 */
		public int word(int doc, int token) {
			return mDocWords[doc][token];
		}

		public int docWordCount(int doc, int id){
			int ret =0;
			for(int i=0; i < mDocWords[doc].length; i++){
				if(mDocWords[doc][i] == id){
					ret ++;
				}
			}
			return ret;
		}
		/**
		 * Returns the uniform Dirichlet concentration hyperparameter
		 * <code>&alpha;</code> for document distributions over topics from
		 * which this sample was produced.
		 * 
		 * @return The document-topic prior.
		 */
		public double documentTopicPrior() {
			return mDocTopicPrior;
		}

		/**
		 * Returns the uniform Dirichlet concentration hyperparameter
		 * <code>&beta;</code> for topic distributions over words from which
		 * this sample was produced.
		 */
		public double topicWordPrior() {
			return mTopicWordPrior;
		}

		/**
		 * Returns the number of times the specified topic was assigned to the
		 * specified document in this sample.
		 * 
		 * @param doc
		 *            Identifier for a document.
		 * @param topic
		 *            Identifier for a topic.
		 * @return The count of the topic in the document in this sample.
		 * @throws IndexOutOfBoundsException
		 *             If the document identifier is not between 0 (inclusive)
		 *             and the number of documents (exclusive) or if the topic
		 *             identifier is not between 0 (inclusive) and the number of
		 *             topics (exclusive).
		 */
		public int documentTopicCount(int doc, int topic) {
			return mDocTopicCount[doc][topic];
		}

		/**
		 * Returns the length of the specified document in tokens.
		 * 
		 * @param doc
		 *            Identifier for a document.
		 * @return The length of the specified document in tokens.
		 * @throws IndexOutOfBoundsException
		 *             If the document identifier is not between 0 (inclusive)
		 *             and the number of documents (exclusive).
		 */
		public int documentLength(int doc) {
			return mDocWords[doc].length;
		}

		/**
		 * Returns the number of times tokens for the specified word were
		 * assigned to the specified topic.
		 * 
		 * @param topic
		 *            Identifier for a topic.
		 * @param word
		 *            Identifier for a word.
		 * @return The number of tokens of the specified word assigned to the
		 *         specified topic.
		 * @throws IndexOutOfBoundsException
		 *             If the specified topic is not between 0 (inclusive) and
		 *             the number of topics (exclusive), or if the word is not
		 *             between 0 (inclusive) and the number of words
		 *             (exclusive).
		 */
		public int topicWordCount(int topic, int word) {
			return mWordTopicCount[word][topic];
		}

		/**
		 * Returns the total number of tokens assigned to the specified topic in
		 * this sample.
		 * 
		 * @param topic
		 *            Identifier for a topic.
		 * @return The total number of tokens assigned to the specified topic.
		 * @throws IllegalArgumentException
		 *             If the specified topic is not between 0 (inclusive) and
		 *             the number of topics (exclusive).
		 */
		public int topicCount(int topic) {
			return mTopicCount[topic];
		}

		/**
		 * Returns the total number of topic assignments to tokens that changed
		 * between the last sample and this one. Note that this is the last
		 * sample in the chain, not the last sample necessarily passed to a
		 * handler, because handlers may not be configured to handle every *
		 * sample.
		 * 
		 * @return The number of topics assignments that changed in this sample
		 *         relative to the previous sample.
		 */
		public int numChangedTopics() {
			return mNumChangedTopics;
		}

		/**
		 * Returns the probability estimate for the specified word in the
		 * specified topic in this sample. This value is calculated as a maximum
		 * a posteriori estimate computed as described in the class
		 * documentation for {@link LatentDirichletAllocation} using the topic
		 * assignment counts in this sample and the topic-word prior.
		 * 
		 * @param topic
		 *            Identifier for a topic.
		 * @param word
		 *            Identifier for a word.
		 * @return The probability of generating the specified word in the
		 *         specified topic.
		 * @throws IndexOutOfBoundsException
		 *             If the specified topic is not between 0 (inclusive) and
		 *             the number of topics (exclusive), or if the word is not
		 *             between 0 (inclusive) and the number of words
		 *             (exclusive).
		 */
		public double topicWordProb(int topic, int word) {
			return (topicWordCount(topic, word) + topicWordPrior())
					/ (topicCount(topic) + numWords() * topicWordPrior());
		}

		/**
		 * Returns the number of times tokens of the specified word appeared in
		 * the corpus.
		 * 
		 * @param word
		 *            Identifier of a word.
		 * @return The number of tokens of the word in the corpus.
		 * @throws IndexOutOfBoundsException
		 *             If the word identifier is not between 0 (inclusive) and
		 *             the number of words (exclusive).
		 */
		public int wordCount(int word) {
			int count = 0;
			for (int topic = 0; topic < numTopics(); ++topic)
				count += topicWordCount(topic, word);
			return count;
		}

		/**
		 * Returns the estimate of the probability of the topic being assigned
		 * to a word in the specified document given the topic * assignments in
		 * this sample. This is the maximum a posteriori estimate computed from
		 * the topic assignments * as described in the class documentation for
		 * {@link LatentDirichletAllocation} using the topic assignment counts
		 * in this sample and the document-topic prior.
		 * 
		 * @param doc
		 *            Identifier of a document.
		 * @param topic
		 *            Identifier for a topic.
		 * @return An estimate of the probabilty of the topic in the document.
		 * @throws IndexOutOfBoundsException
		 *             If the document identifier is not between 0 (inclusive)
		 *             and the number of documents (exclusive) or if the topic
		 *             identifier is not between 0 (inclusive) and the number of
		 *             topics (exclusive).
		 */
		public double documentTopicProb(int doc, int topic) {
			return (documentTopicCount(doc, topic) + documentTopicPrior())
					/ (documentLength(doc) + numTopics() * documentTopicPrior());
		}

		public double docWordProb(int doc, int wordid){
			double ret =0;
			for(int i=0; i < mTopicCount.length; i++){
				ret += mtopicWordProbs[i][wordid] * documentTopicProb(doc, i);
			}
			return ret;
		}
		
		public double docWordProb(int doc, int wordid, double[] theta){
			double ret =0;
			for(int i=0; i < mTopicCount.length; i++){
				ret += mtopicWordProbs[i][wordid] * documentTopicProb(doc, i) * theta[i];
			}
			return ret;
		}
		
		public double docWordProb(int wordid, float[][] thetas){
			double ret =0;
			int times = thetas.length;
			double weight =0;
			for(int i=0; i < times; i++){
				for(int j=0; j < mTopicCount.length; j++){
					weight += thetas[i][j] * topicWordProb(j, wordid);
				}
			}
			ret = weight/ times;
			return ret;
		}
		
		public double docWordProb(int doc, int wordid, float[] theta){
			double ret =0;
			double weight =0;
			for(int i=0; i < mTopicCount.length; i++){
				ret += mtopicWordProbs[i][wordid] * documentTopicProb(doc, i);
				weight = documentTopicProb(doc, i) * theta[i];
			}
			return ret;
		}
		
		public double docWordProb(int doc, int wordid, float[] theta, float thredhold){
			double ret =0;
			double totalweight =0;
			for(int i=0; i < mTopicCount.length; i++){
				if(theta[i] > thredhold){
					ret += mtopicWordProbs[i][wordid] * documentTopicProb(doc, i);
					totalweight += documentTopicProb(doc, i);
//					weight = documentTopicProb(doc, i) * theta[i];
				}
			}
			return ret/totalweight;
		}
		/**
		 * Returns an estimate of the log (base 2) likelihood of the corpus
		 * given the point estimates of topic and document multinomials
		 * determined from this sample.
		 * 
		 * <p>
		 * This likelihood calculation uses the methods
		 * {@link #documentTopicProb(int,int)} and
		 * {@link #topicWordProb(int,int)} for estimating likelihoods according
		 * the following formula:
		 * 
		 * <blockquote>
		 * 
		 * <pre>
		 * corpusLog2Probability()
		 * = <big><big><big>&Sigma;</big></big></big><sub><sub>doc,i</sub></sub> log<sub><sub>2</sub></sub> <big><big><big>&Sigma;</big></big></big><sub><sub>topic</sub></sub> p(topic|doc) * p(word[doc][i]|topic)
		 * </pre>
		 * 
		 * </blockquote>
		 * 
		 * <p>
		 * Note that this is <i>not</i> the complete corpus likelihood, which
		 * requires integrating over possible topic and document multinomials
		 * given the priors.
		 * 
		 * @return The log (base 2) likelihood of the training corpus * given
		 *         the document and topic multinomials determined by this
		 *         sample.
		 */
		public double corpusLog2Probability() {
			double corpusLog2Prob = 0.0;
			int numDocs = numDocuments();
			int numTopics = numTopics();
			for (int doc = 0; doc < numDocs; ++doc) {
				int docLength = documentLength(doc);
				for (int token = 0; token < docLength; ++token) {
					int word = word(doc, token);
					double wordProb = 0.0;
					for (int topic = 0; topic < numTopics; ++topic) {
						double wordTopicProbGivenDoc = topicWordProb(topic,
								word)
								* documentTopicProb(doc, topic);
						wordProb += wordTopicProbGivenDoc;
					}
					corpusLog2Prob += Math.log2(wordProb);
				}
			}
			return corpusLog2Prob;
		}

		/**
		 * Returns a latent Dirichlet allocation model corresponding to this
		 * sample. The topic-word probabilities are calculated according to
		 * {@link #topicWordProb(int,int)}, and the document-topic prior is as
		 * specified in the call to LDA that produced this sample.
		 * 
		 * @return The LDA model for this sample.
		 */
		public LatentDirichletAllocation lda() {
			int numTopics = numTopics();
			int numWords = numWords();
			double topicWordPrior = topicWordPrior();
			double[][] topicWordProbs = new double[numTopics][numWords];
			for (int topic = 0; topic < numTopics; ++topic) {
				double topicCount = topicCount(topic);
				double denominator = topicCount + numWords * topicWordPrior;
				for (int word = 0; word < numWords; ++word)
					topicWordProbs[topic][word] = (topicWordCount(topic, word) + topicWordPrior)
							/ denominator;
			}
			this.mtopicWordProbs = topicWordProbs;
			return new LatentDirichletAllocation(mDocTopicPrior, topicWordProbs);
		}
		
		public short[][] sampleTopics(int[] tokens, int numSamples, int burnin,
				int sampleLag, Random random) {

			if (burnin < 0) {
				String msg = "Burnin period must be non-negative."
						+ " Found burnin=" + burnin;
				throw new IllegalArgumentException(msg);
			}

			if (numSamples < 1) {
				String msg = "Number of samples must be at least 1."
						+ " Found numSamples=" + numSamples;
				throw new IllegalArgumentException(msg);
			}

			if (sampleLag < 1) {
				String msg = "Sample lag must be at least 1." + " Found sampleLag="
						+ sampleLag;
				throw new IllegalArgumentException(msg);
			}

			double docTopicPrior = documentTopicPrior();
			int numTokens = tokens.length;

			int numTopics = numTopics();

			int[] topicCount = new int[numTopics];

			short[][] samples = new short[numSamples][numTokens];
			int sample = 0;
			short[] currentSample = samples[0];
			for (int token = 0; token < numTokens; ++token) {
				int randomTopic = random.nextInt(numTopics);
				++topicCount[randomTopic];
				currentSample[token] = (short) randomTopic;
			}

			double[] topicDistro = new double[numTopics];

			int numEpochs = burnin + sampleLag * (numSamples - 1);
			for (int epoch = 0; epoch < numEpochs; ++epoch) {
				for (int token = 0; token < numTokens; ++token) {
					int word = tokens[token];

					int currentTopic = currentSample[token];
					--topicCount[currentTopic];
					if (topicCount[currentTopic] < 0) {
						throw new IllegalArgumentException("bomb");
					}
					for (int topic = 0; topic < numTopics; ++topic) {
						topicDistro[topic] = (topicCount[topic] + docTopicPrior)
								*  this.mtopicWordProbs[topic][word]
								+ (topic == 0 ? 0.0 : topicDistro[topic - 1]);
					}
					int sampledTopic = Statistics.sample(topicDistro, random);
					++topicCount[sampledTopic];
					currentSample[token] = (short) sampledTopic;
				}
				if ((epoch >= burnin) && (((epoch - burnin) % sampleLag) == 0)) {
					short[] pastSample = currentSample;
					++sample;
					currentSample = samples[sample];
					for (int token = 0; token < numTokens; ++token)
						currentSample[token] = pastSample[token];
				}
			}
			return samples;
		}
		
	}



}
