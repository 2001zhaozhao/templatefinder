package database_templatefinder.templatefinder.types;

import java.util.ArrayList;

/**
 * A format in the template. Contains all the template data
 * @author Jason
 *
 */
public class TemplateFormat {
	public String formatName;
	public Template template;
	public ItemProgression baseProgression;
	public ArrayList<ItemVariable> variableData = new ArrayList<>();
	
	public TemplateFormat(Template template, String formatName) {
		this.template = template;
		this.formatName = formatName;
		baseProgression = new ItemProgression();
	}
	
	/**
	 * Convenience method that returns the name of the parent template.
	 * @return
	 */
	public String getTemplateName() {
		return template.getName();
	}
	
	@Override
	public boolean equals(Object o) {
		return (o instanceof TemplateFormat) && ((TemplateFormat) o).template.equals(template) && ((TemplateFormat) o).formatName.equals(formatName);
	}
	
	@Override
	public int hashCode() {
		return template.templateName.hashCode() + (formatName.hashCode() << 16);
	}
	
}
