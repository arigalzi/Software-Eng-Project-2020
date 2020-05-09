package it.polimi.ingsw.PSP47.Network.Message;

import it.polimi.ingsw.PSP47.Enumerations.Color;
import it.polimi.ingsw.PSP47.Enumerations.GodName;
import it.polimi.ingsw.PSP47.Enumerations.MessageType;
import it.polimi.ingsw.PSP47.Network.Client.Client;
import it.polimi.ingsw.PSP47.Network.Server.Server;
import it.polimi.ingsw.PSP47.Network.Server.VirtualView;
import it.polimi.ingsw.PSP47.Visitor.Visitable;

import java.io.ObjectOutputStream;
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

    public PublicInformation(ArrayList<String> usernames, ArrayList<Color> colors, ArrayList<GodName> godNames) {
        this.usernames = usernames;
        this.colors = colors;
        this.godNames = godNames;
        this.messageType=MessageType.PUBLIC_INFORMATION;
    }

    public void setUsernames(ArrayList<String> usernames) {
        this.usernames = usernames;
    }

    public void setColors(ArrayList<Color> colors) {
        this.colors = colors;
    }

    public void setGodNames(ArrayList<GodName> godNames) {
        this.godNames = godNames;
    }

    public ArrayList<String> getUsernames() {
        return usernames;
    }

    public ArrayList<Color> getColors() {
        return colors;
    }

    public ArrayList<GodName> getGodNames() {
        return godNames;
    }
}
