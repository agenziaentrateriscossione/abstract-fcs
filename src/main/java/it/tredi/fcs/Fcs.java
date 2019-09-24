package it.tredi.fcs;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.AccessControlException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jodconverter.office.DefaultOfficeManagerBuilder;
import org.jodconverter.office.OfficeException;
import org.jodconverter.office.OfficeManager;

/**
 * Servizio FCS di indicizzazione/conversione di documenti
 * @author mbernardini
 */
public abstract class Fcs {

	private static final Logger logger = LogManager.getLogger(Fcs.class.getName());

	public static final String FCS_ARTIFACTID = "abstract-fcs";
	public static final String FCS_GROUPID = "it.tredi";

	private ServerSocket serverSocket = null; // server Socket

	/** Shutdown hook thread instance **/
	private FcsShutdownHook shutdownHook;

	/** Manager di connessione OpenOffice (o LibreOffice) **/
	private OfficeManager officeManager;

	/**
	 * Costruttore
	 * @throws Exception
	 */
	public Fcs() throws Exception {

		if (logger.isInfoEnabled()) {
			logger.info(" _____    ____   ____  ");
			logger.info("|  ___|  / ___| / ___| ");
			logger.info("| |_    | |     \\___ \\ ");
			logger.info("|  _|   | |___   ___) |");
			logger.info("|_|      \\____| |____/ ");
			logger.info("FCS version: " + getAppVersion() + " " + getAppBuildDate());
		}

		// caricamento della configurazione di FCS
		FcsConfig.getInstance();
	}
	
	/**
	 * Ritorna la versione dell'applicazione
	 * @return
	 */
	public abstract String getAppVersion();
	
	/**
	 * Ritorna la data di rilascio dell'applicazione
	 * @return
	 */
	public abstract String getAppBuildDate();

	/**
	 * Avvio del server Socket (attesa di richieste di indicizzazione/conversione)
	 * @param config
	 */
	public void run() throws Exception {
		try {
			serverSocket = new ServerSocket(FcsConfig.getInstance().getFcsPort());
		}
		catch (IOException e) {
			logger.error("Fcs.run(): got exception creating server socket... " + e.getMessage(), e);
			return; // ERROR_EXIT_STANDARD_EXC;
        }

		this.shutdownHook = new FcsShutdownHook();
		try {
			Runtime.getRuntime().addShutdownHook(shutdownHook);
			if (logger.isInfoEnabled())
				logger.info("Fcs.run(): FcsShutdownHook registered!");
		}
		catch (AccessControlException e) {
			logger.error("Fcs.run(): could not register shutdown hook... " + e.getMessage(), e);
		}

		try {
			startOpenOfficeManager();

			if (logger.isInfoEnabled())
				logger.info("Fcs.run(): server listening to the port " + serverSocket.getLocalPort());

			while(true) {
				try {
					// mi preparo ad accettare una connessione
					Socket clientSocket = serverSocket.accept();

					FcsThread clientThread = getFcsThread(clientSocket);
					clientThread.setOfficeManager(officeManager);
					clientThread.start();
				}
				catch (Exception e1) {
					logger.error("Fcs.run(): got exception... " + e1.getMessage(), e1);
				}
			}
		}
		catch (Exception e) {
			onRunException(e);
			throw e;
		}
		finally {
			stopOpenOfficeManager();
			onRunFinally();

			if (shutdownHook != null) {
				try {
					Runtime.getRuntime().removeShutdownHook(shutdownHook);
				} catch (IllegalStateException e) {
					// May fail if the JVM is already shutting down
					logger.warn("Fcs.run(): FcsShutdown already shutting down! " + e.getMessage());
				}
				this.shutdownHook = null;
			}
		}
	}

	/**
	 * Metodo chiamato per arrestare il servizio
	 */
	public static void stop(String[] args) {
		logger.info("Fcs.stop(): exit method now call System.exit(0)");
		System.exit(0);
	}

	/**
	 * Eventuali azioni da compiere in caso di eccezione su RUN di FCS
	 * @param e
	 */
	public abstract void onRunException(Exception e);

	/**
	 * Eventuali azioni da compiere su finally del RUN di FCS (chiusura del servizio)
	 */
	public abstract void onRunFinally();

	/**
	 * Avvio del manager di OpenOffice (o LibreOffice). Identificazione della home directory e delle porte sulle quali comunicare. Viene settato
	 * l'eventuale tempo limite di attesa prima di killare il processo di conversione.
	 * @throws Exception
	 */
	private void startOpenOfficeManager() throws Exception {
		DefaultOfficeManagerBuilder officeManagerBuild = new DefaultOfficeManagerBuilder();

		// Evenuale path alla home directory di OpenOffice (o LibreOffice)
		if (FcsConfig.getInstance().getFcsConversionDocOpenOfficeHomeDir() != null && !FcsConfig.getInstance().getFcsConversionDocOpenOfficeHomeDir().isEmpty())
			officeManagerBuild.setOfficeHome(FcsConfig.getInstance().getFcsConversionDocOpenOfficeHomeDir());

		if (FcsConfig.getInstance().getFcsConversionDocOpenOfficePorts() != null && FcsConfig.getInstance().getFcsConversionDocOpenOfficePorts().length > 0)
			officeManagerBuild.setPortNumbers(FcsConfig.getInstance().getFcsConversionDocOpenOfficePorts());
		if (FcsConfig.getInstance().getFcsConversionTimout() > 0)
			officeManagerBuild.setTaskExecutionTimeout(FcsConfig.getInstance().getFcsConversionTimout());

		this.officeManager = officeManagerBuild.build();
		this.officeManager.start();

		if (logger.isInfoEnabled())
			logger.info("Fcs.startOpenOfficeManager(): OfficeManager started!");
	}

	/**
	 * Stop del manager di OpenOffice
	 */
	private void stopOpenOfficeManager() {
		try {
			if (officeManager != null) {
				officeManager.stop();

				if (logger.isInfoEnabled())
					logger.info("Fcs.stopOpenOfficeManager(): OfficeManager stopped!");
			}
		}
		catch (OfficeException e) {
			logger.error("Fcs.stopOpenOfficeManager(): got exception on OpenOffice closure... " + e.getMessage(), e);
		}
	}

	/**
	 * Istanzia l'FcsThread per la specifica connessione Socket. Estensione della classe astratta FcsThread che richiede l'implementazione
	 * dei meccanismi di caricamento e salvataggio del documento per i quali e' richiesta l'indicizzazione/conversione
	 * @param clientSocket
	 * @return
	 * @throws Exception
	 */
	public abstract FcsThread getFcsThread(Socket clientSocket) throws Exception;

	/**
	 * Called on shutdown. This gives use a chance to store the keys and to optimize even if the cache manager's shutdown method was not called
	 * manually.
	 */
	class FcsShutdownHook extends Thread {

		/**
		 * This will persist the keys on shutdown.
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			if (logger.isInfoEnabled())
				logger.info("FcsShutdownHook hook ACTIVATED. Shutdown was not called. CALL onRunFinally().");
			try {
				stopOpenOfficeManager();
				onRunFinally();
			}
			catch (Exception e) {
				logger.error("FcsShutdownHook: got exception on fcs closure... " + e.getMessage(), e);
			}
		}
	}

}
