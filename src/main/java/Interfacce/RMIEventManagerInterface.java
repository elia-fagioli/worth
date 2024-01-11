package Interfacce;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * @author Elia Fagioli matricola:580115
 * @version RELEASE
 */

public interface RMIEventManagerInterface extends Remote{
    boolean register(String nickUtente, String password) throws IOException;
    void registerForCallback(RMIClientInterface client) throws RemoteException;
    void unregisterFromCallback(RMIClientInterface client) throws RemoteException;
    void updateChat(String nickUtente, String projectName, String address) throws RemoteException;
}
