package org.jdyna.view.swing;

import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.InvocationTargetException;

import javax.swing.*;

import org.apache.commons.lang.exception.ExceptionUtils;

/**
 * Swing utilities not covered in {@link SwingUtilities}.
 */
public final class SwingUtils
{
    private SwingUtils()
    {
        // No instances.
    }
    
    /**
     * Constants for window snapping. 
     */
    public enum SnapSide
    {
        LEFT, RIGHT, TOP, BOTTOM
    }

    /**
     * Track status and visibility of a given frame. 
     */
    public final static class VisibilityTracker
    {
        private boolean visible;
        private int state;

        private volatile Frame frame;
        private WindowAdapter l;

        VisibilityTracker(final JComponent component)
        {        
            component.addHierarchyListener(new HierarchyListener()
            {
                public void hierarchyChanged(HierarchyEvent e)
                {
                    if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0)
                    {
                        detach(frame);
                        frame = (Frame) SwingUtilities.getWindowAncestor(component);
                        attach(frame);
                        updateState();
                    }
                }
            });

            frame = (Frame) SwingUtilities.getWindowAncestor(component);
            attach(frame);
            updateState();
        }

        VisibilityTracker(Frame frame)
        {
            this.frame = frame;
            attach(frame);
        }

        private void attach(Window frame)
        {
            if (frame == null) return;

            l = new WindowAdapter()
            {
                public void windowActivated(WindowEvent e)
                {
                    updateState();
                }

                public void windowClosed(WindowEvent e)
                {
                    updateState();
                }
                
                public void windowIconified(WindowEvent e)
                {
                    updateState();
                }
                
                public void windowDeiconified(WindowEvent e)
                {
                    updateState();
                }
            };

            frame.addWindowListener(l);            
        }

        private void detach(Window frame)
        {
            if (frame != null) frame.removeWindowListener(l);
            frame = null;
        }

        /*
         * 
         */
        private synchronized void updateState()
        {
            if (frame == null) return;

            this.visible = frame.isVisible();
            this.state = frame.getExtendedState();
        }

        /**
         * @return True if the window is visible and not iconified.
         */
        public synchronized boolean isVisibleNotMinimized()
        {
            return this.visible && (state & JFrame.ICONIFIED) == 0;
        }
    }    

    /**
     * Snaps one frame to another.
     */
    private static class FrameSnapper
    {
        private final JFrame parent;
        private final JFrame child;

        private final Rectangle parentBounds = new Rectangle();
        private final Rectangle childBounds = new Rectangle();

        /*
         * 
         */
        public FrameSnapper(JFrame parent, JFrame child)
        {
            this.parent = parent;
            this.child = child;

            parent.addComponentListener(new ComponentAdapter()
            {
                private final int GLUED_SIZE = 1;
                private final int SNAP_SIZE = 25;

                public void componentResized(ComponentEvent e)
                {
                    // Check if we were glued together.
                    SnapSide side = isGluedTo(parentBounds, childBounds, GLUED_SIZE);
                    if (side != null)
                    {
                        glueTo(side);
                    }
                }

                @Override
                public void componentMoved(ComponentEvent e)
                {
                    // Check if we were glued together previously.
                    SnapSide side = isGluedTo(parentBounds, childBounds, GLUED_SIZE);
                    if (side == null)
                    {
                        // If not, are we within the snapping distance? If so, glue together.
                        updateBounds();
                        side = isGluedTo(parentBounds, childBounds, SNAP_SIZE);
                    }

                    if (side != null)
                    {
                        glueTo(side);
                    }
                }
            });

            child.addComponentListener(new ComponentAdapter()
            {
                public void componentMoved(ComponentEvent e)
                {
                    updateBounds();
                }

                public void componentResized(ComponentEvent e)
                {
                    updateBounds();
                }
            });
        }

        /**
         * Unconditionally glue child to parent.
         */
        private void glueTo(SnapSide glueSide)
        {
            SwingUtils.glueTo(parent, child, glueSide);
            updateBounds();
        }        
        
        /**
         * Update current bounds of the parent and child.
         */
        private void updateBounds()
        {
            parent.getBounds(parentBounds);
            child.getBounds(childBounds);
        }
    }

    /**
     * @return Returns <code>true</code> if the frame is visible, showing and not
     *         iconified.
     */
    public static boolean isVisibleNotMinimized(JFrame frame)
    {
        assertEventDispatcherThread();

        return (frame.isVisible() && frame.isShowing() && (frame.getExtendedState() & JFrame.ICONIFIED) == 0);
    }

    /**
     * Glue the given docked frame to the given parent's frame.
     */
    public static void glueTo(JFrame parent, JFrame docked, SnapSide side)
    {
        assertEventDispatcherThread();

        final Point location = new Point();
        location.setLocation(parent.getLocation());

        switch (side)
        {
            case LEFT:
                location.translate(-docked.getWidth(), 0);
                break;
            case RIGHT:
                location.translate(parent.getWidth(), 0);
                break;
            case TOP:
                location.translate(0, -docked.getHeight());
                break;
            case BOTTOM:
                location.translate(0, parent.getHeight());
                break;
        }

        docked.setLocation(location);
    }

    /**
     * Check if two rectangles are glued together and return the side on which they
     * are (r2 wrt r1) or null if they are not.
     */
    public static SnapSide isGluedTo(Rectangle r1, Rectangle r2, int SNAP)
    {
        final Rectangle tmp = new Rectangle();

        Rectangle tmp2;
        int weight = 0;
        SnapSide side = null;

        // Check the right side.
        tmp.setBounds(r1.x + r1.width, r1.y, 0, r1.height);
        tmp.grow(SNAP, 0);
        tmp2 = tmp.intersection(r2);
        if (!tmp2.isEmpty())
        {
            side = SnapSide.RIGHT;
            weight = tmp.intersection(r2).height;
        }
        else
        {
            // Check the left side.
            tmp.setBounds(r1.x, r1.y, 0, r1.height);
            tmp.grow(SNAP, 0);
            tmp2 = tmp.intersection(r2);
            if (!tmp2.isEmpty())
            {
                side = SnapSide.LEFT;
                weight = tmp2.height;
            }
        }

        // Check the top side.
        tmp.setBounds(r1.x, r1.y, r1.width, 0);
        tmp.grow(0, SNAP);
        tmp2 = tmp.intersection(r2);
        if (!tmp2.isEmpty() && tmp2.width > weight)
        {
            side = SnapSide.TOP;
        }
        else
        {
            // Check the bottom side.
            tmp.setBounds(r1.x, r1.y + r1.height, r1.width, 0);
            tmp.grow(0, SNAP);
            tmp2 = tmp.intersection(r2);
            if (!tmp2.isEmpty() && tmp2.width > weight)
            {
                side = SnapSide.BOTTOM;
            }
        }

        return side;
    }

    /**
     * Create a "docked child window" (window glued to one side of a given frame). 
     */
    public static void snapFrame(final JFrame parent, final JFrame child)
    {
        assertEventDispatcherThread();
        new FrameSnapper(parent, child);
    }
    
    /**
     * Assert we are in the event dispatching thread.
     */
    public static void assertEventDispatcherThread()
    {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Not an AWT thread.");        
    }

    /**
     * Invoke and wait for the result. 
     */
    public static void invokeAndWait(Runnable runnable)
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            runnable.run();
        }
        else
        {
            try
            {
                SwingUtilities.invokeAndWait(runnable);
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException("AWT queue job interrupted.");
            }
            catch (InvocationTargetException e)
            {
                throw new RuntimeException("Unhandled exception in the AWT event queue.", e.getCause());
            }
        }
    }

    /**
     * Track frame visibility for asynchronous client threads.
     */
    public static VisibilityTracker createVisibilityTracker(JFrame frame)
    {
        final VisibilityTracker tracker = new VisibilityTracker(frame);
        return tracker;
    }

    /**
     * Track visibility of a single component. 
     */
    public static VisibilityTracker createVisibilityTracker(JComponent component)
    {
        return new VisibilityTracker(component);
    }

    /**
     * Schedule disposal of a frame (async).
     */
    public static void dispose(final JFrame frame)
    {
        SwingUtilities.invokeLater(new Runnable() {
            public void run()
            {
                if (frame.isDisplayable()) frame.dispose();
            }
        });
    }
    
    /**
     * Displays a message dialog with exception information.
     */
    public static void showExceptionDialog(Component component, String message, Throwable t)
    {
        final JPanel panel = new JPanel(new BorderLayout(5, 5));

        final JLabel messageLabel = new JLabel(message);
        messageLabel.setFont(new Font("Dialog", Font.BOLD, messageLabel
            .getFont().getSize()));

        final JTextArea stacktraceArea = new JTextArea(20, 60);
        stacktraceArea.setFont(new Font("Monospaced", Font.PLAIN,
            stacktraceArea.getFont().getSize()));
        stacktraceArea.setWrapStyleWord(false);
        stacktraceArea.setLineWrap(false);
        stacktraceArea.setText("Exception: " + t.getClass().getName() + "\n\n"
            + "Exception message: " + t.getMessage() + "\n\n"
            + "Stack trace:\n" + ExceptionUtils.getFullStackTrace(t));
        stacktraceArea.setEditable(false);
        final JScrollPane stacktraceAreaScroller = new JScrollPane(
            stacktraceArea);

        panel.add(messageLabel, BorderLayout.NORTH);
        panel.add(stacktraceAreaScroller, BorderLayout.CENTER);

        // Adjust stack trace dimensions
        final Dimension stacktraceDimension = stacktraceArea
            .getPreferredScrollableViewportSize();
        final Dimension screenDimension = Toolkit.getDefaultToolkit()
            .getScreenSize();
        screenDimension.setSize(screenDimension.getWidth() * 0.7,
            screenDimension.getHeight() * 0.7);
        final Dimension maxStackTraceDimension = new Dimension(500, 500);
        maxStackTraceDimension.setSize(Math.min(maxStackTraceDimension
            .getWidth(), screenDimension.getWidth()), Math.min(
            maxStackTraceDimension.getHeight(), screenDimension.getHeight()));
        stacktraceDimension.setSize(Math.min(stacktraceDimension.getWidth(),
            maxStackTraceDimension.getWidth()), Math.min(stacktraceDimension
            .getHeight(), maxStackTraceDimension.getHeight()));
        stacktraceAreaScroller.setPreferredSize(stacktraceDimension);
        stacktraceArea.setCaretPosition(0);

        JOptionPane.showMessageDialog(component, panel, "Program error.", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Centers a {@link JFrame} on screen.
     */
    public static void centerFrameOnScreen(JFrame frame)
    {
        final Dimension position = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setLocation((position.width - frame.getWidth()) / 2,
            (position.height - frame.getHeight()) / 2);
    }

    /**
     * Adds a key listener to a given {@link JDialog} that diposes it when the escape
     * key is pressed.
     */
    public static void addEscapeKeyCloseAction(final JDialog dialog)
    {
        dialog.getRootPane().registerKeyboardAction(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                dialog.dispose();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /**
     * Disable any selections on a table.
     */
    public static void disableSelection(JTable table)
    {
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(false);
        table.setCellSelectionEnabled(false);
        table.setFocusable(false);
    }    
}
