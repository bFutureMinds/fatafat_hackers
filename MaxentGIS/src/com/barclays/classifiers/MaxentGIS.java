package com.barclays.classifiers;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishMinimalStemFilter;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.util.Version;

import opennlp.maxent.GIS;
import opennlp.maxent.GISModel;
import opennlp.maxent.io.GISModelWriter;
import opennlp.maxent.io.SuffixSensitiveGISModelReader;
import opennlp.maxent.io.SuffixSensitiveGISModelWriter;
import opennlp.model.EventStream;
import opennlp.model.FileEventStream;

//TEST AND TRAIN INPUTS IN STANDARDIZED "TWEET SENTIMENT" FORMAT. MODIFY SEPARATOR AND SUBSTRING INDEX IF USING DIFFERENT FORMAT.

public class MaxentGIS {
	final static Logger logger = Logger.getLogger(MaxentGIS.class);
	private static final String INPUT_TWEETS_REMOVED_STOPWORDS_TXT = "tweets_stemmed_filtered_stopwords.txt";
	private static String INPUT_TWEETS_ORIG_TXT = "D:\\Hackathon\\workspace\\MaxentGIS\\conf\\moviereview_train.tsv";
	private static final boolean USE_SMOOTHING = true;
	private static final boolean VERBOSE = true;
	private String CONF_ENGLISH_CUSTOM_STOPWORDS_TXT = null;
	final String TAB_SEPARATOR = "\t";
	String TEST_TWEETS_TXT = null;
	String MOVIE_REVIEW_DATA = null;
	String EMOTICON_DATA = null;
	static Object model;
	Properties prop = new Properties();

	public MaxentGIS() {
		MOVIE_REVIEW_DATA = "/moviereview_train.tsv";
		EMOTICON_DATA = "/emoticon.tsv";
		CONF_ENGLISH_CUSTOM_STOPWORDS_TXT = "/english_stopwords.txt";
	}

	// Specifies the minimum number of times a feature must be seen and is used
	// to designate a method of reducing the size of n-gram language models.
	// Removal of n-grams that occur infrequently in the training data.
	private static final int CUTOFF_FREQ = 2;

	// refers to the general notion of iterative algorithms, where one sets out
	// to solve a problem by successively producing (hopefully increasingly more
	// accurate) approximations of some "ideal" solution. Generally speaking,
	// the more iterations, the more accurate ("better") the result will be, but
	// of course the more computational steps have to be carried out.
	private static final int TRAINING_ITERATIONS = 1000;

	public static void main(String[] args) throws IOException,
			URISyntaxException {
		MaxentGIS classifier = new MaxentGIS();		
		//classifier.readModelAndTrain(false,null);
		//classifier.serialize(model);
		Path path = Paths.get("D:\\Hackathon\\workspace\\MaxentGIS\\GISmaxEnt_MovieReview"); byte[] data = Files.readAllBytes(path);
		Double sentimentScore = classifier.getTweetSentimentScore("Our little baby boy has arrived!!", classifier.deserialize(data));
		System.out.println("Sentiment : "+ sentimentScore);
}

	public Double getTweetSentimentScore(String tweetString, Object model)
			throws IOException {
		// readModelAndTrain(readExistingModel, MAXENTROPY_SERIALIZED_MODEL);
		setModel(model);
		Double sentiment = predict(removeStopWords(tweetString));
		logger.info("sentiment : " + sentiment);
		return sentiment;
	}

	/**
	 * Create the filtered base data for the model to train on
	 * "Sentiment"[SPACE]"Tweet"
	 * 
	 * @throws IOException
	 */
	private File createModelInput(String fileName) throws IOException {
		LineIterator it = null;
		File newFile = new File(INPUT_TWEETS_REMOVED_STOPWORDS_TXT);
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(newFile);

			it = FileUtils.lineIterator(new File(fileName), "UTF-8");
			while (it.hasNext()) {
				String line = it.nextLine();
				String tweet = removeStopWords(line.split(TAB_SEPARATOR)[1]);
				// System.out.println(tweet);
				if (tweet == null || "".equals(tweet)) {
					continue;
				}
				IOUtils.write(
						line.split(TAB_SEPARATOR)[2] + " " + tweet + "\n", fos);
				System.out.println(tweet);
			}
		} finally {
			if (it != null) {
				it.close();
			}
			if (fos != null) {
				fos.close();
			}
		}
		return newFile;
	}

	/**
	 * Train and create the model on the stop-word filtered data
	 * 
	 * @throws IOException
	 */
	private void readModelAndTrain(boolean readExistingModel,
			byte[] trainingDataFile) {
		InputStream dataIn = null;
		try {
			// System.out.println(INPUT_TWEETS_ORIG_TXT);
			EventStream sampleStream = new FileEventStream(
					createModelInput(INPUT_TWEETS_ORIG_TXT));
			System.out.println("HERE");
			setModel(GIS.trainModel(sampleStream, TRAINING_ITERATIONS,
					CUTOFF_FREQ, USE_SMOOTHING, VERBOSE));
			serialize(model);
		} catch (EOFException e) {
			System.out.println("EOF");
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		finally {
			if (dataIn != null) {
				try {
					dataIn.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}

	/**
	 * Evaluate the tweet against the trained model and output the sentiment
	 * 
	 * @param tweet
	 *            Incoming tweet
	 * @throws IOException
	 */
	private double predict(String tweet) {
		GISModel myCategorizer = (GISModel) model;
		String[] tweetTokens = tweet.split("\\s");
		double[] outcomes = myCategorizer.eval(tweetTokens);
		try {
			return Double.parseDouble(myCategorizer.getBestOutcome(outcomes));
		} catch (NumberFormatException e) {
			return 0.5;
		}

	}

	/**
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	void serialize(Object model) throws IOException {
		File f = new File("D:\\Hackathon\\workspace\\MaxentGIS\\GISmaxEnt_MovieReview");
		// new DoccatModel(model).serialize(file);
		GISModelWriter writer = new SuffixSensitiveGISModelWriter(
				(GISModel) model, f);

		writer.persist();
	}

	/**
	 * @param fileName
	 * @return
	 * @throws IOException
	 */
	public GISModel deserialize(byte[] fileName) throws IOException {
		InputStream in = new ByteArrayInputStream(fileName);
		File fileStream = stream2file(in);

		SuffixSensitiveGISModelReader reader = new SuffixSensitiveGISModelReader(
				fileStream);
		return (GISModel) reader.getModel();
	}

	// method to convert stream to file
	private static File stream2file(InputStream in) throws IOException {
		String PREFIX = "stream2file";
		String SUFFIX = ".tmp";
		final File tempFile = File.createTempFile(PREFIX, SUFFIX);
		tempFile.deleteOnExit();
		try (FileOutputStream out = new FileOutputStream(tempFile)) {
			IOUtils.copy(in, out);
		}
		return tempFile;
	}

	/**
	 * Remove stop words from the tweet
	 * 
	 * @param line
	 *            - Incoming tweet
	 * @return - Tweet minus the stop words
	 * @throws IOException
	 */
	private String removeStopWords(String line) throws IOException {
		line = getStemming(line);
		TokenStream tokenStream = createTokenStream(new StringReader(line));
		StringBuilder sb = new StringBuilder();
		try {
			CharTermAttribute token = tokenStream
					.getAttribute(CharTermAttribute.class);
			tokenStream.reset();
			while (tokenStream.incrementToken()) {
				if (sb.length() > 0) {
					sb.append(" ");
				}
				sb.append(token.toString());
			}
		} finally {
			tokenStream.close();
		}
		return sb.toString();
	}

	/**
	 * @param line
	 * @return
	 * @throws IOException
	 */
	private String getStemming(String line) throws IOException {
		return line;
	}

	/**
	 * Create a chained filter stream to filter the base data
	 * 
	 * @param reader
	 *            - Handle to base data file reader
	 * @return Handle to filtered base data file reader
	 * @throws IOException
	 *             if there is an exception reading the data
	 */
	private TokenStream createTokenStream(Reader reader) throws IOException {
		TokenStream result = new LowerCaseFilter(Version.LUCENE_48,
				new StandardFilter(Version.LUCENE_48, new StandardTokenizer(
						Version.LUCENE_48, reader)));
		result = new StopFilter(Version.LUCENE_48, result,
				readCustomStopWords());
		return new EnglishMinimalStemFilter(new EnglishPossessiveFilter(
				Version.LUCENE_48, result));
	}

	/**
	 * Read the stop word configuration file into a @CharArraySet and return the
	 * same
	 * 
	 * @return Set of stop words
	 * @throws IOException
	 *             if there was an error reading the stop-word configuration
	 *             file
	 */
	private CharArraySet readCustomStopWords() throws IOException {
		CharArraySet engStopWords = new CharArraySet(Version.LUCENE_48,
				StandardAnalyzer.STOP_WORDS_SET.size(), Boolean.TRUE);
		InputStream englishFileStream = getClass().getResourceAsStream(
				CONF_ENGLISH_CUSTOM_STOPWORDS_TXT);
		// List<String> lines = IOUtils.readLines(new FileInputStream(new File(
		// CONF_ENGLISH_CUSTOM_STOPWORDS_TXT)));
		List<String> lines = IOUtils.readLines(englishFileStream);
		for (String line : lines) {
			// System.out.println("Line is"+line);
			engStopWords.add(line);
		}
		return engStopWords;
	}	

	private void setModel(Object model) {
		this.model = model;
	}
}
