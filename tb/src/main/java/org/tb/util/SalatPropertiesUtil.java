package org.tb.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

import org.apache.commons.lang.SystemUtils;
import org.tb.GlobalConstants;
import org.tb.helper.JiraConnectionOAuthHelper;

public class SalatPropertiesUtil {

	/**
	 * Reads Salat Properties from the given URL.
	 * If the file doesn't exist, a new file is created and filled with data
	 * from /org/tb/props/salat.properties, Salat is then SHUTDOWN in order
	 * to let an administrator configure the salat.properties
	 * @param properties_url - URL of the property file
	 * @return Properties Object
	 */
	public static Properties readSalatProperties() {
		
		String properties_url = null;
		if (SystemUtils.IS_OS_LINUX) {
			properties_url = GlobalConstants.SALAT_PROPERTIES_URL_TUX; 
		} else {
			properties_url = GlobalConstants.SALAT_PROPERTIES_URL_WIN;
		}
		InputStream property = null;
    	File file = null;
    	try {
    		// check if the the external properies file exists and readable
    		new FileInputStream(properties_url);
    	} catch (FileNotFoundException e) {
    		// if the external properties file not found - create a new file and write the properties from the internal file
			property = JiraConnectionOAuthHelper.class.getClassLoader().getResourceAsStream("/org/tb/props/salat.properties");
			file = new File(properties_url);
			// create the directory if itdoesnt exist
			file.getParentFile().mkdirs();
    		try {
    			// create an empty file file
    			file.createNewFile();
    			// copy the properties from one file to another
    			BufferedReader br = new BufferedReader(new InputStreamReader(property));
				BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
				try {
			        String line = null;
			        while (( line = br.readLine()) != null) {
			        	bw.append(line);
			        	bw.newLine();
			        }
			        br.close();
			        bw.close();
				} catch (IOException e2) {
					e2.printStackTrace();
				}
			} catch (IOException e1) {
				e1.printStackTrace();
			} finally {
				System.err.println("Please configure Salat: " + properties_url + ", and start Salat again!");
				System.exit(0);
			}
    	} catch (SecurityException e) {
			e.printStackTrace();
		}
    	
    	return readProps(properties_url);
	}
	
	 private static Properties readProps(String properties_url) {
    	InputStream property = null;
    	Properties prop = new Properties();
    	try {
			property = new BufferedInputStream(new FileInputStream(properties_url));    	
			prop.load(property);
			
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
			throw new ExceptionInInitializerError("There was a problem initializing the properties file: " + e.toString());
		} finally {
			if (property != null) {
				try {
					property.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
    	return prop;
    }
}
