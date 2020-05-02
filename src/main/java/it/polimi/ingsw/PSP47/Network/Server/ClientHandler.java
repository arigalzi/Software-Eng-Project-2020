package it.polimi.ingsw.PSP47.Network.Server;

import it.polimi.ingsw.PSP47.Enumerations.Color;
import it.polimi.ingsw.PSP47.Enumerations.GodName;
import it.polimi.ingsw.PSP47.Enumerations.MessageType;
import it.polimi.ingsw.PSP47.Model.Slot;
import it.polimi.ingsw.PSP47.Network.Client.Client;
import it.polimi.ingsw.PSP47.Network.Message.*;
import it.polimi.ingsw.PSP47.Network.Message.ConnectionFailed;
import it.polimi.ingsw.PSP47.Visitor.Visitable;
import it.polimi.ingsw.PSP47.Visitor.VisitableInformation;
import it.polimi.ingsw.PSP47.Visitor.VisitableListOfGods;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

public class ClientHandler implements Runnable{
    private Socket clientSocket;
    private Server server;
    private VirtualView virtualView;
    private ObjectInputStream inputClient;
    private ObjectOutputStream outputClient;
    private boolean isConnected;
    private final static Object firstConnectionLock = new Object();
    private Timer timer;
    
    /**
     * This constructor set up the management between the {@link Client} and the {@link Server}.
     *
     * @param clientSocket the socket of the {@link Client} connected to the server.
     * @param server the server
     */
    public ClientHandler(Socket clientSocket, Server server){
        this.clientSocket = clientSocket;
        this.server = server;
        this.isConnected = true;
    }
    
    /**
     * This method instantiates the {@link ObjectInputStream} and the {@link ObjectOutputStream} with
     * {@link java.io.InputStream} and {@link java.io.OutputStream} of the client's socket in order to
     * handle serialization.
     */
    @Override
    public void run() {
        try {
            inputClient = new ObjectInputStream(clientSocket.getInputStream());
            outputClient = new ObjectOutputStream(clientSocket.getOutputStream());
            
            dispatchMessages();
        }
        catch (IOException e){
            System.out.println("client " + clientSocket.getInetAddress() + " connection dropped.");
            e.printStackTrace();
        }
    }
    
    public VirtualView getVirtualView() {
        return virtualView;
    }
    
    /**
     * This method handle the messages that come from the client.
     * Each different message is handled by a method of this class, method which is called within this method.
     */
    public void dispatchMessages() {
        System.out.println("Started listening the client at the address" + clientSocket.getInetAddress());
        
        while (isConnected) {
            Message message;
            try {
                message = (Message) inputClient.readObject();
                switch (message.getMessageType()) {
                    case REQUEST_CONNECTION:
                        handleFirstConnection(message);
                        break;
                    case REQUEST_NUMBER_OF_PLAYERS:
                        // This method notify all the threads which are waiting to know how many players
                        // can be added to the game. See handleFirstConnection for more information.
                        synchronized (firstConnectionLock) {
                            //message.handleServerSide(server, virtualView, outputClient);
                            server.notifyMessageListeners(message, virtualView);
                            firstConnectionLock.notifyAll();
                        }
                        break;
                    case REQUEST_DISCONNECTION:
                        handleDisconnection();
                        // TODO non ancora testato questo caso
                    case FIRST_PLAYER_CONNECTION:
                        //TODO controlla che i parametri siano corretti
                    default:
                        Visitable visitableObject = message.getContent();
                        virtualView.notifyVirtualViewListener(visitableObject);
                        break;
                }
            } catch (ClassNotFoundException e) {
                System.out.println("The casting of the message of the client " + clientSocket.getInetAddress() + " was not good.");
                try {
                    clientSocket.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
                finally {
                    isConnected = false;
                }
                System.out.println("Client " + clientSocket.getInetAddress() + " disconnected.");
                
                handleDisconnection();
                
                //TODO scollegamento:
                // scollegamento di rete: boh.
                
                //e.printStackTrace();
            } catch (IOException e) {
                System.out.println("Error in the I/O of the client " + clientSocket.getInetAddress() + ":" +
                        " client " + clientSocket.getInetAddress() + " disconnected.");
                
                handleDisconnection();
                
                //TODO scollegamento:
                // scollegamento di rete: boh.
                
                //e.printStackTrace();
            }
        }
    }
    
    /**
     * It handles the first connection.
     * If the connection went well, the client is added and his {@link VirtualView} is instantiated.
     * If not, an error message is sent.
     *
     * @throws IOException if there are troubles in sending the message to the client.
     */
    private void handleFirstConnection(Message message) throws IOException {
        System.out.println("Handle first connection with the client at the address " + clientSocket.getInetAddress());
        
        // Variables to be used in this method.
        RequestConnection requestConnection = (RequestConnection) message;
        VisitableInformation visitableInformation = (VisitableInformation)requestConnection.getContent();
        String username = visitableInformation.getUsername();
        Color color = visitableInformation.getColor();
        ArrayList<ClientHandler> players;
        int maxNumberOfPlayers;
        
        // The players in the server and the parameters of the virtual view mustn't be modified while an other client
        // gets them. Hence, the getter and setter methods are inside a synchronized block.
        synchronized (firstConnectionLock){
            // Getter methods inside the synchronized block. The setter methods are below
            players = server.getPlayers();
            maxNumberOfPlayers = server.getMaxNumberOfPlayers();
            
            // When the first player sets the maxNumberOfPlayers, they are outside this method, because the request
            // is consumed in the client through the socket connection. Hence, their clientHandler could have already
            // added to the players of the server (because the addPlayer is called inside this synchronized block).
            // Hence, for the thread that owns the lock now, the number of players could result more than zero
            // (because the addPlayer is called inside this synchronized block), but the maxNumberOfPlayers could
            // be zero, because the setting is done outside this method in the client.
            // A client cannot proceed neither be added in the players of the server until the first player doesn't
            // decide which is the maxNumberOfPlayers of the game.
            // In the case that the first player has already been added but they didn't choose the maxNumberOfPlayers,
            // the current thread has to wait. In this case, an advice is sent to the client.
            if (players.size()!=0 && maxNumberOfPlayers==0) {
                outputClient.writeObject(new WaitChooseNumberPlayers());
                while (server.getMaxNumberOfPlayers()==0) {
                    try {
                        firstConnectionLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            
            // After the wait, the players inside the server and the maxNumberOfPlayers may be modified.
            // An other call has to be done.
            // Getter methods inside the synchronized block. The setter methods are below
            players = server.getPlayers();
            maxNumberOfPlayers = server.getMaxNumberOfPlayers();
        
            // If the game is already full of players
            if (players.size()!=0 && players.size() == maxNumberOfPlayers) {
                ConnectionFailed connectionFailed = new ConnectionFailed("The game is already started. Try later.");
                //WARNING: the following message MUST be equal to the one checked in handleConnectionFailed in the network handler
                outputClient.writeObject(connectionFailed);
                clientSocket.close();
                isConnected = false;
                return;
            }
            // It checks if this client decided an univocal name and color for the game
            for (ClientHandler clientHandler : players) {
                // Getter methods of the virtual view inside the synchronized block. There is the constructor below
                if (clientHandler.getVirtualView().getUsername().equals(username)) {
                    ConnectionFailed connectionFailed = new ConnectionFailed("Somebody else has already taken this username.");
                    //WARNING: the following message MUST be equal to the one checked in handleConnectionFailed in the network handler
                    outputClient.writeObject(connectionFailed);
                    return;
                } else if (clientHandler.getVirtualView().getColor().equals(color)) {
                    ConnectionFailed connectionFailed = new ConnectionFailed("Somebody else has already taken this color.");
                    //WARNING: the following message MUST be equal to the one checked in handleConnectionFailed in the network handler
                    outputClient.writeObject(connectionFailed);
                    return;
                }
            }
    
            // the virtual view is added and it is added to the message listeners.
            // Constructor of the virtual view inside the synchronized block. There are the getter methods above.
            virtualView = new VirtualView(username, color, this);
            //server.addMessageListener(virtualView);
    
            // if the player is the first, he will decide the number of players
            if (players.size() == 0)
                outputClient.writeObject(new RequestNumberOfPlayers(null));
    
            // the player is added to the list of players of the server
            // Setter methods of the players field of the server inside the synchronized block. There are the getter methods above.
            server.addPlayer(this);
            server.addUsernameAndColorToMap(username, color);
            server.addUsernameAndVirtualViewToMap(username, virtualView);
            
            // The players size and the maxNumberOfPlayers is called after the modifies and before exiting the
            // synchronized block to control if the game can be initialized.
            players = server.getPlayers();
            maxNumberOfPlayers = server.getMaxNumberOfPlayers();
        }
        
        // The client is advised of the successful connection.
        VisitableInformation usernameAndColor = new VisitableInformation();
        usernameAndColor.setUsername(username);
        usernameAndColor.setColor(color);
        ConnectionAccepted connectionAccepted = new ConnectionAccepted(usernameAndColor);
        outputClient.writeObject(connectionAccepted);
        
        // If the number of players is reached, the game is initialized.
        if (players.size() == maxNumberOfPlayers)
            server.initGame();
    }
    
    /**
     * This method disconnects the server from this client telling him who disconnected first.
     * @param message it tells this client who disconnected first.
     */
    public void disconnectFromClient(OpponentPlayerDisconnection message){
        send(message);
        isConnected = false;
        try {
            clientSocket.close();
        } catch (IOException e) {
            System.out.println("Unable to close the socket of this client: " + clientSocket.getInetAddress());
            e.printStackTrace();
        }
    }
    
    /**
     * This method handles the disconnection of the client. If it is the first client to disconnect from the game,
     * it will make others disconnect as well. Otherwise, the method is ignored.
     */
    void handleDisconnection() {
        // If the virtualView is set to null, it means that the player didn't enter the handleFirstConnection,
        // hence nothing has to be done.
        if (virtualView==null){
            isConnected = false;
            return;
        }
        
        // This is synchronized in the case there is an attempt of connection by another client
        // during the disconnection of this client.
        synchronized (firstConnectionLock) {
            // If isConnected is true, it means that this method hasn't been called by other clients. This means
            // that the client which has to advise the others is this.
            // Hence, for each client this method sends to them a message of incoming disconnection.
            if (isConnected) {
                OpponentPlayerDisconnection message = new OpponentPlayerDisconnection("Someone has disconnected");
                message.setUsername(virtualView.getUsername());
                server.notifyMessageListeners(message, virtualView);
                server.cleanServer();
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.out.println("Unable to close the socket of the client " + clientSocket.getInetAddress() + ".");
                    e.printStackTrace();
                }
            }
            
            isConnected = false;
        }
    }
    
    /**
     * This method serializes the messages and sends them to the client.
     *
     * @param message the message that must be sent.
     */
    void send(Message message) {
        try {
            outputClient.writeObject(message);
        } catch (IOException e) {
            System.out.println("Error in the serialization of " +message.toString()+ " message.");
            e.printStackTrace();
        }
        
        if (message.getMessageType() == MessageType.TIMER_UPDATER) return;
        
        timer = new Timer(this);
        new Thread(timer).start();
    }

    /**
     * This method sends a message to the client with the number of players
     *
     * @param numberOfPlayers parameter that must be sent.
     */
    void sendNumberOfPlayers(int numberOfPlayers) {
        NumberOfPlayers message = new NumberOfPlayers(numberOfPlayers);
        send(message);
    }

    /**
     * This method send a message to the client to tell him that he is the Challenger.
     */
    void sendYouAreTheChallenger()  {
        send(new YouAreTheChallenger());
    }

    /**
     * This method sends a message to the client with the list of available gods he can choose from.
     *
     * @param gods list of available gods.
     */
    void sendGodsList(ArrayList<GodName> gods) {
        VisitableListOfGods listOfGods = new VisitableListOfGods();
        listOfGods.setGodNames(gods);
        ListOfGods message = new ListOfGods(listOfGods);
        send(message);
    }

    /**
     * This method sends a message to the client with all the information about the players of the current game.
     *
     * @param usernames the username of each client
     * @param colors the color of each client
     * @param godNames the god chose from each client
     */
    void sendPublicInformation(ArrayList<String> usernames, ArrayList<Color> colors, ArrayList<GodName> godNames)  {
        PublicInformation message = new PublicInformation(usernames,colors,godNames);
        send(message);
    }

    /**
     * This method sends a message to the client with an update of the model (a modified slot).
     *
     * @param updatedSlot the modified slot.
     */
    void sendUpdateSlot(Slot updatedSlot) {
        Slot newSlot = new Slot(updatedSlot.getRow(), updatedSlot.getColumn());
        newSlot.setWorker(updatedSlot.getWorker()) ;
        newSlot.setWorkerColor(updatedSlot.getWorkerColor());
        newSlot.setLevel(updatedSlot.getLevel());
        newSlot.setWorkerOn(updatedSlot.isWorkerOnSlot());
        UpdatedSlot message = new UpdatedSlot(newSlot);
        send(message);
    }

    /**
     * This method sends a message to the client to ask the initial position of his workers.
     */
    void sendAskWorkersPosition() {
        send(new AskWorkersPosition(null));
    }

    /**
     * This method sends a message to the client to warn him that he did something wrong.
     * @param errorText the errorString that explain what he did wrong.
     */
    void sendError(String errorText) {
        ErrorMessage message = new ErrorMessage(errorText);
        send(message);
    }

    /**
     * This method sends a message to the client to ask which worker he wants to use, asking the slot he is on.
     */
    void sendWhichWorker() {
        send(new ChooseWorkerByPosition(null));
    }

    /**
     * This method sends a message to the client to ask which action he wants to do next.
     */
    void sendAction() {
        send(new ChooseAction(null));
    }
}
