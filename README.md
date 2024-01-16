# worth
Corso: Reti
Descrizione: Applicazione focalizzata sull’organizzazione e la gestione di progetti in modo collaborativo secondo la metodologia Kanban con chat IP
Multicast tramite datagrammi UDP

## Compilazione ed Esecuzione tramite IDE (Es. IntelliJ IDEA)
1. Esegui la "build project".
2. Avvia il server eseguendo `MainClassServer` dalla directory Server.
3. Avvia uno o più client (con possibilità di creare istanze multiple) eseguendo `MainClassClient` dalla directory Client.

Il progetto è stato strutturato con porte di default per semplificare il testing. In alternativa, le porte possono essere specificate da linea di comando.

## Compilazione tramite Maven

Il progetto può essere facilmente compilato tramite Maven. Segui i passaggi seguenti:

### Installazione Maven su Windows:

- Scarica il file zip di Maven
- Estrai il file scaricato e aggiungi il percorso della sottodirectory `bin` alle variabili di ambiente.

### Installazione Maven su Ubuntu:

sudo apt update
sudo apt install maven

### Utilizzo di Maven:

Da riga di comando, posizionati nella directory `./worth` e esegui:

mvn package

### Eliminare directory "target"

mvn clean

### Eseguire il server e il/i client:

java -jar ./Server/target/Server-jar-with-dependencies.jar
java -jar ./Client/target/Client-jar-with-dependencies.jar



