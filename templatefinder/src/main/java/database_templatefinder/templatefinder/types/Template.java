package database_templatefinder.templatefinder.types;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

/**
 * A template with different formats. Currently it is just a HashMap of the formats by the format name
 * @author Jason
 *
 */
public class Template extends HashMap<String, TemplateFormat> {
	
	String templateName;
	
	/**
	 * Create an empty template
	 */
	public Template(String templateName) {
		super();
		this.templateName = templateName;
	}
	
	/**
	 * Create a template from an initial list of formats
	 * @param items
	 */
	public Template(String templateName, Collection<TemplateFormat> items) {
		super();
		this.templateName = templateName;
		addFormats(items);
	}
	
	/**
	 * Gets the name of this template
	 */
	public String getName() {
		return templateName;
	}
	
	/**
	 * Add a format to this template
	 */
	public void addFormat(TemplateFormat format) {
		this.put(format.formatName, format);
	}
	
	/**
	 * Adds a list of formats to this template
	 */
	public void addFormats(Collection<? extends TemplateFormat> formats) {
		for(TemplateFormat format : formats) {
			addFormat(format);
		}
	}
	
	public TemplateSet getFormats() {
		return new TemplateSet(this);
	}
	
	public int hashCode() {
		return templateName.hashCode();
	}
	
	public boolean equals(Object o) {
		if(!(o instanceof Template)) return false;
		return templateName.equals(((Template) o).templateName);
	}
	
	private static final long serialVersionUID = 3977739026399250576L;
	
	/**
	 * A fully-implemented set to manipulate formats in this template
	 * @author Jason
	 *
	 * @param <T>
	 */
	public static class TemplateSet implements Set<TemplateFormat> {
		
		Template template;
		
		/**
		 * Create a new TemplateSet from a Template.
		 * @param template
		 */
		public TemplateSet(Template template) {
			this.template = template;
		}

		public boolean add(TemplateFormat e) {
			template.addFormat(e);
			return true;
		}

		public boolean addAll(Collection<? extends TemplateFormat> c) {
			template.addFormats(c);
			return true;
		}

		public void clear() {
			template.clear();
		}

		public boolean contains(Object o) {
			return template.containsValue(o);
		}

		public boolean containsAll(Collection<?> c) {
			for(Object obj : c) {
				if(!template.containsValue(obj)) {
					return false;
				}
			}
			return true;
		}

		public boolean isEmpty() {
			return template.isEmpty();
		}
		
		/**
		 * Just returns template.values().iterator(). Has the same behavior of a HashMap iterator.
		 */
		public Iterator<TemplateFormat> iterator() {
			return template.values().iterator();
		}

		public boolean remove(Object o) {
			return template.values().remove(o);
		}

		public boolean removeAll(Collection<?> c) {
			return template.values().removeAll(c);
		}

		public boolean retainAll(Collection<?> c) {
			return template.values().retainAll(c);
		}

		public int size() {
			return template.size();
		}

		public Object[] toArray() {
			return toArray(new TemplateFormat[size()]);
		}

		/**
		 * https://stackoverflow.com/questions/4010924/java-how-to-implement-toarray-for-collection
		 */
		@SuppressWarnings("unchecked")
		public <T> T[] toArray(T[] array) { 
		    int size = size();
		    if (array.length < size) { 
		        // If array is too small, allocate the new one with the same component type
		        array = (T[]) Array.newInstance(array.getClass().getComponentType(), size);
		    } else if (array.length > size) {
		        // If array is to large, set the first unassigned element to null
		        array[size] = null;
		    }

		    int i = 0;
		    for (TemplateFormat e: this) {
		        // No need for checked cast - ArrayStoreException will be thrown 
		        // if types are incompatible, just as required
		        array[i] = (T) e;
		        i++;
		    }
		    return array;
		} 
		
	}

}
