package org.tb.web.util;

public class OvertimeString {

	public static String overtimeToString(int overtime) {
		StringBuilder sb = new StringBuilder();
		if(overtime < 0) {
			sb.append('-');
			overtime *= -1; 
		}
		int hours = overtime / 60;
		sb.append(hours).append(':');
		int minutes = overtime % 60;
		if(minutes < 10) {
			sb.append('0');
		}
		sb.append(minutes);
		
		return sb.toString();
	}
}
