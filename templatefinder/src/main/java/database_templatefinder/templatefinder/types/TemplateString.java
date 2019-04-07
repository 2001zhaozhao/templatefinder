package database_templatefinder.templatefinder.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import database_templatefinder.templatefinder.App;

/**
 * A string result generated from a template
 * @author Jason
 *
 */
public class TemplateString {
	public TemplateFormat tf;
	public HashMap<Integer, VarData> variables = new HashMap<>();
	public String string;
	
	/**
	 * Creates a template string with no variables
	 * @param string
	 */
	public TemplateString(TemplateFormat tf, String string) {
		this.tf = tf;
		this.string = string;
	}
	
	/**
	 * Adds a variable to this template string
	 * @param varID
	 * @param beginIndex
	 * @param endIndex
	 * @param choice
	 */
	public void addVariable(int varID, int beginIndex, int endIndex, int choice) {
		variables.put(varID, new VarData(varID, beginIndex, endIndex, choice));
	}
	
	/**
	 * Gets deep copy of self
	 * @return
	 */
	public TemplateString getDeepCopy() {
		TemplateString copy = new TemplateString(tf, string);
		for(VarData var : variables.values()) {
			copy.addVariable(var.id, var.beginIndex, var.endIndex, var.choice);
		}
		return copy;
	}
	
	// Implementing hashCode and equals because this object needs to be used in HashSets
	@Override
	public int hashCode() {
		return this.string.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if(o == null || !(o instanceof TemplateString)) return false;
		return Objects.equals(this.string, ((TemplateString) o).string) && Objects.equals(this.variables, ((TemplateString) o).variables);
	}
	
	@Override
	public String toString() {
		return string;
	}
	
	public String toImportantInfoString() {
		String string = this.string.replace("\n", " ");
		String trimmedString = string.length() >= 60 ? (string.substring(0,60) + "...") : string;
		return trimmedString + "[" + tf.templateName + "]" + variables;
	}
	
	public static class VarData {
		public int id;
		public int beginIndex;
		public int endIndex;
		public int choice;
		
		public VarData(int id, int beginIndex, int endIndex, int choice) {
			this.id = id; this.beginIndex = beginIndex; this.endIndex = endIndex; this.choice = choice;
		}
		
		@Override
		public boolean equals(Object o) {
			if(o == null || !(o instanceof VarData)) return false;
			VarData d = (VarData) o;
			return id == d.id && beginIndex == d.beginIndex && endIndex == d.endIndex && choice == d.choice;
		}
		
		public String toString() {
			return String.valueOf(choice);
		}
	}
	
	public JsonArray getJsonVariableArray() {
		JsonArray varArray = new JsonArray();
		for(VarData variable : variables.values()) {
			int index = variable.id;
			// Expand list until target index is reached, then set variable
			while(varArray.size() <= index) {
				varArray.add(-1);
			}
			
			JsonObject varObject = new JsonObject();
			varObject.add("choice", new JsonPrimitive(variable.choice));
			varObject.add("dropdown", tf.variableData.get(index).variableRoot);
			
			varArray.set(index, varObject);
		}
		
		return varArray;
	}
	
	public JsonObject getJsonOutputObject() {
		JsonArray arr = getJsonVariableArray();
		JsonObject out = new JsonObject();
		out.add("dropdowns", arr);
		out.add("name", new JsonPrimitive(tf.templateName));
		return out;
	}
}
