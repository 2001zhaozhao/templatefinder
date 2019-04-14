package database_templatefinder.templatefinder.output;

/**
 * A measure of how well the output weight represents the input; stores all information parts in the object.
 */
public class OutputWeight extends Number {
	public double stringWeightInTemplate;
	public double stringWeightAcrossTemplates;
	public double normalizedTemplateWeight;
	public double inTemplateWeightPortion;
	
	public OutputWeight(double stringWeightInTemplate, double stringWeightAcrossTemplates, double normalizedTemplateWeight, double inTemplateWeightPortion) {
		this.stringWeightAcrossTemplates = stringWeightAcrossTemplates;
		this.stringWeightInTemplate = stringWeightInTemplate;
		this.normalizedTemplateWeight = normalizedTemplateWeight;
		this.inTemplateWeightPortion = inTemplateWeightPortion;
	}

	@Override
	public int intValue() {
		return (int) doubleValue();
	}

	@Override
	public long longValue() {
		return (long) doubleValue();
	}

	@Override
	public float floatValue() {
		return (float) doubleValue();
	}
	
	@Override
	public double doubleValue() {
		return getFinalWeight();
	}
	
	private double getFinalWeight() {
		//System.out.println(getTemplateWeightContribution() + " " + getStringWeightInTemplateContribution() + " " + getStringWeightAcrossTemplatesContribution());
		return normalizedTemplateWeight + stringWeightInTemplate * inTemplateWeightPortion + stringWeightAcrossTemplates * (1 - inTemplateWeightPortion);
	}
	
	public double getFinalWeightModifiedByLength(int length) {
		return getTemplateWeightContribution() / length + getStringWeightInTemplateContribution() + getStringWeightAcrossTemplatesContribution();
	}
	
	public double getTemplateWeightContribution() {
		return normalizedTemplateWeight;
	}
	
	public double getStringWeightInTemplateContribution() {
		return stringWeightInTemplate * inTemplateWeightPortion;
	}
	
	public double getStringWeightAcrossTemplatesContribution() {
		return stringWeightAcrossTemplates * (1 - inTemplateWeightPortion);
	}

	private static final long serialVersionUID = 8977879114567829505L;
}
