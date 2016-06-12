package com.barclays.classifiers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

/**
 * @author Ashwath K Alva
 * 
 */
public class Stemmer {
	
	
	public  Stemmer(){}
	
	private final String TEST_TWEETS_TXT = "25k_classified.csv";

	private final String NORMAL_EYES = ":";
	private final String WINK = ";";

	private final String NOSE_AREA = "[-\\'`\\\\]*";
	private final String HAPPY_MOUTHS = "[dD)\\]]";
	private final String SAD_MOUTHS = "[\\(\\[]";
	private final String TONGUE = "[pP]";
	private final String OTHER_MOUTHS = "[oO\\/\\\\|@]"; // remove forward slash
															// if http://'s
															// aren't cleaned

	public final Pattern HAPPY_REGEX = Pattern.compile(NORMAL_EYES + NOSE_AREA
			+ HAPPY_MOUTHS);
	public final Pattern SAD_REGEX = Pattern.compile(NORMAL_EYES + NOSE_AREA
			+ SAD_MOUTHS);
	public final Pattern WINK_REGEX = Pattern.compile(WINK + NOSE_AREA
			+ HAPPY_MOUTHS);
	public final Pattern TONGUE_REGEX = Pattern.compile(NORMAL_EYES + NOSE_AREA
			+ TONGUE);
	public final Pattern OTHER_REGEX = Pattern.compile("(" + NORMAL_EYES + "|"
			+ WINK + ")" + NOSE_AREA + OTHER_MOUTHS);

	/**
	 * @param inputTweet
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public String processTweets(String inputTweet)
			throws UnsupportedEncodingException {
		String tweet = new String(inputTweet.getBytes(StandardCharsets.UTF_8));
		tweet = tweet.replaceAll("(http|https)://[^\\s]+", "|URL|"); // replaces
																		// urls
																		// with
																		// |URL|
		tweet = tweet.replaceAll("(www|ww)[.][^\\s]+", "|URL|"); // replaces
																	// urls with
																	// |URL|
		tweet = tweet.replaceAll("[#\"=`<>~+^]", " "); // removes the hash
		tweet = tweet.replaceAll("[']", ""); // removes the hash
		tweet = tweet.replaceAll("@\\w+", "|AT_USER|"); // occurence of
														// @<anywordwithoutspace>
														// with AT_USER, ensures
														// @ symbol is the first
														// character
		tweet = tweet.replaceAll("(.)(?=\\1{2})", ""); // Removes duplicates
														// beyond 2 letters,
														// wayyyyyyy will be
														// converted to wayy
		tweet = Pattern.compile(OTHER_REGEX.pattern()).matcher(tweet)
				.replaceAll("|OTHER|");
		tweet = Pattern.compile(TONGUE_REGEX.pattern()).matcher(tweet)
				.replaceAll("|TONGUE|");
		tweet = Pattern.compile(WINK_REGEX.pattern()).matcher(tweet)
				.replaceAll("|WINK|");
		tweet = Pattern.compile(SAD_REGEX.pattern()).matcher(tweet)
				.replaceAll("|SAD|");
		tweet = Pattern.compile(HAPPY_REGEX.pattern()).matcher(tweet)
				.replaceAll("|SMILEYFACE|");
		tweet = tweet.replaceAll("\\p{P}", " "); // remove all punctuations
		tweet = tweet.replaceAll("[\\s]+", " ").trim(); // [ \t\n\r\f\v],
														// replaces with ' '
		return tweet.toLowerCase();
	}

	/**
	 * @param args
	 * @throws UnsupportedEncodingException
	 */
	public void main(String[] args) throws Exception {
		// String[] originalArr =
		// {"remove http:///www.google.com","remove hashtags #user123",
		// "remove @gtgdhf123 user mentions","check for smileys :)","check for sad face :(","check for normal face :|",
		// "check for tongue face :p","wayyyy tooooo tough"};
		List<String> lines = IOUtils.readLines(Thread.currentThread()
				.getContextClassLoader().getResourceAsStream(TEST_TWEETS_TXT));
		lines.remove(0);
		FileOutputStream fos = new FileOutputStream(new File("out_stemmer.txt"));
		for (String line : lines) {
			int index = line.indexOf(",");
			int lastIndex = line.lastIndexOf(",");
			String tweet = line.substring(index + 1, lastIndex);
			// System.out.println(processTweets(tweet));
			IOUtils.write(processTweets(tweet) + "\n", fos);
		}

	}

}