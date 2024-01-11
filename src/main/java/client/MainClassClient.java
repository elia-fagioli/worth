package client;

import java.io.IOException;
import java.net.InetAddress;
import java.rmi.NotBoundException;

/**
 *
 * @author Elia Fagioli matricola:580115
 * @version RELEASE
 */

public class MainClassClient {
    //Porta connessione TCP con server
    private static final int SOCKET_PORT = 6789;
    //Porta servizio RMI
    private static final int RMI_PORT = 5000;
    //Porta chat multicast
    private static final int CHAT_PORT = 1234;

    public static void main(String[] args) throws IOException, NotBoundException, InterruptedException{
        //Istanzio il gestore chat per il client corrente
        ChatRegister chatRegister = new ChatRegister(CHAT_PORT, InetAddress.getLocalHost().getHostAddress());
        //Istanzio un client
        WorthClient c = new WorthClient(SOCKET_PORT, RMI_PORT, chatRegister);
        //Thread chat multicast
        Thread chat = new Thread(new ChatStarter(chatRegister));
        chat.start();
        c.start();
    }
}
