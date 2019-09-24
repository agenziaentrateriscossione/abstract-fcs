package it.tredi.fcs.command.conversion;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.jodconverter.office.OfficeManager;

import com.google.common.io.Files;

import it.tredi.fcs.FcsConfig;

/**
 * Conversione da un formato all'altro
 */
public class Convert {
	
	// Elenco di estensioni da gestire tramite ImageMagick
	private static final String[] IMAGEMAGICK_EXTENSIONS_DEFAULT_VALUE = { "bmp", "jpeg", "jpg", "png", "tif", "tiff", "gif" }; // TODO da verificare se devono essere specificate altre estensioni

	/**
	 * Effettua la conversione del file passato al formato di cui viene passata l'estensione extTo
	 * @param officeManager l'OfficeManager di libre office
	 * @param workDir la directory di lavoro su cui appoggiarsi
	 * @param inputByteArray il byte array del file da convertire
	 * @param extTo l'estensione in cui convertire il file
	 * @return
	 */
	public static byte[] convertToByteArray(OfficeManager officeManager, File workDir, byte[] inputByteArray, String extFrom, String extTo) throws Exception {
		File inputFile = File.createTempFile("conv_", extFrom, workDir);
		FileUtils.writeByteArrayToFile(inputFile, inputByteArray);
		File output = convert(officeManager, workDir, inputFile, extFrom, extTo);
		return Files.toByteArray(output);
	}

	/**
	 * Effettua la conversione del file passato al formato di cui viene passata l'estensione extTo
	 * @param officeManager l'OfficeManager di libre office
	 * @param workDir la directory di lavoro su cui appoggiarsi
	 * @param inputByteArray il byte array del file da convertire
	 * @param extTo l'estensione in cui convertire il file
	 * @return
	 */
	public static File convert(OfficeManager officeManager, File workDir, byte[] inputByteArray, String extFrom, String extTo) throws Exception {
		File inputFile = File.createTempFile("conv_", extFrom, workDir);
		FileUtils.writeByteArrayToFile(inputFile, inputByteArray);
		return convert(officeManager, workDir, inputFile, extFrom, extTo);
	}

	/**
	 * Effettua la conversione del file passato al formato di cui viene passata l'estensione extTo
	 * @param officeManager l'OfficeManager di libre office
	 * @param workDir la directory di lavoro su cui appoggiarsi
	 * @param inputFile il file da convertire
	 * @param extTo l'estensione in cui convertire il file
	 * @return
	 */
	public static File convert(OfficeManager officeManager, File workDir, File inputFile, String extTo) throws Exception {
		String extFrom = getFileExtension(inputFile.getName());
		return convert(officeManager, workDir, inputFile, extFrom);
	}

	/**
	 * Effettua la conversione del file dal formato di cui viene passata l'estensione extFrom al formato di cui viene passata l'estensione extTo
	 * @param officeManager l'OfficeManager di libre office
	 * @param workDir la directory di lavoro su cui appoggiarsi
	 * @param inputFile il file da convertire
	 * @param extFrom l'estensione di partenza
	 * @param extTo l'estensione in cui convertire il file
	 * @return
	 * @throws Exception
	 */
	public static File convert(OfficeManager officeManager, File workDir, File inputFile, String extFrom, String extTo) throws Exception {
		File outFile = null;
		if (!extFrom.toLowerCase().equals(extTo.toLowerCase())) {
			if (extTo.toLowerCase().equals("pdf")) { // conversione in PDF
				List<String> imagemagickSupportedExtensions = getImageMagickSupportedExtensions();
				
				// mbernardini 16/02/2018 : trasformazione lowercase dell'estensione di origine
				if (imagemagickSupportedExtensions != null && imagemagickSupportedExtensions.contains(extFrom.toLowerCase())) {
					// Conversione in PDF tramite ImageMagick

					ImageMagickConversionExecutor imConversionExecutor = new ImageMagickConversionExecutor(FcsConfig.getInstance().getFcsConversionImageMagickCommand());
					outFile = imConversionExecutor.convert(inputFile, workDir);
				}
				else {
					// Conversione in PDF tramite OpenOffice
					OpenOfficeConversionExecutor ooConversionExecutor = new OpenOfficeConversionExecutor(officeManager);
					if (FcsConfig.getInstance().isFcsConversionDocOpenOfficePdfA())
						outFile = ooConversionExecutor.convertToPDFA(inputFile, workDir);
					else
						outFile = ooConversionExecutor.convertToPDF14(inputFile, workDir);
				}
			} else {
				throw new Exception("Converter - convert Not Supported extension: " + extTo);
			}
		} else {
			outFile = inputFile;
		}
		return outFile;
	}
	
	/**
	 * Ritorna l'elenco di estensioni supportate in conversione files tramite ImageMagick
	 * @return
	 */
	private static List<String> getImageMagickSupportedExtensions() throws Exception {
		String[] imExts = FcsConfig.getInstance().getFcsConversionImageMagickExtensions();
		if (imExts == null)
			imExts = IMAGEMAGICK_EXTENSIONS_DEFAULT_VALUE;
		return Arrays.asList(imExts);
	}

	/**
	 * Ritorna l'estensione del file di cui viene passato il nome
	 * @param filename
	 * @return
	 */
	private static String getFileExtension(String filename) {
		if (filename != null && !filename.isEmpty()) {
			int pos = filename.lastIndexOf(".");
			if (pos != -1)
				return filename.substring(pos+1);
		}
		return "";
	}
}
