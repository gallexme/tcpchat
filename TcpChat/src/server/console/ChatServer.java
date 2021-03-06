/*
 * The MIT License
 *
 * Copyright 2015 Manuel Schmid.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package server.console;

import networking.packets.KickPacket;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import logging.general.Counters;
import logging.general.LoggingController;
import logging.enums.LogName;
import logging.enums.LogPath;
import static server.console.ChatServer.*;

/**
 * Class ChatServer initializes threads and accepts new clients
 */
public final class ChatServer {

    // Setting up client
    // maxClientsCount = 0 means infinite clients
    protected static final int maxClientsCount = 0;
    protected static final List<ClientThread> threads = new ArrayList<>();
    protected static List<String> userList = new ArrayList<>();

    // Logging
    protected static Logger logConnection = null;
    protected static Logger logException = null;
    protected static Logger logGeneral = null;
    protected static LoggingController logControl = null;

    public static void main(String args[]) {

        // Default values
        int portNumber = 0;
        boolean loggingEnabled = false;
        boolean showOnConsole = false;
        boolean init = false;

        // Switch command line arguments
        switch (args.length) {
            case 2:
                portNumber = Integer.parseInt(args[0]);
                if (args[1].equals("yes")) {
                    loggingEnabled = true;
                }
                init = true;
                break;
            default:
                System.out.println("Usage: java ChatServer <portNumber> <logging yes/NO>");
        }

        // Check if everything is set up successfully
        if (init) {
            // Setting up LoggingController
            logControl = new LoggingController(loggingEnabled, showOnConsole);
            initLoggers();
            System.out.println("Server started");
            logControl.log(logGeneral, Level.INFO, "Server started on port " + portNumber);

            // Open a server socket on the portNumber (default 8000)
            try {
                ServerSocket serverSocket = new ServerSocket(portNumber);

                // Adding shutdown handle
                Runtime.getRuntime().addShutdownHook(new ShutdownHandle());

                Socket clientSocket = null;

                // Create client socket for each connection
                while (true) {
                    // Handle for new connection, put it into empty array-slot
                    clientSocket = serverSocket.accept();
                    Counters.connection();
                    // maxClientsCount = 0 means infinite clients
                    if (threads.size() < maxClientsCount || maxClientsCount == 0) {
                        ClientThread clientThread = new ClientThread(clientSocket);
                        threads.add(clientThread);
                        clientThread.start();
                        logControl.log(logConnection, Level.INFO, clientSocket.getRemoteSocketAddress() + ": accepted, thread started");
                        Counters.login();
                    } else {
                        // Only when maxclients is reached        
                        RejectionThread fThread = new RejectionThread(clientSocket);
                        fThread.start();
                    }
                }
            } catch (IOException ex) {
                System.out.println(ex);
                logControl.log(logException, Level.SEVERE, "Could not open Server Socket");
                logControl.log(logException, Level.SEVERE, "Exiting Server");
                logControl.log(logGeneral, Level.SEVERE, "Exiting Server");
                logging.general.Counters.exception();
            }
        }
    }

    /**
     * Initializes loggers with LoggingController
     */
    protected static void initLoggers() {
        logConnection = logControl.create(LogName.SERVER, LogPath.CONNECTION);
        logException = logControl.create(LogName.SERVER, LogPath.EXCEPTION);
        logGeneral = logControl.create(LogName.SERVER, LogPath.GENERAL);
    }

    /**
     * Getter for the userlist
     *
     * @return
     */
    public static List<String> getUserList() {
        return userList;
    }
}

class ShutdownHandle extends Thread {

    @Override
    public void run() {
        logControl.log(logGeneral, Level.INFO, "*** SERVER IS GOING DOWN ***");
        logControl.log(logConnection, Level.INFO, "*** SERVER IS GOING DOWN ***");

        // Send closing of server to all clients
        for (ClientThread thread : threads) {
            if (thread != null && thread.state == ConnectionState.Online) {
                thread.send(new KickPacket("*** SERVER IS GOING DOWN ***"), thread);
            }
        }
        // Close all loggers
        for (Logger logger : logControl.getAllLoggers()) {
            for (Handler handler : logger.getHandlers()) {
                handler.close();
            }
        }
    }
}
