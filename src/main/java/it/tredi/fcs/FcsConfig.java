package it.tredi.fcs;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.tredi.fcs.socket.commands.entity.FcsActivationParams;
import it.tredi.utils.properties.PropertiesReader;

/**
 * Parametri di configurazione di FCS
 * @author mbernardini
 */
public class FcsConfig {
	
	private static final Logger logger = LogManager.getLogger(Fcs.class.getName());
	
	private static final String PROPERTIES_FILE_NAME = "it.tredi.abstract-fcs.properties";
	
	private static final String FCS_PORT_PROPERTY = "fcs.port";
	private static final String FCS_WORKING_FOLDER_PROPERTY = "fcs.working.folder";
	private static final String FCS_WORKING_TIMEOUT_PROPERTY = "fcs.working.timeout";
	
	private static final String FCS_CONVERSION_TIMEOUT_PROPERTY = "fcs.conversion.timeout";
	
	private static final String FCS_CONVERSION_DOC_OPENOFFICE_HOMEDIR_PROPERTY = "fcs.conversion.doc.openoffice.homedir";
	private static final String FCS_CONVERSION_DOC_OPENOFFICE_PORTS_PROPERTY = "fcs.conversion.doc.openoffice.ports";
	private static final String FCS_CONVERSION_DOC_OPENOFFICE_PDFA_PROPERTY = "fcs.conversion.doc.openoffice.pdfa";
	
	private static final String FCS_CONVERSION_IMAGEMAGICK_EXTENSIONS_PROPERTY = "fcs.conversion.imagemagick.extensions";
	private static final String FCS_CONVERSION_IMAGEMAGICK_COMMAND_PROPERTY = "fcs.conversion.imagemagick.command";
	
	private static final int FCS_PORT_DEFAULT_VALUE = 4870;
	private static final long FCS_WORKING_TIMEOUT_DEFAULT_VALUE = 60000;
	
	private static final long FCS_CONVERSION_TIMEOUT_DEFAULT_VALUE = 0;
	
	private static final String FCS_CONVERSION_IMAGEMAGICK_EXTENSIONS_DEFAULT_VALUE = "tiff,tif,png,jpeg,jpg";
	
	private static final boolean FCS_CONVERSIONE_DOC_OPENOFFICE_PDFA_DEFAULT_VALUE = false;
	
	private int fcsPort = 0;
	private File fcsWorkingFolder;
	private long fcsWorkingTimeout = 0; // TODO da rimuovere
	
	private long fcsConversionTimout = 0;
	
	private String fcsConversionDocOpenOfficeHomeDir;
	private int[] fcsConversionDocOpenOfficePorts = null;
	private boolean fcsConversionDocOpenOfficePdfA = false;
	
	private String[] fcsConversionImageMagickExtensions = null;
	private String fcsConversionImageMagickCommand;
	
	private FcsActivationParams activationParams = null;
	
	// Singleton
    private static FcsConfig instance = null;
    
    /**
     * Costruttore privato
     */
    private FcsConfig() throws Exception {
    	PropertiesReader propertiesReader = new PropertiesReader(PROPERTIES_FILE_NAME); 
    	
    	this.fcsPort = propertiesReader.getIntProperty(FCS_PORT_PROPERTY, 0);
    	if (this.fcsPort == 0) {
    		if (logger.isInfoEnabled())
    			logger.info("FcsConfig: FCS port value not specified, assign default value = " + FCS_PORT_DEFAULT_VALUE);
    		this.fcsPort = FCS_PORT_DEFAULT_VALUE;
    	}
    	
    	String strWorkingFolder = propertiesReader.getProperty(FCS_WORKING_FOLDER_PROPERTY, null);
    	if (strWorkingFolder == null || strWorkingFolder.isEmpty()) {
    		// directory di lavoro non specificata assegno la directory temporanea...
    		strWorkingFolder = System.getProperty("java.io.tmpdir") + File.separator + "fcs";
    		if (logger.isInfoEnabled())
    			logger.info("FcsConfig: FCS working folder not specified, assign temp dir " + strWorkingFolder);
    	}
    	this.fcsWorkingFolder = new File(strWorkingFolder);
    	
    	// Cancellazione della directory di lavoro con successiva rigenerazione (svuoto eventuali file temporanei di precedenti elaborazioni)
    	if (this.fcsWorkingFolder.exists()) 
    		FileUtils.deleteDirectory(this.fcsWorkingFolder); 
    	if (!this.fcsWorkingFolder.mkdirs())
    		throw new Exception("Unable to create FCS working folder... " + strWorkingFolder);
    	
    	this.fcsWorkingTimeout = propertiesReader.getLongProperty(FCS_WORKING_TIMEOUT_PROPERTY, FCS_WORKING_TIMEOUT_DEFAULT_VALUE);
    	
    	// Lettura di tutti i parametri di configurazione degli strumenti di conversione (OpenOffice, ImageMagick, ecc.)
    	
    	this.fcsConversionTimout = propertiesReader.getLongProperty(FCS_CONVERSION_TIMEOUT_PROPERTY, FCS_CONVERSION_TIMEOUT_DEFAULT_VALUE);
    	
    	this.fcsConversionDocOpenOfficeHomeDir = propertiesReader.getProperty(FCS_CONVERSION_DOC_OPENOFFICE_HOMEDIR_PROPERTY, null);
    	String[] ooPorts = propertiesReader.getProperty(FCS_CONVERSION_DOC_OPENOFFICE_PORTS_PROPERTY, "").split(",");
    	if (ooPorts.length > 0) {
    		this.fcsConversionDocOpenOfficePorts = new int[ooPorts.length];
    		int i = 0;
    		for (String port : ooPorts) {
				this.fcsConversionDocOpenOfficePorts[i] = Integer.parseInt(port);
				i++;
			}
    	}
    	this.fcsConversionDocOpenOfficePdfA = propertiesReader.getBooleanProperty(FCS_CONVERSION_DOC_OPENOFFICE_PDFA_PROPERTY, FCS_CONVERSIONE_DOC_OPENOFFICE_PDFA_DEFAULT_VALUE);
    	
    	this.fcsConversionImageMagickExtensions = propertiesReader.getProperty(FCS_CONVERSION_IMAGEMAGICK_EXTENSIONS_PROPERTY, FCS_CONVERSION_IMAGEMAGICK_EXTENSIONS_DEFAULT_VALUE).split(",");
    	this.fcsConversionImageMagickCommand = propertiesReader.getProperty(FCS_CONVERSION_IMAGEMAGICK_COMMAND_PROPERTY, null);
    	
    	if (logger.isDebugEnabled()) {
    		logger.debug("------------------- FCS CONFIGURATION PARAMETERS -------------------");
    		logger.debug(FCS_PORT_PROPERTY + " = " + this.fcsPort);
    		logger.debug(FCS_WORKING_FOLDER_PROPERTY + " = " + this.fcsWorkingFolder.getAbsolutePath());
    		logger.debug(FCS_WORKING_TIMEOUT_PROPERTY + " = " + this.fcsWorkingTimeout);
    		
    		logger.debug(FCS_CONVERSION_TIMEOUT_PROPERTY + " = " + this.fcsConversionTimout);
    		
    		logger.debug(FCS_CONVERSION_DOC_OPENOFFICE_HOMEDIR_PROPERTY + " = " + this.fcsConversionDocOpenOfficeHomeDir);
    		logger.debug(FCS_CONVERSION_DOC_OPENOFFICE_PDFA_PROPERTY + " = " + String.valueOf(this.fcsConversionDocOpenOfficePdfA));
    		logger.debug(FCS_CONVERSION_DOC_OPENOFFICE_PORTS_PROPERTY + " = " + (this.fcsConversionDocOpenOfficePorts != null ? String.join(", ", ooPorts) : "NULL"));
    		
    		logger.debug(FCS_CONVERSION_IMAGEMAGICK_EXTENSIONS_PROPERTY + " = " + String.join(", ", this.fcsConversionImageMagickExtensions));
    		logger.debug(FCS_CONVERSION_IMAGEMAGICK_COMMAND_PROPERTY + " = " + this.fcsConversionImageMagickCommand);
    	}
    	
    	if (this.fcsConversionImageMagickCommand == null || this.fcsConversionImageMagickCommand.isEmpty()) {
    		if (logger.isInfoEnabled())
        		logger.info("ImageMagick conversion is DISABLED!");
    	}
    }
    
	/**
     * Ritorna l'oggetto contenente tutte le configurazioni di FCA (e parametri di indicizzazione/conversione da inviare agli host FCS)
	 * @return
	 */
	public static FcsConfig getInstance() throws Exception {
		if (instance == null) {
			synchronized (FcsConfig.class) {
				if (instance == null) {
					if (logger.isInfoEnabled())
						logger.info("FcsConfig instance is null... create one");
					instance = new FcsConfig();
				}
			}
		}
		return instance;
	}
	
	public int getFcsPort() {
		return fcsPort;
	}

	public File getFcsWorkingFolder() {
		return fcsWorkingFolder;
	}

	public long getFcsWorkingTimeout() {
		return fcsWorkingTimeout;
	}
	
	public long getFcsConversionTimout() {
		return fcsConversionTimout;
	}

	public String getFcsConversionDocOpenOfficeHomeDir() {
		return fcsConversionDocOpenOfficeHomeDir;
	}

	public int[] getFcsConversionDocOpenOfficePorts() {
		return fcsConversionDocOpenOfficePorts;
	}

	public boolean isFcsConversionDocOpenOfficePdfA() {
		return fcsConversionDocOpenOfficePdfA;
	}

	public String[] getFcsConversionImageMagickExtensions() {
		return fcsConversionImageMagickExtensions;
	}

	public String getFcsConversionImageMagickCommand() {
		return fcsConversionImageMagickCommand;
	}
	
	public FcsActivationParams getActivationParams() {
		return activationParams;
	}

	public void setActivationParams(FcsActivationParams activationParams) {
		this.activationParams = activationParams;
	}
	
	/**
	 * Impostazione dei parametri di attivazione di FCS in base al JSON di configurazione inviato da FCA
	 * @param json
	 */
	public void setActivationParamsFromJson(String json) throws Exception {
		if (json != null)
			this.activationParams = new ObjectMapper().readValue(json, FcsActivationParams.class);
	}

}
