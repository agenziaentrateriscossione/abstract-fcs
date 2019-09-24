package it.tredi.fcs.test;

import java.io.File;
import java.net.Socket;

import it.tredi.fcs.FcsThread;
import it.tredi.fcs.command.FcaCommandExecutor;

public class DummyFcsThread extends FcsThread {

	public DummyFcsThread(Socket client) {
		super(client);
	}

	@Override
	public FcaCommandExecutor getFcaCommandExecutor(String id, String[] convTo, String additionalParams, File workDir) throws Exception {
		return new DummyFcaCommandExecutor(id, convTo, additionalParams, workDir);
	}

}
