package database_templatefinder.templatefinder.types;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import database_templatefinder.templatefinder.types.TemplateString.VarData;

/**
 * An item that outputs the combination of the list of children chained together
 * @author Jason
 *
 */
public class ItemProgression implements Item {
	ArrayList<Item> content;

	public ItemProgression(ArrayList<Item> content) {
		this.content = content;
	}
	
	public ItemProgression(Collection<Item> content) {
		this.content = new ArrayList<Item>(content);
	}
	
	public ItemProgression() {
		this.content = new ArrayList<Item>();
	}

	@Override
	public ArrayList<TemplateString> getPossibilities() {
		ArrayList<TemplateString> out = new ArrayList<>();
		
		for(Item item : content) {
			ArrayList<TemplateString> childPossibilities = item.getPossibilities();
			//If the output currently has nothing, just initialize it with the results in the first child
			if(out.isEmpty()) {
				out.addAll(childPossibilities);
				continue;
			}
			
			// Otherwise multiply each result in output with each result in child item
			ArrayList<TemplateString> newOut = new ArrayList<>();
			for(TemplateString possibility : out) {
				for(TemplateString childPossibility : childPossibilities) {
					// Deep copy the child possibilities to change variable index
					TemplateString childPossibilityCopy = childPossibility.getDeepCopy();
					// Modify variable index
					int currentLength = possibility.string.length();
					for(VarData var : childPossibilityCopy.variables.values()) {
						var.beginIndex += currentLength;
						var.endIndex += currentLength;
					}
					
					// Add all variables that currently exist in the base sentence we're adding the child to
					childPossibilityCopy.variables.putAll(possibility.variables);
					// Concatenate the string values
					childPossibilityCopy.string = possibility.string + childPossibilityCopy.string;
					
					newOut.add(childPossibilityCopy);
				}
			}
			// Now we have the new list, replace current one with it so we can continue multiplying out
			out = newOut;
			
		}
		return out;
	}

	public ArrayList<Item> getContent() {
		return content;
	}

}
