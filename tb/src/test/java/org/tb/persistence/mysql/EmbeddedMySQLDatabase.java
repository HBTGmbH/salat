package org.tb.persistence.mysql;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.google.common.io.Files;
import com.mysql.management.MysqldResource;
import com.mysql.management.MysqldResourceI;

public class EmbeddedMySQLDatabase extends DriverManagerDataSource {
	
	private final static Logger LOG = LoggerFactory.getLogger(EmbeddedMySQLDatabase.class); 

	private MysqldResource resource;
	private File baseDir;
	private ResourceDatabasePopulator populator;
	private ResourceLoader loader;
	private int port;
	private String dbName; 
	private String script;
	
	public EmbeddedMySQLDatabase() throws IOException {
		this.baseDir = Files.createTempDir();
		this.port = 3306 + 1;
		this.dbName = "salatunittests";
		this.resource = createResource(this.baseDir, port, dbName);
		createDatabase(this.resource, this, port, dbName);
		this.populator = new ResourceDatabasePopulator();
		this.loader = new DefaultResourceLoader();
	}
	
	public void setScript(String script) {
		this.script = script;
	}
	
	private static MysqldResource createResource(File baseDir, int port, String dbName) {
		Map<String, String> options = new HashMap<>();
		options.put(MysqldResourceI.PORT, Integer.toString(port));
		options.put(MysqldResourceI.KILL_DELAY, "5000");
		
		MysqldResource resource = new MysqldResource(new File(baseDir, dbName));
		resource.start("embedded-mysqld-thread-" + System.currentTimeMillis(), options);

        if (!resource.isRunning()) {
            throw new RuntimeException("MySQL did not start.");
        }

        LOG.info("MySQL started successfully @ {}", System.currentTimeMillis());
        
        return resource;
	}
	
	private static void createDatabase(MysqldResource resource, EmbeddedMySQLDatabase database, int port, String dbName) {
		if(!resource.isRunning()) {
			LOG.error("MySQL instance is not running!");
			throw new RuntimeException("MySQL instance is not running!");
		}
		database.setDriverClassName("com.mysql.jdbc.Driver");
		database.setUsername("root");
		database.setPassword("");
		
		String url = "jdbc:mysql://localhost:" + port + "/" + dbName + "?" + "createDatabaseIfNotExist=true&sessionVariables=FOREIGN_KEY_CHECKS=0";
		database.setUrl(url);
	}
	
	private void populateScript() {
		try {
			DatabasePopulatorUtils.execute(populator, this);
		} catch(Exception e) {
			LOG.error(e.getMessage(), e);
			shutdown();
		}
	}
	
	private void addScript(String location) {
		populator.addScript(loader.getResource(location));
	}
	
	public void init() {
		addScript(this.script);
		populateScript();
	}
	
	public void shutdown() {
		if(resource != null) {
			resource.shutdown();
			if(resource.isRunning()) {
				LOG.info("deleting MySQL base dir [{}]", resource.getBaseDir());
				try {
					FileUtils.forceDelete(resource.getBaseDir());
				} catch(IOException e) {
					LOG.error(e.getMessage(), e);
				}
			}
		}
	}
}
