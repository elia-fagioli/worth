package server;

import org.jdom2.JDOMException;

/**
 * @author Elia Fagioli matricola:580115
 * @version RELEASE
 */

public class MainClassServer {
    //Porta connessione TCP
    private static final int SOCKET_PORT = 6789;
    //Porta servizio RMI
    private static final int RMI_PORT = 5000;

    public static void main(String[] args) throws JDOMException {
        //Istanzio il server
        WorthServer s = new WorthServer(SOCKET_PORT, RMI_PORT);
        s.start();
    }
}
