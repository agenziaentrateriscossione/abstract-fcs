package it.tredi.fcs.test;

import java.io.File;

import it.tredi.fcs.command.FcaCommandExecutor;
import it.tredi.fcs.entity.Documento;

public class DummyFcaCommandExecutor extends FcaCommandExecutor {

	private static final int MIN_MILLIS = 10;
	private static final int MAX_MILLIS = 2000;
	
	public DummyFcaCommandExecutor(String docId, String[] convTo, String additionalParams, File workDir) throws Exception {
		super(docId, convTo, additionalParams, workDir);
	}

	@Override
	public boolean saveDocumento(Documento documento) throws Exception {
		//sleep();
		return true;
	}

	@Override
	public Documento getDocumento(String id, File workDir) throws Exception {
		sleep();
		return null;
	}
	
	private void sleep() throws Exception {
		int randomNum = MIN_MILLIS + (int)(Math.random() * MAX_MILLIS);
		Thread.sleep(randomNum);
	}

}
