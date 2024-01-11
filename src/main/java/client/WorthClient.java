package client;

import Interfacce.RMIClientInterface;
import Interfacce.RMIEventManagerInterface;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Elia Fagioli matricola:580115
 * @version RELEASE
 */

public class WorthClient {
    private final ConcurrentHashMap<String, String> worthUsers = new ConcurrentHashMap<>();
    private String username = null;
    private String password = null;
    private String curr_projectName = null;
    private String curr_cardName = null;
    private final int BUFFER_DIM = 8192;
    private final int SOCKET_PORT;
    private boolean exit;
    Registry registry;
    RMIEventManagerInterface remoteEventManager;
    BufferedReader consoleReader;
    Scanner scanner;
    private RMIClient callbackAgent;
    private final ChatRegister chatRegister;
    private final ConcurrentHashMap<String, String> userProjects = new ConcurrentHashMap<>();

    /**
     * Costruttore client Worth
     * @param port indica la porta della connessione TCP con il server
     * @param rmiport è la porta del servizio RMI
     * @param c è il riferimento al ChatRegister istanziato nel main
     * @throws NotBoundException nel lookup RMI
     * @throws RemoteException nel getRegistry
     */
    public WorthClient(int port, int rmiport, ChatRegister c) throws NotBoundException, RemoteException {
        this.exit = false;
        chatRegister = c;
        this.SOCKET_PORT = port;
        this.registry = LocateRegistry.getRegistry(rmiport);
        remoteEventManager = (RMIEventManagerInterface) this.registry.lookup("RMI_EVENT_MANAGER");
        this.consoleReader = new BufferedReader(new InputStreamReader(System.in));
        this.scanner = new Scanner(System.in);
    }

    /**
     * Rappresenta l'architettura del Client
     * @throws InterruptedException joinGroup e wait
     */
    public void start() throws InterruptedException{
        //Instaura la connessione TCP con il server
        try ( SocketChannel client = SocketChannel.open(new InetSocketAddress(InetAddress.getLocalHost(), SOCKET_PORT)) ){
            do{
                System.out.print("Digitare:\n1 - Per eseguire il login\n2 - Per registrarsi\n");
                String msg = scanner.next();
                System.out.println("Hai scelto: "+msg);
                switch(msg){
                    case "1": //login
                        loginUser(client);
                        //registrazione servizio di notifica RMI
                        registerForCallback();
                        this.exit = true;
                    break;
                    case "2": //registrazione
                        signInUser();
                        //notifico l'avvenuta registrazione e ricarico il Document nel server
                        sendData("signin", client);
                        //una volta registrato effettuo il login direttamente
                        sendData2("login "+this.username + " " + this.password, client);
                        //registrazione servizio di notifica RMI
                        registerForCallback();
                        this.exit = true;
                    break;
                    default:
                        System.out.println("Operazione non valida! Inserire 1 o 2");
                    break;
                }
            }while(!this.exit);
            //Una volta connesso riceve la lista dei progetti di cui è membro dal server e si mette in ascolto su tutte le chat
            //Qui si inizializza la lista progetti utente
            sendData2("projects-list-address "+this.username, client);
            for(Entry<String,String> e : this.userProjects.entrySet()){
                chatRegister.joinGroup(e.getKey(), e.getValue());
            }
            //riutilizzo la variabile booleana per il menù Worth
            this.exit = false;
            while (!this.exit) {
                printMenu();
                String msg = scanner.next();
                System.out.println("Hai scelto: "+msg);
                switch(msg){
                    case "1"://logout ed esci
                        //non è necessario il ritorno boolean perché in questa sezione siamo obbligatoriamente connessi
                        sendData("logout "+this.username, client);
                        //esco dal servizio di notifica RMI
                        remoteEventManager.unregisterFromCallback(callbackAgent);
                        this.exit = true;
                    break;
                    case "2": //stampa lista utenti Worth dalla struttura locale
                        printUsers();
                    break;
                    case "3": //stampa lista utenti Online dalla struttura locale
                        printOnlineUsers();
                    break;
                    case "4": //crea un nuovo progetto
                        System.out.println("Inserire nome progetto da creare:");
                        msg = scanner.next();
                        //prova a creare il progetto se non ne esiste già uno con lo stesso nome
                        sendData2("newProject "+this.username+" "+msg, client);
                    break;
                    case "5": //stampa lista progetti
                        //può anche stampare la lista vuota
                        sendData("projects-list "+this.username, client);
                    break; 
                    case "6": //seleziona progetto
                        System.out.println("Inserire nome progetto selezionato:");
                        this.curr_projectName = scanner.next();
                        if(sendData2("enterProject "+this.username+" "+this.curr_projectName, client)){
                            //passo al menù progetto
                            enterProject(client);
                        } else {
                            System.out.println("Nome progetto non valido.");
                        }
                        //Se torno qui non sono + all'interno di un progetto.
                        this.curr_projectName = null;
                    break;
                    default:
                        System.out.println("Scelta non valida, inserire numero 1-6");
                    break;
                }
            }
            //Termino il client
            System.out.println("Client:> chiusura");
            client.close();
            System.exit(0);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Stampa la ConcurrentHashMap degli utenti con il relativo stato
     * */
    public synchronized void printUsers(){
        for(Entry<String, String> e : this.worthUsers.entrySet()){
            System.out.println(e.toString());
        }
    }

    /**
     * Stampa solo gli utenti attualmente online
     * */
    public synchronized void printOnlineUsers(){
        for(Entry<String, String> e : this.worthUsers.entrySet()){
            if(e.getValue().equals("ONLINE"))System.out.println(e);
        }
    }

    /**
     * Metodo login utente già registrato
     * @param c è il channel per il metodo sendData2
     * @throws IOException sendData2
     */
    public void loginUser(SocketChannel c) throws IOException {
        boolean bool = false;
        do{
            System.out.println("Inserire [username]:");
            String username = scanner.next();
            System.out.println("Inserire [password]:");
            String password = scanner.next();
            if(username!=null && password!=null) {
                //INVIO DATI AL SERVER E ATTENDO RISPOSTA
                if (sendData2("login " + username + " " + password, c)) {
                    this.username = username;
                    this.password = password;
                    bool = true;
                } else System.out.println("Errore! dati non corretti o utente già collegato!");
            } else {
                System.out.println("Errore inserimento dati!");
            }
        }while(!bool);
    }

    /**
     * Registrazione RMI utente
     */
    public void signInUser(){
        boolean bool = false;
        do{
            System.out.println("Inserire [username]:");
            String username = scanner.next();
            System.out.println("Inserire [password]:");
            String password = scanner.next();
            try {
                if(username!=null && password!=null){
                    if(remoteEventManager.register(username, password)){
                        this.username = username;
                        this.password = password;
                        bool = true;
                    } else {
                        System.out.println("Username già in uso.");
                    }
                } else {
                    System.out.println("Formato dati inseriti non corretto");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }while(!bool);
    }

    /**
     * metodo per inviare messaggi al Server
     * @param data è il messaggio da inviare al server
     * @param c channel per comunicare con il server
     * @throws IOException operazioni write e read sul channel
     */
    public void sendData(String data, SocketChannel c) throws IOException{
        // Creo il messaggio da inviare al server, la prima parte del messaggio contiene la lunghezza del messaggio
        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
        length.putInt(data.length());
        length.flip();
        c.write(length);
        length.clear();
        // la seconda parte del messaggio contiene il messaggio da inviare
        ByteBuffer readBuffer = ByteBuffer.wrap(data.getBytes());
        c.write(readBuffer);
        readBuffer.clear();
        //Risposta del server
        ByteBuffer reply = ByteBuffer.allocate(BUFFER_DIM);
        c.read(reply);
        reply.flip();
        System.out.printf("Server:> %s\n", new String(reply.array()).trim());
        reply.clear();
    }

    /**
     * metodo per inviare messaggi al Server che ritorna boolean
     * @param data è il messaggio da inviare al server
     * @param c channel per comunicare con il server
     * @return boolean per verificare la correttezza dell'operazione
     * @throws IOException operazioni read e write sul channel
     */
    public boolean sendData2(String data, SocketChannel c) throws IOException{
        // Creo il messaggio da inviare al server, la prima parte del messaggio contiene la lunghezza del messaggio
        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
        length.putInt(data.length());
        length.flip();
        c.write(length);
        length.clear();
        // la seconda parte del messaggio contiene il messaggio da inviare
        ByteBuffer readBuffer = ByteBuffer.wrap(data.getBytes());
        c.write(readBuffer);
        readBuffer.clear();
        //Leggo risposta del server
        ByteBuffer reply = ByteBuffer.allocate(BUFFER_DIM);
        c.read(reply);
        reply.flip();
        String s = new String(reply.array()).trim();
        //In base alla stringa di risposta:
        if(s.startsWith("ERROR")){
            //Stampo il messaggio di errore
            System.out.println(s);
            return false;
        } else if(s.startsWith("{")){
            receiveList(s);
        } else if(s.startsWith("ProjectsList")){
            if(s.length()>13){
                //rimuovo "ProjectsList;"
                s = s.substring(13);
                String[] projects = s.split(";");
                for(String p : projects){
                    String[] split= p.split("=");
                    this.userProjects.putIfAbsent(split[0], split[1]);
                }
            }            
        } else if(s.startsWith("TODO")){
            String[] lists = s.split("-");
            for(String list: lists){
                System.out.println(list);
            }
        } else if((s.startsWith("Membri progetto:"))||(s.startsWith("Nome card:"))||(s.startsWith("Storia movimenti card:"))){
            System.out.println(s);
        }
        reply.clear();
        return true;
    }

    /**
     * metodo per inserire messaggio del server nella hashmap degli utenti con relativo status
     * @param s messaggio del server
     */
    public void receiveList(String s){
        String str = s.replace("{", "");
        str = str.replace("}", "");
        str = str.replaceAll(" ", "");
        String[] users = str.split(",");
        for(String x : users){
            String[] split = x.split("=");
            this.worthUsers.put(split[0], split[1]);
        }
    }

    /**
     * menù principale Worth - post login
     */
    private void printMenu() {
        System.out.println("---------------  Worth Operations   ---------------");
        System.out.println("1   |    logout ed esci                            |");
        System.out.println("2   |    Stampare lista utenti Worth               |");
        System.out.println("3   |    Stampare lista utenti online Worth        |");
        System.out.println("4   |    creare nuovo progetto                     |");
        System.out.println("5   |    stampa lista progetti                     |");
        System.out.println("6   |    seleziona progetto                        |");
        System.out.println("---------------------------------------------------");

    }

    /**
     * menù post selezione progetto
     */
    private void printMenu_Project_Operations() {
        System.out.println("--------------- Project Operations ---------------");
        System.out.println("0   |    Torna indietro");
        System.out.println("1   |    Lista membri progetto");
        System.out.println("2   |    Aggiungi membro al progetto");
        System.out.println("3   |    Cancella progetto");
        System.out.println("4   |    Stampa liste");
        System.out.println("5   |    Nuova card");
        System.out.println("6   |    Seleziona card");
        System.out.println("7   |    Invia messaggio alla chat di progetto");
        System.out.println("8   |    Leggi la chat di progetto");
        System.out.println("--------------------------------------------------");

    }

    /**
     * menù post selezione card nel progetto
     */
    private void printMenu_Card_Operations() {
        System.out.println("---------------  Card Operations   ---------------");
        System.out.println("0   |    Torna indietro");
        System.out.println("1   |    Sposta in altra lista");
        System.out.println("2   |    Mostra info card");
        System.out.println("3   |    Mostra storia card");
        System.out.println("--------------------------------------------------");

    }

    /**
     * parte dell'architettura worth con le operazioni all'interno di un progetto
     * @param c channel comunicazione server
     * @throws IOException sendData2, metodi consoleReader e messaggi chat
     * @throws InterruptedException nella wait della lettura messaggi della chat
     */
    private synchronized void enterProject(SocketChannel c) throws IOException, InterruptedException {
        boolean back = false;
        while (!back) {
            printMenu_Project_Operations();
            String msg = scanner.next();
            System.out.println("Hai scelto: "+msg);
            switch(msg){
                case "0":
                    back=true;
                break;
                case "1": //lista membri progetto
                    if(!sendData2("members_list "+this.curr_projectName, c))back=true;
                break;
                case "2": //aggiungi membro al progetto
                    System.out.println("Inserire membro da aggiungere:");
                    msg = scanner.next();
                    if(sendData2("addMember "+msg+ " "+this.curr_projectName, c)){
                        chatRegister.sendMessage(this.curr_projectName, this.username, "Membro aggiunto al progetto: "+msg);
                    } else {
                        back = true;
                    }
                break;
                case "3": //cancella progetto
                    System.out.println("Conferma eliminazione progetto: si/no");
                    msg = scanner.next();
                    if(msg.equals("si")){
                        if(sendData2("deleteProject "+curr_projectName, c)){
                            chatRegister.sendMessage(curr_projectName, this.username, "delete "+ this.curr_projectName);
                            back = true;
                        }
                    }
                break;
                case "4": //stampa liste
                    if(!sendData2("showLists "+this.curr_projectName,c))back=true;
                break;
                case "5"://crea card in lista TODO
                    System.out.println("Inserire nome card:");
                    String nomeCard = scanner.next();
                    System.out.println("Inserire descrizione card");
                    String descCard = consoleReader.readLine();
                    descCard = descCard.replaceAll(" ", "-");
                    if(sendData2("addCard "+ this.curr_projectName + " " + nomeCard + " " + descCard, c)){
                        chatRegister.sendMessage(this.curr_projectName, this.username, "Creazione card: "+nomeCard);
                    } else {
                        back = true;
                    }
                break;
                case "6"://seleziona card
                    System.out.println("Inserire nome card selezionata:");
                    this.curr_cardName = scanner.next();
                    if(sendData2("enterCard "+this.curr_projectName+" "+this.curr_cardName, c)){
                        enterCard(c);
                    } else {
                        this.curr_cardName = null;
                        back = true;
                    }
                    this.curr_cardName = null;
                break;
                case "7": //invia messaggio alla chat di gruppo
                    System.out.println("Inserire messaggio da inviare nella chat di progetto:");
                    msg = consoleReader.readLine();
                    if(!chatRegister.sendMessage(this.curr_projectName, this.username, msg)){
                        System.out.println("Il progetto non è più attivo");
                        back = true;
                    }
                break;
                case "8": // ricevi messaggi chat di gruppo
                    List<String> listMessages = chatRegister.getProjectMessages(curr_projectName);
                    if(listMessages == null){
                        System.out.println("Il progetto non è più attivo");
                        back = true;
                    } else {
                        for(String s: listMessages){
                            System.out.println(s);
                        }
                        wait(2000);
                    }
                break;
                default:
                    System.out.println("Scelta non valida, inserire numero 0-8");
                break;
            }
        }
    }

    /**
     * Parte dell'architettura con operazioni sulla singola card
     * @param c per comunicare con il server
     * @throws IOException sendData2 e sendMessage per notifica spostamenti
     */
    private synchronized void enterCard(SocketChannel c) throws IOException{
        boolean back = false;
        while (!back) {
            printMenu_Card_Operations();
            String msg = scanner.next();
            System.out.println("Hai scelto: "+msg);
            switch(msg){
                case "0": //indietro
                    back = true;
                break;
                case "1": //sposta in altra lista
                    System.out.printf("Inserire lista destinazione %s:\n", this.curr_cardName);
                    String listaDestinazione = scanner.next().toUpperCase();
                    if(sendData2("moveCard "+ this.curr_projectName + " " + this.curr_cardName + " " + listaDestinazione, c)) {
                        chatRegister.sendMessage(this.curr_projectName, this.username, "Card: " + this.curr_cardName + "spostata in " + listaDestinazione);
                    } else {
                        back = true;
                    }
                break;
                case "2": //mostra info card
                    if(!sendData2("showCard "+this.curr_projectName + " " + this.curr_cardName, c)){
                        back = true;
                    }
                break;
                case "3": //mostra storia card
                    if(!sendData2("getCardHistory "+this.curr_projectName + " " + this.curr_cardName, c)){
                        back = true;
                    }
                break;
                default:
                    System.out.println("Scelta non valida, inserire numero 0-4");
                break;
            }
        }
    }

    /**
     * Metodo per unirsi al servizio di notifica RMI
     */
    private void registerForCallback(){
        callbackAgent = new RMIClient(worthUsers, this.username, this.chatRegister);

        try {
            RMIClientInterface stub = (RMIClientInterface) UnicastRemoteObject.exportObject(callbackAgent, 0);
            remoteEventManager.registerForCallback(stub);

        }catch (RemoteException e){
            e.printStackTrace();
            System.out.println("> ERROR: (RMI) cannot subscribe to server callback");
        }
    }
}
