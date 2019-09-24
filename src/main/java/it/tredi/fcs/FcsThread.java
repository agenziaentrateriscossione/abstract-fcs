package it.tredi.fcs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jodconverter.office.OfficeManager;

import it.tredi.fcs.command.FcaCommandExecutor;
import it.tredi.fcs.command.comparison.Compare;
import it.tredi.fcs.command.comparison.CompareResult;
import it.tredi.fcs.command.conversion.Convert;
import it.tredi.fcs.socket.commands.HeaderRequest;
import it.tredi.fcs.socket.commands.HeaderResponse;
import it.tredi.fcs.socket.commands.Protocol;

/**
 * Thread di richiesta di indicizzazione/conversione da parte di un client al socket FCS
 * @author mbernardini
 */
public abstract class FcsThread extends Thread {

	private static final Logger logger = LogManager.getLogger(Fcs.class.getName());

	private DataInputStream dis = null;
	private DataOutputStream dos = null;
	private Socket clientSocket = null;

	/** Manager di connessione OpenOffice (o LibreOffice) **/
	private OfficeManager officeManager;

	public FcsThread(Socket client) {
		this.clientSocket = client;
	}

	/**
	 * Setta il manager di gestione delle conversioni tramite OpenOffice (o LibreOffice)
	 * @param manager
	 */
	public void setOfficeManager(OfficeManager manager) {
		this.officeManager = manager;
	}

	@Override
	public void run() {
		File workDir = null;
		try {
			// inizializza i buffer in entrata e uscita
			dis = new DataInputStream(clientSocket.getInputStream());
			dos = new DataOutputStream(clientSocket.getOutputStream());

			if (logger.isDebugEnabled())
				logger.debug("FcsThread.run(): receiving data from client " + clientSocket.getInetAddress());

			Protocol protocol = new Protocol(dis, dos);

			// resto in attesa di capire la tipologia di richiesta... init o is_alive?
			HeaderRequest initReq = HeaderRequest.getHeaderRequest(protocol.receiveHeader());
			if (logger.isInfoEnabled())
				logger.info("FcsThread.run(): header received from client... " + initReq.header());


			if (initReq == HeaderRequest.INIT_HEADER || initReq == HeaderRequest.ALIVE_HEADER) {
				// inizializzazione del processo

				if (initReq == HeaderRequest.ALIVE_HEADER && FcsConfig.getInstance().getActivationParams() == null) {
					if (logger.isInfoEnabled())
						logger.info("FcsThread.run(): alive header received, but empty activation params...");

					protocol.sendHeader(HeaderResponse.TO_CONFIG_HEADER.bytes()); // parametri di attivazione mancanti, lo si comunica al client

					// recupero parametri di attivazione di FCS...
					String fcsConfigJson = protocol.receiveString();

					HeaderRequest configReq = HeaderRequest.getHeaderRequest(protocol.receiveHeader());
					if (configReq == HeaderRequest.FCS_CONF_HEADER) {
						FcsConfig.getInstance().setActivationParamsFromJson(fcsConfigJson);

						protocol.sendHeader(HeaderResponse.ACK_HEADER.bytes()); // configurazione di FCS completata con successo
					}
					else {
						logger.error("FcsThread.run(): unrecognized configuration header! " + initReq.header());
						protocol.sendHeader(HeaderResponse.ERRORS_HEADER.bytes());
					}
				}
				else {
					protocol.sendHeader(HeaderResponse.ACK_HEADER.bytes()); // invio acknowledge al client
				}

				if (initReq == HeaderRequest.INIT_HEADER) {
					try {
						HeaderRequest commandReq = HeaderRequest.getHeaderRequest(protocol.receiveHeader());
						if (logger.isInfoEnabled())
							logger.info("FcsThread.run(): command header received from client... " + commandReq.header());

						// istanzio la directory di lavoro
						workDir = new File(FcsConfig.getInstance().getFcsWorkingFolder(), String.valueOf(currentThread().getId()));
						if (!workDir.exists()) {
							if (!workDir.mkdirs())
								throw new Exception("Impossible to create work dir: " + workDir.getAbsolutePath());
						}
						logger.info("FcsThread.run(): thread work dir = " + workDir);


						if (commandReq == HeaderRequest.FCA_HEADER) {
							// richiesta di indicizzazione/conversione da parte di FCA

							protocol.sendHeader(HeaderResponse.ACK_HEADER.bytes()); // invio acknowledge al client

							// lettura dei parametri necessari al completamento dell'attivita'
							String docId = protocol.receiveString();
							String[] convTo = {};
							String paramConvTo = protocol.receiveString();
							if (paramConvTo != null && !paramConvTo.isEmpty())
								convTo = paramConvTo.split(",");
							String additionalParams = protocol.receiveString();

							// elaborazione della richiesta ricevuta da FCA
							FcaCommandExecutor fcaCommandExecutor = getFcaCommandExecutor(docId, convTo, additionalParams, workDir);
							fcaCommandExecutor.setOfficeManager(officeManager);

							boolean done = fcaCommandExecutor.processDocumento();
							if (done)
								protocol.sendHeader(HeaderResponse.DONE_HEADER.bytes());
							else
								protocol.sendHeader(HeaderResponse.ERRORS_HEADER.bytes());
						}
						else if (commandReq == HeaderRequest.CONV_HEADER) {
							// conversione di un file verso PDF o altro/i formato/i

							protocol.sendHeader(HeaderResponse.ACK_HEADER.bytes()); // invio acknowledge al client

							ByteArrayOutputStream baos = null;
							try {
								// lettura dei parametri necessari al completamento dell'attivita'

								int fileSize = Integer.parseInt(protocol.receiveString()); // lettura della dimensione del file di input
								baos = new ByteArrayOutputStream();
								protocol.receiveFile(baos, fileSize); // lettura del file da convertire
								byte[] inputFile = baos.toByteArray();
								String fromExt = protocol.receiveString(); // lettura dell'estensione di input
								String toExt = protocol.receiveString(); // lettura dell'estensione di output

								// elaborazione della richiesta di conversione
								byte[] convFile = null;
								boolean done = false;
								try {
									convFile = Convert.convertToByteArray(officeManager, workDir, inputFile, fromExt, toExt);
									done = true;
								}
								catch (Exception e) {
									logger.warn("FcsThread.run(): got execption on conversion command from FcsBridge... " + e.getMessage(), e);
									done = false;
								}

								if (done && convFile != null) {
									protocol.sendHeader(HeaderResponse.DONE_HEADER.bytes());
									// invio del risultato della conversione
									protocol.sendString(String.valueOf(convFile.length)); // invio dimensione del file convertito
									protocol.sendFile(new ByteArrayInputStream(convFile), convFile.length); // invio del contenuto del file convertito
								}
								else {
									protocol.sendHeader(HeaderResponse.ERRORS_HEADER.bytes());
								}
							}
							finally {
								try {
									if (baos != null)
										baos.close();
								}
								catch(Exception e) {
									logger.warn("FcsThread.run(): unable to close OutputStream... " + e.getMessage());
								}
							}
						}
						else if (commandReq == HeaderRequest.DIFF_HEADER) {
							// differenza fra 2 files

							protocol.sendHeader(HeaderResponse.ACK_HEADER.bytes()); // invio acknowledge al client

							ByteArrayOutputStream baos = null;
							try {
								// lettura dei parametri necessari al completamento dell'attivita'

								int fileSize = Integer.parseInt(protocol.receiveString()); // lettura della dimensione del primo file da comparare
								baos = new ByteArrayOutputStream();
								protocol.receiveFile(baos, fileSize); // lettura del primo file da comparare
								byte[] firstFile = baos.toByteArray();
								String firstFileExt = protocol.receiveString(); // lettura dell'estensione del primo file da comparare
								fileSize = Integer.parseInt(protocol.receiveString()); // lettura della dimensione del secondo file da comparare
								baos = new ByteArrayOutputStream();
								protocol.receiveFile(baos, fileSize); // lettura del secondo file da comparare
								byte[] secondFile = baos.toByteArray();
								String secondFileExt = protocol.receiveString(); // lettura dell'estensione del secondo file da comparare

								String outExt = protocol.receiveString(); // lettura dell'estensione del file di output della comparazione (valori possibili 'pdf', 'od')
								boolean outPdf = false;
								if (outExt != null && outExt.toLowerCase().equals("pdf"))
									outPdf = true;

								// elaborazione della richiesta di confronto
								CompareResult result = null;
								try {
									result = Compare.compareToByteArray(officeManager, workDir, firstFile, secondFile, firstFileExt, secondFileExt, outPdf);
								}
								catch (Exception e) {
									logger.warn("FcsThread.run(): got execption on comparison command from FcsBridge... " + e.getMessage(), e);
								}

								if (result != null && result.getContent() != null) {
									protocol.sendHeader(HeaderResponse.DONE_HEADER.bytes());
									// invio del risultato della comparazione
									protocol.sendString(result.getFileExtension()); // invio dell'estensione prodotta
									byte[] content = result.getContent();
									protocol.sendString(String.valueOf(content.length)); // invio dimensione del file di comparazione
									protocol.sendFile(new ByteArrayInputStream(content), content.length); // invio del contenuto del file di comparazione
								}
								else {
									protocol.sendHeader(HeaderResponse.ERRORS_HEADER.bytes());
								}
							}
							finally {
								try {
									if (baos != null)
										baos.close();
								}
								catch(Exception e) {
									logger.warn("FcsThread.run(): unable to close OutputStream... " + e.getMessage());
								}
							}

						}
						else
							throw new Exception("Unable to recognize command type: " + commandReq.header());

					}
					catch (Exception e) {
						logger.error("FcsThread.run(): Action FAILED! Got exception on index/conversion... " + e.getMessage(), e);
						protocol.sendHeader(HeaderResponse.ERRORS_HEADER.bytes());
					}
				}
			}
			else {
				logger.error("FcsThread.run(): unrecognized header! " + initReq.header());
				protocol.sendHeader(HeaderResponse.ERRORS_HEADER.bytes());
			}
		}
		catch (Exception e) {
			logger.error("FcsThread.run(): Action FAILED! Got exception on socket protocol... " + e.getMessage(), e);
		}
		finally {
			// chiusura dei buffer e del socket
			try {
				if (dis != null)
					dis.close();
			}
			catch(Exception e) {
				logger.warn("FcsThread.run(): unable to close InputStream... " + e.getMessage());
			}
			try {
				if (dos != null)
					dos.close();
			}
			catch(Exception e) {
				logger.warn("FcsThread.run(): unable to close OutputStream... " + e.getMessage());
			}
			try {
				if (clientSocket != null)
					clientSocket.close();
			}
			catch(Exception e) {
				logger.warn("FcsThread.run(): unable to close socket connection... " + e.getMessage(), e);
			}

			// cancellazione della directory di lavoro dello specifico thread
			if (workDir != null && workDir.exists()) {
				try {
					FileUtils.deleteDirectory(workDir);
				} catch (IOException e) {
					logger.error("FcsThread.run(): unable to remove work dir... " + e.getMessage(), e);
				}
			}
		}
	}

	/**
	 * Istanzia l'implementazione di FcaCommandExecutor per una specifica applicazione
	 * @param id
	 * @param convTo
	 * @param additionalParams
	 * @param workDir
	 * @return
	 * @throws Exception
	 */
	public abstract FcaCommandExecutor getFcaCommandExecutor(String id, String[] convTo, String additionalParams, File workDir) throws Exception;

}
