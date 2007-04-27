package org.tb.logging;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;

/**
 * Use this class for logging by calling the static methode getLogger.
 * Correct call is e.g.: 
 * 		TbLogger.getLogger.debug("Logging information!");
 * 
 * @author ts
 */
public class TbLogger {

	private static Logger logger;
	private static boolean isInitialized = false;
	
	
	public static Logger getLogger(){
		if (!isInitialized){
			initLogger();
			isInitialized = true;
		}
		return logger;
	}
	
	private static void initLogger(){
		logger = Logger.getRootLogger();
		try {
		     SimpleLayout layout = new SimpleLayout();
		     FileAppender fileAppender = new FileAppender( layout, "MeineLogDatei.log", false );
		     logger.addAppender( fileAppender );
		     logger.setLevel( Level.ALL );
		   } catch( Exception ex ) {}
		   logger.debug( "--------------------------------------------------" );
		   logger.debug( "----------------Logging started-------------------" );
		   logger.debug( "--------------------------------------------------" );
	}
	
}
