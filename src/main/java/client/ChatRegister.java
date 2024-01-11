package client;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Elia Fagioli matricola:580115
 * @version RELEASE
 */

public class ChatRegister {

    //HashMap formato <nomeProgetto, indirizzo associato>
    private final ConcurrentHashMap<String, String> projectChats;
    //Hashmap formato <nomeProgetto, chiave>
    private final ConcurrentHashMap<String, MembershipKey> groupKeys;
    //Hashmap con lista messaggi foramto <nomeProgetto, lista messaggi ricevuti da quando in linea>
    private final ConcurrentHashMap<String, List<String>> messages;
    //NetworkInterface usata per identificare l'interfaccia locale cui il gruppo multicast partecipa
    private final NetworkInterface networkInterface;
    //gestione multicast
    private final DatagramChannel datagramChannel;
    private final Selector selector;

    /**
    * Costruttore della classe per la gestione delle chat
    * @param port è la porta scelta per il servizio Multicast
    * @param address è l'indirizzo locale
    * */
    public ChatRegister(int port, String address) throws IOException {
        projectChats = new ConcurrentHashMap<>();
        messages = new ConcurrentHashMap<>();
        groupKeys = new ConcurrentHashMap<>();
        selector = Selector.open();
        networkInterface = NetworkInterface.getByInetAddress(InetAddress.getByName(address));
        
        datagramChannel = DatagramChannel.open(StandardProtocolFamily.INET);
        datagramChannel.socket().setReuseAddress(true);
        datagramChannel.socket().bind(new InetSocketAddress(port));
        datagramChannel.configureBlocking(false);
        datagramChannel.register(selector, SelectionKey.OP_READ);
    }

    /**
     * Metodo del thread avviato nel MainClassClient per restare in ascolto per le chat
     */
    public void chatStart(){
            try {
                if (selector.select() == 0) return;
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keysIterator = selectedKeys.iterator();

                while (keysIterator.hasNext()) {
                    SelectionKey selectionKey = keysIterator.next();
                    keysIterator.remove();
                    if (selectionKey.isReadable()) readDatagramChannel(selector, selectionKey);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
    }


    /**
     * Metodo utilizzato da un Utente per partecipare al gruppo Multicast
    * @param projectName è il nome del progetto su cui inviare il messaggio
    * @param address sta ad indicare l'indirizzo del gruppo multicast del progetto
    * */
    public void joinGroup(String projectName, String address) throws IOException, InterruptedException {
        MembershipKey membershipKey = datagramChannel.join(InetAddress.getByName(address), networkInterface);
        projectChats.putIfAbsent(projectName, address);
        groupKeys.putIfAbsent(address, membershipKey);
        messages.putIfAbsent(projectName, Collections.synchronizedList(new ArrayList<>()));
    }

    /**
     * Metodo utilizzato per abbandonare il gruppo Multicast
    * @param projectName indica il progetto eliminato da rimuovere dalle chat
    * */
    public void deleteGroup(String projectName){
        String address = projectChats.get(projectName);
        projectChats.remove(projectName);
        messages.remove(projectName);
        groupKeys.remove(address).drop();
        System.out.printf("Il progetto %s è stato terminato\n", projectName);
    }
    
    /**
     * prova a leggere sul channel e inoltra i messaggi sui gruppi appositi
    */
    private void readDatagramChannel(Selector selector, SelectionKey selectionKey) throws IOException {
        DatagramChannel dc = (DatagramChannel) selectionKey.channel();
        dc.configureBlocking(false);
        ByteBuffer buffer = ByteBuffer.wrap(new byte[512]);
        buffer.clear();
        dc.receive(buffer);
        buffer.flip();
        
        String msg  = StandardCharsets.UTF_8.decode(buffer).toString();
        //split sul separatore scelto che toglie la prima parte di msg con l'indirizzo multicast
        String[] split = msg.split("/");
        //String addr = split[0];// indirizzo;
        String projectName = split[0];
        String data = split[1];
        //Quando ricevo il seguente comando vuol dire che un membro ha eliminato il progetto
        if(data.contains("delete "+projectName)){
            deleteGroup(projectName);
        } else {
            //Altrimenti aggiungo il messaggio alla lista della chat progetto
            try {
                messages.get(projectName).add(data);
            } catch (Exception e) {
                e.printStackTrace();
            }
            dc.register(selector, SelectionKey.OP_READ);
        }
    }

    /**
     * @param projectName indica il progetto su cui viene inviato il messaggio
     * @param sender indica l'username del mittente del messaggio
     * @param body indica il corpo del messaggio
     * */
    public boolean sendMessage(String projectName, String sender, String body) throws IOException{
        String address = projectChats.get(projectName);
        if(address == null){
            return false;
        } else {
            //compongo la stringa con i parametri del metodo
            String msg = projectName + "/";
            msg = msg + sender + ": ";
            msg = msg + body;

            ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
            buffer.clear();
            buffer.put(msg.getBytes(StandardCharsets.UTF_8));
            buffer.flip();
            //invio sul channel
            datagramChannel.send(buffer, new InetSocketAddress(address, 1234));
            return true;
        }
    }

    /**
     * ritorna il valore dell'hashmap che consiste nella lista dei messaggi del progetto
     * il tutto è thread safe perché la struttura dati è Concurrent
     * @param projectName nome della chiave nella ConcurrentHashMap
     */
    public List<String> getProjectMessages(String projectName){
        return this.messages.get(projectName);
    }

    /**
     * Chiude il channel dell'ascoltatore di chat
     * @throws IOException
     */
    public void close() throws IOException {
        datagramChannel.close();
    }

}
