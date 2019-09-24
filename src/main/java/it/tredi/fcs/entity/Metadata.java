package it.tredi.fcs.entity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Oggetto contenente i metadati estratti da un file elaborato da FCS
 */
public class Metadata {

	private Map<String, String> meta;
	
	public Metadata(String extension, long size) {
		this.meta = new LinkedHashMap<String, String>();
	}
	
	public void addMeta(String key, String value) {
		this.meta.put(key, value);
	}

	public Map<String, String> getMeta() {
		return meta;
	}
	
}
