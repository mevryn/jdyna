package org.jdyna.network.sockets;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.Socket;

import org.apache.commons.lang.StringUtils;
import org.jdyna.network.sockets.packets.CreateGameRequest;
import org.jdyna.network.sockets.packets.CreateGameResponse;
import org.jdyna.network.sockets.packets.FailureResponse;
import org.jdyna.network.sockets.packets.JoinGameRequest;
import org.jdyna.network.sockets.packets.JoinGameResponse;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 */
public class GameClient
{
    /**
     * Internal logger.
     */
    private final static Logger logger = LoggerFactory.getLogger(GameClient.class);

    /**
     * Port for the TCP server connection.
     */
    @Option(name = "-p", aliases = "--port", required = false, metaVar = "port", usage = "Server's TCP port (default: "
        + GameServer.DEFAULT_TCP_CONTROL_PORT + ").")
    public int port = GameServer.DEFAULT_TCP_CONTROL_PORT;

    /**
     * Host for the server's TCP connection.
     */
    @Option(name = "-s", aliases = "--server", required = true, metaVar = "address", usage = "Server's host address.")
    public String host;

    /**
     * Server connection.
     */
    private TCPPacketEmitter pe;

    /**
     * Connect to the server.
     */
    public void connect() throws IOException
    {
        if (pe != null) throw new IllegalStateException("Already connected.");
        if (StringUtils.isEmpty(host)) throw new IllegalStateException("host is required.");

        pe = new TCPPacketEmitter(new Socket(InetAddress.getByName(host), port));
        logger.info("Connected.");
    }

    /**
     * 
     */
    public void disconnect()
    {
        pe.close();
        pe = null;
    }

    /**
     * Create a new game on the server.
     */
    public GameHandle createGame(String gameName, String boardName) throws IOException
    {
        CreateGameResponse response = sendReceive(CreateGameResponse.class,
            new CreateGameRequest(gameName, boardName));
        return response.handle;
    }

    /**
     * Join or re-join an existing game. Re-joining will happen if player with tha given
     * name and the same source IP address was already registered.
     */
    public PlayerHandle joinGame(GameHandle gameHandle, String playerName)
        throws IOException
    {
        JoinGameResponse response = sendReceive(JoinGameResponse.class,
            new JoinGameRequest(gameHandle.gameID, playerName));
        return response.handle;
    }

    /**
     * Check if the received packet is of the required type.
     */
    private <T> T sendReceive(Class<T> clazz, Serializable packet) throws IOException
    {
        logger.debug("Sending: " + packet.getClass().getSimpleName());

        final Packet p = pe.sendAndReceive(ObjectPacket.serialize(packet));
        if (p == null)
        {
            throw new IOException("Server connection closed.");
        }
        final Object result = ObjectPacket.deserialize(p);

        logger.debug("Received: " + result.getClass().getSimpleName());

        if (result instanceof FailureResponse)
        {
            final FailureResponse response = (FailureResponse) result;

            throw new IOException("Server indicates failure: " + response.message,
                response.throwable);
        }

        if (clazz.isAssignableFrom(result.getClass()))
        {
            return clazz.cast(result);
        }

        throw new IOException("Unexpected packet content received: "
            + result.getClass().getSimpleName() + " (expected: " + clazz.getSimpleName()
            + ")");
    }
}
