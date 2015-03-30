package org.tb.logging;

import java.util.Date;

import org.apache.log4j.Logger;

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
		getLogger().debug("DEBUG: [" + className + " ; " + new Date().toString() + "]  -  " + debugString);
	}
	public static void info(String className, String infoString){
		getLogger().info("INFO: [" + className + " ; " + new Date().toString() + "]  -  " + infoString);
	}
	public static void warn(String className, String warnString){
		getLogger().warn("WARN: [" + className + " ; " + new Date().toString() + "]  -  " + warnString);
	}
	public static void error(String className, String errorString){
		getLogger().error("ERROR: [" + className + " ; " + new Date().toString() + "]  -  " + errorString);
	}
	public static void fatal(String className, String fatalString){
		getLogger().fatal("FATAL: [" + className + " ; " + new Date().toString() + "]  -  " + fatalString);
	}
	
	/**
	 * initializes the logger
	 */
	private static void initLogger() {
		logger = Logger.getLogger("SalatLogger");
		
//		String salat_log_url = GlobalConstants.SALAT_LOG;
//		if (SystemUtils.IS_OS_WINDOWS) {
//			salat_log_url = SystemUtils.USER_DIR + "\\salat.log";
//		}
//		
//		try {
//			SimpleLayout layout = new SimpleLayout();
//			FileAppender fileAppender = new FileAppender(layout, salat_log_url, false);
////			ConsoleAppender consoleAppender = new ConsoleAppender(new ANSIColorLayout());
//			ConsoleAppender consoleAppender = new ConsoleAppender(new PatternLayout());
//			
//			logger.addAppender(fileAppender);
//			logger.addAppender(consoleAppender);
//			logger.setLevel(Level.ALL);
//		} catch (Exception ignored) {
//		}
		logger.debug("--------------------------------------------------");
		logger.debug("--------Logging started for SalatLogger-----------");
		logger.debug("--------        (c) HBT GmbH 2007      -----------");
		logger.debug("--------------------------------------------------");
//		logger.debug("Logging in: " + salat_log_url);
//		logger.debug("--------------------------------------------------");
	}

}
