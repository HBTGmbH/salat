package org.tb.util;

/*
 * Util class to build up a list collection to be used with html:options collection=...
 * in a JSP
 * 
 */
public class OptionItem {
	private String value;
	private String label;
	
	public OptionItem(String v, String l) { value = v; label = l; }
	   public String getValue() { return value; }
	   public String getLabel() { return label; }
}
