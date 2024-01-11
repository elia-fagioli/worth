package server;

import Interfacce.RMIEventManagerInterface;
import Interfacce.ServerInterface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.apache.commons.io.FileUtils;

/**
 * @author Elia Fagioli matricola:580115
 * @version RELEASE
 */

public class WorthServer implements ServerInterface {
    private final String off = "OFFLINE";
    private final String on = "ONLINE";
    private final int RMI_PORT;
    private final int SOCKET_PORT;
    private static final int BUFFER_DIM = 8192;
    private Document users_document;
    private final String DB_PATH = Paths.get(".").toAbsolutePath().normalize().toString()+File.separator+"db";
    private final String USERS_PATH = DB_PATH+File.separator+"utenti.xml";
    private final String PROJECTS_PATH = DB_PATH+File.separator+"Progetti";
    private final String EXIT_CMD = "exit";
    private RMIEventManager eventManager = null;
    private final ConcurrentHashMap<String, String> worthUsers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> worthProjects = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SocketAddress, String> clients = new ConcurrentHashMap<>();
    /**
     * Costruttore Server Worth
     */
    public WorthServer(int port, int rmiport){
        this.SOCKET_PORT = port;
        this.RMI_PORT = rmiport;
        try {
            create_db();
            //Creo un SAXBuilder e con esso costruisco un document
            SAXBuilder builder = new SAXBuilder();
            this.users_document = builder.build(new File(USERS_PATH));
            List<Element> list = this.users_document.getRootElement().getChildren("Utente");
            //ConcurrentHashMap utenti e progetti
            for(Element e : list){
                String s = e.getChild("username").getText();
                this.worthUsers.put(s, off);
            }
        } catch (JDOMException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Utilizzato per creare la directory db Database di Worth
     * @throws IOException nell'utilizzo di File e di stream
     */
    public synchronized void create_db() throws IOException{
        File f;
        f = new File(DB_PATH);
        if(f.mkdir()){ //alla prima esecuzione di Worth viene creata la directory db
            System.out.println("Creata cartella db");
        }
        f = new File(PROJECTS_PATH);
        if(f.mkdir()){//Se non esiste la directory Progetti la crea ed è vuota
            System.out.println("Creata cartella Progetti");
        } else {
            //altrimenti genera un indirizzo multicast per ogni sotto-directory progetto al suo interno
            for (String s : f.list()) {
                this.worthProjects.put(s, Objects.requireNonNull(MulticastGenerator.getNewMulticastAddress()));
            }
        }
        f = new File(USERS_PATH);
        if(f.createNewFile()) { // se non esiste il file utenti.xml lo genera
            Element root = new Element("utenti");
            Document document = new Document(root);
            XMLOutputter outputter = new XMLOutputter();
            outputter.setFormat(Format.getPrettyFormat().setExpandEmptyElements(true));
            FileOutputStream fout = new FileOutputStream(USERS_PATH);
            outputter.output(document, fout);
            fout.close();
        }
    }

    /**
     * Corpo del server in attesa di ricevere connessioni e di leggere messaggi dai client
     * @throws JDOMException nel richiamare readClientMessage
     */
    public void start() throws JDOMException{
        //Avvio il server RMI
        rmiRegister();
        try (ServerSocketChannel s_channel = ServerSocketChannel.open()){
            s_channel.socket().bind(new InetSocketAddress(this.SOCKET_PORT));
            s_channel.configureBlocking(false);
            Selector sel = Selector.open();
            s_channel.register(sel, SelectionKey.OP_ACCEPT);
            System.out.printf("Server:> in attesa di connessioni sulla porta %d\n", this.SOCKET_PORT);
            while(true){
                if (sel.select() == 0)
                    continue;
                // insieme delle chiavi corrispondenti a canali pronti
                Set<SelectionKey> selectedKeys = sel.selectedKeys();
                // iteratore dell'insieme sopra definito
                Iterator<SelectionKey> iter = selectedKeys.iterator();
                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();
                    try { // utilizzo la try-catch per gestire la terminazione improvvisa del client
                        if (key.isAcceptable()) {// ACCETTABLE
                            /*
                             * accetta una nuova connessione creando un SocketChannel per la
                             * comunicazione con il client che la richiede
                             */
                            ServerSocketChannel server = (ServerSocketChannel) key.channel();
                            SocketChannel c_channel = server.accept();
                            c_channel.configureBlocking(false);
                            System.out.println("Server:> accettata nuova connessione dal client: " + c_channel.getRemoteAddress());
                            this.clients.put(c_channel.getRemoteAddress(), "");
                            this.registerRead(sel, c_channel);
                        } else if (key.isWritable()) {// WRITABLE
                            this.sendAnswer(sel, key);
                        } else if (key.isReadable()) {// READABLE
                            this.readClientMessage(sel, key);
                        }                        
                    }
                    catch (IOException e) {// terminazione improvvisa del client
                        e.printStackTrace();
                        SocketChannel user = (SocketChannel) key.channel();
                        System.out.printf("Ricevuta terminazione improvvisa client %s\n", user.getRemoteAddress());
                        //disconnessione
                        String username = this.clients.get(user.getRemoteAddress());
                        if(!username.equals("")){
                            logout(username);
                        }
                        this.clients.remove(user.getRemoteAddress());
                        //
                        key.channel().close();
                        key.cancel();
                    }
                }
            }
        }
        catch (IOException e){
            e.printStackTrace();
        }
        
    }

    /**
     * registra l'interesse all'operazione di READ sul selettore
     *
     * @param sel selettore utilizzato dal server
     * @param c_channel socket channel relativo al client
     * @throws IOException se si verifica un errore di I/O
     */
    private void registerRead(Selector sel, SocketChannel c_channel) throws IOException {
        // crea il buffer
        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
        ByteBuffer message = ByteBuffer.allocate(BUFFER_DIM);
        ByteBuffer[] bfs = {length, message};
        // aggiunge il canale del client al selector con l'operazione OP_READ
        // e aggiunge l'array di bytebuffer [length, message] come attachment
        c_channel.register(sel, SelectionKey.OP_READ, bfs);
    }

    /**
     * legge il messaggio inviato dal client e registra l'interesse all'operazione di WRITE sul selettore
     * @param sel selettore utilizzato dal server
     * @param key chiave di selezione
     * @throws IOException se si verifica un errore di I/O
     */
    private void readClientMessage(Selector sel, SelectionKey key) throws IOException, JDOMException {
        /*
         * accetta una nuova connessione creando un SocketChannel per la comunicazione con il client che la richiede
         */
        SocketChannel c_channel = (SocketChannel) key.channel();
        // recupera l'array di bytebuffer (attachment)
        ByteBuffer[] bfs = (ByteBuffer[]) key.attachment();
        c_channel.read(bfs);
        if (!bfs[0].hasRemaining()){
            bfs[0].flip();
            int l = bfs[0].getInt();
            if (bfs[1].position() == l) {
                bfs[1].flip();
                //Inviato in risposta al client:
                StringBuilder msg = new StringBuilder(new String(bfs[1].array()).trim());
                //split sul carattere spazio per dividere i dati ricevuti dal client
                String[] data = msg.toString().split(" ");
                switch(data[0]){
                    case "login"://operazione di login
                        if(!login(data[1], data[2])){
                            //Se la funzione di login non va a buon fine restituisco questo messaggio di errore
                            msg = new StringBuilder("ERROR-WHILE-LOGGING-IN");
                        } else {
                            //Altrimenti invio al client le informazioni sugli utenti con i relativi stati
                            this.clients.replace(c_channel.getRemoteAddress(), data[1]);
                            msg = new StringBuilder(this.worthUsers.toString());
                        }
                    break;
                    case "projects-list-address"://Invio lista progetti e indirizzi chat Multicast
                        List<String> pList = listProjects(data[1]);
                        //Stringa di risposta contenente insieme vuoto o lista di progetti di cui l'utente è membro
                        msg = new StringBuilder("ProjectsList");
                        for(String s : pList){
                            msg.append(";").append(s).append("=").append(this.worthProjects.get(s));
                        }
                    break;
                    case "signin"://Quando il client invia questo la registrazione è andata a buon fine
                        //aggiorno il Document locale
                        utentiReload();
                    break;
                    case "logout"://Operazione di logout dell'utente
                        logout(data[1]);
                        msg = new StringBuilder(this.EXIT_CMD);
                    break;
                    case "newProject"://Se la creazione del progetto non va a buon fine invio messaggio di errore
                        if(!createProject(data[1], data[2])){
                            msg = new StringBuilder("ERROR-WHILE-CREATING-PROJECT");
                        }
                    break;
                    case "projects-list": //ricavo lista progetti utente dal file utenti.xml
                        List<String> projects = listProjects(data[1]);
                        msg = new StringBuilder("Lista progetti utente: "+projects.toString());
                    break;
                    case "enterProject"://Verifica esistenza progetto
                        if(!listProjects(data[1]).contains(data[2]))
                            //Se il progetto non esiste più o l'utente non è un membro:
                            msg = new StringBuilder("ERROR-WHILE-ACCESSING-PROJECT");
                    break;
                    case "members_list": //recupero dal progetto il file membri.xml
                        if(this.projectSearch(data[1])){
                            List<String> members = showMembers(data[1]);
                            msg = new StringBuilder("Membri progetto:"+members.toString());
                        } else {
                            //Se è stato eliminato invio messaggio di errore
                            msg = new StringBuilder("ERROR-WHILE-LOADING-PROJECT");
                        }
                    break;
                    case "addMember": //Operazione di aggiunta membro
                        if(!addMember(data[1], data[2])) msg = new StringBuilder("ERROR-WHILE-ADDING-MEMBER");
                    break;
                    case "addCard": //Operazione di creazione card nella lista TODO
                        data[3] = data[3].replaceAll("-", " ");
                        if(!addCard(data[1], data[2], data[3])) msg = new StringBuilder("ERROR-WHILE-ADDING-CARD");
                    break;
                    case "deleteProject"://Elimino il progetto se tutte le card sono nella lista DONE
                        if(!cancelProject(data[1])) msg = new StringBuilder("ERROR-WHILE-DELETING-PROJECT");
                    break;
                    case "showLists": //Stampo il contenuto delle directory delle liste
                        if(projectSearch(data[1])){
                            msg = new StringBuilder(showCards(data[1]));
                        } else {
                            //Se il progetto non è più disponibile restituisco messaggio di errore
                            msg = new StringBuilder("ERROR-WHILE-LOADING-PROJECT");
                        }
                    break;
                    case "enterCard"://Verifico esistenza card nel progetto
                        if(this.cardSearch(data[1], data[2]) == null){
                            msg = new StringBuilder("ERROR-WHILLE-RETRIEVING-THE-CARD");
                        }
                    break;
                    case "moveCard"://Sposto card rispettando grafico spostamenti
                        if(!moveCard(data[1], data[2], data[3])) msg = new StringBuilder("ERROR-WHILE-MOVING-CARD");
                    break;
                    case "showCard"://Recupero le info della card (nome, descrizione, lista attuale)
                        if(cardSearch(data[1], data[2])!=null){
                            msg = new StringBuilder(showCard(data[1], data[2]));
                        } else {
                            msg = new StringBuilder("ERROR-WHILE-RETRIEVING-CARD");
                        }                        
                    break;
                    case "getCardHistory": //Recupero la lista degli spostamenti della card
                        if(cardSearch(data[1], data[2])!=null){
                            msg = new StringBuilder(getCardHistory(data[1], data[2]));
                        } else {
                            msg = new StringBuilder("ERROR-WHILE-RETRIEVING-CARD");
                        }
                    break;
                }
                //In caso di logout msg = EXIT_CMD
                if (msg.toString().equals(this.EXIT_CMD)){
                    System.out.println("Server:> chiusa la connessione con il client " + c_channel.getRemoteAddress());
                    c_channel.close();
                    key.cancel();
                } else { //invio risposta al client
                    /*
                     * aggiunge il canale del client al selector con l'operazione OP_WRITE
                     * e aggiunge il messaggio ricevuto come attachment (aggiungendo la risposta addizionale)
                     */
                    c_channel.register(sel, SelectionKey.OP_WRITE, msg.toString());
                }
            }
        }
    }

    /**
     * scrive il buffer sul canale del client
     * @param key chiave di selezione
     * @throws IOException se si verifica un errore di I/O
     */
    private void sendAnswer(Selector sel, SelectionKey key) throws IOException {
        SocketChannel c_channel = (SocketChannel) key.channel();
        String echoAnsw= (String) key.attachment();
        ByteBuffer bbEchoAnsw = ByteBuffer.wrap(echoAnsw.getBytes());
        c_channel.write(bbEchoAnsw);
        System.out.println("Client:> " + echoAnsw + " inviato dal client " + c_channel.getRemoteAddress());
        if (!bbEchoAnsw.hasRemaining()) {
            bbEchoAnsw.clear();
            this.registerRead(sel, c_channel);
        }
    }

    /**
     * Server pronto per registrare utenti
     */
    private void rmiRegister(){
        try {
            eventManager = new RMIEventManager(worthUsers, USERS_PATH);
            // esporta l'oggetto
            RMIEventManagerInterface stub = (RMIEventManagerInterface) UnicastRemoteObject.exportObject(eventManager, RMI_PORT);
            // crea il registro
            LocateRegistry.createRegistry(RMI_PORT);
            Registry register = LocateRegistry.getRegistry(RMI_PORT);
            // binding
            register.rebind("RMI_EVENT_MANAGER", stub);
            //Server pronto alla registrazione
        }
        catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * In caso di modifiche richiamo questo metodo per aggiornare il Document utenti locale
     * @throws JDOMException builder
     * @throws IOException File
     */
    public synchronized void utentiReload() throws JDOMException, IOException{
        SAXBuilder builder = new SAXBuilder();
        this.users_document = builder.build(new File(USERS_PATH));
    }

    /**
     * Sovrascrivo il file utenti.xml con il document locale
     * @throws IOException
     */
    public synchronized void utentiUpdate() throws IOException{
        XMLOutputter outputter = new XMLOutputter();
        //Imposto il formato dell'outputter come "bel formato"
        outputter.setFormat(Format.getPrettyFormat().setExpandEmptyElements(true));
        //Produco l'output sul file xml.foo
        FileOutputStream f = new FileOutputStream(USERS_PATH);
        outputter.output(users_document, f);
        f.close();
        System.out.println("File utenti.xml aggiornato con successo.");
    }

    /**
     * Richiamata quando sposto una card per aggiornarla
     * @param c_path path della card
     * @param dest lista destinazione
     * @throws IOException
     * @throws JDOMException
     */
    public synchronized void cardUpdate(String c_path, String dest) throws IOException, JDOMException{
        SAXBuilder builder = new SAXBuilder();
        Document card_document = builder.build(new File(c_path));
        Element root = card_document.getRootElement();
        root.getChild("listaAttuale").setText(dest);
        Element lista = new Element("lista").setText(dest);
        root.getChild("storia").addContent(lista);
        XMLOutputter outputter = new XMLOutputter();
        //Imposto il formato dell'outputter come "bel formato"
        outputter.setFormat(Format.getPrettyFormat().setExpandEmptyElements(true));
        //Produco l'output sul file xml.foo
        FileOutputStream f = new FileOutputStream(c_path);
        outputter.output(card_document, f);
        f.close();
        System.out.println("card aggiornata:");
    }

    /**
     * Verifico presenza utente nel file utenti.xml
     * @param nick utente da cercare
     * @return ritorno indice nel document locale
     */
    public synchronized int search(String nick){
        int i = 0;
        Element root = users_document.getRootElement();
        //Estraggo i figli dalla radice Utenti
        List<Element> children = root.getChildren();
        Iterator<Element> iterator = children.iterator();
        //Per ogni figlio
        while(iterator.hasNext()){
            Element item = iterator.next();
            Element username = item.getChild("username");
            if(nick.equals(username.getText())) return i;
            i++;
        }
        return -1;
    }

    /**
     * Verifico esistenza progetto
     * @param projectName nome progetto da cercare
     */
    public synchronized boolean projectSearch(String projectName){
        File f = new File(PROJECTS_PATH+File.separator+projectName);
        return f.exists();
    }

    /**
     * Ricerco card nel progetto
     * @param projectName nome progetto in cui cercare
     * @param cardName nome card da cercare
     * @return la lista in cui è presente la card
     */
    public synchronized String cardSearch(String projectName, String cardName){
        String p_path = PROJECTS_PATH+File.separator+projectName+File.separator;
        File f = new File(p_path+"TODO"+File.separator+cardName);
        if(f.exists())return "TODO";
        f = new File(p_path+"TOBEREVISED"+File.separator+cardName);
        if(f.exists())return "TOBEREVISED";
        f = new File(p_path+"INPROGRESS"+File.separator+cardName);
        if(f.exists())return "INPROGRESS";
        f = new File(p_path+"DONE"+File.separator+cardName);
        if(f.exists())return "DONE";
        return null;
    }

    /**
     * login di un utente già registrato per accedere al servizio.
     * - esito positivo: confronterà le password e se andrà a buon fine effettuerà il login
     * - esito negativo (utente non presente o password errata): restituirà messaggio di errore password errata
     * @param nickUtente username inserito
     * @param password password inserita
     * @throws IOException utentiUpdate() e metodo updateUsers RMI
     */
    public synchronized boolean login(String nickUtente, String password) throws IOException{
        try {
            utentiReload();
        } catch (Exception e) {
            e.printStackTrace();
        }
        int i = search(nickUtente);
        if(i!=-1){
            Element root = users_document.getRootElement();
            //Estraggo i figli dalla radice Utenti
            List<Element> children = root.getChildren();
            Element item = children.get(i);
            if(item.getChild("status").getText().equals(this.off)){
                if(item.getChild("password").getText().equals(password)){
                    item.getChild("status").setText(on);
                    utentiUpdate();
                    this.worthUsers.replace(nickUtente, off, on);
                    this.eventManager.updateUsers(nickUtente, on);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * effettua il logout dell’utente dal servizio.
     */
    public synchronized void logout(String nickUtente) throws IOException{
        //non ci sono condizioni perché il logout è attuabile solo se l'utente è loggato
        int i = search(nickUtente);
        Element root = users_document.getRootElement();
        //Estraggo i figli dalla radice Utenti
        List<Element> children = root.getChildren();
        Element item = children.get(i);
        item.getChild("status").setText(off);
        utentiUpdate();
        this.eventManager.updateUsers(nickUtente, off);
    }

    /**
     * Va a recuperare la lista degli utenti nella ConcurrentHashMap
     * @return lista utenti e relativo status
     */
    @Override
    public synchronized List<String> listUsers(){
        List<String> users = new ArrayList<>();
        for(Entry<String, String> e : this.worthUsers.entrySet()){
            users.add(e.getKey()+"-"+e.getValue());
        }
        return Collections.synchronizedList(users);
    }

    /**
     * Va a recuperare la lista degli utenti online nella ConcurrentHashMap
     * @return lista utenti online
     */
    @Override
    public synchronized List<String> listOnlineUsers(){
        List<String> users = new ArrayList<>();
        for(Entry<String, String> e : this.worthUsers.entrySet()){
            if(e.getValue().equals(on))users.add(e.getKey()+"-"+e.getValue());
        }
        return Collections.synchronizedList(users);
    }

    /**
     * Recupera dal Document locale i progetti dell'utente
     * @param nickUtente utente che richiede la lista
     * @return contenuto campo <progetti></progetti> nel Document nel figlio nickUtente
     */
    @Override
    public synchronized List<String> listProjects(String nickUtente) {
        if(nickUtente != null){
            int i = search(nickUtente);
            Element root = users_document.getRootElement();
            //Estraggo i figli dalla radice Utenti
            List<Element> children = root.getChildren();
            Element item = children.get(i);
            children = item.getChild("progetti").getChildren();
            List<String> progetti = new ArrayList<>();
            Iterator<Element> iterator = children.iterator();
            //Per ogni figlio
            while(iterator.hasNext()){
                Element progetto = iterator.next();
                String p_name = progetto.getText();
                progetti.add(p_name);
            }
            return Collections.synchronizedList(progetti);
        }
        return null;
    }

    /**
     * Metodo per la creazione di un progetto
     * @param nickUtente utente che richiede la creazione del progetto
     * @param projectName nome progetto da creare
     * @return true se la creazione va a buon fine
     * @throws IOException in caso di errori di I/O
     */
    @Override
    public synchronized boolean createProject(String nickUtente, String projectName) throws IOException {
        //if((nickUtente == null)||(projectName == null)) return false;
        int i = search(nickUtente);
        //Se l'utente non esiste o il progetto esiste già:
        if((i==-1)||(projectSearch(projectName))) return false;
        //Vado ad aggiungere nel Document il progetto che sto creando
        Element progetto = new Element("progetto");
        progetto.setText(projectName);
        users_document.getRootElement().getChildren().get(i).getChild("progetti").addContent(progetto);
        utentiUpdate();
        //Creazione cartella progetto
        String p_path = PROJECTS_PATH+File.separator+projectName;
        File file = new File(p_path);
        file.mkdir();
        file = new File(p_path+File.separator+"TODO");
        file.mkdir();
        file = new File(p_path+File.separator+"TOBEREVISED");
        file.mkdir();
        file = new File(p_path+File.separator+"INPROGRESS");
        file.mkdir();
        file = new File(p_path+File.separator+"DONE");
        file.mkdir();
        //File membri.xml nella dir del progetto creato
        file = new File(p_path+File.separator+"membri.xml");
        file.createNewFile();
        Element root_m = new Element("Utenti");
        Document p_members = new Document(root_m);
        Element user = new Element("utente").setText(nickUtente);
        p_members.getRootElement().addContent(user);
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat().setExpandEmptyElements(true));
        FileOutputStream f = new FileOutputStream(p_path+File.separator+"membri.xml");
        outputter.output(p_members, f);
        f.close();
        //Genero indirizzo multicast del progetto
        String pAddress = MulticastGenerator.getNewMulticastAddress();
        this.worthProjects.putIfAbsent(projectName, pAddress);
        eventManager.updateChat(nickUtente, projectName, pAddress);
        return true;
    }

    /**
     * Aggiungo membro al progetto
     * @param nickUtente utente da aggiungere a un progetto
     * @param projectName nome progetto
     * @return true se va a buon fine, false altrimenti
     * @throws JDOMException nella build del Document
     * @throws IOException in caso di errori di I/O
     */
    @Override
    public synchronized boolean addMember(String nickUtente, String projectName) throws JDOMException, IOException{
        int i = search(nickUtente);
        if((i==-1)||(!this.projectSearch(projectName))) return false;
        //aggiunta a membri.xml
        SAXBuilder builder = new SAXBuilder();
        Document p_members = builder.build(new File(this.PROJECTS_PATH+File.separator+projectName+File.separator+"membri.xml"));
        p_members.getRootElement().addContent(new Element("utente").setText(nickUtente));
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat().setExpandEmptyElements(true));
        FileOutputStream f = new FileOutputStream(this.PROJECTS_PATH+File.separator+projectName+File.separator+"membri.xml");
        outputter.output(p_members, f);
        f.close();
        //aggiunta a utenti.xml sezione progetti
        Element progetto = new Element("progetto");
        progetto.setText(projectName);
        users_document.getRootElement().getChildren().get(i).getChild("progetti").addContent(progetto);
        utentiUpdate();
        eventManager.updateChat(nickUtente, projectName, this.worthProjects.get(projectName));
        return true;
    }

    /**
     * Metodo per conoscere i membri di un progetto
     * @param projectName progetto di cui voglio conoscere i membri
     * @return lista membri progetto
     * @throws JDOMException sul build
     * @throws IOException operazioni di I/O
     */
    @Override
    public synchronized List<String> showMembers(String projectName) throws JDOMException, IOException {
        SAXBuilder builder = new SAXBuilder();
        Document p_members = builder.build(new File(this.PROJECTS_PATH+File.separator+projectName+File.separator+"membri.xml"));
        Element root = p_members.getRootElement();
        List<Element> children = root.getChildren();
        Iterator<Element> iterator = children.iterator();
        //Per ogni figlio
        List<String> members = new ArrayList<>();
        while(iterator.hasNext()){
            Element member = iterator.next();
            members.add(member.getText());
        }
        return members;
    }

    /**
     * Stampa liste progetto
     * @param projectName nome progetto di cui stampare le liste
     * @return liste progetto
     */
    @Override
    public synchronized String showCards(String projectName) {
        String lists = "";
        String p_path = this.PROJECTS_PATH + File.separator + projectName + File.separator;
        lists = lists+"TODO:"+ Arrays.toString(new File(p_path+"TODO").list())+"-";
        lists = lists+"INPROGRESS:"+ Arrays.toString(new File(p_path+"INPROGRESS").list())+"-";
        lists = lists+"TOBEREVISED:"+ Arrays.toString(new File(p_path+"TOBEREVISED").list())+"-";
        lists = lists+"DONE:"+ Arrays.toString(new File(p_path+"DONE").list());
        return lists;
    }

    /**
     * Metodo per recuperare le info della card, ritorna null se la card non esiste.
     * @param projectName nome progetto
     * @param cardName nome card
     * @return lista attuale e descrizione card
     */
    @Override
    public synchronized String showCard(String projectName, String cardName) {
        String msg = null;
        try{
            msg = "Nome card: " + cardName;
            String list = cardSearch(projectName, cardName);
            msg = msg + " | Lista attuale: " + list;
            String c_path = this.PROJECTS_PATH+File.separator+projectName+File.separator+list+File.separator+cardName;
            SAXBuilder builder = new SAXBuilder();
            Document doc = builder.build(new File(c_path));
            msg = msg + " | Descrizione card: " + doc.getRootElement().getChild("descrizione").getText();
            //ritornare nome lista attuale e descrizione
        } catch(Exception e){
            e.printStackTrace();
        }
        return msg;
    }

    /**
     * Crea una card nella lista TODO del progetto
     * @param projectName nome progetto
     * @param cardName nome card
     * @param descrizione descrizione card
     * @return true se va a buon fine
     */
    @Override
    public synchronized boolean addCard(String projectName, String cardName, String descrizione) {
        if(cardSearch(projectName, cardName)!=null) return false;
        String c_path = PROJECTS_PATH + File.separator + projectName + File.separator + "TODO" + File.separator + cardName;
        Element root = new Element("card");
        root.addContent(new Element("nome").setText(cardName));
        root.addContent(new Element("listaAttuale").setText("TODO"));
        root.addContent(new Element("storia").addContent(new Element("lista").setText("TODO")));
        root.addContent(new Element("descrizione").setText(descrizione));
        Document card = new Document(root);
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat().setExpandEmptyElements(true));
        try { 
            FileOutputStream f = new FileOutputStream(c_path);
            outputter.output(card, f);
            f.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * Sposto la card in base alla gerarchia degli spostamenti
     * @param projectName nome progetto
     * @param cardName nome card
     * @param dest lista destinazione
     * @return true se l'operazione va a buon fine
     */
    @Override
    public synchronized boolean moveCard(String projectName, String cardName, String dest) {
        try{
        String curr_list = cardSearch(projectName, cardName);
        if(curr_list==null)return false;
        switch(curr_list){
            case "TODO":
                if(!dest.equals("INPROGRESS"))return false;
            break;
            case "INPROGRESS":
                if(!(dest.equals("TOBEREVISED")||(dest.equals("DONE"))))return false;
            break;
            case "TOBEREVISED":
                if(!(dest.equals("INPROGRESS")||(dest.equals("DONE"))))return false;
            break;
            case "DONE":
                return false;
        }
        String old_c_path = this.PROJECTS_PATH+File.separator+projectName+File.separator+curr_list+File.separator+cardName;
        String new_c_path = this.PROJECTS_PATH+File.separator+projectName+File.separator+dest+File.separator+cardName;
        File f = new File(old_c_path);
        f.renameTo(new File(new_c_path));
        cardUpdate(new_c_path, dest);
        } catch (Exception e){
            e.printStackTrace();
        }
        return true;
    }

    /**
     * Recupera la lista dei movimenti della card
     * @param projectName nome progetto
     * @param cardName nome card
     * @return lista movimenti card
     */
    @Override
    public String getCardHistory(String projectName, String cardName) {
        StringBuilder msg = new StringBuilder("Storia movimenti card: ");
        String list = cardSearch(projectName, cardName);
        String c_path = this.PROJECTS_PATH+File.separator+projectName+File.separator+list+File.separator+cardName;
        try{
            SAXBuilder builder = new SAXBuilder();
            Document card_mvs = builder.build(new File(c_path));
            List<Element> mvs = card_mvs.getRootElement().getChild("storia").getChildren();
            Iterator<Element> iterator = mvs.iterator();
            while(iterator.hasNext()){
                Element mv = iterator.next();
                msg.append(" --> ").append(mv.getText());
            }
            //ritornare nome lista attuale e descrizione
        } catch(Exception e){
            e.printStackTrace();
        }
        return msg.toString();
    }

    /**
     * Metodo per verificare che le dir TODO INPROGRESS e TOBEREVISED siano vuote per poter eliminare il progetto
     * @param projectName nome progetto
     * @return numero di card trovate
     */
    public int allDONE(String projectName){
        int count;
        String p_path = this.PROJECTS_PATH + File.separator + projectName + File.separator;
        File f = new File(p_path+"TODO");
        count = Objects.requireNonNull(f.list()).length;
        f = new File(p_path+"INPROGRESS");
        count = count + Objects.requireNonNull(f.list()).length;
        f = new File(p_path+"TOBEREVISED");
        count = count+ Objects.requireNonNull(f.list()).length;
        return count;
    }

    /**
     * Metodo per cancellare un progetto
     * @param projectName nome progetto da eliminare
     * @return true se va a buon fine
     */
    @Override
    public synchronized boolean cancelProject(String projectName) {
        try{
            if((!projectSearch(projectName))||(allDONE(projectName)!=0)){
                return false;
            }
            //Ricava membri del progetto
            List<String> members = showMembers(projectName);
            //Rimuove dal file utenti, il progetto dai membri
            for(String s: members){
                removeProject(s, projectName);
            }
            //Elimina ricorsivamente contenuto progetto e poi la cartella del progetto
            String p_path = PROJECTS_PATH + File.separator + projectName;
            File p_dir = new File(p_path);
            FileUtils.cleanDirectory(p_dir);
            return FileUtils.deleteQuietly(p_dir);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Metodo richiamato da cancelProject per rimuovere dagli utenti nel Document il progetto
     * @param nickUtente utente
     * @param projectName nome progetto
     * @throws IOException in caso di errori di I/O
     */
    public synchronized void removeProject(String nickUtente, String projectName) throws IOException{
        int i = search(nickUtente);
        Element root = users_document.getRootElement();
        //Estraggo i figli dalla radice Utenti
        List<Element> children = root.getChildren();
        Element item = children.get(i);
        List<Element> userProjectsList = item.getChild("progetti").getChildren();
        Iterator<Element> iterator = userProjectsList.iterator();
        //Per ogni figlio
        while(iterator.hasNext()){
            Element p_name = iterator.next();
            if(projectName.equals(p_name.getText())) p_name.getParent().removeContent(p_name);
        }
        utentiUpdate();
    }
    
}
