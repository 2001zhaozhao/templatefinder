package database_templatefinder.templatefinder.types;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The base item that just represents a string
 * @author Jason
 *
 */
public class ItemText implements Item {
	
	String str;
	TemplateFormat tf;
	
	public ItemText(TemplateFormat tf, String str) {
		this.str = str;
		this.tf = tf;
	}
	
	public String getText() {
		return str;
	}

	@Override
	public ArrayList<TemplateString> getPossibilities() {
		ArrayList<TemplateString> out = new ArrayList<>();
		out.add(new TemplateString(tf, str));
		return out;
	}

}
