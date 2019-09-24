package it.tredi.fcs.entity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Riferimento ad un file da elaborare tramite FCS (indicizzazione e/o conversione)
 */
public class FileToWork {

	/**
	 * Nome del file da elaborare
	 */
	private String fileName;
	/**
	 * Riferimento al file (caricato su filesystem di FCS). Potrebbe essere NULL nel caso in cui l'elaborazione sul file debba essere ignorata
	 */
	private File inputFile;

	/**
	 * Indica lo stato dell'azione di indicizzazione sul file (da realizzare, da ignorare, completata, fallita)
	 */
	private FileActionState index;

	/**
	 * Testo estratto dal documento passato
	 */
	private String outFileText;

	/**
	 * In caso di richiesta di conversione del file, elenco di conversioni da produrre per il file (estensioni dei file da produrre), stato delle richieste
	 * e riferimenti ai file di output prodotti dalla procedura di conversione
	 */
	private Map<String, ConversionTo> conversions;

	/**
	 * Indica lo stato dell'azione (da realizzare, da ignorare, completata, fallita) di estrazione dei metadati del file (estensione, data di creazione, data di
	 * ultima modifica, dimensione, ecc.)
	 */
	private FileActionState meta;

	/**
	 * Oggetto contenente i metedata estratti dal file
	 */
	private Metadata outMetadata;

	/**
	 * Costruttore
	 * @param fileName Nome del file da elaborare
	 * @param index true in caso sia richiesta l'estrazione del testo dal file (indicizzazione)
	 * @param conversions Elenco di estensioni che corrispondo alle conversioni del file (NULL o lista vuota se nessuna conversione deve essere realizzata)
	 * @param meta true in caso di richiesta di estrazione di metadati dal file
	 */
	public FileToWork(String fileName, boolean index, Map<String, ConversionTo> conversions, boolean meta) {
		this(fileName, null, index, conversions, meta);
	}

	/**
	 * Costruttore
	 * @param fileName Nome del file da elaborare
	 * @param file Riferimento al file da elaborare (caricato su filesystem nella directory di lavoro di FCS)
	 * @param index true in caso sia richiesta l'estrazione del testo dal file (indicizzazione)
	 * @param conversionTo Elenco di estensioni che corrispondo alle conversioni del file (NULL o lista vuota se nessuna conversione deve essere realizzata)
	 * @param meta true in caso di richiesta di estrazione di metadati dal file
	 */
	public FileToWork(String fileName, File file, boolean index, Map<String, ConversionTo> conversions, boolean meta) {
		this.fileName = fileName;
		this.inputFile = file;
		this.index = (index) ? FileActionState.TODO : FileActionState.IGNORE;
		this.conversions = conversions;
		this.meta = (meta) ? FileActionState.TODO : FileActionState.IGNORE;
	}

	public FileActionState getIndex() {
		return index;
	}

	public FileActionState getMeta() {
		return meta;
	}

	/**
	 * Restituisce l'elenco di conversioni effettivamente richieste (vengono escluse quelle ignorate)
	 * @return
	 */
	public List<ConversionTo> getTodoConversionsTo() { 
		List<ConversionTo> todos = new ArrayList<ConversionTo>();
		if (conversions != null) {
			for (Map.Entry<String, ConversionTo> entry : conversions.entrySet()) {
				ConversionTo convTo = entry.getValue();
				if (convTo != null && convTo.getState() == FileActionState.TODO)
					todos.add(convTo);
			}
		}
		return todos;
	}
	
	/**
	 * Restituisce l'elenco completo di conversioni richieste
	 * @return
	 */
	public Collection<ConversionTo> getConversionsTo() {
		if (conversions != null)
			return conversions.values();
		else
			return null;
	}

	/**
	 * Assegnazione del file di input (caricato su filesystem nella directory di lavoro di FCS)
	 * @param file
	 */
	public void setInputFile(File file) {
		this.inputFile = file;
	}

	public File getInputFile() {
		return inputFile;
	}

	/**
	 * Setta il testo estratto dal documento
	 * @param text
	 */
	public void setOutFileText(String text) {
		this.outFileText = text;
		this.index = FileActionState.DONE;
	}

	public String getOutFileText() {
		return outFileText;
	}

	/**
	 * Setta i metadati estratti dal documento
	 * @param metadata
	 */
	public void setOutMetadata(Metadata metadata) {
		this.outMetadata = metadata;
		this.meta = FileActionState.DONE;
	}

	public Metadata getOutMetadata() {
		return outMetadata;
	}

	/**
	 * Assegna il file convertito per una specifica estensione
	 * @param ext
	 * @param outFile
	 */
	public void addConversion(String ext, File outFile) {
		if (conversions != null 
				&& ext != null && !ext.isEmpty() && outFile != null && outFile.exists() && outFile.isFile()) {
			ConversionTo convTo = conversions.get(ext);
			if (convTo != null)
				convTo.setConversionComplete(outFile);
		}
	}

	/**
	 * Restituisce il file convertito per una specifica estensione
	 * @param ext
	 * @return
	 */
	public File getConvertedFile(String ext) {
		File file = null;
		if (conversions != null && ext != null && !ext.isEmpty()) {
			ConversionTo convTo = conversions.get(ext);
			if (convTo != null)
				file = convTo.getOutfile();
		}
		return file;
	}

	public String getFileName() {
		return fileName;
	}

	/**
	 * Setta il fallimento per l'attivita' di indicizzazione (estrazione testo dal file) nel caso in cui questa fosse prevista
	 */
	public void setIndexFailed() {
		if (this.index == FileActionState.TODO)
			this.index = FileActionState.FAIL;
	}

	/**
	 * Setta l'ignore per l'attivita' di indicizzazione (estrazione testo dal file) nel caso in cui questa fosse prevista
	 */
	public void setIndexIgnore() {
		if (this.index == FileActionState.TODO)
			this.index = FileActionState.IGNORE;
	}

	/**
	 * Setta il fallimento per l'attivita' di conversione nel caso in cui questa fosse prevista
	 * @param extensionTo Estensione di destinazione della conversione per la quale settare il fallimento
	 */
	public void setConversionFailed(String extensionTo) {
		changeConversionState(extensionTo, FileActionState.FAIL);
	}

	/**
	 * Setta il fallimento per l'attivita' di estrazione metadati nel caso in cui questa fosse prevista
	 */
	public void setMetadataFailed() {
		if (this.meta == FileActionState.TODO)
			this.meta = FileActionState.FAIL;
	}

	/**
	 * Setta l'ignore per l'attivita' di indicizzazione (estrazione testo dal file) nel caso in cui questa fosse prevista
	 */
	public void setMetadataIgnore() {
		if (this.meta == FileActionState.TODO)
			this.meta = FileActionState.IGNORE;
	}
	
	/**
	 * Setta l'ignore per l'attivita' di conversione nel caso in cui questa fosse prevista
	 * @param extensionTo Estensione di destinazione della conversione per la quale settare l'ignore
	 */
	public void setConversionIgnore(String extensionTo) {
		changeConversionState(extensionTo, FileActionState.IGNORE);
	}
	
	private void changeConversionState(String extensionTo, FileActionState state) {
		if (conversions != null && extensionTo != null && !extensionTo.isEmpty() && state != null) {
			ConversionTo convTo = conversions.get(extensionTo);
			if (convTo != null && convTo.getState() == FileActionState.TODO)
				convTo.setState(state);
		}
	}

	/**
	 * Setta il fallimento su tutte le attivita' previste per il file (indicizzazione, conversioni, estrazione metadati)
	 */
	public void setAllFailed() {
		setIndexFailed();
		for (Map.Entry<String, ConversionTo> entry : conversions.entrySet())
			if (entry != null)
				setConversionFailed(entry.getKey());
		setMetadataFailed();
	}
	
	/**
	 * Setta l'ignore su tutte le attivita' previste per il file (indicizzazione, conversioni, estrazione metadati)
	 */
	public void setAllConversionIgnore() {
		for (Map.Entry<String, ConversionTo> entry : conversions.entrySet())
			if (entry != null)
				setConversionIgnore(entry.getKey());
	}

}
