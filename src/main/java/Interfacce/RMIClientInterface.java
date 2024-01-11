package Interfacce;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * @author Elia Fagioli matricola:580115
 * @version RELEASE
 */

public interface RMIClientInterface extends Remote {
    void joinChat(String projectName, String address) throws RemoteException;
    void notifyUser(String username, String status) throws RemoteException;
    String getUsername() throws RemoteException;
}
