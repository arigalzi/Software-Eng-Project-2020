package Network.Message;

import Enumerations.Color;
import Enumerations.GodName;
import Enumerations.MessageType;

import java.util.ArrayList;

/**
 * This message contains all the informations about the players of the game: their usernames, their colors and the gods they had chosen.
 * It is a S->C message.
 */
public class PublicInformation extends Message{

    private static final long serialVersionUID = 2261428338933766010L;
    private ArrayList<String> usernames;
    private ArrayList<Color> colors;
    private ArrayList<GodName> godNames;


    public PublicInformation(MessageType messageType) {
        super(messageType);
    }

    public ArrayList<String> getUsernames() {
        return usernames;
    }

    public void setUsernames(ArrayList<String> usernames) {
        this.usernames = usernames;
    }

    public ArrayList<Color> getColors() {
        return colors;
    }

    public void setColors(ArrayList<Color> colors) {
        this.colors = colors;
    }

    public ArrayList<GodName> getGodNames() {
        return godNames;
    }

    public void setGodNames(ArrayList<GodName> godNames) {
        this.godNames = godNames;
    }
}
