package Interfacce;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * @author Elia Fagioli matricola:580115
 * @version RELEASE
 */

public interface ServerCallbackInterface extends Remote {

    int  signUp(String Username, String password) throws RemoteException;
    void registerForCallback(ClientCallbackInterface c) throws RemoteException;
    void unregisterForCallback(ClientCallbackInterface c) throws RemoteException;

}