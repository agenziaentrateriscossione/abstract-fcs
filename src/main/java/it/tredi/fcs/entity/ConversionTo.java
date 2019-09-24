package it.tredi.fcs.entity;

import java.io.File;

/**
 * Definizione di una destinazione di una conversione
 */
public class ConversionTo {

	private String extension;
	private FileActionState state;
	private File outfile;
	
	/**
	 * Costruttore
	 * @param extensionTo estensione di destinazione della conversione
	 * @param state stato della conversione (todo, ignore, fail, done)
	 */
	public ConversionTo(String extensionTo, FileActionState state) {
		this.extension = extensionTo;
		this.state = state;
	}

	public String getExtension() {
		return extension;
	}

	public FileActionState getState() {
		return state;
	}
	
	public void setState(FileActionState state) {
		this.state = state;
	}
	
	/**
	 * Restituisce il file di output prodotto dalla conversione
	 * @return
	 */
	public File getOutfile() {
		return outfile;
	}
	
	/**
	 * Imposta come completata la conversione corrente (viene settato il riferimento al file di output generato)
	 * @param outputFile
	 */
	public void setConversionComplete(File outputFile) {
		this.outfile = outputFile;
		// mbernardini 09/02/2018 : mancato controllo su effettiva generazione del file di output
		if (outputFile != null && outputFile.exists())
			this.state = FileActionState.DONE;
		else
			this.state = FileActionState.FAIL;
	}
	
}
