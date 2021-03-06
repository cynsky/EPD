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
package dk.dma.epd.ship.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.swing.BorderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.openmap.BufferedLayerMapBean;
import com.bbn.openmap.Layer;
import com.bbn.openmap.LayerHandler;
import com.bbn.openmap.MapHandler;
import com.bbn.openmap.MouseDelegator;
import com.bbn.openmap.proj.Proj;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.proj.coords.LatLonPoint;

import dk.dma.enav.model.geometry.Position;
import dk.dma.epd.common.prototype.event.HistoryListener;
import dk.dma.epd.common.prototype.gui.util.SimpleOffScreenMapRenderer;
import dk.dma.epd.common.prototype.gui.views.ChartPanelCommon;
import dk.dma.epd.common.prototype.layers.intendedroute.IntendedRouteLayerCommon;
import dk.dma.epd.common.prototype.layers.intendedroute.IntendedRouteTCPALayer;
import dk.dma.epd.common.prototype.layers.routeedit.NewRouteContainerLayer;
import dk.dma.epd.common.prototype.layers.wms.WMSLayer;
import dk.dma.epd.common.prototype.model.route.RoutesUpdateEvent;
import dk.dma.epd.common.prototype.model.voct.SAR_TYPE;
import dk.dma.epd.common.prototype.model.voct.sardata.DatumPointData;
import dk.dma.epd.common.prototype.model.voct.sardata.RapidResponseData;
import dk.dma.epd.common.prototype.sensor.pnt.IPntDataListener;
import dk.dma.epd.common.prototype.sensor.pnt.PntData;
import dk.dma.epd.common.prototype.voct.VOCTUpdateEvent;
import dk.dma.epd.common.prototype.voct.VOCTUpdateListener;
import dk.dma.epd.ship.EPDShip;
import dk.dma.epd.ship.event.DistanceCircleMouseMode;
import dk.dma.epd.ship.event.DragMouseMode;
import dk.dma.epd.ship.event.MSIFilterMouseMode;
import dk.dma.epd.ship.event.NavigationMouseMode;
import dk.dma.epd.ship.event.NoGoMouseMode;
import dk.dma.epd.ship.event.RouteEditMouseMode;
import dk.dma.epd.ship.gui.component_panels.ActiveWaypointComponentPanel;
import dk.dma.epd.ship.gui.nogo.NogoDialog;
import dk.dma.epd.ship.layers.EncLayerFactory;
import dk.dma.epd.ship.layers.GeneralLayer;
import dk.dma.epd.ship.layers.ais.AisLayer;
import dk.dma.epd.ship.layers.background.CoastalOutlineLayer;
import dk.dma.epd.ship.layers.msi.MsiLayer;
import dk.dma.epd.ship.layers.nogo.DynamicNogoLayer;
import dk.dma.epd.ship.layers.nogo.NogoLayer;
import dk.dma.epd.ship.layers.ownship.OwnShipLayer;
import dk.dma.epd.ship.layers.route.RouteLayer;
import dk.dma.epd.ship.layers.ruler.RulerLayer;
import dk.dma.epd.ship.layers.voct.VoctLayer;
import dk.dma.epd.ship.layers.voyage.VoyageLayer;
import dk.dma.epd.ship.service.voct.VOCTManager;
import dk.dma.epd.ship.settings.EPDMapSettings;
import dk.dma.epd.ship.layers.routeedit.RouteEditLayer;

/**
 * The panel with chart. Initializes all layers to be shown on the map.
 */

public class ChartPanel extends ChartPanelCommon implements IPntDataListener,
        MouseWheelListener, VOCTUpdateListener {


    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(ChartPanel.class);

    private NavigationMouseMode mapNavMouseMode;
    private DragMouseMode dragMouseMode;
    
    // Layers
    private OwnShipLayer ownShipLayer;
    private AisLayer aisLayer;
    private GeneralLayer generalLayer;
    private CoastalOutlineLayer coastalOutlineLayer;    
    private RouteLayer routeLayer;
    private VoyageLayer voyageLayer;
    private MsiLayer msiLayer;
    private NogoLayer nogoLayer;
    private DynamicNogoLayer dynamicNogoLayer;
    private VoctLayer voctLayer;
    private IntendedRouteLayerCommon intendedRouteLayer;
    private IntendedRouteTCPALayer intendedRouteTCPALayer;
    private NewRouteContainerLayer newRouteContainerLayer;
    private TopPanel topPanel;
    private RouteEditMouseMode routeEditMouseMode;
    private RouteEditLayer routeEditLayer;
    public int maxScale = 5000;
    private MSIFilterMouseMode msiFilterMouseMode;
    private VOCTManager voctManager;

    private DistanceCircleMouseMode rangeCirclesMouseMode;

    private ActiveWaypointComponentPanel activeWaypointPanel;

    private NogoDialog nogoDialog;
    private RulerLayer rulerLayer;

    private NoGoMouseMode noGoMouseMode;
    

    public ChartPanel(ActiveWaypointComponentPanel activeWaypointPanel) {
        super();
        // Set map handler
        mapHandler = EPDShip.getInstance().getMapHandler();
        dragMapHandler = new MapHandler();
        // Set layout
        setLayout(new BorderLayout());
        // Set border
        setBorder(BorderFactory.createLineBorder(Color.GRAY));
        this.activeWaypointPanel = activeWaypointPanel;
        // Max scale
        this.maxScale = EPDShip.getInstance().getSettings().getMapSettings()
                .getMaxScale();

        setBackground(new Color(0.1f, 0.1f, 0.1f, 0.1f));
    }

    /**
     * 
     */
    public void initChart() {

        EPDMapSettings mapSettings = EPDShip.getInstance().getSettings()
                .getMapSettings();
        Properties props = EPDShip.getInstance().getProperties();

        // Try to create ENC layer
        EncLayerFactory encLayerFactory = new EncLayerFactory(EPDShip
                .getInstance().getSettings().getMapSettings());
        encLayer = encLayerFactory.getEncLayer();

        // Add WMS Layer
        if (mapSettings.isUseWms()) {
            wmsLayer = new WMSLayer(mapSettings.getWmsQuery());
            mapHandler.add(wmsLayer);
        }

        // Create a MapBean, and add it to the MapHandler.
        map = new BufferedLayerMapBean();
        map.setDoubleBuffered(true);

        // Orthographic test = new Orthographic((LatLonPoint)
        // mapSettings.getCenter(), mapSettings.getScale(), 1000, 1000);
        // map.setProjection(test);

        
        
        mouseDelegator = new MouseDelegator();
        mapHandler.add(mouseDelegator);

        // Add MouseMode. The MouseDelegator will find it via the
        // MapHandler.
        // Adding NavMouseMode first makes it active.
        // mapHandler.add(new NavMouseMode());
        mapNavMouseMode = new NavigationMouseMode(this);
        noGoMouseMode = new NoGoMouseMode(this);
        routeEditMouseMode = new RouteEditMouseMode(this);
        
        msiFilterMouseMode = new MSIFilterMouseMode();
        dragMouseMode = new DragMouseMode(this);
        this.rangeCirclesMouseMode = new DistanceCircleMouseMode(false);

        mouseDelegator.addMouseMode(mapNavMouseMode);
        mouseDelegator.addMouseMode(noGoMouseMode);
        mouseDelegator.addMouseMode(routeEditMouseMode);
        mouseDelegator.addMouseMode(msiFilterMouseMode);
        mouseDelegator.addMouseMode(dragMouseMode);
        this.mouseDelegator.addMouseMode(this.rangeCirclesMouseMode);

        mouseDelegator.setActive(mapNavMouseMode);
        // Inform the distance circle mouse mode what mouse mode was initially
        // the active one
        this.rangeCirclesMouseMode
                .setPreviousMouseModeModeID(NavigationMouseMode.MODE_ID);

        mapHandler.add(mapNavMouseMode);
        mapHandler.add(noGoMouseMode);
        mapHandler.add(routeEditMouseMode);
        mapHandler.add(msiFilterMouseMode);
        mapHandler.add(activeWaypointPanel);
        mapHandler.add(rangeCirclesMouseMode);
        // added this to fix bug where cursor panel was not updated when in drag
        // mode
        mapHandler.add(dragMouseMode);

        // Use the LayerHandler to manage all layers, whether they are
        // on the map or not. You can add a layer to the map by
        // setting layer.setVisible(true).
        layerHandler = new LayerHandler();
        // Get plugin layers
        createPluginLayers(props);

        // Add layer handler to map handler
        mapHandler.add(layerHandler);

        // Create the general layer
        generalLayer = new GeneralLayer();
        generalLayer.setVisible(true);
        mapHandler.add(generalLayer);

        // Create VOCT Layer
        voctLayer = new VoctLayer();
        voctLayer.setVisible(true);
        mapHandler.add(voctLayer);

        // Create route layer
        routeLayer = new RouteLayer();
        routeLayer.setVisible(true);
        mapHandler.add(routeLayer);

        // Create ruler layer
        this.rulerLayer = new RulerLayer();
        this.rulerLayer.setVisible(true);
        mapHandler.add(rulerLayer);

        // Create voyage layer
        voyageLayer = new VoyageLayer();
        voyageLayer.setVisible(true);
        mapHandler.add(voyageLayer);

        // Create route editing layer
        newRouteContainerLayer = new NewRouteContainerLayer();
        newRouteContainerLayer.setVisible(true);
        mapHandler.add(newRouteContainerLayer);
        routeEditLayer = new RouteEditLayer();
        routeEditLayer.setVisible(true);
        mapHandler.add(routeEditLayer);

        // Create MSI layer
        msiLayer = new MsiLayer();
        msiLayer.setVisible(true);
        mapHandler.add(msiLayer);

        // Create Nogo layer
        nogoLayer = new NogoLayer();
        nogoLayer.setVisible(true);
        mapHandler.add(nogoLayer);

        dynamicNogoLayer = new DynamicNogoLayer();
        dynamicNogoLayer.setVisible(true);
        mapHandler.add(dynamicNogoLayer);

        // Create AIS layer
        aisLayer = new AisLayer(EPDShip.getInstance().getSettings().getAisSettings().getMinRedrawInterval() * 1000);
        aisLayer.setVisible(true);
        mapHandler.add(aisLayer);

        // Create own ship layer layer
        ownShipLayer = new OwnShipLayer();
        ownShipLayer.setVisible(true);
        mapHandler.add(ownShipLayer);

        // Create Intended Route Layer
        intendedRouteLayer = new IntendedRouteLayerCommon();
        intendedRouteLayer.setVisible(true);
        mapHandler.add(intendedRouteLayer);

//        //Create TCPA Graphics
        intendedRouteTCPALayer = new  IntendedRouteTCPALayer();
        intendedRouteTCPALayer.setVisible(true);
        mapHandler.add(intendedRouteTCPALayer);

        
        // Create a esri shape layer
        // URL dbf = EeINS.class.getResource("/shape/urbanap020.dbf");
        // URL shp = EeINS.class.getResource("/shape/urbanap020.shp");
        // URL shx = EeINS.class.getResource("/shape/urbanap020.shx");
        //
        // DrawingAttributes da = new DrawingAttributes();
        // da.setFillPaint(Color.blue);
        // da.setLinePaint(Color.black);
        //
        // EsriLayer esriLayer = new EsriLayer("Drogden", dbf, shp, shx, da);
        // mapHandler.add(esriLayer);

        // Create background layer
        String layerName = "background";
        coastalOutlineLayer = new CoastalOutlineLayer();
        coastalOutlineLayer.setProperties(layerName, props);
        coastalOutlineLayer.setAddAsBackground(true);
        coastalOutlineLayer.setVisible(true);

        mapHandler.add(coastalOutlineLayer);

        CoastalOutlineLayer coastalOutlineLayerDrag = new CoastalOutlineLayer();
        coastalOutlineLayerDrag.setProperties(layerName, props);
        coastalOutlineLayerDrag.setAddAsBackground(true);
        coastalOutlineLayerDrag.setVisible(true);
        // dragMapHandler.add(coastalOutlineLayerDrag);

        if (encLayer != null) {
            mapHandler.add(encLayer);
        }

        // Add map to map handler
        mapHandler.add(map);

        encLayerFactory.setMapSettings();

        // Set last postion
        map.setCenter(mapSettings.getCenter());

        // Get from settings
        map.setScale(mapSettings.getScale());
        // Set ENC map settings

        add(map);

        // TODO: CLEANUP
        // dragMap
        dragMap = new BufferedLayerMapBean();
        dragMap.setDoubleBuffered(true);
        dragMap.setCenter(mapSettings.getCenter());
        dragMap.setScale(mapSettings.getScale());

        dragMapHandler.add(new LayerHandler());
        if (mapSettings.isUseWms() && mapSettings.isUseWmsDragging()) {
            dragMapHandler.add(dragMap);
            wmsDragLayer = new WMSLayer(mapSettings.getWmsQuery());
            dragMapHandler.add(wmsDragLayer);
            dragMapRenderer = new SimpleOffScreenMapRenderer(map, dragMap, 3);
        } else {
            // create dummy map dragging
            dragMapRenderer = new SimpleOffScreenMapRenderer(map, dragMap, true);
        }

        dragMapRenderer.start();

        // Force a route layer and sensor panel update
        routeLayer.routesChanged(RoutesUpdateEvent.ROUTE_ADDED);
        activeWaypointPanel.routesChanged(RoutesUpdateEvent.ROUTE_ADDED);

        // Force a MSI layer update
        msiLayer.doUpdate();

        // Add this class as PNT data listener
        EPDShip.getInstance().getPntHandler().addListener(this);

        // encLayerFactory2.setMapSettings();

        // Hack to flush ENC layer
        // encLayerFactory.reapplySettings();
        // encLayerFactory2.reapplySettings();

        // Show AIS or not
        aisVisible(EPDShip.getInstance().getSettings().getAisSettings()
                .isVisible());
        // Show ENC or not
        encVisible(EPDShip.getInstance().getSettings().getMapSettings()
                .isEncVisible());

        // Show WMS or not
        wmsVisible(EPDShip.getInstance().getSettings().getMapSettings()
                .isWmsVisible());

        // Show intended routes or not
        setIntendedRouteLayerVisibility(EPDShip.getInstance().getSettings().getCloudSettings().isShowIntendedRoute());
        
        getMap().addMouseWheelListener(this);

    }

    protected void initDragMap() {
        EPDMapSettings mapSettings = EPDShip.getInstance().getSettings()
                .getMapSettings();
        // TODO: CLEANUP
        // dragMap
        dragMap = new BufferedLayerMapBean();
        dragMap.setDoubleBuffered(true);
        dragMap.setCenter(mapSettings.getCenter());
        dragMap.setScale(mapSettings.getScale());

        dragMapHandler.add(new LayerHandler());
        if (mapSettings.isUseWms() && mapSettings.isUseWmsDragging()) {
            dragMapHandler.add(dragMap);
            WMSLayer wmsDragLayer = new WMSLayer(mapSettings.getWmsQuery());
            dragMapHandler.add(wmsDragLayer);
            dragMapRenderer = new SimpleOffScreenMapRenderer(map, dragMap, 3);
        } else {
            // create dummy map dragging
            dragMapRenderer = new SimpleOffScreenMapRenderer(map, dragMap, true);
        }
        dragMapRenderer.start();
    }

    public void saveSettings() {
        EPDMapSettings mapSettings = EPDShip.getInstance().getSettings()
                .getMapSettings();
        mapSettings.setCenter((LatLonPoint) map.getCenter());
        mapSettings.setScale(map.getScale());
    }

    public MapHandler getMapHandler() {
        return mapHandler;
    }

    public Layer getOwnShipLayer() {
        return ownShipLayer;
    }

    public Layer getEncLayer() {
        return encLayer;
    }

    public Layer getBgLayer() {
        return coastalOutlineLayer;
    }
    
    public HistoryListener getProjectChangeListener() {
        return this.getHistoryListener();
    }
    
    public NoGoMouseMode getNoGoMouseMode() {
        return this.noGoMouseMode;
    }

    public void centreOnShip() {
        // Get current position
        PntData gpsData = EPDShip.getInstance().getPntHandler()
                .getCurrentData();
        if (gpsData == null) {
            return;
        }
        if (gpsData.getPosition() == null) {
            return;
        }
        map.setCenter((float) gpsData.getPosition().getLatitude(),
                (float) gpsData.getPosition().getLongitude());

    }

    public void doZoom(float factor) {
        float newScale = map.getScale() * factor;
        if (newScale < maxScale) {
            newScale = maxScale;
        }
        map.setScale(newScale);
        autoFollow();
    }

    public void aisVisible(boolean visible) {
        aisLayer.setVisible(visible);
    }

    public void encVisible(boolean visible) {
        if (encLayer != null) {
            encLayer.setVisible(visible);
            // encDragLayer.setVisible(visible);
            coastalOutlineLayer.setVisible(!visible);
            if (!visible) {
                // Force update of background layer
                coastalOutlineLayer.forceRedraw();
            }
        } else {
            coastalOutlineLayer.setVisible(true);
        }
    }

    public void wmsVisible(boolean visible) {
        if (wmsLayer != null) {
            wmsLayer.setVisible(visible);
        }
    }

    /**
     * Sets the visibility of the intended route layer
     * @param visible the visibility of the intended route layer
     */
    public void setIntendedRouteLayerVisibility(boolean visible) {
        if (intendedRouteLayer != null) {
            intendedRouteLayer.setVisible(visible);
        }
    }

    /**
     * Change the mouse mode.
     * 
     * @param mode
     *            The mode ID of the mouse mode to swap to (e.g.
     *            DistanceCircleMouseMode.MODE_ID).
     */
    public void setMouseMode(String modeID) {
        
        // Switching to RouteEditMouseMode
        if (modeID.equals(RouteEditMouseMode.MODE_ID)) {
            mouseDelegator.setActive(routeEditMouseMode);
            routeEditLayer.setVisible(true);
            routeEditLayer.setEnabled(true);
            newRouteContainerLayer.setVisible(true);

            // make sure toggle button is toggled if this mouse mode is enabled
            // by exiting DistanceCircleMouseMode
            this.topPanel.getNewRouteBtn().setSelected(true);

            EPDShip.getInstance().getMainFrame().getTopPanel()
                    .getNavigationMouseMode().setSelected(false);
            EPDShip.getInstance().getMainFrame().getTopPanel()
                    .getDragMouseMode().setSelected(false);
        }
        if (modeID.equals(NavigationMouseMode.MODE_ID)
                || modeID.equals(DragMouseMode.MODE_ID)
                || modeID.equals(DistanceCircleMouseMode.MODE_ID)) {
            // Clear new route graphics and toggle buttons
            routeEditLayer.setVisible(false);
            routeEditLayer.doPrepare();
            newRouteContainerLayer.setVisible(false);
            newRouteContainerLayer.getWaypoints().clear();
            newRouteContainerLayer.getRouteGraphics().clear();
            newRouteContainerLayer.doPrepare();
            EPDShip.getInstance().getMainFrame().getTopPanel().getNewRouteBtn()
                    .setSelected(false);
            EPDShip.getInstance().getMainFrame().getEeINSMenuBar()
                    .getNewRoute().setSelected(false);
            if (modeID.equals(NavigationMouseMode.MODE_ID)) {
                System.out.println("Setting nav mouse mode");
                mouseDelegator.setActive(mapNavMouseMode);
                EPDShip.getInstance().getMainFrame().getTopPanel()
                        .getNavigationMouseMode().setSelected(true);
                EPDShip.getInstance().getMainFrame().getTopPanel()
                        .getDragMouseMode().setSelected(false);
            }
            if (modeID.equals(DragMouseMode.MODE_ID)) {
                mouseDelegator.setActive(dragMouseMode);

                this.topPanel.getNavigationMouseMode().setSelected(false);
                EPDShip.getInstance().getMainFrame().getTopPanel()
                        .getDragMouseMode()

                        .setSelected(true);
                System.out.println("Setting drag mouse mode");
            }
        }
        if (modeID.equals(DistanceCircleMouseMode.MODE_ID)) {
            // Disable the other mouse mode toggle buttons.
            this.topPanel.getNavigationMouseMode().setSelected(false);
            this.topPanel.getDragMouseMode().setSelected(false);
            this.topPanel.getNewRouteBtn().setSelected(false);
            String prevMouseModeId = this.mouseDelegator.getActiveMouseMode()
                    .getID();
            // Store previous mouse mode ID such that we can go back to that
            // mouse mode
            this.rangeCirclesMouseMode
                    .setPreviousMouseModeModeID(prevMouseModeId);
            // Display the ruler layer.
            this.rulerLayer.setVisible(true);
            this.mouseDelegator.setActive(this.rangeCirclesMouseMode);
        } else {
            // When mouse mode is changed to something different than distance
            // circle mode
            // we untoggle the distance circle mode button
            this.topPanel.getToggleButtonDistanceCircleMouseMode().setSelected(
                    false);
            // hide ruler layer when not in "distance circles mode"
            this.rulerLayer.setVisible(false);
        }
        
        // Request NoGo Area.
        if (modeID.equals(NoGoMouseMode.MODE_ID)) {
            System.out.println("Setting NoGo Mouse Mode");
            
            // Set the mouse mode.
            this.mouseDelegator.setActive(this.noGoMouseMode);
        }     
    }

    public void autoFollow() {
        // Do auto follow
        if (!EPDShip.getInstance().getSettings().getNavSettings()
                .isAutoFollow()) {
            return;
        }

        // Only do auto follow if not bad position
        if (pntData == null || pntData.isBadPosition()) {
            return;
        }

        boolean lookahead = EPDShip.getInstance().getSettings()
                .getNavSettings().isLookAhead();

        // Find desired location (depends on look-ahead or not)

        double centerX = map.getWidth() / 2.0;
        double centerY = map.getHeight() / 2.0;
        double desiredX = centerX;
        double desiredY = centerY;

        if (lookahead) {
            double lookAheadBorder = 100.0;
            double lookAheadMinSpd = 1.0;
            double lookAheadMaxSpd = 15.0;

            // Calculate a factor [0;1] from speed
            double factor = 0;
            if (pntData.getSog() < lookAheadMinSpd) {
                factor = 0;
            } else if (pntData.getSog() < lookAheadMaxSpd) {
                factor = pntData.getSog() / lookAheadMaxSpd;
            } else {
                factor = 1.0;
            }

            double phiX = Math.cos(Math.toRadians(pntData.getCog()) - 3
                    * Math.PI / 2);
            double phiY = Math.sin(Math.toRadians(pntData.getCog()) - 3
                    * Math.PI / 2);

            double fx = factor * phiX;
            double fy = factor * phiY;

            desiredX = centerX + (centerX - lookAheadBorder) * fx;
            desiredY = centerY + (centerY - lookAheadBorder) * fy;

        }

        // Get projected x,y of current position
        Point2D shipXY = map.getProjection().forward(
                pntData.getPosition().getLatitude(),
                pntData.getPosition().getLongitude());

        // Calculate how many percent the position is off for x and y
        double pctOffX = Math.abs(desiredX - shipXY.getX()) / map.getWidth()
                * 100.0;
        double pctOffY = Math.abs(desiredY - shipXY.getY()) / map.getHeight()
                * 100.0;

        // LOG.info("pctOffX: " + pctOffX + " pctOffY: " + pctOffY);

        int tollerated = EPDShip.getInstance().getSettings().getNavSettings()
                .getAutoFollowPctOffTollerance();
        if (pctOffX < tollerated && pctOffY < tollerated) {
            return;
        }

        if (lookahead) {
            Point2D forwardCenter = map.getProjection().inverse(
                    centerX - desiredX + shipXY.getX(),
                    centerY - desiredY + shipXY.getY());
            map.setCenter((float) forwardCenter.getY(),
                    (float) forwardCenter.getX());
        } else {
            map.setCenter((float) pntData.getPosition().getLatitude(),
                    (float) pntData.getPosition().getLongitude());
        }

    }

    /**
     * Receive GPS update
     */
    @Override
    public void pntDataUpdate(PntData gpsData) {
        this.pntData = gpsData;
        autoFollow();
    }

    /**
     * Given a set of points scale and center so that all points are contained
     * in the view
     * 
     * @param waypoints
     */
    public void zoomTo(List<Position> waypoints) {
        if (waypoints.size() == 0) {
            return;
        }

        // Disable auto follow
        EPDShip.getInstance().getSettings().getNavSettings()
                .setAutoFollow(false);
        topPanel.updateButtons();

        if (waypoints.size() == 1) {
            map.setCenter(waypoints.get(0).getLatitude(), waypoints.get(0)
                    .getLongitude());
            return;
        }

        // Find bounding box
        double maxLat = -91;
        double minLat = 91;
        double maxLon = -181;
        double minLon = 181;
        for (Position pos : waypoints) {
            if (pos.getLatitude() > maxLat) {
                maxLat = pos.getLatitude();
            }
            if (pos.getLatitude() < minLat) {
                minLat = pos.getLatitude();
            }
            if (pos.getLongitude() > maxLon) {
                maxLon = pos.getLongitude();
            }
            if (pos.getLongitude() < minLon) {
                minLon = pos.getLongitude();
            }
        }

        double centerLat = (maxLat + minLat) / 2.0;
        double centerLon = (maxLon + minLon) / 2.0;
        map.setCenter(centerLat, centerLon);

        // LatLonPoint minlatlon = new LatLonPoint.Double(maxLat, minLon); //
        // upper left corner
        // LatLonPoint maxlatlon = new LatLonPoint.Double(minLat, maxLon); //
        // lower right corner
        //
        // Point2D pixelminlatlon = map.getProjection().forward(minlatlon);
        // Point2D pixelmaxlatlon = map.getProjection().forward(maxlatlon);
        //
        // float newScale = ProjMath.getScaleFromProjected(pixelminlatlon,
        // pixelmaxlatlon, map.getProjection());

        // map.setScale(newScale*5);
    }

    /**
     * Called when projection has been changed by user
     */
    public void manualProjChange() {
        topPanel.disableAutoFollow();
    }

    public MouseDelegator getMouseDelegator() {
        return mouseDelegator;
    }

    private void createPluginLayers(Properties props) {
        String layersValue = props.getProperty("eeins.plugin_layers");
        if (layersValue == null) {
            return;
        }
        String[] layerNames = layersValue.split(" ");
        for (String layerName : layerNames) {
            String classProperty = layerName + ".class";
            String className = props.getProperty(classProperty);
            if (className == null) {
                LOG.error("Failed to locate property " + classProperty);
                continue;
            }
            try {
                // Create it if you do...
                Object obj = java.beans.Beans.instantiate(null, className);
                if (obj instanceof Layer) {
                    Layer l = (Layer) obj;
                    // All layers have a setProperties method, and
                    // should intialize themselves with proper
                    // settings here. If a property is not set, a
                    // default should be used, or a big, graceful
                    // complaint should be issued.
                    l.setProperties(layerName, props);
                    l.setVisible(true);
                    layerHandler.addLayer(l);
                }
            } catch (java.lang.ClassNotFoundException e) {
                LOG.error("Layer class not found: \"" + className + "\"");
            } catch (java.io.IOException e) {
                LOG.error("IO Exception instantiating class \"" + className
                        + "\"");
            }
        }
    }
    
    public void zoomToPosition(Position pos) {
        map.setCenter((float) pos.getLatitude(), (float) pos.getLongitude());
    }

    public int getMaxScale() {
        return maxScale;
    }
    
    public void setNogoDialog(NogoDialog dialog) {
        this.nogoDialog = dialog;
    }

    public NogoDialog getNogoDialog() {
        return nogoDialog;
    }

    @Override
    public void findAndInit(Object obj) {
        if (obj instanceof TopPanel) {
            topPanel = (TopPanel) obj;
            // Maybe no S52 layer
            if (encLayer == null) {
                topPanel.setEncDisabled();
            }
            if (wmsLayer == null) {
                topPanel.setWMSDisabled();
            }
        }
        if (obj instanceof VOCTManager) {
            voctManager = (VOCTManager) obj;
            voctManager.addListener(this);
        }
    }

    /**
     * Call auto follow when zooming
     */
    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        autoFollow();
    }

    /**
     * 
     * @param direction
     *            1 == Up 2 == Down 3 == Left 4 == Right
     * 
     *            Moving by 100 units in each direction Map center is [745, 445]
     */
    public void pan(int direction) {
        Point point = null;
        Projection projection = map.getProjection();

        int width = projection.getWidth();
        int height = projection.getHeight();

        switch (direction) {
        case 1:
            point = new Point(width / 2, height / 2 - 100);
            break;
        case 2:
            point = new Point(width / 2, height / 2 + 100);
            break;
        case 3:
            point = new Point(width / 2 - 100, height / 2);
            break;
        case 4:
            point = new Point(width / 2 + 100, height / 2);
            break;
        }

        Proj p = (Proj) projection;
        LatLonPoint llp = projection.inverse(point);
        p.setCenter(llp);
        map.setProjection(p);
        manualProjChange();
    }

    @Override
    public void voctUpdated(VOCTUpdateEvent e) {
        if (e == VOCTUpdateEvent.SAR_RECEIVED_CLOUD) {
            if (voctManager.getSarType() == SAR_TYPE.RAPID_RESPONSE) {

                RapidResponseData data = (RapidResponseData) voctManager
                        .getSarData();

                if (data.getEffortAllocationData().size() > 0) {
                    List<Position> waypoints = new ArrayList<Position>();
                    waypoints.add(data.getA());
                    waypoints.add(data.getB());
                    waypoints.add(data.getC());
                    waypoints.add(data.getD());

                    this.zoomTo(waypoints);
                    return;

                } else {
                    List<Position> waypoints = new ArrayList<Position>();

                    waypoints.add(data.getDatum());
                    this.zoomTo(waypoints);
                    return;

                }

            }
        }

        if (e == VOCTUpdateEvent.SAR_DISPLAY) {

            if (voctManager.getSarType() == SAR_TYPE.RAPID_RESPONSE) {
                RapidResponseData data = (RapidResponseData) voctManager
                        .getSarData();

                List<Position> waypoints = new ArrayList<Position>();
                waypoints.add(data.getA());
                waypoints.add(data.getB());
                waypoints.add(data.getC());
                waypoints.add(data.getD());

                this.zoomTo(waypoints);
                return;
            }

            if (voctManager.getSarType() == SAR_TYPE.DATUM_POINT) {
                DatumPointData data = (DatumPointData) voctManager.getSarData();

                List<Position> waypoints = new ArrayList<Position>();
                waypoints.add(data.getA());
                waypoints.add(data.getB());
                waypoints.add(data.getC());
                waypoints.add(data.getD());

                this.zoomTo(waypoints);
                return;
            }

        }
    }

}
