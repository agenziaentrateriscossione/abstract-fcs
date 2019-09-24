package it.tredi.fcs.test.conversion;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import org.jodconverter.office.DefaultOfficeManagerBuilder;
import org.jodconverter.office.OfficeException;
import org.jodconverter.office.OfficeManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import it.tredi.fcs.command.conversion.OpenOfficeConversionExecutor;

/**
 * Test di conversione su OpenOffice (o LibreOffice)
 */
public class OpenOfficeConversionTest {

	private DefaultOfficeManagerBuilder officeManagerBuild = new DefaultOfficeManagerBuilder();
    private OfficeManager officeManager;
	
    private int[] OO_PORTS = { 8100, 8101, 8102, 8103 };
    private int OO_TIMEOUT = 20000; //0;
    private String OO_HOMEDIR = "";
    
    private String OUT_DIR = "/tmp/pdf";
    
    /**
     * Avvio del manager di conversione per OpenOffice
     */
	@Before
	public void officeManagerStart() {
		try {
			if (OO_PORTS != null)
				officeManagerBuild.setPortNumbers(OO_PORTS);
			if (OO_TIMEOUT > 0)
				officeManagerBuild.setTaskExecutionTimeout(OO_TIMEOUT);
			if (OO_HOMEDIR != null && !OO_HOMEDIR.isEmpty())
				officeManagerBuild.setOfficeHome(OO_HOMEDIR);
			officeManager = officeManagerBuild.build();
			
			officeManager.start();
		} 
		catch (OfficeException e) {
			e.printStackTrace();
		}
	}

	/**
     * Arresto del manager di conversione per OpenOffice
     */
	@After
	public void officeManagerStop() {
		try {
			officeManager.stop();
		} 
		catch (OfficeException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Test di conversione di tutti i file presenti nella directory test/resources/doc
	 */
	@Test
	@Ignore
	public void testConversion() {
		NumberFormat formatter = new DecimalFormat("#0.00");
        List<Thread> threads = new ArrayList<Thread>();
        
        String fileName;
		try {
			long startTime = System.currentTimeMillis();
			
			URL fileUrl = Thread.currentThread().getContextClassLoader().getResource("doc2pdf.txt");
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

				FileConversionThread fcd = new FileConversionThread(officeManager, file, convertedFile);
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
	 * Thread di avvio della conversione file tramite OpenOffice (in modo da verificare richieste di conversione concorrenti)
	 */
	private class FileConversionThread extends Thread {
		
		private OfficeManager officeManager;
		private File from;
		private File to;
		
		private boolean done;
		private long spentTime;
		
		public FileConversionThread(OfficeManager officeManager, File from, File to) {
			this.officeManager= officeManager;
			this.from = from;
			this.to = to;
		}
		
		@Override
		public void run() {
			try {
				long start = System.currentTimeMillis();
				
				OpenOfficeConversionExecutor ooConversionExecutor = new OpenOfficeConversionExecutor(officeManager);
				
				// Conversione file in PDF/A
				File out = ooConversionExecutor.convertToPDFA(from, to.getParentFile());
				if (out != null)
					this.done = true;
				else
					this.done = false;
				
				// Conversione file in PDF 1.4
				//this.done = ooConversionExecutor.convert(from, to);

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
