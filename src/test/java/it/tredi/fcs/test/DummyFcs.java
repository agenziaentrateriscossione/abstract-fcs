package it.tredi.fcs.test;

import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.tredi.fcs.Fcs;
import it.tredi.fcs.FcsThread;

public class DummyFcs extends Fcs {

	private static final Logger logger = LogManager.getLogger(DummyFcs.class.getName());
	
	public static void main(String[] args) {
		try {
			DummyFcs dummyFcs = new DummyFcs();
			dummyFcs.run();
			
			if (logger.isInfoEnabled())
				logger.info("DummyFcs.main(): shutdown...");
			System.exit(0);
		}
		catch(Exception e) {
			logger.error("DummyFcs.main(): got exception... " + e.getMessage(), e);
			System.exit(1);
		}
	}
	
	public DummyFcs() throws Exception {
		super();
		
		if (logger.isInfoEnabled())
			logger.info("DummyFcs... specific configuration...");
	}

	@Override
	public FcsThread getFcsThread(Socket clientSocket) throws Exception {
		return new DummyFcsThread(clientSocket);
	}

	@Override
	public void onRunException(Exception e) {
		if (logger.isInfoEnabled())
			logger.info("DummyFcs... run exception...");
	}

	@Override
	public void onRunFinally() {
		if (logger.isInfoEnabled())
			logger.info("DummyFcs... run finally...");
	}

	@Override
	public String getAppVersion() {
		return "1.0.0-DUMMY";
	}

	@Override
	public String getAppBuildDate() {
		return "NOW";
	}
	
}
