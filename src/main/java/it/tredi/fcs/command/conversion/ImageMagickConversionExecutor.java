package it.tredi.fcs.command.conversion;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.tredi.fcs.Fcs;
import it.tredi.fcs.FcsConfig;

/**
 * Conversione di immagini (in PDF) tramite chiamata a ImageMagick
 */
public class ImageMagickConversionExecutor {

	private static final Logger logger = LogManager.getLogger(Fcs.class.getName());
	
	private static final String SOURCE_FILE_REPLACE_IN_COMMAND = "%SOURCE_FILE%";
	private static final String DEST_FILE_REPLACE_IN_COMMAND = "%DEST_FILE%";
	
	private String commandLine;
	
	/**
	 * Costruttore
	 * @param command Comando da eseguire per convertire una immagine in PDF tramite ImageMagick
	 */
	public ImageMagickConversionExecutor(String command) {
		this.commandLine = command;
	}
	
	/**
	 * Conversione di una immagine in PDF tramite chiamata ad ImageMagick
	 * @param from File sorgente da convertire
	 * @param outdir Directory di output
	 * @return File di output derivante dalla conversione, NULL in caso di errori
	 * @throws Exception
	 */
	public File convert(File from, File outdir) throws Exception {
		File out = null;
		if (commandLine != null && !commandLine.isEmpty()) {
			
			Runtime runtime = Runtime.getRuntime();
			try {
				long startTime = System.currentTimeMillis();
				
				if (outdir == null && from != null)
					outdir = from.getParentFile();
				if (from != null)
					out = new File(outdir, from.getName() + ".pdf"); // TODO stessa location di from ma con estensione sostituita a PDF
				
				// mbernardini 19/02/2018 : corretto il comando di avvio di conversione tramite ImageMagick su ambiente Windows (gestione corretta backslash)
				commandLine = commandLine.replace(SOURCE_FILE_REPLACE_IN_COMMAND, from.getAbsolutePath());
				commandLine = commandLine.replace(DEST_FILE_REPLACE_IN_COMMAND, out.getAbsolutePath());
				if (logger.isInfoEnabled())
					logger.info("ImageMagickConversionExecutor.convert(): convert " + from.getName() + " by command " + commandLine);
				
				Process proc = runtime.exec(commandLine);
	
				long convTimeout = FcsConfig.getInstance().getFcsConversionTimout();
				
				int exitValue = -1;
				if (convTimeout > 0) {
					boolean done = proc.waitFor(convTimeout, TimeUnit.MILLISECONDS);
					if (!done)
						proc.destroy();
					exitValue = proc.exitValue();
				}
				else {
					// Esecuzione del comando di conversione senza Timeout
					exitValue = proc.waitFor();
				}

				if (logger.isInfoEnabled())
					logger.info("ImageMagickConversionExecutor.convert(): ImageMagick conversion tooks " + (System.currentTimeMillis()-startTime) + " millis. [exitValue = " + exitValue + "]");
				
				if (exitValue != 0 || (out != null && (!out.isFile() || !out.exists())))
					out = null;
			} 
			catch (Exception e) {
				logger.error("ImageMagickConversionExecutor.convert(): got exception... " + e.getMessage(), e);
				out = null;
			} 
		}
		else {
			// Conversione tramite ImageMagick non configurata
			if (logger.isDebugEnabled())
				logger.debug("ImageMagickConversionExecutor.convert(): ImageMagick conversion is not configured! No command defined on properties file");
		}
		return out;
	}
	
}
