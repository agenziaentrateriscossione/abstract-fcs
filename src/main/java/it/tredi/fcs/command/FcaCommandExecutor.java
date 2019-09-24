package it.tredi.fcs.command;

import java.io.File;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.metadata.Property;
import org.jodconverter.office.OfficeManager;

import it.tredi.fcs.Fcs;
import it.tredi.fcs.FcsConfig;
import it.tredi.fcs.command.conversion.Convert;
import it.tredi.fcs.entity.ConversionTo;
import it.tredi.fcs.entity.Documento;
import it.tredi.fcs.entity.FileActionState;
import it.tredi.fcs.entity.FileToWork;
import it.tredi.fcs.entity.Metadata;
import it.tredi.textextractor.TextExtractor;

/**
 * Elaborazione di un comando inviato da FCA (indicizzazione ed eventuale conversione di tutti i file allegati ad un documento)
 * @author mbernardini
 */
public abstract class FcaCommandExecutor {
	private static final Logger logger = LogManager.getLogger(Fcs.class.getName());

	// TODO dovrebbe diventare un thread che riceve un Interrupt da FcsThread se il tempo di esecuzione supera il timeout impostato. In caso di interrupt bisogna salvare il documento con l'indicazione del fallimento.

	/** Manager di connessione OpenOffice (o LibreOffice) **/
	private OfficeManager officeManager;

	private String docId;
	private String[] convTo;
	private String additionalParameters;
	private File workDir;

	/**
	 * Costruttore. Viene richiesto il caricamento del documento.
	 * @param docId
	 * @param convTo
	 * @throws Exception
	 */
	public FcaCommandExecutor(String docId, String[] convTo, String additionalParams, File workDir) throws Exception {
		this.docId = docId;
		this.convTo = convTo;
		this.additionalParameters = additionalParams;
		this.workDir = workDir;
	}

	/**
	 * Setta il manager di gestione delle conversioni tramite OpenOffice (o LibreOffice)
	 * @param manager
	 */
	public void setOfficeManager(OfficeManager manager) {
		this.officeManager = manager;
	}

	/**
	 * Elaborazione del documento (indicizzazione degli allegati ed eventuali conversioni). Viene lanciata la procedura di aggiornamento (salvataggio) del documento.
	 * @return true in caso di elaborazione terminata con successo, false altrimento
	 * @throws Exception
	 */
	public boolean processDocumento() throws Exception {
		boolean done = false;

		long start = System.currentTimeMillis();

		Documento documento = getDocumento(docId, workDir);
		if (documento != null) {
			for(FileToWork fileToWork : documento.getFilesToWork()) {
				// Estrazione del testo (e di eventuali metadati) dal file
				indexFile(fileToWork);

				// Conversione del file in base alle specifiche inviate
				convertFile(fileToWork);
			}
			done = saveDocumento(documento);
		}

		if (logger.isInfoEnabled())
			logger.info("FcaCommandExecutor.processDocumento(): text extraction and conversions tooks " + (System.currentTimeMillis()-start) + " millis. on document " + docId);

		return done;
	}

	/**
	 * Conversione del file
	 * @param fileToWork
	 * @throws Exception
	 */
	private void convertFile(FileToWork fileToWork) throws Exception {
		boolean convEnabled = true;

		// Verifico che le conversioni dei file siano abilitate sul sistema
		if (!FcsConfig.getInstance().getActivationParams().isConvertEnabled()) {
			if (logger.isDebugEnabled())
				logger.debug("FcaCommandExecutor.convertFile(): File: " + fileToWork.getFileName() + " not converted because isConvertEnabled = false.");
			convEnabled = false;
		}
		else {
			// Verifico che la dimensione del file non sia superiore all'eventuale limite massimo impostato
			if (FcsConfig.getInstance().getActivationParams().getConvertMaxFileSize() > 0 && fileToWork.getInputFile().length() > FcsConfig.getInstance().getActivationParams().getConvertMaxFileSize()) {
				if (logger.isDebugEnabled())
					logger.debug("FcaCommandExecutor.convertFile(): File: " + fileToWork.getFileName() + " not converted because file size ( = " + fileToWork.getInputFile().length() + " bytes) greater than ConvertMaxFileSize (=" + FcsConfig.getInstance().getActivationParams().getConvertMaxFileSize() + ")");
				convEnabled = false;
			}

			// Verifico che l'estensione del file sia fra quelle supportate
			String ext = getFileExtension(fileToWork.getFileName());
			if (convEnabled && !FcsConfig.getInstance().getActivationParams().checkConvertFileExtensionValid(ext)) {
				if (logger.isDebugEnabled())
					logger.debug("FcaCommandExecutor.convertFile(): File: " + fileToWork.getFileName() + " not converted because extension " + ext + " not included on valid extensions");
				convEnabled = false;
			}

			if (convEnabled) {
				// Ciclo su tutte le conversioni richieste per il file corrente e che risultato effettivamente da eseguire (non ignorate)
				List<ConversionTo> conversionsTo = fileToWork.getTodoConversionsTo();
				if (conversionsTo != null && !conversionsTo.isEmpty()) {
					for (ConversionTo convTo : conversionsTo) {

						if (convTo.getState() == FileActionState.TODO) {
							// Procedura di conversione del file

							String extTo = convTo.getExtension();
							if (extTo != null) {
								try {
									File outFile = null;
									if (logger.isInfoEnabled())
										logger.info("FcaCommandExecutor.convertFile(): convert " + fileToWork.getFileName() + " to " + extTo.toUpperCase());

									if (!ext.toLowerCase().equals(extTo.toLowerCase())) {

										if (extTo.toLowerCase().equals("pdf")) { // conversione in PDF
											outFile = Convert.convert(officeManager, workDir, fileToWork.getInputFile(), ext, extTo);
										}
										// TODO Gestire eventuali altre tipologie di conversione, per il momento gestiamo solo la destinazione PDF

										if (logger.isInfoEnabled())
											logger.info("FcaCommandExecutor.convertFile(): conversion " + (outFile != null ? "DONE -> " + outFile.getAbsolutePath() : "FAIL"));
										convTo.setConversionComplete(outFile);
									}
								}
								catch (Exception e) {
									logger.error("FcaCommandExecutor.convertFile(): got exception... " + e.getMessage(), e);
								}
							}

							if (convTo.getState() == FileActionState.TODO) // Significa che tutte le conversioni previste per il file sono fallite
								convTo.setState(FileActionState.FAIL);
						}
					}
				}
			}
		}

		if (!convEnabled) // File da non convertire (imposto l'IGNORE)
			fileToWork.setAllConversionIgnore();
	}

	/**
	 * Estrazione del testo (ed eventualmente dei metadati) dal file passato
	 * @param fileToWork File da processare
	 * @throws Exception
	 */
	private void indexFile(FileToWork fileToWork) throws Exception {
		boolean indexEnabled = true;

		// Verifico che l'estrazione del testo (indicizzazione) dai file sia abilitata sul sistema
		if (!FcsConfig.getInstance().getActivationParams().isIndexEnabled()) {
			if (logger.isDebugEnabled())
				logger.debug("FcaCommandExecutor.indexFile(): File: " + fileToWork.getFileName() + " not indexed because isIndexEnabled = false.");
			indexEnabled = false;
		}
		else {
			org.apache.tika.metadata.Metadata tikaMetadata = new org.apache.tika.metadata.Metadata();

			// Verifico che la dimensione del file non sia superiore all'eventuale limite massimo impostato
			if (FcsConfig.getInstance().getActivationParams().getIndexMaxFileSize() > 0 && fileToWork.getInputFile().length() > FcsConfig.getInstance().getActivationParams().getIndexMaxFileSize()) {
				if (logger.isDebugEnabled())
					logger.debug("FcaCommandExecutor.indexFile(): File: " + fileToWork.getFileName() + " not indexed because file size ( = " + fileToWork.getInputFile().length() + " bytes) greater than IndexMaxFileSize (=" + FcsConfig.getInstance().getActivationParams().getIndexMaxFileSize() + ")");
				indexEnabled = false;
			}

			// Verifico che l'estensione del file sia fra quelle supportate
			String ext = getFileExtension(fileToWork.getFileName());
			if (indexEnabled && !FcsConfig.getInstance().getActivationParams().checkIndexFileExtensionValid(ext)) {
				if (logger.isDebugEnabled())
					logger.debug("FcaCommandExecutor.indexFile(): File: " + fileToWork.getFileName() + " not indexed because extension " + ext + " not included on valid extensions");
				indexEnabled = false;
			}

			if (indexEnabled) {
				if (fileToWork.getIndex() == FileActionState.TODO) {
					// Procedura di estrazione testo (ed eventuali metadati) dal file

					if (checkOcrDisabledForFileExtension(ext)) {
						if (fileToWork.getMeta() == FileActionState.TODO) {
							// Estrazione testo e meta ma OCR disabilitato per il file passato quindi solo meta
							try {
								if (logger.isInfoEnabled())
									logger.info("FcaCommandExecutor.indexFile(): OCR disabled for extension " + ext + " parse only metadata on file " + fileToWork.getFileName());

								long start = System.currentTimeMillis();
								tikaMetadata = TextExtractor.parseMetadata(fileToWork.getInputFile());
								fileToWork.setOutMetadata(metadataFromTikaMetadata(ext, fileToWork.getInputFile().length(), tikaMetadata));
								fileToWork.setIndexIgnore();

								if (logger.isDebugEnabled())
									logger.debug("FcaCommandExecutor.indexFile(): File: " + fileToWork.getFileName() + " OCR disabled meta extracted.");

								if (logger.isInfoEnabled())
									logger.info("FcaCommandExecutor.indexFile(): OCR disabled metadata extractor tooks " + (System.currentTimeMillis()-start) + " millis.");
							}
							catch (Throwable t) {
								logger.error("FcaCommandExecutor.indexFile(): Exception on parse index and meta of file " + fileToWork.getFileName() + "... " + t.getMessage(), t);
								fileToWork.setMetadataFailed();
							}

						}
						else {
							// Estrazione solo testo ma OCR disabilitato per il file passato quindi non si deve estrarre nulla
							if (logger.isInfoEnabled())
								logger.info("FcaCommandExecutor.indexFile(): OCR disabled for extension " + ext + " no parse on file " + fileToWork.getFileName());
							fileToWork.setIndexIgnore();
						}
					}
					else {
						if (fileToWork.getMeta() == FileActionState.TODO) {
							// Estrazione testo e meta
							try {
								if (logger.isDebugEnabled())
									logger.debug("FcaCommandExecutor.indexFile(): parse text and metadata on file " + fileToWork.getFileName());
								long start = System.currentTimeMillis();

								// Devo parsare il file
								tikaMetadata = new org.apache.tika.metadata.Metadata();
								fileToWork.setOutFileText(TextExtractor.parse(fileToWork.getInputFile(), tikaMetadata, FcsConfig.getInstance().getActivationParams().getIndexMaxChars()));
								fileToWork.setOutMetadata(metadataFromTikaMetadata(ext, fileToWork.getInputFile().length(), tikaMetadata));

								if (logger.isDebugEnabled())
									logger.debug("FcaCommandExecutor.indexFile(): File: " + fileToWork.getFileName() + " index and meta extracted.");

								if (logger.isInfoEnabled())
									logger.info("FcaCommandExecutor.indexFile(): text and metadata extractor tooks " + (System.currentTimeMillis()-start) + " millis.");
							}
							catch (Throwable t) {
								logger.error("FcaCommandExecutor.indexFile(): Exception on parse index and meta of file " + fileToWork.getFileName() + "... " + t.getMessage(), t);
								fileToWork.setIndexFailed();
								fileToWork.setMetadataFailed();
							}
						}
						else {
							// Estrazione solo testo
							try {
								if (logger.isInfoEnabled())
									logger.info("FcaCommandExecutor.indexFile(): parse only text on file " + fileToWork.getFileName());
								long start = System.currentTimeMillis();

								// Devo parsare il file
								tikaMetadata = new org.apache.tika.metadata.Metadata();
								fileToWork.setOutFileText(TextExtractor.parse(fileToWork.getInputFile(), tikaMetadata, FcsConfig.getInstance().getActivationParams().getIndexMaxChars()));

								if (logger.isDebugEnabled())
									logger.debug("FcaCommandExecutor.indexFile(): File: " + fileToWork.getFileName() + " index extracted.");

								if (logger.isInfoEnabled())
									logger.info("FcaCommandExecutor.indexFile(): text extractor tooks " + (System.currentTimeMillis()-start) + " millis.");
							}
							catch (Throwable t) {
								logger.error("FcaCommandExecutor.indexFile(): FcaCommandExecutor.processDocumento(): Exception on parse index of file " + fileToWork.getFileName() + "... " + t.getMessage(), t);
								fileToWork.setIndexFailed();
							}
						}
					}
				}
				else {
					if (fileToWork.getMeta() == FileActionState.TODO) {
						// Estrazione solo meta
						try {
							if (logger.isInfoEnabled())
								logger.info("FcaCommandExecutor.indexFile(): parse only metadata on file " + fileToWork.getFileName());

							long start = System.currentTimeMillis();
							tikaMetadata = TextExtractor.parseMetadata(fileToWork.getInputFile());
							fileToWork.setOutMetadata(metadataFromTikaMetadata(ext, fileToWork.getInputFile().length(), tikaMetadata));

							if (logger.isDebugEnabled())
								logger.debug("FcaCommandExecutor.indexFile(): File: " + fileToWork.getFileName() + " meta extracted.");

							if (logger.isInfoEnabled())
								logger.info("FcaCommandExecutor.indexFile(): metadata extractor tooks " + (System.currentTimeMillis()-start) + " millis.");
						}
						catch (Throwable t) {
							logger.error("FcaCommandExecutor.indexFile(): Exception on parse meta of file " + fileToWork.getFileName() + "... " + t.getMessage(), t);
							fileToWork.setMetadataFailed();
						}
					}
				}
			}
		}

		if (!indexEnabled) { // File da non indicizzare
			fileToWork.setIndexIgnore();
			fileToWork.setMetadataIgnore();
		}
	}

	private boolean checkOcrDisabledForFileExtension(String ext) {
		try {
			if (!FcsConfig.getInstance().getActivationParams().isOcrEnabled() && FcsConfig.getInstance().getActivationParams().getOcrFileTypesExclude().contains(ext.toLowerCase())) {
				return true;
			}
		}
		catch (Exception e) {
			logger.error("FcaCommandExecutor.checkOcrDisabledForFileExteension(): Exception on check if extension: " + ext + " is OCR disabled", e);
		}
		return false;
	}

	@SuppressWarnings("deprecation")
	private Metadata metadataFromTikaMetadata(String extension, long size, org.apache.tika.metadata.Metadata tikaMetadata) {
		Metadata metadata = null;
		if (tikaMetadata != null) {
			metadata = new Metadata(extension, size);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.CONTRIBUTOR);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.COVERAGE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.CREATOR);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.DESCRIPTION);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.FORMAT);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.IDENTIFIER);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.LANGUAGE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MODIFIED);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.NAMESPACE_PREFIX_DELIMITER);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.PUBLISHER);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.RELATION);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.RIGHTS);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.SOURCE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.SUBJECT);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.TITLE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.TYPE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.ACKNOWLEDGEMENT);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.APPLICATION_NAME);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.APPLICATION_VERSION);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.AUTHOR);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.CATEGORY);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.COMMAND_LINE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.COMMENT);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.COMMENTS);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.COMPANY);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.CONTACT);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.CONTENT_DISPOSITION);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.CONTENT_ENCODING);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.CONTENT_LANGUAGE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.CONTENT_LENGTH);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.CONTENT_LOCATION);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.CONTENT_MD5);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.CONTENT_STATUS);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.CONTENT_TYPE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.CONVENTIONS);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.CHARACTER_COUNT);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.CHARACTER_COUNT_WITH_SPACES);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.EDIT_TIME);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.EMBEDDED_RELATIONSHIP_ID);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.EMBEDDED_RESOURCE_TYPE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.EMBEDDED_STORAGE_CLASS_ID);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.EXPERIMENT_ID);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.HISTORY);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.INSTITUTION);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.KEYWORDS);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.LAST_AUTHOR);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.LICENSE_LOCATION);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.LICENSE_URL);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.LOCATION);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MANAGER);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MESSAGE_BCC);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MESSAGE_CC);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MESSAGE_FROM);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MESSAGE_PREFIX);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MESSAGE_RAW_HEADER_PREFIX);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MESSAGE_RECIPIENT_ADDRESS);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MESSAGE_TO);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MIME_TYPE_MAGIC);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MODEL_NAME_ENGLISH);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.NOTES);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.PRESENTATION_FORMAT);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.PROGRAM_ID);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.PROJECT_ID);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.PROTECTED);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.REALIZATION);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.REFERENCES);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.RESOURCE_NAME_KEY);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.REVISION_NUMBER);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.SECURITY);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.TABLE_ID);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.TEMPLATE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.TIKA_MIME_FILE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.TOTAL_TIME);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.USER_DEFINED_METADATA_NAME_PREFIX);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.VERSION);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.WORK_TYPE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.DATE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.ALTITUDE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.BITS_PER_SAMPLE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.CHARACTER_COUNT);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.CHARACTER_COUNT_WITH_SPACES);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.CREATION_DATE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.EQUIPMENT_MAKE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.EQUIPMENT_MODEL);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.EXPOSURE_TIME);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.F_NUMBER);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.FLASH_FIRED);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.FOCAL_LENGTH);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.IMAGE_COUNT);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.IMAGE_LENGTH);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.IMAGE_WIDTH);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.ISO_SPEED_RATINGS);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.LAST_MODIFIED);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.LAST_PRINTED);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.LAST_SAVED);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.LATITUDE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.LINE_COUNT);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.LONGITUDE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MESSAGE_BCC_DISPLAY_NAME);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MESSAGE_BCC_EMAIL);

			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MESSAGE_BCC_NAME);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MESSAGE_CC_DISPLAY_NAME);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MESSAGE_CC_EMAIL);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MESSAGE_CC_NAME);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MESSAGE_FROM_EMAIL);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MESSAGE_FROM_NAME);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MESSAGE_TO_DISPLAY_NAME);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MESSAGE_TO_EMAIL);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.MESSAGE_TO_NAME);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.OBJECT_COUNT);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.ORIENTATION);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.ORIGINAL_DATE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.PAGE_COUNT);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.PARAGRAPH_COUNT);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.RESOLUTION_HORIZONTAL);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.RESOLUTION_UNIT);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.RESOLUTION_VERTICAL);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.SAMPLES_PER_PIXEL);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.SLIDE_COUNT);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.SOFTWARE);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.TABLE_COUNT);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.WORD_COUNT);
			setMetadataProperty(metadata, tikaMetadata, org.apache.tika.metadata.Metadata.RESOURCE_NAME_KEY);
		}
		return metadata;
	}

	/**
	 * Ritorna l'estensione del file di cui viene passato il nome
	 * @param filename
	 * @return
	 */
	private String getFileExtension(String filename) {
		if (filename != null && !filename.isEmpty()) {
			int pos = filename.lastIndexOf(".");
			if (pos != -1)
				return filename.substring(pos+1);
		}
		return "";
	}


	private void setMetadataProperty(Metadata metadata, org.apache.tika.metadata.Metadata tikaMetadata, String tikaMetadataProperty) {
		String value = tikaMetadata.get(tikaMetadataProperty);
		if (value != null && !value.isEmpty()) {
			metadata.addMeta(tikaMetadataProperty, value);
		}
	}

	private void setMetadataProperty(Metadata metadata, org.apache.tika.metadata.Metadata tikaMetadata, Property tikaMetadataProperty) {
		String value = tikaMetadata.get(tikaMetadataProperty);
		if (value != null && !value.isEmpty()) {
			metadata.addMeta(tikaMetadataProperty.getName(), value);
		}
	}

	public String[] getConvTo() {
		return convTo;
	}

	public String getAdditionalParameters() {
		return additionalParameters;
	}

	/**
	 * Salvataggio del documento (memorizzazione dei risultati dell'elaborazione)
	 * @return
	 * @throws Exception
	 */
	public abstract boolean saveDocumento(Documento documento) throws Exception;

	/**
	 * Recupero del documento da elaborare in base all'identificativo passato. Il documento risultante contiene l'elenco dei files da elaborare
	 * e la tipologia di elaborazione (indicizzazione/conversione)
	 * @param id Identificativo del documento da caricare
	 * @param workDir Directory di lavoro dello specifico thread (da utilizzare come directory di appoggio per il salvataggio dei file di lavoro)
	 * @return
	 * @throws Exception
	 */
	public abstract Documento getDocumento(String id, File workDir) throws Exception;

}
