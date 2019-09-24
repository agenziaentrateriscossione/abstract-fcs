package it.tredi.fcs.command.conversion;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jodconverter.OfficeDocumentConverter;
import org.jodconverter.document.DefaultDocumentFormatRegistry;
import org.jodconverter.document.DocumentFamily;
import org.jodconverter.document.DocumentFormat;
import org.jodconverter.office.OfficeManager;

import it.tredi.fcs.Fcs;

/**
 * Conversione di documenti tramite OpenOffice (o LibreOffice)
 */
public class OpenOfficeConversionExecutor {
	
	private static final Logger logger = LogManager.getLogger(Fcs.class.getName());
	
	private OfficeManager officeManager;
	
	/**
	 * Costruttore
	 * @param officeManager OfficeManger per la conversione di documenti tramite OpenOffice (o LibreOffice)
	 */
	public OpenOfficeConversionExecutor(OfficeManager officeManager) {
		this.officeManager = officeManager;
	}

	/**
	 * Ritorna l'oggetto DocumentFormat relativo ad una conversione in PDF/A
	 * @param from File di origine per il quale si richiede la conversione in PDF/A
	 */
	public DocumentFormat getPdfaDocumentFormat(File from) {
		DocumentFormat format = null;
		if (from != null) {
			Map<String, Object> filterData = new HashMap<String, Object>();

			// Specifies printing of the document:
			// 0: PDF document cannot be printed
			// 1: PDF document can be printed at low resolution only
			// 2: PDF document can be printed at maximum resolution.
			filterData.put("Printing", 2);
			
			// Specifies the PDF version that should be generated:
			// 0: PDF 1.4 (default selection)
			// 1: PDF/A-1 (ISO 19005-1:2005)
			filterData.put("SelectPdfVersion", 1);

			if (logger.isDebugEnabled())
				logger.debug("OpenOfficeConversionExecutor.getPdfaDocumentFormat(): check doc family for file " + from.getName());
			
			String filterName = null;
			String inputExtension = FilenameUtils.getExtension(from.getName());
			DocumentFormat sourceFormat = DefaultDocumentFormatRegistry.create().getFormatByExtension(inputExtension);
			DocumentFamily docFamily = DocumentFamily.TEXT;
			if (sourceFormat != null)
				docFamily = sourceFormat.getInputFamily();
			
			if (logger.isDebugEnabled())
				logger.debug("OpenOfficeConversionExecutor.getPdfaDocumentFormat(): docFamily = " + docFamily.name());
			
			if (docFamily == DocumentFamily.TEXT)
				filterName = "writer_pdf_Export";
			else if (docFamily == DocumentFamily.SPREADSHEET)
				filterName = "calc_pdf_Export";
			else if (docFamily == DocumentFamily.PRESENTATION)
				filterName = "impress_pdf_Export";
			else if (docFamily == DocumentFamily.DRAWING)
				filterName = "draw_pdf_Export";
			else
				filterName = "writer_pdf_Export"; // caso di default: writer (doc)
			
			Map<String, Object> properties = new HashMap<>();
			properties.put("FilterName", filterName);
			properties.put("FilterData", filterData);
	
			format = new DocumentFormat("PDF/A", "pdf", "application/pdf");
			format.setStoreProperties(docFamily, properties);
			//format.setStoreProperties(DocumentFamily.TEXT, properties);
			//format.setStoreProperties(DocumentFamily.SPREADSHEET, properties);
			//format.setStoreProperties(DocumentFamily.PRESENTATION, properties);
			//format.setStoreProperties(DocumentFamily.DRAWING, properties);
		}
		return format;
	}
	
	/**
	 * Conversione di un file in PDF/A
	 * @param from Riferimento al file da convertire
	 * @return File di output derivante dalla conversione, NULL in caso di errori
	 * @throws Exception
	 */
	public File convertToPDFA(File from) throws Exception {
		return convertToPDFA(from, null);
	}
	
	/**
	 * Conversione di un file in PDF/A
	 * @param from Riferimento al file da convertire
	 * @param outdir Directory di output
	 * @return File di output derivante dalla conversione, NULL in caso di errori
	 * @throws Exception
	 */
	public File convertToPDFA(File from, File outdir) throws Exception {
		File to = defineOutputPdfFile(from, outdir);
		if (convert(from, to, getPdfaDocumentFormat(from)))
			return to;
		else
			return null;
	}
	
	private File defineOutputPdfFile(File from, File outdir) {
		File to = null;
		if (outdir == null && from != null)
			outdir = from.getParentFile();
		if (from != null)
			to = new File(outdir, from.getName() + ".pdf"); // TODO stessa location di from ma con estensione sostituita a PDF
		return to;
	}
	
	/**
	 * Conversione di un file in PDF 1.4
	 * @param from Riferimento al file da convertire
	 * @return File di output derivante dalla conversione, NULL in caso di errori
	 * @throws Exception
	 */
	public File convertToPDF14(File from) throws Exception {
		return convertToPDFA(from, null);
	}
	
	/**
	 * Conversione di un file in PDF 1.4
	 * @param from Riferimento al file da convertire
	 * @param outdir Directory di output
	 * @return File di output derivante dalla conversione, NULL in caso di errori
	 * @throws Exception
	 */
	public File convertToPDF14(File from, File outdir) throws Exception {
		File to = defineOutputPdfFile(from, outdir);
		if (convert(from, to, null))
			return to;
		else
			return null;
	}
	
	/**
	 * Conversione di un file tramite OpenOffice (o LibreOffice) senza la specifica del formato di output. In caso di conversione in PDF
	 * verranno generati file in formato PDF 1.4 e non PDF/A.
	 * @param from File di origine (file da convertire)
	 * @param to File di destinazione (risultato della conversione)
	 * @return Esito della conversione: true se la conversione termina con esito positivo, false altrimenti
	 * @throws Exception
	 */
	public boolean convert(File from, File to) throws Exception {
		return convert(from, to, null);
	}

	/**
	 * Conversione di un file tramite OpenOffice (o LibreOffice)
	 * @param from File di origine (file da convertire)
	 * @param to File di destinazione (risultato della conversione)
	 * @param formatTo Oggetto DocumentFormat contenente le specifiche di conversione
	 * @return Esito della conversione: true se la conversione termina con esito positivo, false altrimenti
	 * @throws Exception
	 */
	public boolean convert(File from, File to, DocumentFormat formatTo) throws Exception {
		if (officeManager == null)
			throw new Exception("OfficeManager is NULL, conversion is not possible!");
		if (from == null || !from.exists() || !from.isFile())
			throw new Exception("Impossible to start conversion for file " + (from != null ? from.getAbsolutePath() : "NULL"));
		if (to == null)
			throw new Exception("Destination file is NULL!");
		
		boolean done = false;
		
		try {
			long startTime = System.currentTimeMillis();
			
			OfficeDocumentConverter converter = new OfficeDocumentConverter(officeManager);
			
			if (logger.isInfoEnabled())
				logger.info("OpenOfficeConversionExecutor.convert(): from " + from.getAbsolutePath() + " to " + to.getAbsolutePath());
			
			if (formatTo != null)
				converter.convert(from, to, formatTo);
			else
				converter.convert(from, to);
			
			if (logger.isInfoEnabled())
				logger.info("OpenOfficeConversionExecutor.convert(): conversion tooks " + (System.currentTimeMillis()-startTime) + " millis.");
			
			// Verifico che effettivamente il file sia stato creato nella directory di destinazione
			if (to != null && to.isFile() && to.exists())
				done = true;
		}
		catch(Exception e) {
			logger.error("OpenOfficeConversionExecutor.convert(): got exception on OfficeDocumentConverter for file " + from.getAbsolutePath() + "... " + e.getMessage(), e);
		}
		
		return done;
	}
	
}
