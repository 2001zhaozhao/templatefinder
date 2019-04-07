package database_templatefinder.templatefinder.types;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import database_templatefinder.templatefinder.App;

public interface Item {
	
	/**
	 * Gets a list of Strings that this item can produce.
	 * @param variableCounter A counter used to produce the ID of variables. Counts up by one every time a variable is initialized.
	 * @return
	 */
	public ArrayList<TemplateString> getPossibilities();
	
	/**
	 * Parse the appropriate type of item from template JSON
	 * @param root
	 * @return
	 */
	public static Item parseItemFromJson(TemplateFormat tf, JsonObject root) {
		if(root.get("type") == null) {
			return new ItemText(tf, "[ITEM ERROR]");
		}
		
		String type = root.get("type").getAsString();
		Item item;
		switch(type) {
		case "text":
			item = new ItemText(tf, root.get("content").getAsString());
			break;
		case "dropdown":
			item = new ItemVariable(tf, root, getItemListFromJson(tf, root.get("content").getAsJsonArray()));
			tf.variableData.add((ItemVariable) item);
			break;
		case "progression":
			item = new ItemProgression(getItemListFromJson(tf, root.get("content").getAsJsonArray()));
			break;
		default:
			item = new ItemText(tf, "[TYPE ERROR]");
			break;
		}
		return item;
	}
	
	/**
	 * Get a list of items from a JSON array
	 * @param arr
	 * @return
	 */
	public static ArrayList<Item> getItemListFromJson(TemplateFormat tf, JsonArray arr) {
		ArrayList<Item> content = new ArrayList<>();
		for(JsonElement e : arr) {
			if(e.isJsonObject()) {
				JsonObject obj = e.getAsJsonObject();
				content.add(parseItemFromJson(tf, obj));
			}
		}
		return content;
	}
}
