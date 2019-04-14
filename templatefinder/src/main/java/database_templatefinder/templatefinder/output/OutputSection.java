package database_templatefinder.templatefinder.output;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import database_templatefinder.templatefinder.types.TemplateString;

/**
 * A section of output
 */
public class OutputSection {
	/**
	 * A reference to the original input string.
	 */
	public String originalInput;
	public int beginIndex;
	public int endIndex;
	
	public String output;
	public TemplateString outputSource;
	public OutputWeight outputWeight;

	public OutputSection(String originalInput, int beginIndex, int endIndex) {
		this.originalInput = originalInput;
		this.beginIndex = beginIndex;
		this.endIndex = endIndex;
	}
	
	public OutputSection(String originalInput, int beginIndex, int endIndex, String output, TemplateString outputSource, OutputWeight outputWeight) {
		this.originalInput = originalInput;
		this.beginIndex = beginIndex;
		this.endIndex = endIndex;
		this.output = output;
		this.outputSource = outputSource;
		this.outputWeight = outputWeight;
	}
	
	/**
	 * Simple copies the object
	 */
	public OutputSection clone() {
		return new OutputSection(originalInput, beginIndex, endIndex, output, outputSource, outputWeight);
	}
	
	/**
	 * Deep copies the object (will not create new instances of the strings, only the TemplateString outputSource)
	 */
	public OutputSection deepClone() {
		return new OutputSection(originalInput, beginIndex, endIndex, output, outputSource.getDeepCopy(), outputWeight);
	}
	
	/**
	 * Returns the section of the original input that corresponds to this OutputSection
	 * @return
	 */
	public String getInputSection() {
		return originalInput.substring(beginIndex, endIndex);
	}
	
	/**
	 * Get the length of the input section
	 * @return
	 */
	public int getInputSectionLength() {
		return endIndex - 1 - beginIndex;
	}
	
	/**
	 * Returns the output that this object suggests the input section should be replaced by
	 * @return
	 */
	public String getOutput() {
		return output;
	}
	
	/**
	 * Get a general measure of how well the template-generated output substitutes the original input
	 * @return
	 */
	public double getOutputWeight() {
		return outputWeight.doubleValue();
	}
	
	/**
	 * Generates a JSON Object with the relevant information from this object
	 * @return
	 */
	public JsonObject toJson() {
		JsonObject out = new JsonObject();
		out.add("start", new JsonPrimitive(beginIndex));
		out.add("end", new JsonPrimitive(endIndex));
		out.add("suggestion", new JsonPrimitive(output));
		out.add("source", outputSource.getJsonOutputObject());
		out.add("similarityScore", new JsonPrimitive(outputWeight.doubleValue()));
		return out;
	}
}
