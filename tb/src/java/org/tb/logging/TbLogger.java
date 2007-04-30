package org.tb.logging;

import java.util.Date;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;

/**
 * Use this class for logging by calling the static methode debug.
 * Correct call is e.g.: 
 * 		TbLogger.debug(<classname>.class.toString, "Logging information!");
 * 
 * @author ts
 */
public class TbLogger {

	private static Logger logger;
	private static boolean isInitialized = false;
	
	/**
	 * returns the logger; is static for easy access without creating an object of the class
	 * @return
	 */
	private static Logger getLogger(){
		if (!isInitialized){
			initLogger();
			isInitialized = true;
		}
		return logger;
	}
	
	public static void debug(String className, String debugString){
		Date date = new Date();
		getLogger().debug("[" + className + " ; " + date.toString() + "]  -  " + debugString);
	}
	
	/**
	 * initializes the logger
	 */
	private static void initLogger(){
		logger = Logger.getLogger("SalatLogger");
		try {
		     SimpleLayout layout = new SimpleLayout();
		     FileAppender fileAppender = new FileAppender( layout, "SalatLog.log", false );
		     logger.addAppender( fileAppender );
		     logger.setLevel( Level.ALL );
		   } catch( Exception ex ) {}
		   logger.debug( "--------------------------------------------------" );
		   logger.debug( "--------Logging started for SalatLogger-----------" );
		   logger.debug( "--------        (c) HBT GmbH 2007      -----------" );
		   logger.debug( "--------------------------------------------------" );
	}
	
}
