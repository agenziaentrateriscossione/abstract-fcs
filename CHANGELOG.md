# Change Log

## [6.0.7] - 2018-09-25

### Changed
- Aggiunti metodi abstract per il recupero della versione e data di rilascio dell'applicazione (scrittura su log della versione dell'implementazione di FCA)

## [6.0.6] - 2018-09-25

### Changed
- Aggiunto il file di configurazione di Tesseract per permettere la personalizzazione dei propri parametri di configurazione

## [6.0.5] - 2018-02-19

### Fixed
- Corretto il comando di avvio di conversione tramite ImageMagick su ambiente Windows

## [6.0.4] - 2018-02-16

### Fixed
- Corretto bug in conversione immagini in PDF tramite chiamata di convert a FCS (trasformazione lowercase dell'estensione di origine)

## [6.0.3] - 2018-02-09

### Fixed
- Corretto bug in controllo dello stato di conversione in caso di timeout su conversione da TIFF a PDF con ImageMagick

## [6.0.2] - 2018-02-08

### Fixed
- Corretta configurazione log4j2 per elminazione di vecchie copie dei file di log

## [6.0.1] - 2017-09-22

### Fixed
- Corretto bug in Lettura del file di output generato dalla conversione
- Corretto bug in caricamento dei parametri di attivazione (lettura sempre da singleton di configurazione)

## [6.0.0] - 2017-09-05

### Added
- Ridefinizione di FCS (File Conversion Service) per estrazione testo e conversione di files
- Implementazione indipendente dalla sorgente dati
- Definizione di una classe astratta FCS che deve essere implementata per esporre i servizi di estrazione testo e conversione di files per una specifica applicazione 
- Per il momento è possibile convertire i file solo verso il formato PDF