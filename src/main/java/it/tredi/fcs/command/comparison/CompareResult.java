package it.tredi.fcs.command.comparison;

/**
 * Risultato di una comparazione fra 2 files tramite OpenOffice (o LibreOffice)
 */
public class CompareResult {

	private String fileExtension;
	private byte[] content;
	
	/**
	 * Costruttore
	 * @param extension Estensione restituita in output
	 * @param content Contenuto del file risultante dalla comparazione
	 */
	public CompareResult(String extension, byte[] content) {
		this.fileExtension = extension;
		this.content = content;
	}
	
	public String getFileExtension() {
		return fileExtension;
	}

	public byte[] getContent() {
		return content;
	}
	
}
