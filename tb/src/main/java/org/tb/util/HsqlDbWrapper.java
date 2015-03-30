package org.tb.util;

import java.io.PrintWriter;

import org.hsqldb.Server;
import org.hsqldb.ServerConstants;

public class HsqlDbWrapper {

	private String path;

	private String name;

	private Server server;

	public void setName(String name) {
		this.name = name;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public void start() {
		server = new Server();
		server.setNoSystemExit(true);
		server.setDatabasePath(0, path);
		server.setDatabaseName(0, name);
		server.setErrWriter(new PrintWriter(System.err));
		server.setLogWriter(new PrintWriter(System.out));
		server.start();
		while (server.getState() != ServerConstants.SERVER_STATE_ONLINE) {
			System.out.println("Starting server. State: " + server.getStateDescriptor());
			System.out.println("Waiting 5 seconds ...");
			try {
				Thread.sleep(5000);
			} catch (InterruptedException ignore) {
			}
		}
	}

	public void stop() {
		server.stop();
		while (server.getState() != ServerConstants.SERVER_STATE_SHUTDOWN) {
			System.out.println("Shutting down server. State: " + server.getStateDescriptor());
			System.out.println("Waiting 5 seconds ...");
			try {
				Thread.sleep(5000);
			} catch (InterruptedException ignore) {
			}
		}
		server = null;
	}
}
