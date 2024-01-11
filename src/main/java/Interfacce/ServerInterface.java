package Interfacce;

import org.jdom2.JDOMException;

import java.io.IOException;
import java.util.List;

/**
 * @author Elia Fagioli matricola:580115
 * @version RELEASE
 */

/**
 * metodi da implementare secondo la consegna del progetto
 */
public interface ServerInterface{
    boolean login(String nickUtente, String password) throws IOException;
    void logout(String nickUtente) throws IOException;
    List<String> listUsers();
    List<String> listOnlineUsers();
    List<String> listProjects(String nickUtente);
    boolean createProject(String nickUtente, String projectName) throws IOException;
    boolean addMember(String nickUtente, String projectName)throws JDOMException, IOException;
    List<String> showMembers(String projectName) throws JDOMException, IOException;
    String showCards(String projectName);
    String showCard(String projectName, String cardName);
    boolean addCard(String projectName, String cardName, String descrizione);
    boolean moveCard(String projectName, String cardName, String dest);
    String getCardHistory(String projectName, String cardName);
    boolean cancelProject(String projectName);
}
