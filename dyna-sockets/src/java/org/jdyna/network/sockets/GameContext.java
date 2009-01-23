package org.jdyna.network.sockets;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.jdyna.network.sockets.packets.FrameData;

import com.dawidweiss.dyna.Game;
import com.dawidweiss.dyna.GameEvent;
import com.dawidweiss.dyna.IGameEventListener;
import com.dawidweiss.dyna.Player;
import com.dawidweiss.dyna.IPlayerController.Direction;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * 
 */
final class GameContext
{
    private static class PlayerAddress
    {
        private final String IP, name;

        public PlayerAddress(String remoteIP, String name)
        {
            this.IP = remoteIP;
            this.name = name;
        }
        
        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof PlayerAddress)
            {
                final PlayerAddress other = (PlayerAddress) obj;
                return this.IP.equals(other.IP) && this.name.equals(other.name);
            }
            return false;
        }

        @Override
        public int hashCode()
        {
            return IP.hashCode() ^ name.hashCode();
        }
    }

    private final static AtomicInteger playerIdGenerator = new AtomicInteger();
    private final GameHandle handle;
    private final Game game;
    private final List<IFrameDataListener> listeners = Lists.newArrayList();
    private final HashMap<PlayerAddress, PlayerHandle> players = Maps.newHashMap();
    private final HashMap<Integer, PlayerHandle> playersByID = Maps.newHashMap();
    private final HashMap<Integer, ControllerState> controllerUpdates = Maps.newHashMap();

    private GameThread thread;

    private final IGameEventListener frameDataBroadcaster = new IGameEventListener() { 
        public void onFrame(int frame, List<? extends GameEvent> events)
        {
            final FrameData fd = new FrameData(frame, events);

            for (IFrameDataListener fdl : listeners)
            {
                fdl.onFrame(fd);
            }
        }
    };

    private final IGameEventListener controllersUpdater = new IGameEventListener() { 
        public void onFrame(int frame, List<? extends GameEvent> events)
        {
            /*
             * Apply pending controller updates or reset current state if
             * valid frame count reached 0.
             */
            synchronized (controllerUpdates)
            {
                for (PlayerHandle ph : players.values())
                {
                    final ControllerState newState = controllerUpdates.get(ph.playerID);
                    if (newState != null)
                    {
                        ph.controller.update(newState.direction, newState.dropsBomb, newState.validFrames);
                    }
                    else
                    {
                        if (ph.controller.validFrames > 0)
                        {
                            if (--ph.controller.validFrames == 0)                            
                            {
                                // Reset controller state.
                                ph.controller.update(null, false, 0);
                            }
                        }
                    }
                }
            }
        }
    };
    
    /*
     * 
     */
    public GameContext(GameHandle handle, Game game)
    {
        this.handle = handle;
        this.game = game;
    }

    public GameHandle getHandle()
    {
        return handle;
    }

    /*
     * 
     */
    public synchronized void startGame()
    {
        if (this.thread != null)
        {
            throw new IllegalStateException("Already started.");
        }

        this.game.addListener(frameDataBroadcaster);
        this.game.addListener(controllersUpdater);
        this.thread = new GameThread(this);
        this.thread.start();
    }

    final Game getGame()
    {
        return game;
    }

    public void addFrameDataListener(IFrameDataListener l)
    {
        this.listeners.add(l);
    }
    
    /*
     * 
     */
    public void dispose()
    {
        synchronized (this)
        {
            thread.interrupt();
        }
    }

    /*
     * 
     */
    public PlayerHandle getOrCreatePlayer(String ip, String playerName)
    {
        synchronized (this)
        {
            final PlayerAddress address = new PlayerAddress(ip, playerName);
            PlayerHandle playerHandle = players.get(address);
            if (playerHandle == null)
            {
                if (game.hasPlayer(playerName))
                {
                    throw new FailureResponseException("This game already has" +
                    		" a player named: " + playerName); 
                }

                playerHandle = new PlayerHandle(
                    playerIdGenerator.incrementAndGet(), getHandle().gameID, playerName);

                game.addPlayer(new Player(playerName, playerHandle.controller));
                players.put(address, playerHandle);
                playersByID.put(playerHandle.playerID, playerHandle);
            }

            return playerHandle;
        }
    }

    /*
     * 
     */
    public PlayerHandle getPlayer(int playerID)
    {
        synchronized (this)
        {
            return playersByID.get(playerID);
        }
    }

    /*
     * 
     */
    public void updateControllerState(int playerID, Direction direction,
        boolean dropsBomb, int validFrames)
    {
        synchronized (controllerUpdates)
        {
            controllerUpdates.put(playerID, new ControllerState(direction, dropsBomb, validFrames));
        }
    }
}
