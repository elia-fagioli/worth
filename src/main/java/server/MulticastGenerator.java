package server;

import java.util.ArrayList;

/**
 * @author Elia Fagioli matricola:580115
 * @version RELEASE
 */

public class MulticastGenerator {
    private static int multicastSuffix = 0x000001;
    private static final int[] address = new int[4];
    private static final ArrayList<String> reusableAddresses = new ArrayList<>();

    //ritorna un nuovo indirizzo multicast di classe D
    public static String getNewMulticastAddress() {
        //primo ottetto
        address[0] = 0xE0;

        if(reusableAddresses.size() > 0){
            return reusableAddresses.remove(0);
        }

        if (!(multicastSuffix > 0xFF)){
            multicastSuffix++;
            address[3] = (multicastSuffix  & 0xFF);         //mask sugli ultimi otto bit
            address[2] = ((multicastSuffix >> 0x8) & 0xFF); //shift 8 bits a destra e mask sugli ultimi 8 bit
            address[1] = ((multicastSuffix >> 0xF) & 0xFF); //shift 16 bits a destra e mask sugli ultimi 8 bit
            return address[0] + "." + address[1] + "." + address[2] + "." + address[3]; //ritorna l'indirizzo multicast
        } else return null;
    }

    public static void releaseAddress(String address){
        reusableAddresses.add(address);
    }
}
