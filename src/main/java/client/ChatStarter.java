package client;

import java.io.IOException;

/**
 *
 * @author Elia Fagioli matricola:580115
 * @version RELEASE
 */

/**
 * Classe che implementa l'interfaccia runnable, richiamata dal main client
 * per creare un'istanza del ChatRegister e partecipare alle chat di progetto
 */
public class ChatStarter implements Runnable{
    private final ChatRegister chatRegister;
    public ChatStarter(ChatRegister c){
        this.chatRegister = c;
    }
    @Override
    public void run() {
        while(!Thread.interrupted()) {
            chatRegister.chatStart();
        }
        try {
            chatRegister.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
