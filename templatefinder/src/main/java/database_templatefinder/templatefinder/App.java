package database_templatefinder.templatefinder;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;

import org.apache.commons.collections4.trie.PatriciaTrie;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

import database_templatefinder.templatefinder.types.Item;
import database_templatefinder.templatefinder.types.ItemProgression;
import database_templatefinder.templatefinder.types.Template;
import database_templatefinder.templatefinder.types.TemplateFormat;
import database_templatefinder.templatefinder.types.TemplateString;

/**
 * Hello world!
 *
 */
public class App {
	// Word info
	public static LinkedHashSet<String> commonWordsSet;
	public static LinkedHashSet<String> veryCommonWordsSet;
	
	// Weights to subtract for removing a prefix/suffix
	public static PatriciaTrie<Double> prefixRemovalWeights = new PatriciaTrie<>();
	/**Keys are reversed since this map is for suffixes*/
	public static PatriciaTrie<Double> suffixRemovalWeights = new PatriciaTrie<>();
	public static PatriciaTrie<HashMap<String, Double>> prefixSubstitutionWeights = new PatriciaTrie<>();
	/**Keys are reversed since this map is for suffixes*/
	public static PatriciaTrie<HashMap<String, Double>> suffixSubstitutionWeights = new PatriciaTrie<>();
	
	public static ArrayList<Template> templates = new ArrayList<>();
	public static HashMap<String, HashMap<String, HashMap<String, TemplateString>>> templatesExpanded = new HashMap<>();
	public static HashMap<String, HashMap<String, TemplateString>> templatesExpandedConcat = new HashMap<>();
	
	// Word -> Template Name -> Value
	public static PatriciaTrie<HashMap<String, Double>> templateWordWeights = new PatriciaTrie<>();
	// Word -> TemplateString -> Value
	public static PatriciaTrie<HashMap<TemplateString, Double>> stringWeightsInTemplate = new PatriciaTrie<>();
	public static PatriciaTrie<HashMap<TemplateString, Double>> stringWeightsAcrossTemplates = new PatriciaTrie<>();
	
	public static PatriciaTrie<String> allWordsInReverse = new PatriciaTrie<>();
	
	public static final String BASE_FORMAT = "English";

	public static void main( String[] args )
	{
		HashSet<String> argsSet = new HashSet<>();
		for(String s : args) argsSet.add(s);
		boolean jsonInterface = argsSet.contains("json");
		
		long startTime = System.currentTimeMillis();

		// Temp variables to use in the loading process
		final HashMap<String, HashMap<String, Double>> rawTemplateWordWeights = new HashMap<>();
		
		// These would be too costly to check at runtime so they are just added to the weights map at startup
		// This might cause a weak effect of a few words being counted "twice" but this is worth it
		// to distinguish common spelling differences (mostly for British vs American)
		final PatriciaTrie<HashMap<String, Double>> anywhereSubstitutionWeights = new PatriciaTrie<>();
		
		// -----Load files
		// google-10000-english-usa - list of the 10,000 most common English words

		// If a word is not on this list it's treated as a technical word
		Scanner sc;
		sc = new Scanner(App.class.getResourceAsStream("/google-10000-english-usa.txt"));
		commonWordsSet = new LinkedHashSet<String>();
		while(sc.hasNext()) {
			commonWordsSet.add(sc.next());
		}
		sc.close();

		// If a word is in the first 75 in the list it's treated as a very common word
		veryCommonWordsSet = new LinkedHashSet<String>();
		Iterator<String> iter = commonWordsSet.iterator();
		for(int i = 0; i < 75 && iter.hasNext(); i++) {
			veryCommonWordsSet.add(iter.next());
		}
		// Add all the single-digit numbers
		for(int i = 0; i <= 9; i++) {
			veryCommonWordsSet.add(String.valueOf(i));
		}
		
		// equivalence.json - prefixes and suffixes
		{
			JsonObject json;
			try {
				json = new JsonParser().parse(new InputStreamReader(App.class.getResourceAsStream("/equivalence.json"))).getAsJsonObject();
			} catch (JsonIOException | JsonSyntaxException e1) {
				System.out.println("Could not find file");
				return;
			}
			// Suffixes
			JsonArray suffixArray = json.getAsJsonArray("suffixes");
			for(JsonElement e : suffixArray) {
				JsonArray arr = e.getAsJsonArray();
				Double weight = null;
				LinkedList<String> list = new LinkedList<>();
				
				for(JsonElement ee : arr) {
					JsonPrimitive prim = ee.getAsJsonPrimitive();
					if(prim.isNumber()) {
						weight = prim.getAsDouble();
					}
					else if(prim.isString()){
						list.add(prim.getAsString());
					}
					else {
						System.out.println("Suffix " + prim + " has an invalid format, skipping");
					}
				}
				
				if(weight == null) {
					System.out.println("No weight found in suffixes " + list + ", skipping");
				}

				// Suffixes ONLY: reverse strings in list
				for(int i = 0; i < list.size(); i++) {
					list.set(i, new StringBuilder(list.get(i)).reverse().toString());
				}
				if(list.size() > 1) {
					// Load substitution between 2 or more prefixes (used in loading)
					for(String s : list) {
						HashMap<String, Double> weightMap;
						if(!suffixSubstitutionWeights.containsKey(s))
							suffixSubstitutionWeights.put(s, weightMap = new HashMap<String, Double>());
						else
							weightMap = suffixSubstitutionWeights.get(s);
						for(String s2 : list) {
							if(s == s2) continue;
							// Taking max weight so we don't override larger weights with smaller ones
							if(weightMap.containsKey(s2) && weightMap.get(s2) > weight) continue;
							weightMap.put(s2, weight);
						}
					}
				}
				else {
					// Load removal of 1 prefix (used in runtime)
					String str = list.get(0);
					suffixRemovalWeights.put(str, weight);
				}
			}

			// Prefixes
			JsonArray prefixArray = json.getAsJsonArray("prefixes");
			for(JsonElement e : prefixArray) {
				JsonArray arr = e.getAsJsonArray();
				Double weight = null;
				LinkedList<String> list = new LinkedList<>();
				
				for(JsonElement ee : arr) {
					JsonPrimitive prim = ee.getAsJsonPrimitive();
					if(prim.isNumber()) {
						weight = prim.getAsDouble();
					}
					else if(prim.isString()){
						list.add(prim.getAsString());
					}
					else {
						System.out.println("Prefix " + prim + " has an invalid format, skipping");
					}
				}
				
				if(weight == null) {
					System.out.println("No weight found in prefixes " + list + ", skipping");
					continue;
				}
				
				if(list.size() > 1) {
					// Load substitution between 2 or more prefixes (used in loading)
					for(String s : list) {
						HashMap<String, Double> weightMap;
						if(!prefixSubstitutionWeights.containsKey(s))
							prefixSubstitutionWeights.put(s, weightMap = new HashMap<String, Double>());
						else
							weightMap = prefixSubstitutionWeights.get(s);
						for(String s2 : list) {
							if(s == s2) continue;
							// Taking max weight so we don't override larger weights with smaller ones
							if(weightMap.containsKey(s2) && weightMap.get(s2) > weight) continue;
							weightMap.put(s2, weight);
						}
					}
				}
				else if(list.size() == 1){
					// Load removal of 1 prefix (used in runtime)
					String str = list.get(0);
					prefixRemovalWeights.put(str, weight);
				}
			}
			
			// Anywhere (diff format than prefix/suffix)
			JsonObject anywhereObj = json.getAsJsonObject("anywhere");
			for(Entry<String, JsonElement> entry : anywhereObj.entrySet()) {
				JsonArray arr = entry.getValue().getAsJsonArray();
				String from = entry.getKey();

				// Copied code
				Double weight = null;
				LinkedList<String> list = new LinkedList<>();
				
				for(JsonElement ee : arr) {
					JsonPrimitive prim = ee.getAsJsonPrimitive();
					if(prim.isNumber()) {
						weight = prim.getAsDouble();
					}
					else if(prim.isString()){
						list.add(prim.getAsString());
					}
					else {
						System.out.println("Prefix " + prim + " has an invalid format, skipping");
					}
				}
				
				if(weight == null) {
					System.out.println("No weight found in prefixes " + list + ", skipping");
					continue;
				}
				if(list.isEmpty()) {
					continue;
				}
				
				HashMap<String, Double> weightMap = new HashMap<>();
				anywhereSubstitutionWeights.put(from, weightMap);
				for(String to : list) {
					weightMap.put(to, weight);
				}
			}
		}
		
		// -----Load templates (multi core)
		long startTimeJson = System.currentTimeMillis();
		System.out.println("Loaded word list and equivalence files in " + (startTimeJson - startTime) + "ms.");
		JsonArray json;
		try {
			json = new JsonParser().parse(new InputStreamReader(App.class.getResourceAsStream("/templates.json"))).getAsJsonArray();
		} catch (JsonIOException | JsonSyntaxException e1) {
			System.out.println("Could not find file");
			return;
		}
		long endTimeJson = System.currentTimeMillis();
		System.out.println("Loaded JSON file in " + (endTimeJson - startTimeJson) + "ms.");
		

		int numThreads = Runtime.getRuntime().availableProcessors();
		
		System.out.println("Beginning template load with " + numThreads + " threads.");
		LinkedList<Runnable> taskList = new LinkedList<>();
		
		// Add task for each element
		int templateCount = 0;
		for(JsonElement tElement : json) {
			taskList.add(new Runnable() {
				public void run() {
					if(tElement.isJsonObject()) {
						
						JsonObject tObj = tElement.getAsJsonObject();
						Template t = new Template(tObj.get("name").getAsString());
						
						// TemplateFormat
						for(Entry<String, JsonElement> tfEntry : tObj.entrySet()) {
							if(tfEntry.getValue().isJsonObject()) {
								String formatName = tfEntry.getKey();
								JsonObject tfObj = tfEntry.getValue().getAsJsonObject();
								TemplateFormat tf = new TemplateFormat(t, formatName);
								
								// baseProgression
								tf.baseProgression = new ItemProgression(Item.getItemListFromJson(tf, tfObj.get("content").getAsJsonArray()));
								
								t.addFormat(tf);
							}
						}
						
						synchronized(templates) {
							templates.add(t);
						}
						
						HashMap<String, HashMap<String, TemplateString>> expanded = TFIDFEngine.expandTemplate(t, BASE_FORMAT);
						
						synchronized(templatesExpanded) {
							templatesExpanded.put(t.getName(), expanded);
							templatesExpandedConcat.putAll(expanded);
						}
						
						// Add new asynchronous task to load template weights.
						synchronized(taskList) {
							taskList.add(new Runnable() {
								public void run() {
									// !!!!!!!!!! Template Weights

									// Find template word frequency (get tf)
									HashMap<String, Double> wordWeights = TFIDFEngine.getTemplateWeights(t.get(BASE_FORMAT));
									// Modify by function (divide by idf)
									TFIDFEngine.tfidfTemplateWeights(wordWeights, TFIDFEngine.listForFormat(templates, BASE_FORMAT));
									
									synchronized(rawTemplateWordWeights) {
										rawTemplateWordWeights.put(t.getName(), wordWeights);
									}
									
									// !!!!!!!!!! String Weights in Template
									
									// Get tf (and fill collection of all strings)
									LinkedList<TemplateString> allStrings = new LinkedList<TemplateString>();
									
									HashMap<TemplateString, HashMap<String, Double>> stringWeights = new HashMap<>();
									for(HashMap<String, TemplateString> localeMapForString : expanded.values()) {
										TemplateString tStr = localeMapForString.get(BASE_FORMAT);
										stringWeights.put(tStr, TFIDFEngine.getStringWeights(tStr));
										allStrings.add(tStr);
									}
									
									// Get idf
									HashMap<String, HashMap<TemplateString, Double>> stringWeightsByWord = TFIDFEngine.createStringWeightMapByWord(stringWeights);
									TFIDFEngine.tfidfStringWeightsWithinTemplate(stringWeightsByWord, allStrings);
									
									synchronized(stringWeightsInTemplate) {
										TFIDFEngine.merge(stringWeightsInTemplate, stringWeightsByWord);
									}
								}
							});
						}
					}
				}
			});
			templateCount++;
		}
		
		// Create threads and run
		ArrayList<Thread> threadList = new ArrayList<Thread>();
		for(int i = 0; i < numThreads; i++) {
			int finali = i;
			Thread t = new Thread() {
				public void run() {
					long startTime = System.currentTimeMillis();
					while(true) {
						Runnable r;
						synchronized(taskList) {
							if(taskList.isEmpty()) break;
							r = taskList.poll();
						}
						r.run();
					}
					long endTime = System.currentTimeMillis();
					System.out.println("Thread " + finali + " finished in " + (endTime - startTime) + "ms.");
				}
			};
			threadList.add(t);
			t.start();
		}
		
		// Sleep main thread until all workers are done
		boolean stringWeightsAcrossTemplatesThreadsStarted = false;
		while(true) {
			boolean alive = false;
			for(Thread t : threadList) {
				if(t.isAlive()) {
					alive = true;
					// Sleep 1ms if running thread detected
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
			boolean allTemplatesLoaded;
			synchronized(templates) { allTemplatesLoaded = templates.size() >= templateCount; }
			// Check if all templates themselves have been loaded. If this is the case, start the stringWeightsAcrossTemplates threads
			if(!stringWeightsAcrossTemplatesThreadsStarted && allTemplatesLoaded) {
				stringWeightsAcrossTemplatesThreadsStarted = true;
				ArrayList<Template> templatesCopy = new ArrayList<>(templates);
				for(Template template : templatesCopy) {
					synchronized(taskList) {
						taskList.add(new Runnable() {
							public void run() {
								// !!!!!!!!!! String weights across templates
								
								// Get tf
								
								HashMap<TemplateString, HashMap<String, Double>> stringWeights = new HashMap<>();
								for(HashMap<String, TemplateString> localeMapForString : templatesExpanded.get(template.getName()).values()) {
									TemplateString tStr = localeMapForString.get(BASE_FORMAT);
									stringWeights.put(tStr, TFIDFEngine.getStringWeights(tStr));
								}
								
								// Get idf
								HashMap<String, HashMap<TemplateString, Double>> stringWeightsByWord = TFIDFEngine.createStringWeightMapByWord(stringWeights);
								TFIDFEngine.tfidfStringWeightsInAllTemplates(stringWeightsByWord, TFIDFEngine.listForFormat(templatesCopy, BASE_FORMAT));
								
								synchronized(stringWeightsAcrossTemplates) {
									TFIDFEngine.merge(stringWeightsAcrossTemplates, stringWeightsByWord);
								}
							}
						});
					}
				}
			}
			if(!alive && stringWeightsAcrossTemplatesThreadsStarted) break;
		}
		
		long endTime = System.currentTimeMillis();
		System.out.println("Program startup took " + (endTime - startTime) + "ms for " + templates.size() + " templates.");
		
		// Create the final template word weights map
		for(Entry<String, HashMap<String, Double>> templateEntry : rawTemplateWordWeights.entrySet()) {
			for(Entry<String, Double> wordEntry : templateEntry.getValue().entrySet()) {
				String template = templateEntry.getKey();
				String word = wordEntry.getKey();
				if(!templateWordWeights.containsKey(word)) templateWordWeights.put(word, new HashMap<String, Double>());
				templateWordWeights.get(word).put(template, wordEntry.getValue());
			}
		}
		// Add all maps to reverse word tree
		addToReverseTree(templateWordWeights);
		addToReverseTree(stringWeightsInTemplate);
		addToReverseTree(stringWeightsAcrossTemplates);
		// Apply "anywhere" substitutions
		TFIDFEngine.applySubstitutionToWeights(templateWordWeights, anywhereSubstitutionWeights);
		TFIDFEngine.applySubstitutionToWeights(stringWeightsInTemplate, anywhereSubstitutionWeights);
		TFIDFEngine.applySubstitutionToWeights(stringWeightsAcrossTemplates, anywhereSubstitutionWeights);
		
		// Start web server
		if(!jsonInterface) {
			try {
				new HttpHandler();
			} catch (IOException e) {
				System.err.println("Couldn't start web server:\n" + e);
			}
		}
		
		// Interactive cmdline starts here
		@SuppressWarnings("resource")
		Scanner scanner = new Scanner(System.in);
		while(true) {
			if(jsonInterface) {
				String line = scanner.nextLine();
				JsonArray js = InputProcessing.toJson(InputProcessing.process(line));
				System.out.print(js.toString());
			}
			else {
				System.out.println("Please input the string to check.");
				String line = scanner.nextLine();
				InputProcessing.legacyProcessInput(line, System.out);
			}
		}
	}
	
	public static <T extends Object> void addToReverseTree(Map<String, T> map) {
		for(String s : map.keySet()) {
			// Reverse, Forward
			allWordsInReverse.put(new StringBuilder(s).reverse().toString(), s);
		}
	}
}
