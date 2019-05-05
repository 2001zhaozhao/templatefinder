package database_templatefinder.templatefinder;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.collections4.trie.PatriciaTrie;

import database_templatefinder.templatefinder.output.OutputWeight;
import database_templatefinder.templatefinder.types.*;
import database_templatefinder.templatefinder.types.TemplateString.VarData;

public class TFIDFEngine {
	
	// !!!!!!!!!!! Weight-getters
	
	/**
	 * Determines the word weights between strings of the template.
	 * <p>
	 * Uses two dictionaries to find "meaningless" and "specific/technical" words which impacts the weights of words in the results
	 * @param template
	 * @return
	 */
	public static HashMap<String, Double> getTemplateWeights(TemplateFormat template) {
		HashMap<String, Double> out = new HashMap<>();
		
		// Find all words in the template and accumulate weight
		
		LinkedList<Entry<Item, Double>> queue = new LinkedList<>();
		queue.add(new SimpleEntry<Item, Double>(template.baseProgression, 1d));
		while(!queue.isEmpty()) {
			Entry<Item, Double> entry = queue.poll();
			Item i = entry.getKey();
			double weight = entry.getValue();
			if(i instanceof ItemText) {
				// Search words in text
				String[] split = ((ItemText) i).getText().replace('-', ' ').split(" ");
				int size = split.length;
				for(String str : split) {
					if(str.isEmpty()) size --;
				}
				// Remove punctuation
				for(String str : split) {
					if(str.isEmpty()) continue;
					StringBuilder wordBuilder = new StringBuilder();
					for(int j = 0; j < str.length(); j++) {
						char c = str.charAt(j);
						if((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
							wordBuilder.append(c);
						}
					}
					
					String word = wordBuilder.toString();
					if(word.isEmpty()) continue;
					// Generate final weight
					double finalWeight;
					if(!App.commonWordsSet.contains(word)) { // Technical words get more weight even if appearance is rare
						finalWeight = Math.sqrt(weight);
					}
					else {
						finalWeight = weight;
					}
					if(App.veryCommonWordsSet.contains(word)) { // Very common words weight multiplied by x0.25
						finalWeight *= 0.25;
					}
					finalWeight /= size + 1.0;
					
					// Add any current weight so the final result is additive
					if(out.containsKey(word)) finalWeight += out.get(word);
					
					// Add finished word to the word list
					out.put(word, finalWeight);
				}
			}
			// For other types of items just add their children to queue so we reach all the string items in the end
			else if(i instanceof ItemVariable){
				// ItemVariable degrades weight
				Collection<Item> itemList = ((ItemVariable) i).getContent();
				for(Item newItem : itemList) {
					queue.add(new SimpleEntry<Item, Double>(newItem, weight / itemList.size()));
				}
			}
			else if(i instanceof ItemProgression){
				// ItemProgression doesn't
				Collection<Item> itemList = ((ItemProgression) i).getContent();
				for(Item newItem : itemList) {
					queue.add(new SimpleEntry<Item, Double>(newItem, weight));
				}
			}
		}
		
		return out;
	}
	
	/**
	 * Uses "idf" to modify all template weights in these words based on how many templates they appear in
	 * @param wordWeights
	 * @param allTemplates
	 */
	public static void tfidfTemplateWeights(HashMap<String, Double> wordWeights, Collection<TemplateFormat> allTemplates) {
		for(Entry<String, Double> entry : wordWeights.entrySet()) {
			int appearances = 0;
			// Try to find this word in all templates
			for(TemplateFormat template : allTemplates) {
				if(searchWordInTemplate(template, entry.getKey())) appearances ++;
			}
			
			// Modify weights to respect tfidf
			// Formula from here http://ethen8181.github.io/machine-learning/clustering_old/tf_idf/tf_idf.html
			entry.setValue(entry.getValue() * Math.log(allTemplates.size() * 1.0 / (1 + appearances)));
		}
	}
	
	/**
	 * Get the weight of a word in the string provided.
	 * @param str
	 * @return
	 */
	public static HashMap<String, Double> getStringWeights(TemplateString templateStr) {
		HashMap<String, Double> out = new HashMap<>();
		
		String[] split = templateStr.string.replace('-', ' ').split(" ");
		int size = split.length;
		for(String str : split) {
			if(str.isEmpty()) size --;
		}
		// Remove punctuation
		for(String str : split) {
			if(str.isEmpty()) continue;
			StringBuilder wordBuilder = new StringBuilder();
			for(int j = 0; j < str.length(); j++) {
				char c = str.charAt(j);
				if((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
					wordBuilder.append(c);
				}
			}
			
			String word = wordBuilder.toString().toLowerCase();
			if(word.isEmpty()) continue;
			// Generate final weight
			double finalWeight = 1;
			if(!App.commonWordsSet.contains(word)) { // Technical words get more weight even if appearance is rare
				finalWeight = Math.sqrt(finalWeight);
			}
			if(App.veryCommonWordsSet.contains(word)) { // Very common words weight multiplied by x0.25
				finalWeight *= 0.25;
			}
			finalWeight /= size + 1.0;
			
			// Add previous weights
			if(out.containsKey(word)) finalWeight += out.get(word);
			
			// Add finished word to the word list
			out.put(word, finalWeight);
		}
		
		return out;
	}
	
	/**
	 * Transposes a weight map so it becomes word-first
	 * @param stringMap
	 * @return
	 */
	public static HashMap<String, HashMap<TemplateString, Double>> createStringWeightMapByWord(HashMap<TemplateString, HashMap<String, Double>> stringMap) {
		HashMap<String, HashMap<TemplateString, Double>> out = new HashMap<>();
		
		for(Entry<TemplateString, HashMap<String, Double>> entry : stringMap.entrySet()) {
			TemplateString template = entry.getKey();
			HashMap<String, Double> wordMap = entry.getValue();
			for(Entry<String, Double> wordEntry : wordMap.entrySet()) {
				String word = wordEntry.getKey();
				Double weight = wordEntry.getValue();
				// If doesn't already have word, put in an empty map
				if(!out.containsKey(word)) out.put(word, new HashMap<TemplateString, Double>());
				out.get(word).put(template, weight);
			}
		}
		
		return out;
	}
	
	public static final double STRING_WEIGHT_WITHIN_TEMPLATE_MULTIPLIER = 10;
	public static final double STRING_WEIGHT_ACROSS_TEMPLATES_MULTIPLIER = 10;
	
	/**
	 * Use "idf" for each word to modify all word weights based on how many strings in the list the word appears in
	 * @param stringWeights
	 * @param allTemplates
	 */
	public static void tfidfStringWeightsWithinTemplate(HashMap<String, HashMap<TemplateString, Double>> stringWeights, Collection<TemplateString> otherStrings) {
		int count = otherStrings.size();
		for(Entry<String, HashMap<TemplateString, Double>> entry : stringWeights.entrySet()) {
			String word = entry.getKey();
			HashMap<TemplateString, Double> templateWeightsMap = entry.getValue();
			
			// Loop through the list of other strings to find how many it appears in
			int appearances = 0;
			for(TemplateString tempStr : otherStrings) {
				if(searchWordInTemplateString(tempStr, word)) appearances ++;
			}
			
			// Divide the weights
			double idf = Math.log(count * 1.0 / (1 + appearances));
			
			for(Entry<TemplateString, Double> tEntry : templateWeightsMap.entrySet()) {
				tEntry.setValue(tEntry.getValue() * idf * STRING_WEIGHT_WITHIN_TEMPLATE_MULTIPLIER);
			}
		}
	}
	
	/**
	 * Use "idf" for each word to modify all word weights based on how many templates they appear in
	 * @param stringWeights
	 * @param allTemplates
	 */
	public static void tfidfStringWeightsInAllTemplates(HashMap<String, HashMap<TemplateString, Double>> stringWeights, Collection<TemplateFormat> allTemplates) {
		int count = allTemplates.size();
		for(Entry<String, HashMap<TemplateString, Double>> entry : stringWeights.entrySet()) {
			String word = entry.getKey();
			HashMap<TemplateString, Double> templateWeightsMap = entry.getValue();
			
			// Loop through the list of other strings to find how many it appears in
			int appearances = 0;
			for(TemplateFormat template : allTemplates) {
				if(searchWordInTemplate(template, word)) appearances ++;
			}
			
			// Divide the weights
			double idf = Math.log(count * 1.0 / (1 + appearances));

			//double divisor = ((appearances * 1.0 + 1) / count);
			for(Entry<TemplateString, Double> tEntry : templateWeightsMap.entrySet()) {
				tEntry.setValue(tEntry.getValue() * idf * STRING_WEIGHT_ACROSS_TEMPLATES_MULTIPLIER);
			}
		}
	}
	
	// !!!!!!!!!!!!!!! Template initialization
	
	/**
	 * Expand a template into all of its possible strings. The output HashMap will have the String for the base format string as key,
	 * and a HashMap of format to all strings as value.
	 * @param template
	 * @param baseFormat - The format to use as "base" - usually the English format. All other formats will be "flattened" to a 1:1
	 * correspondence to the base format. This might involve linking the same string to multiple base format strings, or ignoring
	 * some strings that are more specific than the base format string in terms of variables.
	 * @return
	 */
	public static HashMap<String, HashMap<String, TemplateString>> expandTemplate(Template template, String baseFormat) {
		// This map is a different format than the output map despite same dimensions - the key is the template format
		HashMap<String, ArrayList<TemplateString>> rawExpandedData = new HashMap<>();
		for(TemplateFormat format : template.values()) {
			rawExpandedData.put(format.formatName, format.baseProgression.getPossibilities());
		}
		
		// Find matching formats
		ArrayList<TemplateString> baseStrings = rawExpandedData.get(baseFormat);
		if(baseStrings == null) {
			// If base format not found, return an empty result
			return new HashMap<String, HashMap<String, TemplateString>>();
		}
		
		HashMap<String, HashMap<String, TemplateString>> result = new HashMap<>();
		for(TemplateString baseString : baseStrings) {
			// Initialize array 
			HashMap<String, TemplateString> stringList = new HashMap<>();
			stringList.put(baseFormat, baseString);
			
			// Check other formats to find the possibility with the most variable matches
			outer:
			for(Entry<String, ArrayList<TemplateString>> entry : rawExpandedData.entrySet()) {
				if(entry.getKey().equals(baseFormat)) continue; // Skip the base format
				
				// Find the string with the most matches to the base string 
				int maxMatches = -1;
				TemplateString currentMatch = null;
				for(TemplateString otherString : entry.getValue()) {
					int matches = 0;
					for(VarData var : otherString.variables.values()) {
						VarData baseVar = baseString.variables.get(var.id);
						if(baseVar == null) continue; // If the variable simply doesn't exist in the base string do nothing
						if(baseVar.choice != var.choice) {
							continue outer; // Variable is mismatched so skip
						}
						else {
							matches++;
						}
					}
					if(matches > maxMatches) { // Check for most matches
						currentMatch = otherString;
						maxMatches = matches;
					}
				}
				
				// Add any found string (there should be one) to the list
				if(currentMatch != null) {
					stringList.put(entry.getKey(), currentMatch);
				}
			}
			
			result.put(baseString.string, stringList);
		}
		
		return result;
	}
	
	// !!!!!!!!!!!!! Computation (all methods will require the App class to be initialized by calling main function
	
	public static HashMap<Template, Double> getTemplateWeight(String input) {
		HashMap<Template, Double> templateWeight = new HashMap<Template, Double>();
		// Search for the sum of tfidf in each template - time complexity = nk(log(nk))
		ArrayList<String> words = TFIDFEngine.getWordsInString(input);
		for(String word : words) {
			HashMap<String, Double> wordsToConsider = lenientWordSimilarityMap(word, App.templateWordWeights);
			for(Entry<String, Double> wordEntry : wordsToConsider.entrySet()) {
				double weightScale = wordEntry.getValue();

				HashMap<String, Double> wordWeightMap = App.templateWordWeights.get(wordEntry.getKey());
				if(wordWeightMap == null) continue;
				for(Template template : App.templates) {
					// Accumulate weight based on how weighted this word is in the template
					Double weight = wordWeightMap.get(template.getName());
					if(weight != null) {
						if(!templateWeight.containsKey(template))
							templateWeight.put(template, 0d);
						double current = templateWeight.get(template);
						
						weight *= weightScale;
						templateWeight.put(template, (current+weight) * (current+weight) / (Math.sqrt((current*current) + (weight*weight)) + Double.MIN_NORMAL));
					}
				}
			}
		}

		return templateWeight;
	}
	
	public static HashMap<TemplateString, Double> getStringWeight(String input, PatriciaTrie<HashMap<TemplateString, Double>> weightTree) {
		HashMap<TemplateString, Double> out = new HashMap<>();

		ArrayList<String> words = TFIDFEngine.getWordsInString(input);
		for(String word : words) {
			HashMap<String, Double> wordsToConsider = lenientWordSimilarityMap(word, weightTree);
			for(Entry<String, Double> wordEntry : wordsToConsider.entrySet()) {
				double weightScale = wordEntry.getValue();
				
				HashMap<TemplateString, Double> weightMap = weightTree.get(wordEntry.getKey());
				for(HashMap<String, TemplateString> templateStringMap : App.templatesExpandedConcat.values()) {
					// Accumulate Weight
					TemplateString tStr = templateStringMap.get(App.BASE_FORMAT);
					
					Double weight = null;
					if(weightMap != null) weight = weightMap.get(tStr);
					if(weight == null) weight = 0d;
					
					if(!out.containsKey(tStr))
						out.put(tStr, 0d);
					double current = out.get(tStr);
					out.put(tStr, current + weight * weightScale);
				}
			}
			
		}
		
		return out;
	}
	
	public static <T extends Object> HashMap<String, Double> lenientWordSimilarityMap(String word, PatriciaTrie<T> candidates) {
		HashMap<String, Double> out = new HashMap<>();
		
		// Assume prefix match (find suffix matches)
		{
			Set<String> possibleCandidates = candidates.prefixMap(word.substring(0,1)).keySet();
			for(String candidate : possibleCandidates) {
				if(candidate.equals(word)) continue;
				// Check prefix matching length
				int prefixMatchingLength = 1;
				for(int i = 1; i < word.length() && i < candidate.length(); i++) {
					if(word.charAt(i) == candidate.charAt(i)) {
						prefixMatchingLength ++;
					}
					else {
						break;
					}
				}
				// Check suffix matching length, taking in account of the prefix match to not match the same characters again
				int suffixMatchingLength = 0;
				for(int i = 0; i < word.length() - prefixMatchingLength && i < candidate.length() - prefixMatchingLength; i++) {
					if(word.charAt(word.length() - 1 - i) == candidate.charAt(candidate.length() - 1 - i)) {
						suffixMatchingLength ++;
					}
					else {
						break;
					}
				}
				int maxLength = Math.max(word.length(), candidate.length());
				int nonMatchingLength = maxLength - prefixMatchingLength;
				
				// Reverse (suffix only)
				String wordR = new StringBuilder(word).reverse().toString();
				String candidateR = new StringBuilder(candidate).reverse().toString();
				
				// The "normal" match level would be quartic of nonmatching length (excl. any matches in prefix & suffix) to word length
				// so if 1/3 the letters don't match the final match level is (2/3)^4 = 16/81
				double bestMatch = Math.pow(1 - ((double) nonMatchingLength - suffixMatchingLength) / maxLength, 4);
				
				// Try to get the best match by removal
				
				int lengthDiff = word.length() - candidate.length();
				if(lengthDiff > 0) {
					// Try removing from word since word is longer
					// Find longest defined suffix that can be removed (longest is almost always the best)
					double maxWeight = 0; // Not actually "max weight" since we're finding longest
					String maxSuffix = null;
					for(int i = 1; i <= lengthDiff; i++) {
						Double weight = App.suffixRemovalWeights.get(wordR.substring(0, i));
						if(weight != null) {
							maxSuffix = wordR.substring(0, i);
							maxWeight = weight;
						}
					}
					
					if(maxSuffix != null) {
						double match = (maxWeight * 0.9 + 0.1)
								* Math.pow(1 - ((double) nonMatchingLength - maxSuffix.length()) / (maxLength - maxSuffix.length()), 4);
						bestMatch = Math.max(bestMatch, match);
					}
				}
				else if(lengthDiff < 0) {
					// Try removing from candidate
					// Find longest defined suffix that can be removed (longest is almost always the best)
					double maxWeight = 0; // Not actually "max weight" since we're finding longest
					String maxSuffix = null;
					for(int i = 1; i <= -lengthDiff; i++) {
						Double weight = App.suffixRemovalWeights.get(candidateR.substring(0, i));
						if(weight != null) {
							maxSuffix = candidateR.substring(0, i);
							maxWeight = weight;
						}
					}
					
					if(maxSuffix != null) {
						double match = (maxWeight * 0.9 + 0.1)
								* Math.pow(1 - ((double) nonMatchingLength - maxSuffix.length()) / (maxLength - maxSuffix.length()), 4);
						bestMatch = Math.max(bestMatch, match);
					}
				}
				
				// Try to get the best match by substitution
				Map<String, HashMap<String, Double>> validSuffixSubs = App.suffixSubstitutionWeights.prefixMap(wordR.substring(0, 1));
				for(Entry<String, HashMap<String, Double>> entry : validSuffixSubs.entrySet()) {
					String fix = entry.getKey();
					if(wordR.startsWith(fix)) {
						for(Entry<String, Double> subEntry : entry.getValue().entrySet()) {
							String cFix = subEntry.getKey();
							if(candidateR.startsWith(cFix)) {
								// We found a match, see if it's the best match
								int newMaxLength = Math.max(word.length() - fix.length(), candidate.length() - cFix.length());
								int newNonMatchingLength = newMaxLength - prefixMatchingLength;
								
								double match = (subEntry.getValue() * 0.9 + 0.1)
										* Math.pow(1 - ((double) newNonMatchingLength) / newMaxLength, 4);
								bestMatch = Math.max(bestMatch, match);
							}
						}
					}
				}
				
				if(bestMatch > 1) {
					System.out.println("Possible error! Match between word '" + word + "' and candidate '" + candidate + "' was greater than 1: " + bestMatch);
					bestMatch = 1;
				}
				double bestMatchFix = (bestMatch - 0.1) / 0.9; // Scale the match so we never consider <10% matching words (for performance)
				if(bestMatchFix > 0) {
					double prevOutData = out.containsKey(candidate) ? out.get(candidate) : 0;
					if(bestMatchFix > prevOutData) out.put(candidate, bestMatchFix);
				}
			}
		}
		
		// Assume suffix match (find prefix matches)
		{
			for(Entry<String, String> candidateEntry : App.allWordsInReverse.prefixMap(word.substring(word.length() - 1)).entrySet()) {
				String candidate = candidateEntry.getValue(); // The forward-spelled word is the value of the reverse map, for convenience
				if(candidate.equals(word)) continue;
				// Check prefix matching length
				int prefixMatchingLength = 0;
				for(int i = 0; i < word.length() && i < candidate.length(); i++) {
					if(word.charAt(i) == candidate.charAt(i)) {
						prefixMatchingLength ++;
					}
					else {
						break;
					}
				}
				// Check suffix matching length, taking in account of the prefix match to not match the same characters again
				int suffixMatchingLength = 1;
				for(int i = 1; i < word.length() - prefixMatchingLength && i < candidate.length() - prefixMatchingLength; i++) {
					if(word.charAt(word.length() - 1 - i) == candidate.charAt(candidate.length() - 1 - i)) {
						suffixMatchingLength ++;
					}
					else {
						break;
					}
				}
				int maxLength = Math.max(word.length(), candidate.length());
				int nonMatchingLength = maxLength - suffixMatchingLength;
				
				// The "normal" match level would be quartic of nonmatching length (excl. any matches in prefix & suffix) to word length
				// so if 1/3 the letters don't match the final match level is (2/3)^4 = 16/81
				double bestMatch = Math.pow(1 - ((double) nonMatchingLength - prefixMatchingLength) / maxLength, 4);
				
				// Try to get the best match by removal
				
				int lengthDiff = word.length() - candidate.length();
				if(lengthDiff > 0) {
					// Try removing from word since word is longer
					// Find longest defined prefix that can be removed (longest is almost always the best)
					double maxWeight = 0; // Not actually "max weight" since we're finding longest
					String maxPrefix = null;
					for(int i = 1; i <= lengthDiff; i++) {
						Double weight = App.prefixRemovalWeights.get(word.substring(0, i));
						if(weight != null) {
							maxPrefix = word.substring(0, i);
							maxWeight = weight;
						}
					}
					
					if(maxPrefix != null) {
						double match = (maxWeight * 0.9 + 0.1)
								* Math.pow(1 - ((double) nonMatchingLength - maxPrefix.length()) / (maxLength - maxPrefix.length()), 4);
						bestMatch = Math.max(bestMatch, match);
					}
				}
				else if(lengthDiff < 0) {
					// Try removing from candidate
					// Find longest defined prefix that can be removed (longest is almost always the best)
					double maxWeight = 0; // Not actually "max weight" since we're finding longest
					String maxPrefix = null;
					for(int i = 1; i <= -lengthDiff; i++) {
						Double weight = App.prefixRemovalWeights.get(candidate.substring(0, i));
						if(weight != null) {
							maxPrefix = candidate.substring(0, i);
							maxWeight = weight;
						}
					}
					
					if(maxPrefix != null) {
						double match = (maxWeight * 0.9 + 0.1)
								* Math.pow(1 - ((double) nonMatchingLength - maxPrefix.length()) / (maxLength - maxPrefix.length()), 4);
						bestMatch = Math.max(bestMatch, match);
					}
				}
				
				// Try to get the best match by substitution
				Map<String, HashMap<String, Double>> validSuffixSubs = App.prefixSubstitutionWeights.prefixMap(word.substring(0, 1));
				for(Entry<String, HashMap<String, Double>> entry : validSuffixSubs.entrySet()) {
					String fix = entry.getKey();
					if(word.startsWith(fix)) {
						for(Entry<String, Double> subEntry : entry.getValue().entrySet()) {
							String cFix = subEntry.getKey();
							if(candidate.startsWith(cFix)) {
								// We found a match, see if it's the best match
								int newMaxLength = Math.max(word.length() - fix.length(), candidate.length() - cFix.length());
								int newNonMatchingLength = newMaxLength - suffixMatchingLength;
								
								double match = (subEntry.getValue() * 0.9 + 0.1)
										* Math.pow(1 - ((double) newNonMatchingLength) / newMaxLength, 4);
								bestMatch = Math.max(bestMatch, match);
							}
						}
					}
				}
				
				if(bestMatch > 1) {
					System.out.println("Possible error! Match between word '" + word + "' and candidate '" + candidate + "' was greater than 1: " + bestMatch);
					bestMatch = 1;
				}
				double bestMatchFix = (bestMatch - 0.1) / 0.9; // Scale the match so we never consider <10% matching words (for performance)
				if(bestMatchFix > 0) {
					double prevOutData = out.containsKey(candidate) ? out.get(candidate) : 0;
					if(bestMatchFix > prevOutData) out.put(candidate, bestMatchFix);
				}
			}
		}
		// Include the word itself if in candidates, and return
		if(candidates.containsKey(word)) out.put(word, 1d);
		return out;
	}
	
	public static HashMap<TemplateString, OutputWeight> getFinalWeight(String input) {
		HashMap<Template, Double> templateWeight = getTemplateWeight(input);
		HashMap<TemplateString, Double> stringWeightInTemplate = getStringWeight(input, App.stringWeightsInTemplate);
		HashMap<TemplateString, Double> stringWeightAcrossTemplates = getStringWeight(input, App.stringWeightsAcrossTemplates);
		
		// Find the template with the most templateWeight & simultaneously find length-squared of vector
		
		//String maxTemplate = null;
		double max = -1;
		
		double lengthSquaredOfTemplateWeights = 0;
		for(Entry<Template, Double> entry : templateWeight.entrySet()) {
			if(entry.getValue() > max) {
				//maxTemplate = entry.getKey().getName();
				max = entry.getValue();
			}
			lengthSquaredOfTemplateWeights += entry.getValue() * entry.getValue();
		}
		
		// The greatest weight / length of the weight vector is the weighting for the in-template values
		// This is because if the winning template is winning by a lot, we want to compare with other strings in the same template
		// since we are fairly sure which template the string is from - and vice versa.
		double inTemplateWeight = max / Math.sqrt(lengthSquaredOfTemplateWeights + Double.MIN_NORMAL);
		
		// Square inTemplateWeight portion so competition between a small number of templates more effectively drives the weighting to
		// consider the weight across templates. In the event of perfect competition between 2 templates the portion will now be
		// 1/2 instead of 1/sqrt(2)
		inTemplateWeight *= inTemplateWeight;
		
		HashMap<TemplateString, OutputWeight> out = new HashMap<>();
		
		for(TemplateString tStr : stringWeightInTemplate.keySet()) {
			double templateWeightComponent = templateWeight.containsKey(tStr.tf.template) ? templateWeight.get(tStr.tf.template) : 0;
			OutputWeight weight = new OutputWeight(stringWeightInTemplate.get(tStr), stringWeightAcrossTemplates.get(tStr), templateWeightComponent, inTemplateWeight);
			
			out.put(tStr, weight);
		}
		
		return out;
	}
	
	// !!!!!!!!!!!!! Helper functions below
	
	/**
	 * Get a list of the words in a string
	 * @param s
	 * @return
	 */
	public static ArrayList<String> getWordsInString(String s) {
		// Search words in text
		String[] split = s.replace('-', ' ').split(" ");
		ArrayList<String> words = new ArrayList<String>(split.length);
		// Remove punctuation
		for(String str : split) {
			if(str.isEmpty()) continue;
			StringBuilder wordBuilder = new StringBuilder();
			for(int j = 0; j < str.length(); j++) {
				char c = str.charAt(j);
				if(c >= 'A' && c <= 'Z') c = (char) (c + 'a' - 'A');
				if((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
					wordBuilder.append(c);
				}
			}
			words.add(wordBuilder.toString());
		}
		return words;
	}
	
	/**
	 * Search for whether a word exists in a template
	 * @param template
	 * @param word
	 * @return
	 */
	public static boolean searchWordInTemplate(TemplateFormat template, String word) {
		LinkedList<Item> queue = new LinkedList<>();
		queue.add(template.baseProgression);
		while(!queue.isEmpty()) {
			Item i = queue.poll();
			if(i instanceof ItemText) {
				// Search for word, ignoring every character that isn't letter or number (very raw code for efficiency)
				String text = ((ItemText) i).getText();
				int currentIndex = 0;
				for(int j = 0; j < text.length() && currentIndex < word.length(); j++) {
					char c = text.charAt(j);
					// Check uppercase
					if(c >= 'A' && c <= 'Z') c = (char) ((int) c + 'a' - 'A');
					// Check if equal to current word char
					if(c == word.charAt(currentIndex)) {
						// If last index then the word exists in template
						if(currentIndex == word.length() - 1) return true;
						// Otherwise add one to index
						currentIndex++;
						continue;
					}
					if(currentIndex > 0 && ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
						// If the char was alphanumerical but unequal, the word train was broken..
						currentIndex = 0;
					}
				}
			}
			// For other types of items just add their children to queue so we reach all the string items in the end
			else if(i instanceof ItemVariable){
				queue.addAll(((ItemVariable) i).getContent());
			}
			else if(i instanceof ItemProgression){
				queue.addAll(((ItemProgression) i).getContent());
			}
		}
		return false;
	}
	
	public static boolean searchWordInTemplateString(TemplateString tStr, String word) {
		// Search for word, ignoring every character that isn't letter or number (very raw code for efficiency)
		String text = tStr.string;
		int currentIndex = 0;
		for(int j = 0; j < text.length() && currentIndex < word.length(); j++) {
			char c = text.charAt(j);
			// Check uppercase
			if(c >= 'A' && c <= 'Z') c = (char) (c + 'a' - 'A');
			// Check if equal to current word char
			if(c == word.charAt(currentIndex)) {
				// If last index then the word exists in template
				if(currentIndex == word.length() - 1) return true;
				// Otherwise add one to index
				currentIndex++;
				continue;
			}
			if(currentIndex > 0 && ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
				// If the char was alphanumerical but unequal, the word train was broken..
				currentIndex = 0;
			}
		}
		return false;
	}
	
	/**
	 * Converts data for a specific format in a Collection of templates into a List of such format data
	 * @param templates
	 * @param format
	 * @return
	 */
	public static ArrayList<TemplateFormat> listForFormat(List<Template> templates, String formatName) {
		ArrayList<TemplateFormat> out = new ArrayList<>();
			for(int i = 0; i < templates.size(); i++) {
				Template template;
				synchronized(templates) {
					template = templates.get(i);
				}
				TemplateFormat format = template.get(formatName);
				if(format != null) {
					out.add(format);
				}
		}
		return out;
	}
	
	public static <T extends Object> void applySubstitutionToWeights(Map<String, HashMap<T, Double>> weightMap, PatriciaTrie<HashMap<String, Double>> substitutionMap) {
		// This map is a change done by this function that will be merged into the main map at the end
		HashMap<String, HashMap<T, Double>> mapChanges = new HashMap<>();
		
		for(Entry<String, HashMap<T, Double>> entry : weightMap.entrySet()) {
			String word = entry.getKey();
			for(int i = 0; i < word.length(); i++) {
				// Search for entries that apply to this sentence (use first letter in prefix map to speed up the search)
				Map<String, HashMap<String, Double>> potentialSubstitutionMap = substitutionMap.prefixMap(word.substring(i, i+1));
				for(Entry<String, HashMap<String, Double>> subEntry : potentialSubstitutionMap.entrySet() ) {
					String from = subEntry.getKey();
					if(word.length() - i >= from.length()) {
						if(word.substring(i, i+from.length()).equals(from)) {
							// We have a confirmed substitution
							for(Entry<String, Double> substitution : subEntry.getValue().entrySet()) {
								String newWord = word.substring(0, i) + substitution.getKey() + word.substring(i+from.length());
								mapChanges.put(newWord, returnScaledMap(weightMap.get(word), substitution.getValue()));
							}
						}
					}
				}
			}
		}
		
		// Merge substitutions
		for(Entry<String, HashMap<T, Double>> entry : mapChanges.entrySet()) {
			String word = entry.getKey();
			HashMap<T, Double> map = entry.getValue();
			// If the weight map didn't already contain the modified word, add the word to it
			if(!weightMap.containsKey(word)) {
				weightMap.put(word, map);
			}
			else {
				HashMap<T, Double> currentMap = weightMap.get(word);
				for(Entry<T, Double> weightEntry : map.entrySet()) {
					T key = weightEntry.getKey();
					if(currentMap.containsKey(key)) {
						// Add the old weight and new weight together if already exists
						currentMap.put(key, currentMap.get(key) + weightEntry.getValue());
					}
					else {
						currentMap.put(key, weightEntry.getValue());
					}
				}
			}
		}
	}
	
	public static <T extends Object> HashMap<T, Double> returnScaledMap(Map<T, Double> map, double scale) {
		HashMap<T, Double> out = new HashMap<>();
		for(Entry<T, Double> entry : map.entrySet()) {
			out.put(entry.getKey(), entry.getValue() * scale);
		}
		return out;
	}
	
	/**
	 * "Deep"-merges a map of HashMaps, combining all keys of individual child maps in the "from" map into the "to" map
	 */
	public static <T,S,V extends Object> void merge(Map<T, HashMap<S, V>> to, Map<T, HashMap<S, V>> from) {
		for(Entry<T, HashMap<S, V>> entry : from.entrySet()) {
			if(to.containsKey(entry.getKey())) {
				to.get(entry.getKey()).putAll(entry.getValue());
			}
			else {
				to.put(entry.getKey(), entry.getValue());
			}
		}
	}
}

