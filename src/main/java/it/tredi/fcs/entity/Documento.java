package it.tredi.fcs.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Rappresenta l'oggetto sul quale sono state richiesta attivita' di indicizzazione e/o conversione da parte di FCA
 */
public class Documento {

	/**
	 * Identificativo del record
	 */
	private String id; // TODO l'identificativo del record da indicizzare/convertire potrebbe essere definito come object generico
	
	/**
	 * Lista di file allegati al record che devono essere elaborati
	 */
	private List<FileToWork> filesToWork;
	
	/**
	 * Eventuale contenuto del record (insieme di tutte le informazioni sul documento da elaborare)
	 */
	private Object content; // intero contenuto del documento da elaborare
	
	/**
	 * Costruttore
	 * @param id
	 */
	public Documento(String id) {
		this.id = id;
		this.filesToWork = new ArrayList<FileToWork>();
	}
	
	public void addFileToWork(FileToWork file) {
		if (file != null)
			this.filesToWork.add(file);
	}
	
	public String getId() {
		return id;
	}

	public List<FileToWork> getFilesToWork() {
		return filesToWork;
	}
	
	public void setFilesToWork(List<FileToWork> filesToWork) {
		this.filesToWork = filesToWork;
	}
	
	public Object getContent() {
		return content;
	}

	public void setContent(Object content) {
		this.content = content;
	}
	
}
