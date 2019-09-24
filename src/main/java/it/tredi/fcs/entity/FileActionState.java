package it.tredi.fcs.entity;

public enum FileActionState {

	/**
	 * Indica che l'azione (estrazione testo o conversione) deve essere ancora realizzata
	 */
	TODO, 
	/**
	 * Indica che l'azione sul file deve essere ignorata (es. dimensione del file eccessiva o estensione non supportata)
	 */
	IGNORE,
	/**
	 * Indica che l'azione sul file e' stata completata con successo
	 */
	DONE ,
	/**
	 * Indica che sono stati riscontrati errori durante l'esecuzione dell'azione sul file
	 */
	FAIL 
	
	;
	
}
