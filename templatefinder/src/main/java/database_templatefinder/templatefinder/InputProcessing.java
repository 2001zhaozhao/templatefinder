package database_templatefinder.templatefinder;

import java.io.PrintStream;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import com.google.gson.JsonArray;

import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.AbstractMap.SimpleEntry;


import database_templatefinder.templatefinder.output.OutputSection;
import database_templatefinder.templatefinder.output.OutputWeight;
import database_templatefinder.templatefinder.types.TemplateString;

public class InputProcessing {
	
	/**
	 * Legacy method to process the input
	 */
	public static TemplateString legacyProcessInput(String input, PrintStream ps) {
		// Call compute functions
		HashMap<TemplateString, OutputWeight> weight = TFIDFEngine.getFinalWeight(input);
		
		// Calculate most appropriate template
		TemplateString maxTemplate = null;
		OutputWeight max = new OutputWeight(0,0,0,0);
		TreeSet<Entry<TemplateString, Double>> msgSet = new TreeSet<>(new Comparator<Entry<TemplateString, Double>>()  {
			@Override
			public int compare(Entry<TemplateString, Double> o1, Entry<TemplateString, Double> o2) {
				if(o1.getValue() == o2.getValue()) return 0;
				if(o1.getValue() > o2.getValue()) return 1;
				return -1;
			}
		});
		for(Entry<TemplateString, OutputWeight> entry : weight.entrySet()) {
			if(entry.getKey() == null) continue;
			if(entry.getValue().doubleValue() > max.doubleValue()) {
				maxTemplate = entry.getKey();
				max = entry.getValue();
			}
			msgSet.add(new SimpleEntry<TemplateString, Double>(entry.getKey(), entry.getValue().doubleValue()));
		}
		
		// Debug
		int count = 0;
		if(ps != null) ps.println("!!!!! Top 100 Templates: !!!!!");
		for(Entry<TemplateString, Double> entry : msgSet) {
			count++;
			if(count < msgSet.size() - 100) continue; // Only count the top 100
			if(ps != null) ps.println(((long) (entry.getValue() * 1000000)) / 1000000.0 + " | " + entry.getKey().toImportantInfoString());
		}
		
		if(ps != null) {
			ps.println("Most likely template:   " + maxTemplate);
			ps.println("Template weight: " + max.getTemplateWeightContribution() + 
					" | String weight in template: " + max.getStringWeightInTemplateContribution() + "(" + (int) (max.inTemplateWeightPortion * 100) + "%)" +
					" | String weight across templates: " + max.getStringWeightAcrossTemplatesContribution());
		}
		
		return maxTemplate;
	}
	
	public static final double MINIMUM_WEIGHT = 0.5;
	public static final double MINIMUM_WEIGHT_PER_SQRT_WORDCOUNT = 0.5;
	
	/**
	 * Processes a whole paragraph or document of input. Returns a list of output items ("recommendations") which could conflict with one another.
	 */
	public static ArrayList<OutputSection> preProcessInput(String input) {
		// Split the input into individual sentences
		BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
		iterator.setText(input);
		
		ArrayList<OutputSection> sections = new ArrayList<>();
		// Get the text boundaries
		int start = iterator.first();
		while(true) {
			int end = iterator.next();
			if(end == BreakIterator.DONE) break;
			
			sections.add(new OutputSection(input, start, end));
			start = end;
		}
		
		ArrayList<OutputSection> out = new ArrayList<>();
		for(OutputSection section : sections) {
			String text = input.substring(section.beginIndex, section.endIndex);
			List<String> words = TFIDFEngine.getWordsInString(text);
			HashMap<TemplateString, OutputWeight> weight = TFIDFEngine.getFinalWeight(text);
			
			// Remove entries below a threshold weight sqrt(len) (minimum correspondence to be considered an "okay" suggestion)
			for(Iterator<Entry<TemplateString, OutputWeight>> iter = weight.entrySet().iterator(); iter.hasNext();) {
				Entry<TemplateString, OutputWeight> entry = iter.next();
				if(entry.getValue().doubleValue() > MINIMUM_WEIGHT + MINIMUM_WEIGHT_PER_SQRT_WORDCOUNT * Math.sqrt(words.size())) {
					OutputSection newSection = section.clone();
					newSection.output = entry.getKey().string;
					newSection.outputSource = entry.getKey();
					newSection.outputWeight = entry.getValue();

					out.add(newSection);
				}
			}
		}
		
		return out;
	}
	
	// Say you have two strings and a larger string that contains these two strings.
	// WEIGHT_EXPONENT greater than 1 makes the large string favorable (always) if you just concatenate the final weights of the strings and maximize that sum.
	// WEIGHT_LENGTH_EXPONENT less than 1 makes the large string less favorable, but even more if it has "wasted" words beyond the two component strings.
	// Thus if large string does not have much waste, WEIGHT_EXPONENT 1.2 and WEIGHT_LENGTH_EXPONENT 0.88 means that the final weight will be a bit more for larger string
	// If large string has a lot of wasted/unused parts, the length weight will overtake the normal weight meaning that smaller strings will win
	public static final double WEIGHT_EXPONENT = 1.2;
	public static final double WEIGHT_LENGTH_EXPONENT = 0.88;
	
	/**
	 * Figures out a best way to group appropriate suggestions into the passage.
	 * <p>
	 * Works by optimizing a sum of slightly-modified weights with the restraint that there must be no overlaps.
	 * <p>
	 * This optimal suggestion would not contain any overlaps within the input.
	 * @param input
	 * @return
	 */
	public static ArrayList<OutputSection> deduplicateInput(ArrayList<OutputSection> raw) {
		LinkedList<SectionStorage> sections = new LinkedList<SectionStorage>();
		for(OutputSection sec : raw) {
			List<String> words = TFIDFEngine.getWordsInString(sec.getInputSection());
			int wordCount = words.size();
			double weight = sec.outputWeight.getFinalWeightModifiedByLength(wordCount);
			// Modify weight
			double modifiedWeight = Math.pow(weight, WEIGHT_EXPONENT) * Math.pow(wordCount, WEIGHT_LENGTH_EXPONENT);
			sections.add(new SectionStorage(sec, modifiedWeight));
		}
		
		// Find the best grouping in average case O(n log n) (the only n log n part is the sorting at the beginning)
		PriorityQueue<SectionStorage> beginQueue = new PriorityQueue<>(new Comparator<SectionStorage>() {
			public int compare(SectionStorage o1, SectionStorage o2) {
				return o1.section.beginIndex - o2.section.beginIndex;
			}
		});
		beginQueue.addAll(sections);
		PriorityQueue<SectionStorage> endQueue = new PriorityQueue<>(new Comparator<SectionStorage>() {
			public int compare(SectionStorage o1, SectionStorage o2) {
				return o1.section.endIndex - o2.section.endIndex;
			}
		});
		endQueue.addAll(sections);
		
		Link currentBestCompleteLink = new Link();
		while((!beginQueue.isEmpty()) || (!endQueue.isEmpty())) {
			// Find whether a new begin or new end is first
			boolean end;
			if(beginQueue.isEmpty()) {
				end = true;
			}
			else if(endQueue.isEmpty()) {
				end = false;
			}
			else {
				end = endQueue.peek().section.endIndex - 1 <= beginQueue.peek().section.beginIndex;
			}
			
			if(end) {
				// For ending, if the current link is already better than this one, ignore, otherwise replace the best link with the stored link
				SectionStorage section = endQueue.poll();
				//System.out.println(section.bestLinkAtStart.totalWeight);
				if(section.bestLinkAtStart.totalWeight + section.weight > currentBestCompleteLink.totalWeight) {
					currentBestCompleteLink = new Link(section.bestLinkAtStart, section.section, section.weight);
				}
			}
			else {
				// For beginning, store the current link
				SectionStorage section = beginQueue.poll();
				section.bestLinkAtStart = currentBestCompleteLink;
				//System.out.println(currentBestCompleteLink.toList());
			}
		}

		// Return the weight-maximizing set of sections
		return currentBestCompleteLink.toList();
	}
	public static class SectionStorage {
		OutputSection section;
		Link bestLinkAtStart = null;
		double weight;
		
		public SectionStorage(OutputSection section, double weight) {
			this.section = section;
			this.weight = weight;
		}
	}
	public static class Link {
		double totalWeight;
		OutputSection section;
		Link previousInLine;
		int lineLength;
		
		public Link() {
			totalWeight = 0;
			section = null;
			previousInLine = null;
			lineLength = 0;
		}
		
		public Link(Link previousInLine, OutputSection section, double newWeight) {
			this.section = section;
			totalWeight = previousInLine.totalWeight + newWeight;
			this.previousInLine = previousInLine;
			lineLength = previousInLine.lineLength + 1;
		}
		
		/**
		 * Change this "linked list" into an actual ArrayList (this link will be the end of the list, etc)
		 */
		public ArrayList<OutputSection> toList() {
			if(this.lineLength == 0) return new ArrayList<OutputSection>();
			
			ArrayList<OutputSection> l = new ArrayList<>(Collections.nCopies(lineLength, null));
			
			int i = lineLength - 1;
			Link currentLink = this;
			while(i >= 0) {
				l.set(i, currentLink.section);
				currentLink = currentLink.previousInLine;
				i--;
			}
			return l;
		}
	}
	
	/**
	 * Processes the input, returns an output.
	 * @param input
	 * @return
	 */
	public static ArrayList<OutputSection> process(String input) {
		ArrayList<OutputSection> preOutput = preProcessInput(input);
		return deduplicateInput(preOutput);
	}
	
	/**
	 * Changes the output array to a Json array
	 * @param outputArray
	 * @return
	 */
	public static JsonArray toJson(ArrayList<OutputSection> outputArray) {
		JsonArray out = new JsonArray();
		for(OutputSection section : outputArray) {
			out.add(section.toJson());
		}
		return out;
	}
}
