package com.dawidweiss.dyna.launchers;

import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.dawidweiss.dyna.*;
import com.dawidweiss.dyna.audio.jxsound.GameSoundEffects;
import com.dawidweiss.dyna.view.swing.BoardFrame;

/**
 * Start a game:
 * <ul>
 *  <li>two players,</li>
 *  <li>one game,</li>
 *  <li>both players controlled via keyboard (default mappings).</li>
 * </ul>
 * 
 * @see Globals#getDefaultKeyboardController(int)
 */
public final class Main
{
    /* Command-line entry point. */
    public static void main(String [] args) throws IOException
    {
        /*
         * Load board configurations.
         */
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        final Boards boards = Boards.read(new InputStreamReader(cl
            .getResourceAsStream("boards.conf"), "UTF-8"));

        /*
         * Set up a single game between two players.
         */
        final Board board = boards.get("classic");

        final IPlayerController c1 = Globals.getDefaultKeyboardController(0);
        final IPlayerController c2 = Globals.getDefaultKeyboardController(1);

        final BoardInfo boardInfo = new BoardInfo(
            new Dimension(board.width, board.height), Globals.DEFAULT_CELL_SIZE);

        final Player p1 = new Player("Player 1", c1);
        final Player p2 = new Player("Player 2", c2);
        final Game game = new Game(board, boardInfo, p1, p2);
        game.setFrameRate(25);

        /*
         * Attach sounds view to the game.
         */
        game.addListener(new GameSoundEffects());

        /*
         * Attach a display view to the game.
         */
        final BoardFrame frame = new BoardFrame(boardInfo);
        game.addListener(frame);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        final GameResult result = game.run(Game.Mode.LAST_MAN_STANDING);
        Logger.getAnonymousLogger().info(result.toString());

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                frame.dispose();
            }
        });
    }
}