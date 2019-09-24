package it.tredi.fcs.test.conversion;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import it.tredi.fcs.command.conversion.ImageMagickConversionExecutor;

/**
 * Test di conversione su OpenOffice (o LibreOffice)
 */
public class ImageMagickConversionTest {

	//private String IMAGEMAGICK_COMMAND = "convert -limit memory 250mb -limit map 500mb -colorspace Gray %SOURCE_FILE% %DEST_FILE%";
	private String IMAGEMAGICK_COMMAND = "convert -colorspace Gray %SOURCE_FILE% %DEST_FILE%";
	//private String IMAGEMAGICK_COMMAND = "convert -limit memory 250mb -limit map 500mb -colorspace RGB %SOURCE_FILE% %DEST_FILE%";
	
    private String OUT_DIR = "/tmp/pdf";
    
	/**
	 * Test di conversione di tutti i file presenti nella directory test/resources/img
	 */
	@Test
	@Ignore
	public void testConversion() {
		NumberFormat formatter = new DecimalFormat("#0.00");
        List<Thread> threads = new ArrayList<Thread>();
        
        String fileName;
		try {
			long startTime = System.currentTimeMillis();
			
			URL fileUrl = Thread.currentThread().getContextClassLoader().getResource("img2pdf.txt");
			URL fileToParse;
			List<String> lines = Files.readAllLines(Paths.get(fileUrl.toURI()));
			int todo = 0;
			for (String line : lines) {
				if (line.startsWith("-"))
					continue;
				
				todo++;
				String[] split = line.split(";", -1);
				fileName = split[0];
				
				fileToParse = Thread.currentThread().getContextClassLoader().getResource(fileName);

				File file = new File(fileToParse.toURI());
				String outdir = OUT_DIR;
				if (outdir == null || outdir.isEmpty())
					outdir = file.getParentFile().getAbsolutePath() + "/out";
				File convertedFile = new File(outdir, file.getName() + ".pdf");
				
				System.out.println("INPUT FILE: " + file.getAbsolutePath());
				System.out.println("OUTPUT FILE: " + convertedFile.getAbsolutePath());

				
				
				FileConversionThread fcd = new FileConversionThread(IMAGEMAGICK_COMMAND, file, convertedFile);
				threads.add(fcd);
				fcd.start();
			}

			// Mi metto in attesa della conclusione di tutti i thread avviati
			for (Thread t : threads) {
				t.join();
			}
			
			System.out.println("\nNumero TOTALE files: " + todo);
			System.out.println("Tempo Totale: " + (System.currentTimeMillis()-startTime) + " ms\n");

			int done = 0;
			long doneTime = 0;
			int failed = 0;
			long failedTime = 0;
			for (int i=0; i<threads.size(); i++) {
				FileConversionThread t = (FileConversionThread) threads.get(i);
				if (t.isDone()) {
					done++;
					doneTime += t.getSpentTime();
				}
				else {
					failed++;
					failedTime += t.getSpentTime();
				}
			}
			
			System.out.println("Numero SUCCESSI: " + done);
			System.out.println("Tempo Totale SUCCESSI: " + doneTime + " ms");
			double medio = 0;
			if (done > 0)
				medio = (double) doneTime / (double) done;
			System.out.println("Tempo Medio SUCCESSI: " + formatter.format(medio) + " ms\n");
			
			System.out.println("Numero FALLIMENTI: " + failed);
			System.out.println("Tempo Totale FALLIMENTI: " + failedTime + " ms");
			medio = 0;
			if (failed > 0)
				medio = (double) failedTime / (double) failed;
			System.out.println("Tempo Medio FALLIMENTI: " + formatter.format(medio) + " ms\n");
			
		} 
		catch (Exception e) {
			e.printStackTrace();
		} 
	}
	
	/**
	 * Thread di avvio della conversione file tramite ImageMagick (in modo da verificare richieste di conversione concorrenti)
	 */
	private class FileConversionThread extends Thread {
		
		private String imagemagickCommand;
		private File from;
		private File to;
		
		private boolean done;
		private long spentTime;
		
		public FileConversionThread(String imagemagickCommand, File from, File to) {
			this.imagemagickCommand= imagemagickCommand;
			this.from = from;
			this.to = to;
		}
		
		@Override
		public void run() {
			try {
				long start = System.currentTimeMillis();
				
				ImageMagickConversionExecutor imConversionExecutor = new ImageMagickConversionExecutor(imagemagickCommand);

				// Conversione in PDF tramite ImageMagick
				File out = imConversionExecutor.convert(from, to.getParentFile());
				if (out != null)
					this.done = true;
				else
					this.done = false;
				
				if (this.done)
					System.out.println(from.getName() + ": conversion DONE");
				else
					System.out.println(from.getName() + ": conversion FAILED");
				
				this.spentTime = System.currentTimeMillis() - start;
			} 
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		public boolean isDone() {
			return done;
		}
		
		public long getSpentTime() {
			return spentTime;
		}
	}

}
