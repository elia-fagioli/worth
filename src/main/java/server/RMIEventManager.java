package server;

import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import Interfacce.RMIEventManagerInterface;
import Interfacce.RMIClientInterface;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

/**
 * @author Elia Fagioli matricola:580115
 * @version RELEASE
 */

public class RMIEventManager extends RemoteServer implements RMIEventManagerInterface {
    private final String USERS_PATH;
    private final String ON = "ONLINE";
    private final String OFF = "OFFLINE";
    private final ConcurrentHashMap<String, String> worthUsers;
    private final List<RMIClientInterface> clients = Collections.synchronizedList(new ArrayList<>());

    /**
     * Costruttore classe RMI Server
     * @param users formato <username, status>
     * @param path path db
     */
    public RMIEventManager(ConcurrentHashMap<String, String> users, String path){
        this.USERS_PATH = path;
        this.worthUsers = users;
    }

    /**
     * il server mette a disposizione una operazione di registrazione di un utente.
     * Il server risponde per indicare l’avvenuta registrazione, oppure, se il nickname è già presente, restituisce
     * un messaggio d'errore.Le registrazioni vengono persistite nel file utenti.xml
     * @param nickUtente username da registrare
     * @param password relativa password
     * @return true se utente registrato con successo, altrimenti false
     */
    public synchronized boolean register(String nickUtente, String password){
        try {
           if(worthUsers.containsKey(nickUtente)){
                return false;
            } else {
                worthUsers.putIfAbsent(nickUtente, ON);
                //Creo un SAXBuilder e con esso costruisco un document
                SAXBuilder builder = new SAXBuilder();
                Document document = builder.build(new File(USERS_PATH));
                //creo l'utente
                Element user = new Element("Utente");
                Element username = new Element("username");
                username.setText(nickUtente);
                Element pass = new Element("password");
                pass.setText(password);
                Element progetti = new Element("progetti");
                Element status = new Element("status").setText(OFF);
                user.addContent(username);
                user.addContent(pass);
                user.addContent(progetti);
                user.addContent(status);
                document.getRootElement().addContent(user);
                XMLOutputter outputter = new XMLOutputter();
                //Imposto il formato dell'outputter come "bel formato"
                outputter.setFormat(Format.getPrettyFormat().setExpandEmptyElements(true));
                //Produco l'output sul file 
                outputter.output(document, new FileOutputStream(USERS_PATH));
                System.out.println("File utenti aggiornato:");
           }   
        } catch (JDOMException | IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * Notifica agli utenti connessi del cambiamento di status di un utente
     * @param username username che cambia status
     * @param status status attuale user
     * @throws RemoteException
     */
    public synchronized void updateUsers(String username, String status) throws RemoteException{
        //L'iterator per evitare ConcurrentModificationException nel remove
        Iterator<RMIClientInterface> iterator = clients.iterator();
        while(iterator.hasNext()) {
            try {
                iterator.next().notifyUser(username, status);
            } catch (RemoteException e) {
                //client no longer available
                iterator.remove();
            }
        }
    }

    /**
     * Registrazione servizio di notifica RMI
     * @param client Client che si registra al servizio di notifica RMI
     * @throws RemoteException
     */
    @Override
    public synchronized void registerForCallback(RMIClientInterface client) throws RemoteException {
        if(!clients.contains(client)){
            System.out.printf("> INFO: CLIENT %s REGISTERED TO CALLBACK\n", client.getUsername());
            clients.add(client);
        }
    }

    /**
     * Cancellazione servizio di notifica RMI
     * @param client Client che esce dal servizio di notifica RMI
     * @throws RemoteException
     */
    @Override
    public synchronized void unregisterFromCallback(RMIClientInterface client) throws RemoteException{
        if(clients.remove(client)){
            System.out.printf("> INFO: CLIENT %s UNREGISTERED FROM CALLBACK\n", client.getUsername());
        }
    }

    /**
     * User che si aggiunge a una chat di gruppo
     * @param nickUtente username
     * @param projectName progetto in questione
     * @param address indirizzo progetto
     * @throws RemoteException
     */
    @Override
    public synchronized void updateChat(String nickUtente, String projectName, String address) throws RemoteException{
        //L'iterator per evitare ConcurrentModificationException nel remove
        Iterator<RMIClientInterface> iterator = clients.iterator();
        while(iterator.hasNext()) {
            try {
                RMIClientInterface client = iterator.next();
                if(client.getUsername().equals(nickUtente)) client.joinChat(projectName, address);
            } catch (RemoteException e) {
                //client no longer available
                iterator.remove();
            }
        }
    }
}
