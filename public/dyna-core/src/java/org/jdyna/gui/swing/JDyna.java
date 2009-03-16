package org.jdyna.gui.swing;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.util.*;

import javax.swing.*;

import org.apache.commons.lang.StringUtils;
import org.jdyna.*;
import org.jdyna.audio.jxsound.GameSoundEffects;
import org.jdyna.network.packetio.UDPPacketEmitter;
import org.jdyna.network.sockets.*;
import org.jdyna.network.sockets.packets.ServerInfo;
import org.jdyna.players.HumanPlayerFactory;
import org.jdyna.players.RabbitFactory;
import org.jdyna.view.resources.ImageUtilities;
import org.jdyna.view.swing.BoardFrame;
import org.jdyna.view.swing.SwingUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jgoodies.forms.builder.ButtonBarBuilder;
import com.jgoodies.forms.factories.DefaultComponentFactory;
import com.jgoodies.looks.LookUtils;
import com.jgoodies.looks.Options;

/**
 * Simple Swing GUI to JDyna.
 */
public final class JDyna
{
    /**
     * Default GUI elements spacing.
     */
    private final static int SPACING = 4;

    /**
     * Main launcher frame.
     */
    private JFrame frame;

    /**
     * All available boards.
     */
    private Boards boards;

    /**
     * Enable sounds effects?
     */
    private boolean sound = false;

    /**
     * Remember most recent board selection.
     */
    private String mostRecentBoard = "classic";

    /**
     * Bots.
     */
    private static final HashMap<String, IPlayerFactory> bots;
    static
    {
        bots = Maps.newHashMap();
        
        final IPlayerFactory [] factories = new IPlayerFactory [] {
            new RabbitFactory(),
        };

        for (IPlayerFactory i : factories)
            bots.put(i.getDefaultPlayerName(), i);
    }
    
    /*
     * 
     */
    private void start()
    {
        /*
         * Load board configurations.
         */
        try
        {
            final ClassLoader cl = Thread.currentThread().getContextClassLoader();
            this.boards = Boards.read(
                new InputStreamReader(cl.getResourceAsStream("boards.conf"), "UTF-8"));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        /*
         * Initialize the main GUI.
         */
        frame = new JFrame("JDyna.com");
        frame.getContentPane().add(createMainPanelGUI());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationByPlatform(true);
        frame.setResizable(false);
        try
        {
            frame.setIconImage(ImageUtilities.loadResourceImage("icons/window-icon.png"));
        }
        catch (IOException e)
        {
            // Ignore if icon not found.
        }

        frame.pack();
        frame.setVisible(true);
    }

    /*
     * 
     */
    private JComponent createMainPanelGUI()
    {
        final JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(SPACING, SPACING, SPACING, SPACING));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        panel.add(createSectionTitleGUI("Local: two-player modes"));
        panel.add(Box.createVerticalStrut(2));
        panel.add(createLocalModeGUI());
        panel.add(Box.createVerticalStrut(5));

        panel.add(createSectionTitleGUI("Network: multiplayer modes"));
        panel.add(Box.createVerticalStrut(2));
        panel.add(createNetworkModeGUI());

        panel.add(Box.createVerticalStrut(10));
        panel.add(Box.createGlue());
        panel.add(new JSeparator());
        panel.add(Box.createVerticalStrut(4));
        panel.add(createBottomButtonsGUI());
        return panel;
    }

    /*
     * 
     */
    private JComponent createSectionTitleGUI(String text)
    {
        DefaultComponentFactory f = new DefaultComponentFactory();
        return f.createSeparator(text);
    }
    
    /*
     * 
     */
    private JComponent createBottomButtonsGUI()
    {
        final ButtonBarBuilder builder = new ButtonBarBuilder();
        
        final JButton quitButton = new JButton("Quit");
        quitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                frame.dispose();
            }
        });

        builder.addGlue();
        builder.addGridded(quitButton);
        return builder.getPanel();
    }

    /*
     * 
     */
    private Component createLocalModeGUI()
    {
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        final JButton twoPlayersGameButton = new JButton("Human vs. Human");
        twoPlayersGameButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                runTwoPlayersGame();
            }
        });
        panel.add(twoPlayersGameButton);

        panel.add(Box.createHorizontalStrut(SPACING));

        final JButton onePlayerGameButton = new JButton("Human vs. Computer");
        onePlayerGameButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                runOnePlayerGame();
            }
        });        
        panel.add(onePlayerGameButton);

        return panel;
    }

    /*
     * 
     */
    private Component createNetworkModeGUI()
    {
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        final JButton startGame = new JButton("Start game");
        startGame.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                runNetworkedGame();
            }
        });
        panel.add(startGame);

        panel.add(Box.createHorizontalStrut(SPACING));

        final JButton joinGame = new JButton("Join game");
        joinGame.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                joinNetworkGame();
            }
        });
        panel.add(joinGame);

        return panel;
    }    
    /**
     * Run a local game between two players. 
     */
    private void runTwoPlayersGame()
    {
        assert SwingUtilities.isEventDispatchThread();

        final Board board = selectBoard();
        if (board == null) return;

        hideMainGUI();
        runLocalGame(
            board,
            new HumanPlayerFactory(Globals.getDefaultKeyboardController(0), "Player 1"),
            new HumanPlayerFactory(Globals.getDefaultKeyboardController(1), "Player 2"));
    }

    /**
     * Run one player game (vs. bot(s)). 
     */
    private void runOnePlayerGame()
    {
        assert SwingUtilities.isEventDispatchThread();

        final Board board = selectBoard();
        if (board == null) return;

        final String bot = Dialogs.selectOneFromList(frame,
            "Select opponent", "Select opponent",
            null, "Rabbit");
        if (bot == null) return;

        hideMainGUI();
        runLocalGame(
            board,
            new HumanPlayerFactory(Globals.getDefaultKeyboardController(0), "Player"),
            getBot(bot));        
    }
    
    /**
     * Join an existing game on the local network. 
     */
    private void joinNetworkGame()
    {
        assert SwingUtilities.isEventDispatchThread();

        /*
         * Look for existing game servers on the local network. There are a number of 
         * blocking calls that should be replaced with background SwingWorker later on.
         */
        try
        {
            List<ServerInfo> servers = GameServerClient.lookup(
                GameServer.DEFAULT_UDP_BROADCAST, 
                /* Look for all servers. */ 0,
                2 * GameServer.AUTO_DISCOVERY_INTERVAL);

            if (servers.size() == 0)
            {
                throw new IOException("No active servers in your network.");
            }

            final List<GameListEntry> games = Lists.newArrayList();
            for (ServerInfo si : servers)
            {
                final GameServerClient gsc = new GameServerClient(si);
                gsc.connect();
                for (GameHandle gh : gsc.listGames())
                {
                    games.add(new GameListEntry(si, gh));
                }
                gsc.disconnect();
            }
            
            GameListEntry gameEntry;
            if (games.size() == 0) throw new IOException("No active games.");
            else if (games.size() == 1) gameEntry = games.get(0);
            else
            {
                final Object [] options = games.toArray(new GameListEntry [games.size()]);
                gameEntry = (GameListEntry) Dialogs.selectOneFromList(frame, 
                    "Game selection.", "Select the game to join.", null, options);
                if (gameEntry == null) return;
            }

            /*
             * Join the given game.
             */
            final String playerName = "Player 2";
            final IPlayerFactory playerFactory = new HumanPlayerFactory(
                Globals.getDefaultKeyboardController(0), playerName);

            final GameServerClient client = new GameServerClient(gameEntry.server);
            client.connect();
            final PlayerHandle playerHandle = client.joinGame(
                gameEntry.handle, playerName);
            client.disconnect();

            /*
             * Prepare asynchronous player using a loopback connection.
             */
            final IPlayerController localController = playerFactory.getController(playerName);

            final UDPPacketEmitter serverUpdater = new UDPPacketEmitter(new DatagramSocket());
            serverUpdater.setDefaultTarget(
                Inet4Address.getLocalHost(), gameEntry.server.UDPFeedbackPort);

            final IViewListener viewListener = new IViewListener() {
                public void viewClosed()
                {
                    /* Disconnect from the game. */
                    showMainGUI();
                }
            };

            final GameClient gameClient = new GameClient(gameEntry.handle, gameEntry.server);
            gameClient.addListener(createView(playerName, viewListener));

            final AsyncPlayerController asyncController = 
                new AsyncPlayerController(localController);
            gameClient.addListener(asyncController); 
            gameClient.addListener(
                new ControllerStateDispatch(playerHandle, asyncController, serverUpdater));

            new Thread() {
                public void run()
                {
                    try
                    {
                        gameClient.runLoop();
                    }
                    catch (IOException e)
                    {
                        // Ignore, not much to do.
                    }
                }
            }.start();
        }
        catch (IOException e)
        {
            SwingUtils.showExceptionDialog(frame, "Server lookup problems.", e);
        }
    }

    /**
     * Start a server for a network game. 
     */
    private void runNetworkedGame()
    {
        assert SwingUtilities.isEventDispatchThread();

        final Board board = selectBoard();
        if (board == null) return;

        hideMainGUI();

        /*
         * Start local server, create the game and attach the player to the server.
         */
        final GameServer server = new GameServer();
        server.gameStateLogging = false;

        try
        {
            final String playerName = "Player 1";
            final IPlayerFactory playerFactory = new HumanPlayerFactory(
                Globals.getDefaultKeyboardController(0), playerName);

            /*
             * Start the server. 
             */
            final ServerInfo serverInfo = server.start();
            final GameServerClient client = new GameServerClient(serverInfo);
            client.connect();

            /*
             * Create a new game on the server. 
             */
            final GameHandle gameHandle = client.createGame("Game: " + board.name, board.name);
            final PlayerHandle playerHandle = client.joinGame(gameHandle, playerName);
            client.disconnect();

            /*
             * Prepare asynchronous player using a loopback connection.
             */
            final IPlayerController localController = playerFactory.getController(playerName);

            final UDPPacketEmitter serverUpdater = new UDPPacketEmitter(new DatagramSocket());
            serverUpdater.setDefaultTarget(
                Inet4Address.getLocalHost(), serverInfo.UDPFeedbackPort);

            final IViewListener viewListener = new IViewListener() {
                public void viewClosed()
                {
                    /* Close the game by shutting down the server */
                    server.stop();
                    showMainGUI();
                }
            };

            final GameClient gameClient = new GameClient(gameHandle, serverInfo);
            gameClient.addListener(createView(playerName, viewListener));

            final AsyncPlayerController asyncController = 
                new AsyncPlayerController(localController);
            gameClient.addListener(asyncController); 
            gameClient.addListener(
                new ControllerStateDispatch(playerHandle, asyncController, serverUpdater));

            new Thread() {
                public void run()
                {
                    try
                    {
                        gameClient.runLoop();
                    }
                    catch (IOException e)
                    {
                        // Ignore, not much to do.
                    }
                }
            }.start();
        }
        catch (IOException e)
        {
            server.stop();
            showMainGUI();
            SwingUtils.showExceptionDialog(frame, "Could not start server.", e);
            return;
        }
    }
    
    /**
     * Run a local game.
     */
    private void runLocalGame(Board board, IPlayerFactory... players)
    {
        final BoardInfo boardInfo = new BoardInfo(
            new Dimension(board.width, board.height), Globals.DEFAULT_CELL_SIZE);
    
        final Game game = new Game(board, boardInfo);
        game.setFrameRate(Globals.DEFAULT_FRAME_RATE);
    
        for (IPlayerFactory pf : players)
        {
            final String name = pf.getDefaultPlayerName();
            game.addPlayer(new Player(name, pf.getController(name)));
        }

        /*
         * Attach sound effects to the game.
         */
        if (sound) game.addListener(new GameSoundEffects());

        /*
         * Attach a swing display view to the game.
         */
        final Thread gameThread = new Thread() {
            @SuppressWarnings("unused")
            public void run()
            {
                final GameResult result = game.run(Game.Mode.INFINITE_DEATHMATCH);
            }
        };

        final IViewListener viewListener = new IViewListener() {
            public void viewClosed()
            {
                game.interrupt();
                try
                {
                    gameThread.join();
                }
                catch (InterruptedException e1)
                {
                    throw new RuntimeException();
                }
    
                showMainGUI();
            }
        };
        game.addListener(createView(/* no name */ null, viewListener));

        gameThread.start();
    }

    /**
     * Create a game view for the given player.
     */
    private IGameEventListener createView(String playerName, final IViewListener listener)
    {
        BoardFrame boardFrame = new BoardFrame();
        boardFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        if (!StringUtils.isEmpty(playerName))
        {
            boardFrame.getGamePanel().trackPlayer(playerName);
        }

        boardFrame.addWindowListener(new WindowAdapter()
        {
            public void windowClosed(WindowEvent e)
            {
                if (listener != null) listener.viewClosed();
            }
        });

        boardFrame.setVisible(true);
        return boardFrame;
    }

    /**
     * Get bot for a given name.
     */
    private IPlayerFactory getBot(String bot)
    {
        return bots.get(bot);
    }

    /**
     * Prompt the user to select one of the boards.
     */
    private Board selectBoard()
    {
        final TreeSet<String> boardNames = 
            new TreeSet<String>(boards.getBoardNames());

        String boardName = Dialogs.selectOneFromList(frame,
            "Select game board", "Select game board",
            mostRecentBoard,
            (String []) boardNames.toArray(new String [boardNames.size()]));
        
        if (boardName == null) return null;
        mostRecentBoard = boardName;
        return boards.get(boardName);
    }

    /*
     * 
     */
    private void hideMainGUI()
    {
        assert SwingUtilities.isEventDispatchThread();
        frame.setVisible(false);
    }

    /*
     * 
     */
    private void showMainGUI()
    {
        assert SwingUtilities.isEventDispatchThread();
        frame.setVisible(true);
    }

    /**
     * Command line entry point.
     */
    public static void main(String [] args)
    {
        try
        {
            final String lafName = LookUtils.IS_OS_WINDOWS_XP ? Options
                .getCrossPlatformLookAndFeelClassName() : Options
                .getSystemLookAndFeelClassName();

            UIManager.setLookAndFeel(lafName);
        }
        catch (Exception e)
        {
            // Ignore if not found.
        }

        new JDyna().start();
    }
}