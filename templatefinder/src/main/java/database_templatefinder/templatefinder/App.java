package database_templatefinder.templatefinder;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.TreeSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import database_templatefinder.templatefinder.types.Item;
import database_templatefinder.templatefinder.types.ItemProgression;
import database_templatefinder.templatefinder.types.ItemVariable;
import database_templatefinder.templatefinder.types.Template;
import database_templatefinder.templatefinder.types.TemplateFormat;
import database_templatefinder.templatefinder.types.TemplateString;

/**
 * Hello world!
 *
 */
public class App {
	public static LinkedHashSet<String> commonWordsSet;
	public static LinkedHashSet<String> veryCommonWordsSet;
	
	public static ArrayList<Template> templates = new ArrayList<>();
	public static HashMap<String, HashMap<String, HashMap<String, TemplateString>>> templatesExpanded = new HashMap<>();
	public static HashMap<String, HashMap<String, TemplateString>> templatesExpandedConcat = new HashMap<>();
	
	// Template -> Word -> Value
	public static HashMap<String, HashMap<String, Double>> templateWordWeights = new HashMap<>();
	// Word -> TemplateString -> Value
	public static HashMap<String, HashMap<TemplateString, Double>> stringWeightsInTemplate = new HashMap<>();
	public static HashMap<String, HashMap<TemplateString, Double>> stringWeightsAcrossTemplates = new HashMap<>();
	
	public static final String BASE_FORMAT = "English";
	
	// TODO Word tenses
	//Separate sentence 

	public static void main( String[] args )
	{
		long startTime = System.currentTimeMillis();
		
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
		
		
		// -----Load templates (multi core)
		long startTimeJson = System.currentTimeMillis();
		System.out.println("Loaded word list file in " + (startTimeJson - startTime) + "ms.");
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
									
									synchronized(templateWordWeights) {
										templateWordWeights.put(t.getName(), wordWeights);
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
										stringWeightsInTemplate.putAll(stringWeightsByWord);
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
			// Check if all templates themselves have been loaded. If this is the case, start the stringWeightsAcrossTemplates threads
			if(!stringWeightsAcrossTemplatesThreadsStarted && templates.size() >= templateCount) {
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
									stringWeightsAcrossTemplates.putAll(stringWeightsByWord);
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
		
		// Start web server
		/*try {
			new HttpHandler();
		} catch (IOException e) {
			System.err.println("Couldn't start web server:\n" + e);
		}*/
		
		// Interactive cmdline starts here
		@SuppressWarnings("resource")
		Scanner scanner = new Scanner(System.in);
		while(true) {
			String line = scanner.nextLine();
			JsonArray js = InputProcessing.toJson(InputProcessing.process(line));
			System.out.print(js.toString());
		}
	}

}
