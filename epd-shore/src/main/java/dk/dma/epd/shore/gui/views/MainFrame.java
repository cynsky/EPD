/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.dma.epd.shore.gui.views;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.beans.PropertyVetoException;
import java.beans.beancontext.BeanContextServicesSupport;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;

import dk.dma.enav.model.geometry.Position;
import dk.dma.epd.common.prototype.EPD;
import dk.dma.epd.common.prototype.gui.MainFrameCommon;
import dk.dma.epd.common.prototype.gui.notification.ChatServiceDialog;
import dk.dma.epd.common.prototype.model.route.Route;
import dk.dma.epd.common.util.VersionInfo;
import dk.dma.epd.shore.EPDShore;
import dk.dma.epd.shore.gui.route.RouteManagerDialog;
import dk.dma.epd.shore.gui.voct.SRUManagerDialog;
import dk.dma.epd.shore.gui.route.strategic.SendStrategicRouteDialog;
import dk.dma.epd.shore.settings.EPDGuiSettings;
import dk.dma.epd.shore.settings.EPDMapSettings;
import dk.dma.epd.shore.settings.Workspace;
import dk.dma.epd.shore.util.ThreadedMapCreator;
import dk.dma.epd.shore.voyage.Voyage;

/**
 * The main frame containing map and panels
 * 
 * @author David A. Camre (davidcamre@gmail.com)
 */
public class MainFrame extends MainFrameCommon {

    private static final String TITLE = "EPD-shore " + VersionInfo.getVersion();

    private static final long serialVersionUID = 1L;

    private int windowCount;
    private Dimension size = new Dimension(1000, 700);
    private Point location;
    private JMenuWorkspaceBar topMenu;
    private boolean fullscreen;
    private int mouseMode = 2;
    private boolean wmsLayerEnabled;
    private boolean msiLayerEnabled = true;
    private boolean encLayerEnabled;
    private boolean useEnc;

    private BeanContextServicesSupport beanHandler;
    private List<JMapFrame> mapWindows;
    private JMainDesktopPane desktop;

    private JScrollPane scrollPane;
    private boolean toolbarsLocked;
    private ToolBar toolbar = new ToolBar(this);
    private RouteManagerDialog routeManagerDialog = new RouteManagerDialog(this);
    private SendRouteDialog sendRouteDialog = new SendRouteDialog(this);
    private SRUManagerDialog sruManagerDialog = new SRUManagerDialog(this);
    private SendStrategicRouteDialog sendVoyageDialog = new SendStrategicRouteDialog();

    private StatusArea statusArea = new StatusArea(this);
    private JMapFrame activeMapWindow;
    private long selectedMMSI = -1;

    private boolean sarCreated;

    /**
     * Constructor
     */
    public MainFrame() {
        super(TITLE);
        // System.out.println("before init gui");
        initGUI();

    }

    /**
     * Initializes the glass pane of the frame
     */
    @Override
    protected void initGlassPane() {
        // Do nothing. EPDShore uses MapFrames for the various maps
    }

    /**
     * Returns the chart panel of the active map window
     * @return the chart panel of the active map window
     */
    public ChartPanel getActiveChartPanel() {
        if (getActiveMapWindow() != null) {
            return getActiveMapWindow()
                .getChartPanel();
        } else if (getMapWindows().size() > 0) {
            getMapWindows()
                .get(0)
                .getChartPanel();
        }
        return null;
    }
    
    /**
     * Zooms the active map to the given position
     * @param pos the position to zoom to
     */
    @Override
    public void zoomToPosition(Position pos) {
        if (getActiveChartPanel() != null) {
            getActiveChartPanel().zoomToPoint(pos);
        }
    }
    
    public synchronized void increaseWindowCount() {
        windowCount++;
    }

    public int getWindowCount() {
        return windowCount;
    }

    public JMapFrame getActiveMapWindow() {
        return activeMapWindow;
    }

    public void setActiveMapWindow(JMapFrame activeMapWindow) {
        this.activeMapWindow = activeMapWindow;
    }

    /**
     * Create and add a new map window
     * 
     * @return
     */
    public void addMapWindow() {

        new Thread(new ThreadedMapCreator(this)).run();

    }

    /**
     * 
     */
    public void addSARWindow(MapFrameType type) {

        if (sarCreated) {
            // Warning message about one SAR operation being underway?
        } else {
            (new ThreadedMapCreator(this, sarCreated, type)).run();
            // SwingUtilities.invokeLater(new ThreadedMapCreator(this, sarCreated, type));

        }

        // When creating a SAR window it displays map but also input boxes for starting it.

    }

    public void addStrategicRouteExchangeHandlingWindow(Route originalRoute, String shipName, Voyage voyage, boolean renegotiate) {
        new ThreadedMapCreator(this, shipName, voyage, originalRoute, renegotiate).run();
    }

    /**
     * Add a new mapWindow with specific parameters, usually called when loading a workspace ======= new
     * ThreadedMapCreator(this).run(); }
     * 
     * public void addStrategicRouteHandlingWindow(Route originalRoute, String shipName, Voyage voyage, boolean renegotiate) { new
     * ThreadedMapCreator(this, shipName, voyage, originalRoute, renegotiate).run(); }
     * 
     * /** Add a new mapWindow with specific parameters, usually called when loading a workspace >>>>>>>
     * 70dcfc231d7d05f4c850ee37d75d3e74bb7cea56
     * 
     * @param workspace
     * @param center
     * @param scale
     * @param boolean3
     * @param boolean2
     * @param boolean1
     * @param point
     * @param dimension
     * @param string
     * @return
     */
    public void addMapWindow(boolean workspace, boolean locked, boolean alwaysInFront, Point2D center, float scale, String title,
            Dimension size, Point location, Boolean maximized) {

        ThreadedMapCreator windowCreator = new ThreadedMapCreator(this, workspace, locked, alwaysInFront, center, scale, title,
                size, location, maximized);

        windowCreator.run();

        if (this.getMapWindows().size() > 0) {
            if (this.getMapWindows().get(0).getChartPanel().getEncLayer() != null && !this.getToolbar().isEncButtonEnabled()) {
                this.getToolbar().enableEncButton();
            }
        }

    }

    public boolean isUseEnc() {
        return useEnc;
    }

    public void setUseEnc(boolean useEnc) {
        this.useEnc = useEnc;
    }

    /**
     * Return the desktop
     * 
     * @return
     */
    public JMainDesktopPane getDesktop() {
        return desktop;
    }

    /**
     * Return a list of all active mapwindows
     * 
     * @return
     */
    public List<JMapFrame> getMapWindows() {
        return mapWindows;
    }

    /**
     * Get the route manager dialog frame
     * 
     * @return
     */
    public RouteManagerDialog getRouteManagerDialog() {
        return routeManagerDialog;
    }

    /**
     * Return the max resolution possible across all monitors
     * 
     * @return
     */
    public Dimension getMaxResolution() {
        int width = 0;
        int height = 0;

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gs = ge.getScreenDevices();

        for (GraphicsDevice curGs : gs) {
            DisplayMode mode = curGs.getDisplayMode();
            width += mode.getWidth();

            // System.out.println("Width: " + width);

            if (height < mode.getHeight()) {
                height = mode.getHeight();
            }

        }
        return new Dimension(width, height);

    }

    /**
     * Return current active mouseMode
     * 
     * @return
     */
    public int getMouseMode() {
        return mouseMode;
    }

    /**
     * Return the status area
     * 
     * @return
     */
    public StatusArea getStatusArea() {
        return statusArea;
    }

    /**
     * Return the toolbar
     * 
     * @return
     */
    public ToolBar getToolbar() {
        return toolbar;
    }

    /**
     * Initialize the GUI
     */
    private void initGUI() {

        beanHandler = EPDShore.getInstance().getBeanHandler();
        // Get settings
        EPDGuiSettings guiSettings = EPDShore.getInstance().getSettings().getGuiSettings();
        EPDMapSettings mapSettings = EPDShore.getInstance().getSettings().getMapSettings();

        // System.out.println("Setting wmslayer enabled to:" +
        // guiSettings.useWMS());
        wmsLayerEnabled = mapSettings.isUseWms();
        encLayerEnabled = EPDShore.getInstance().getSettings().getMapSettings().isEncVisible();
        useEnc = EPDShore.getInstance().getSettings().getMapSettings().isUseEnc();

        Workspace workspace = EPDShore.getInstance().getSettings().getWorkspace();

        // Set location and size
        if (guiSettings.isMaximized()) {
            setExtendedState(getExtendedState() | MAXIMIZED_BOTH);
        } else {
            setLocation(guiSettings.getAppLocation());
        }
        if (guiSettings.isFullscreen()) {
            toggleFullScreen();
        } else {
            setSize(guiSettings.getAppDimensions());
        }

        this.setLayout(new BorderLayout(0, 0));

        desktop = new JMainDesktopPane(this);
        scrollPane = new JScrollPane();

        scrollPane.getViewport().add(desktop);
        this.getContentPane().add(scrollPane, BorderLayout.CENTER);

        desktop.setBackground(new Color(39, 39, 39));

        mapWindows = new ArrayList<JMapFrame>();

        topMenu = new JMenuWorkspaceBar(this);
        this.setJMenuBar(topMenu);

        BottomPanel bottomPanel = new BottomPanel();

        // Initiate the permanent window elements
        desktop.getManager().setStatusArea(statusArea);
        desktop.getManager().setToolbar(toolbar);
        desktop.getManager().setRouteManager(routeManagerDialog);
        desktop.getManager().setSendVoyageDialog(sendVoyageDialog);
        desktop.getManager().setSRUManagerDialog(sruManagerDialog);

        desktop.add(statusArea, true);
        desktop.add(toolbar, true);
        desktop.add(sendVoyageDialog, true);

        beanHandler.add(bottomPanel);
        beanHandler.add(sendRouteDialog);
        beanHandler.add(sendVoyageDialog);

        chatServiceDialog = new ChatServiceDialog(this);
        
        // Add self to bean handler
        beanHandler.add(this);

        desktop.add(routeManagerDialog, true);
        beanHandler.add(routeManagerDialog);
        beanHandler.add(routeManagerDialog.getRouteManager());

        desktop.add(sruManagerDialog, true);
        beanHandler.add(sruManagerDialog);

        // routeManagerDialog.setVisible(true);

        this.getContentPane().add(bottomPanel, BorderLayout.SOUTH);

        setWorkSpace(workspace);

    }

    /**
     * Return the status on toolbars
     * 
     * @return
     */
    public boolean isToolbarsLocked() {
        return toolbarsLocked;
    }

    /**
     * Load and setup a new workspace from a file
     * 
     * @param parent
     * @param filename
     */
    public void loadNewWorkspace(String parent, String filename) {
        Workspace workspace = EPDShore.getInstance().getSettings().loadWorkspace(parent, filename);
        setWorkSpace(workspace);
    }

    /**
     * Close a mapWindow
     * 
     * @param window
     */
    public void removeMapWindow(JMapFrame window) {
        topMenu.removeMapMenu(window);
        mapWindows.remove(window);
    }

    /**
     * Rename a mapwindow
     * 
     * @param window
     */
    public void renameMapWindow(JMapFrame window) {
        topMenu.renameMapMenu(window);
    }

    /**
     * Lock a window in the top menu bar
     * 
     * @param window
     *            the window
     */
    public void lockMapWindow(JMapFrame window, boolean locked) {
        topMenu.lockMapMenu(window, locked);
    }

    /**
     * Set a window always on top in top menu
     * 
     * @param window
     *            the window
     */
    public void onTopMapWindow(JMapFrame window, boolean locked) {
        topMenu.onTopMapMenu(window, locked);
    }

    /**
     * Save the window settings
     */
    public void saveSettings() {
        // Save gui settings
        EPDGuiSettings guiSettings = EPDShore.getInstance().getSettings().getGuiSettings();
        guiSettings.setFullscreen(fullscreen);
        guiSettings.setMaximized((getExtendedState() & MAXIMIZED_BOTH) > 0);
        guiSettings.setAppLocation(getLocation());
        guiSettings.setAppDimensions(getSize());

        // Save map settings
        // chartPanel.saveSettings();

    }

    /**
     * Save the workspace with a given name
     * 
     * @param filename
     */
    public void saveWorkSpace(String filename) {

        EPDShore.getInstance().getSettings().getWorkspace().setToolbarPosition(toolbar.getLocation());
        EPDShore.getInstance().getSettings().getWorkspace().setStatusPosition(statusArea.getLocation());

        List<JMapFrame> windowsToSave = new ArrayList<JMapFrame>();

        System.out.println("Saving " + mapWindows.size() + " map windows to workspace");
        for (int i = 0; i < mapWindows.size(); i++) {
            System.out.println(mapWindows.get(i).getType() + " id " + i);
            // System.out.println("With type " + mapWindows.get(i).getType());
            if (mapWindows.get(i).getType() == MapFrameType.standard) {
                windowsToSave.add(mapWindows.get(i));
            }
        }

        EPDShore.getInstance().getSettings().saveCurrentWorkspace(windowsToSave, filename);

    }

    /**
     * Set the mouse mode
     * 
     * @param mouseMode
     */
    public void setMouseMode(int mouseMode) {
        this.mouseMode = mouseMode;
    }

    /**
     * Set a workspace as active
     * 
     * @param workspace
     */
    public void setWorkSpace(Workspace workspace) {

        getDesktop().getManager().clearToFront();

        while (mapWindows.size() != 0) {
            try {
                mapWindows.get(0).setClosed(true);
            } catch (PropertyVetoException e) {
                e.printStackTrace();
            }
        }

        // Reset the workspace
        windowCount = 0;
        mapWindows = new ArrayList<JMapFrame>();

        if (workspace.isValidWorkspace()) {
            for (int i = 0; i < workspace.getName().size(); i++) {
                // JMapFrame window =
                addMapWindow(true, workspace.isLocked().get(i), workspace.getAlwaysInFront().get(i), workspace.getCenter().get(i),
                        workspace.getScale().get(i),

                        workspace.getName().get(i), workspace.getSize().get(i), workspace.getPosition().get(i), workspace
                                .isMaximized().get(i)

                );

                // window.getChartPanel().getMap().setScale(0.001f);
                // window.getChartPanel().getMap().setCenter(workspace.getCenter().get(i));
            }

            // Restore the layer toggling panel settings
            for (int x = 0; x < workspace.getLayerPanelPosition().size(); x++) {
                if (x < mapWindows.size()) {
                    mapWindows.get(x).getLayerTogglingPanel().setLocation(workspace.getLayerPanelPosition().get(x));
                }
            }
            for (int x = 0; x < workspace.getLayerPanelVisible().size(); x++) {
                if (x < mapWindows.size()) {
                    mapWindows.get(x).getLayerTogglingPanel().setVisible(workspace.getLayerPanelVisible().get(x));
                }
            }
        }
        statusArea.setLocation(workspace.getStatusPosition());
        toolbar.setLocation(workspace.getToolbarPosition());

        // Bring toolbar elements to the front
        statusArea.toFront();
        toolbar.toFront();
    }

    /**
     * Toggle the toolbars as locked
     * 
     * This function is never called in the current version.
     */
    public void toggleBarsLock() {
        toolbarsLocked = !toolbarsLocked;

        toolbar.toggleLock();
        statusArea.toggleLock();
    }

    /**
     * Set the maindow in fullscreen mode
     */
    public void toggleFullScreen() {

        if (!fullscreen) {
            location = this.getLocation();
            // System.out.println("Size is: " + size);

            this.setSize(getMaxResolution());
            // setLocationRelativeTo(null);
            this.setLocation(0, 0);
            // setExtendedState(JFrame.MAXIMIZED_BOTH);
            dispose();
            this.setUndecorated(true);
            setVisible(true);
            fullscreen = true;
        } else {
            // setExtendedState(JFrame.NORMAL);
            fullscreen = false;
            if (size.getHeight() != 0 && size.getWidth() != 0) {
                size = Toolkit.getDefaultToolkit().getScreenSize();
                // size = new Dimension(1000, 700);
            }
            this.setSize(size);
            this.setLocation(location);
            dispose();
            this.setUndecorated(false);
            setVisible(true);
        }
    }

    /**
     * Get if the WMS status is enabled
     * 
     * @return boolean detailing if the layer is enabled
     */
    public boolean isWmsLayerEnabled() {
        return wmsLayerEnabled;
    }

    /**
     * set the WMS layers enabled/disabled
     * 
     * @param wmsLayerEnabled
     */
    public void setWmsLayerEnabled(boolean wmsLayerEnabled) {
        this.wmsLayerEnabled = wmsLayerEnabled;
    }

    /**
     * @return the encLayerEnabled
     */
    public boolean isEncLayerEnabled() {
        return encLayerEnabled;
    }

    /**
     * @param encLayerEnabled
     *            the encLayerEnabled to set
     */
    public void setEncLayerEnabled(boolean encLayerEnabled) {
        this.encLayerEnabled = encLayerEnabled;
    }

    /**
     * Get if the MSI status is enabled
     * 
     * @return boolean detailing if the layer is enabled
     */
    public boolean isMsiLayerEnabled() {
        return msiLayerEnabled;
    }

    /**
     * set the MSI layers enabled/disabled
     * 
     * @param wmsLayerEnabled
     */
    public void setMSILayerEnabled(boolean msiLayerEnabled) {
        this.msiLayerEnabled = msiLayerEnabled;
    }

    public synchronized long getSelectedMMSI() {
        return selectedMMSI;
    }

    public synchronized void setSelectedMMSI(long selectedMMSI) {
        this.selectedMMSI = selectedMMSI;
        for (int i = 0; i < mapWindows.size(); i++) {
            mapWindows.get(i).getChartPanel().getAisLayer().setSelectedTarget(selectedMMSI, true);
        }
    }

    public SendRouteDialog getSendRouteDialog() {
        return sendRouteDialog;
    }

    public SendStrategicRouteDialog getSendVoyageDialog() {
        return sendVoyageDialog;
    }

    public JMenuWorkspaceBar getTopMenu() {
        return topMenu;
    }

    /**
     * @return the sruManagerDialog
     */
    public SRUManagerDialog getSruManagerDialog() {
        return sruManagerDialog;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SetupDialogShore openSetupDialog() {
        SetupDialogShore setupDialog = new SetupDialogShore(this);
        setupDialog.loadSettings(EPD.getInstance().getSettings());
        setupDialog.setVisible(true);
        return setupDialog;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Action getAboutAction() {
        Action aboutEpdShore = new AbstractAction("About EPD-shore", new ImageIcon(EPD.getInstance().getAppIcon(16))) {
            
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                final ImageIcon icon = new ImageIcon(EPD.getInstance().getAppIcon(45));
                
                final StringBuilder aboutText = new StringBuilder();
                aboutText.append("The E-navigation Prototype Display Shore (EPD-shore) is developed by the Danish Maritime Authority (www.dma.dk).\n");
                aboutText.append("The user manual is available from service.e-navigation.net\n\n");
                aboutText.append("Version   : " + VersionInfo.getVersion() + "\n");
                aboutText.append("Build ID  : " + VersionInfo.getBuildId() + "\n");
                aboutText.append("Build date: " + VersionInfo.getBuildDate());
                
                JOptionPane
                .showMessageDialog(
                        MainFrame.this,
                        aboutText.toString(),
                        "About the EPD-shore", JOptionPane.OK_OPTION, icon);
            }
        };
        return aboutEpdShore;
    }
}
