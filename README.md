# ABSTRACT FCS


## Descrizione

Definizione del servizio [FCS](https://github.com/agenziaentrateriscossione/docway-fcs) (File Conversion Service) per estrazione testo, conversione (e confronto) di files. 

Il progetto contiene già tutta la business logic relativa alla comunicazione con [FCA](https://github.com/agenziaentrateriscossione/docway-fca) (o altro client Socket compatibile), all'estrazione di testo dagli allegati e alla conversione di files. Per poter essere utilizzato necessita l'estensione delle seguenti classi astratte:

### Fcs

Metodi da implementare:

- __public FcsThread getFcsThread(Socket clientSocket) throws Exception__: Istanzia l'implementazione applicativa del thread di FCS (_FcsThread_);
- __public void onRunException(Exception e)__: Metodo invocato in caso di catch di una eccezione bloccante (stop del servizio) su FCS;
- __public void onRunFinally()__: Metodo invocato sul finally dell'eccezione bloccante. Su questo metodo è possibile richiamare tutte le azioni da compiere prima dello stop del servizio.

__N.B.__: Per avviare il processo di elaborazione di FCS occorre invocare all'interno del _main()_ il metodo __run()__ della classe implementata che estende _Fcs_.

### FcsThread

Metodi da implementare:

- __public FcaCommandExecutor getFcaCommandExecutor(String id, String[] convTo, String additionalParams, File workDir) throws Exception__: Istanzia l'implementazione applicativa del CommandExecutor di FCA (_FcaCommandExecutor_)

### FcsCommandExecutor

Metodi da implementare:

- __public boolean saveDocumento(Documento documento) throws Exception__: Procedura di salvataggio del documento (aggiornamento del record con le informazioni su testo estratto e conversioni), file convertiti, ecc.
- __public Documento getDocumento(String id, File workDir) throws Exception__: Recupero di tutte le informazioni del record da elaborare (file sui quali eseguire l'estrazione del testo, file da convertire, ecc.)

### Esempio di estensione di Fcs

```
public class DummyFcs extends Fcs {
  private static final Logger logger = LogManager.getLogger(DummyFcs.class.getName());

  public static void main(String[] args) {
    try {
      DummyFcs dummyFcs = new DummyFcs();
      dummyFcs.run();

      if (logger.isInfoEnabled())
        logger.info("DummyFcs.main(): shutdown...");
      System.exit(0);
    }
    catch(Exception e) {
      logger.error("DummyFcs.main(): got exception... " + e.getMessage(), e);
      System.exit(1);
    }
  }

  public DummyFcs() throws Exception {
    super();
    if (logger.isInfoEnabled())
      logger.info("DummyFcs... specific configuration...");
  }

  @Override
  public FcsThread getFcsThread(Socket clientSocket) throws Exception {
    return new DummyFcsThread(clientSocket);
  }

  @Override
  public void onRunException(Exception e) {
    if (logger.isInfoEnabled())
      logger.info("DummyFcs... run exception...");
  }

  @Override
  public void onRunFinally() {
    if (logger.isInfoEnabled())
      logger.info("DummyFcs... run finally...");
  }

}

```

```
public class DummyFcsThread extends FcsThread {

  public DummyFcsThread(Socket client) {
    super(client);
  }

  @Override
  public FcaCommandExecutor getFcaCommandExecutor(String id, String[] convTo, String additionalParams, File workDir) throws Exception {
    return new DummyFcaCommandExecutor(id, convTo, additionalParams, workDir);
  }

}
```

```
public class DummyFcaCommandExecutor extends FcaCommandExecutor {

  public DummyFcaCommandExecutor(String docId, String[] convTo, String additionalParams, File workDir) throws Exception {
    super(docId, convTo, additionalParams, workDir);
  }

  @Override
  public boolean saveDocumento(Documento documento) throws Exception {
    // TODO aggiornamento del documento su database e caricamento delle conversioni effettuate
    return true;
  }

  @Override
  public Documento getDocumento(String id, File workDir) throws Exception {
    // TODO recupero del documento da elaborare (dato il suo id) e di tutti i file per i quali eseguire l'estrazione del testo e/o conversioni
    return null;
  }

}
```


## Prerequisiti

1. _Java8_


## Configurazione

Per configurare l'FCS occorre settare le properties presenti all'interno del file _it.tredi.abstract-fcs.properties_. 

__N.B.__: Per maggiori informazioni sulla configurazione si rimanda ai commenti presenti nel file di properties.
