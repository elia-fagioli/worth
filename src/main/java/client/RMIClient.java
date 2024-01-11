package client;

import Interfacce.RMIClientInterface;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Elia Fagioli matricola:580115
 * @version RELEASE
 */

/**
 * Utilizzata nell'implementazione delle funzioni RMI sui client
 */
public class RMIClient extends RemoteObject implements RMIClientInterface {
    private final ConcurrentHashMap<String, String> users;
    private final String loginName;
    private final ChatRegister chatRegister;
     
    /**
     * Costruttore client RMI
     * @param worthUsers struttura formato <username, status> thread-safe
     * @param loginName username dell'utente
     * @param chat riferimento al ChatRegister dell'utente
    **/
    public RMIClient(ConcurrentHashMap<String, String> worthUsers, String loginName, ChatRegister chat){
        super();
        this.users = worthUsers;
        this.loginName = loginName;
        this.chatRegister = chat;
    }

    /**
     * Utilizzata dal server per notificare login e registrazioni effettuati
     * @param username username utente che effettua l'accesso
     * @param status parametro status dell'utente in questione
     * @throws RemoteException
     * */
    @Override
    public synchronized void notifyUser(String username, String status) throws RemoteException {
        if(users.containsKey(username)){
            System.out.printf("%s è ora %s\n", username, status);
            users.put(username, status);
        } else {
            System.out.printf("Ricevuta registrazione di %s\n", username);
            users.putIfAbsent(username, status);
        }
    }

    /**
     * @return ritorna l'username
     * @throws RemoteException
     */
    @Override
    public synchronized String getUsername() throws RemoteException{
        return loginName;
    }

    /**
     *
     * @param projectName nome del progetto cui ci si unisce alla chat
     * @param address indirizzo della chat di progetto
     * @throws RemoteException
     */
    @Override
    public synchronized void joinChat(String projectName, String address) throws RemoteException{
        try{
            this.chatRegister.joinGroup(projectName, address);
        }catch (IOException | InterruptedException e){
            e.printStackTrace();
        }
    }
}
