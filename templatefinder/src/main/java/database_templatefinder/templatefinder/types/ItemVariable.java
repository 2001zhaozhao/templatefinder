package database_templatefinder.templatefinder.types;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * An item that combines different possibility items
 * @author Jason
 *
 */
public class ItemVariable implements Item {
	
	ArrayList<Item> content;
	JsonObject variableRoot;
	TemplateFormat tf;
	
	public ItemVariable(TemplateFormat tf, JsonObject root, ArrayList<Item> content) {
		this.tf = tf;
		this.content = content;
		variableRoot = root;
	}
	
	public ItemVariable(TemplateFormat tf, JsonObject root, Collection<Item> content) {
		this.tf = tf;
		this.content = new ArrayList<Item>(content);
		variableRoot = root;
	}
	
	public ItemVariable(TemplateFormat tf, JsonObject root) {
		this.tf = tf;
		this.content = new ArrayList<Item>();
		variableRoot = root;
	}
	
	public ArrayList<Item> getContent() {
		return content;
	}

	@Override
	public ArrayList<TemplateString> getPossibilities() {
		ArrayList<TemplateString> out = new ArrayList<>();
		int varID = tf.variableData.indexOf(this);
		
		int currentChoiceIndex = 0;
		for(Item item : content) {
			ArrayList<TemplateString> childPossibilities = item.getPossibilities();
			for(TemplateString possibility : childPossibilities) {
				// Add mention of the current variable into the item
				possibility.addVariable(varID, 0, possibility.string.length(), currentChoiceIndex);
				out.add(possibility);
			}
			
			// Increment index as we loop through the different variable choices
			currentChoiceIndex ++;
		}
		return out;
	}

}
