package database_templatefinder.templatefinder.types;

import java.util.ArrayList;

/**
 * A format in the template. Contains all the template data
 * @author Jason
 *
 */
public class TemplateFormat {
	public String formatName;
	public String templateName;
	public ItemProgression baseProgression;
	public ArrayList<ItemVariable> variableData = new ArrayList<>();
	
	public TemplateFormat(String templateName, String formatName) {
		this.templateName = templateName;
		this.formatName = formatName;
		baseProgression = new ItemProgression();
	}
	
	@Override
	public boolean equals(Object o) {
		return (o instanceof TemplateFormat) && ((TemplateFormat) o).templateName.equals(templateName) && ((TemplateFormat) o).formatName.equals(formatName);
	}
	
	@Override
	public int hashCode() {
		return templateName.hashCode() + (formatName.hashCode() << 16);
	}
	
}
