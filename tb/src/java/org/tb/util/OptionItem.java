package org.tb.util;

/*
 * Util class to build up a list collection to be used with html:options collection=...
 * in a JSP
 * 
 */
public class OptionItem {
	private String value;
	private String label;
	private Integer intValue;
	
	public OptionItem(String v, String l) { 
		value = v; label = l; 
	}
	public OptionItem(Integer i, String l) { 
		intValue = i; label = l; 
	}
	
	public String getValue() { return value == null ? intValue.toString() : value; }
	public String getLabel() { return label; }
	public Integer getIntValue() {return intValue; }
}
