package it.tredi.fcs.command.comparison;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.jodconverter.OfficeDocumentCompare;
import org.jodconverter.office.OfficeManager;

import com.google.common.io.Files;

/**
 * Conversione da un formato all'altro
 */
public class Compare {
	
	/**
	 * Effettua la comparazione di due versioni di un file ritornando un byteArray
	 * @param officeManager l'OfficeManager di libre office
	 * @param workDir la directory di lavoro su cui appoggiarsi
	 * @param prevVersionByteArray il byte array della versione precedente del file
	 * @param nextVersionByteArray il byte array della versione successiva del file
	 * @param prevVersionExt l'estensione della versione precedente
	 * @param nextVersionExt l'estensione della versione successiva
	 * @param outputPdf true se e' richiesto un output file in formato PDF, false in caso di output in formato OpenOffice
	 * @return
	 */
	public static CompareResult compareToByteArray(OfficeManager officeManager, File workDir, byte[] prevVersionByteArray, byte[] nextVersionByteArray,
			String prevVersionExt, String nextVersionExt, boolean outputPdf) throws Exception {
		File prevVersionFile = File.createTempFile("comp_prev_", "."+prevVersionExt, workDir);
		FileUtils.writeByteArrayToFile(prevVersionFile, prevVersionByteArray);
		File nextVersionFile = File.createTempFile("comp_next_", "."+nextVersionExt, workDir);
		FileUtils.writeByteArrayToFile(nextVersionFile, nextVersionByteArray);
		return compare(officeManager, workDir, prevVersionFile, nextVersionFile, prevVersionExt, nextVersionExt, outputPdf);
	}

	/**
	 * Compara due versioni di un file
	 * @param officeManager l'OfficeManager di libre office
	 * @param workDir la directory di lavoro su cui appoggiarsi
	 * @param prevVersionFile la versione precedente del file
	 * @param nextVersionFile la versione successiva del file
	 * @param outputFile il file in cui salvara la comparazione
	 * @param prevVersionExt l'estensione della versione precedente
	 * @param nextVersionExt l'estensione della versione successiva
	 * @param outputPdf true se e' richiesto un output file in formato PDF, false in caso di output in formato OpenOffice
	 * @return
	 * @throws Exception
	 */
	public static CompareResult compare(OfficeManager officeManager, File workDir, File prevVersionFile, File nextVersionFile,
					String prevVersionExt, String nextVersionExt, boolean outputPdf) throws Exception {
		
		String outputExt = "";
		if (outputPdf)
			outputExt = "pdf";
		else 
			outputExt = prevVersionExt; // se non e' richiesta la conversione in pdf il formato di output e' lo stesso di quello ricevuto in input
		
		File outputFile = File.createTempFile("comp_", "."+outputExt, workDir);
		OfficeDocumentCompare comparer = new OfficeDocumentCompare(officeManager);
		comparer.compare(prevVersionFile, nextVersionFile, outputFile, prevVersionExt, nextVersionExt, outputExt);
		
		return new CompareResult(outputExt, Files.toByteArray(outputFile));
	}

}
