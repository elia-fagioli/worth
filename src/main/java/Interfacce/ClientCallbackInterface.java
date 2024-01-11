package Interfacce;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * @author Elia Fagioli matricola:580115
 * @version RELEASE
 */

public interface ClientCallbackInterface extends Remote {
    void notifyUser(String nickUtente, boolean status) throws RemoteException;
    void notifyChat(String address, String projectName) throws RemoteException;
    void leaveGroup(String address, String projectName) throws RemoteException;
    String getUsername() throws RemoteException;
}