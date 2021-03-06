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

import java.awt.Point;

import com.bbn.openmap.MapBean;

import dk.dma.enav.model.geometry.Position;
import dk.dma.epd.common.prototype.ais.SarTarget;
import dk.dma.epd.common.prototype.ais.VesselTarget;
import dk.dma.epd.common.prototype.gui.MapMenuCommon;
import dk.dma.epd.common.prototype.gui.menuitems.SarTargetDetails;
import dk.dma.epd.common.prototype.gui.menuitems.ToggleAisTargetName;
import dk.dma.epd.common.prototype.gui.menuitems.VoyageHandlingLegInsertWaypoint;
import dk.dma.epd.common.prototype.layers.ais.VesselGraphicComponentSelector;
import dk.dma.epd.common.prototype.layers.routeedit.NewRouteContainerLayer;
import dk.dma.epd.common.prototype.model.route.Route;
import dk.dma.epd.common.prototype.model.route.RouteLeg;
import dk.dma.epd.shore.EPDShore;
import dk.dma.epd.shore.gui.views.menuitems.GeneralNewRoute;
import dk.dma.epd.shore.gui.views.menuitems.LayerToggleWindow;
import dk.dma.epd.shore.gui.views.menuitems.RouteEditEndRoute;
import dk.dma.epd.shore.gui.views.menuitems.SendRouteFromRoute;
import dk.dma.epd.shore.gui.views.menuitems.SendRouteToShip;
import dk.dma.epd.shore.gui.views.menuitems.SendVoyage;
import dk.dma.epd.shore.gui.views.menuitems.ShowVoyagePlanInfo;
import dk.dma.epd.shore.gui.views.menuitems.ToggleAisTargetNames;
import dk.dma.epd.shore.gui.views.menuitems.VoyageDeleteMenuItem;
import dk.dma.epd.shore.gui.views.menuitems.VoyageHandlingAppendWaypoint;
import dk.dma.epd.shore.gui.views.menuitems.VoyageHandlingOptimizeRoute;
import dk.dma.epd.shore.gui.views.menuitems.VoyageHandlingWaypointDelete;
import dk.dma.epd.shore.gui.views.menuitems.VoyageHideAll;
import dk.dma.epd.shore.gui.views.menuitems.VoyageProperties;
import dk.dma.epd.shore.gui.views.menuitems.VoyageRenegotiate;
import dk.dma.epd.shore.gui.views.menuitems.VoyageShowTransaction;
import dk.dma.epd.shore.gui.views.menuitems.VoyageZoomToShip;
import dk.dma.epd.shore.layers.ais.AisLayer;
import dk.dma.epd.shore.layers.voyage.VoyageHandlingLayer;
import dk.dma.epd.shore.layers.voyage.VoyagePlanInfoPanel;
import dk.dma.epd.shore.route.RouteManager;
import dk.dma.epd.shore.service.StrategicRouteHandler;
import dk.dma.epd.shore.voyage.Voyage;

/**
 * Right click map menu
 */
public class MapMenu extends MapMenuCommon {

    private static final long serialVersionUID = 1L;

    // menu items
    private GeneralNewRoute newRoute;

    private SarTargetDetails sarTargetDetails;

    private RouteEditEndRoute routeEditEndRoute;
    private SendRouteToShip sendRouteToShip;
    private SendRouteFromRoute setRouteExchangeRoute;

    private VoyageHandlingLegInsertWaypoint voyageHandlingLegInsertWaypoint;
    private VoyageHandlingWaypointDelete voyageHandlingWaypointDelete;
    private VoyageHandlingAppendWaypoint voyageHandlingAppendWaypoint;
    private VoyageHandlingOptimizeRoute voyageHandlingOptimizeRoute;

    private VoyageProperties voyageProperties;
    private VoyageRenegotiate voyageRenegotiate;
    private VoyageShowTransaction voyageShowTransaction;
    private VoyageZoomToShip voyageZoomToShip;
    private VoyageHideAll voyageHideAll;
    private VoyageDeleteMenuItem voyageDelete;

    private ShowVoyagePlanInfo openVoyagePlan;
    private SendVoyage sendVoyage;

    private RouteManager routeManager;
    private Route route;

    private AisLayer aisLayer;
    private StrategicRouteHandler strategicRouteHandler;

    private ToggleAisTargetNames aisNames;

    private ToggleAisTargetName hideAisTargetName;

    private JMapFrame jMapFrame;
    private LayerToggleWindow layerTogglingWindow;

    // private NogoHandler nogoHandler;

    public MapMenu() {
        super();

        // general menu items
        newRoute = new GeneralNewRoute("Add new route");
        newRoute.addActionListener(this);

        // SART menu items
        sarTargetDetails = new SarTargetDetails("SART details");
        sarTargetDetails.addActionListener(this);

        // route general items
        setRouteExchangeRoute = new SendRouteFromRoute("Send Route");
        setRouteExchangeRoute.addActionListener(this);

        // route edit menu
        routeEditEndRoute = new RouteEditEndRoute("End route");
        routeEditEndRoute.addActionListener(this);

        // ais menu items
        sendRouteToShip = new SendRouteToShip("Send Route to vessel");
        sendRouteToShip.addActionListener(this);

        routeRequestMetoc.setEnabled(false);

        // Voyage menu
        openVoyagePlan = new ShowVoyagePlanInfo("Open Voyage Plans Details");
        openVoyagePlan.addActionListener(this);

        sendVoyage = new SendVoyage("Select and send Voyage");
        sendVoyage.addActionListener(this);
        // sendVoyage.setText("Select and send Voyage");

        this.voyageDelete = new VoyageDeleteMenuItem("Delete Voyage");
        this.voyageDelete.addActionListener(this);

        // voyage leg menu
        voyageHandlingLegInsertWaypoint = new VoyageHandlingLegInsertWaypoint("Insert waypoint here", EPDShore.getInstance()
                .getVoyageEventDispatcher());
        voyageHandlingLegInsertWaypoint.addActionListener(this);

        voyageHandlingWaypointDelete = new VoyageHandlingWaypointDelete("Delete waypoint");
        voyageHandlingWaypointDelete.addActionListener(this);

        voyageHandlingAppendWaypoint = new VoyageHandlingAppendWaypoint("Append waypoint");
        voyageHandlingAppendWaypoint.addActionListener(this);

        voyageProperties = new VoyageProperties("Show Voyage Plan");
        voyageProperties.addActionListener(this);

        voyageRenegotiate = new VoyageRenegotiate("Renegotigate Voyage");
        voyageRenegotiate.addActionListener(this);

        voyageShowTransaction = new VoyageShowTransaction("Show Transaction");
        voyageShowTransaction.addActionListener(this);

        voyageZoomToShip = new VoyageZoomToShip("Zoom to Ship");
        voyageZoomToShip.addActionListener(this);

        voyageHandlingOptimizeRoute = new VoyageHandlingOptimizeRoute("Optimize Voyage via. SSPA");
        voyageHandlingOptimizeRoute.addActionListener(this);

        voyageHideAll = new VoyageHideAll("Toggle Voyage Layer");
        voyageHideAll.addActionListener(this);

        setAisNames(new ToggleAisTargetNames());
        getAisNames().addActionListener(this);
        hideAisTargetName = new ToggleAisTargetName();
        hideAisTargetName.addActionListener(this);

        // Layer Toggling Window
        layerTogglingWindow = new LayerToggleWindow("Show Layer Menu");
        layerTogglingWindow.addActionListener(this);
    }

    /**
     * Adds the general menu to the right-click menu. Remember to always add this first, when creating specific menus.
     * 
     * @param alone
     */
    @Override
    public void generalMenu(boolean alone) {

        generateScaleMenu();

        hideIntendedRoutes.setIntendedRouteHandler(intendedRouteHandler);
        showIntendedRoutes.setIntendedRouteHandler(intendedRouteHandler);
        checkIntendedRouteItems(hideIntendedRoutes, showIntendedRoutes);

        newRoute.setToolBar(EPDShore.getInstance().getMainFrame().getToolbar());

        showPastTracks.setAisHandler(aisHandler);
        hidePastTracks.setAisHandler(aisHandler);

        // newRoute.setMouseDelegator(mouseDelegator);
        // newRoute.setMainFrame(mainFrame);

        if (alone) {
            removeAll();
            add(hideIntendedRoutes);
            add(showIntendedRoutes);
            add(newRoute);
            addSeparator();
            add(showPastTracks);
            add(hidePastTracks);
            addSeparator();
            add(getAisNames());
            addSeparator();
            add(scaleMenu);

            if (jMapFrame.getLayerTogglingPanel() != null) {
                add(layerTogglingWindow);
            }

            // voyageHideAll.setVoyageLayer(voyageLayer);
            // add(voyageHideAll);
            return;
        }

        addSeparator();
        add(hideIntendedRoutes);
        add(scaleMenu);

        if (jMapFrame.getLayerTogglingPanel() != null) {
            add(layerTogglingWindow);
            // addLayerToggle();
        }

        revalidate();
    }

    // private void addLayerToggle(){
    // // layerTogglingWindow.setPosition(position);
    //
    //
    // }

    /**
     * Builds ais target menu
     * 
     * @param vesselTargetGraphic
     */
    public void aisMenu(VesselTarget vesselTarget, VesselGraphicComponentSelector vesselTargetGraphic) {
        removeAll();

        sendRouteToShip.setMSSI(vesselTarget.getMmsi());
        sendRouteToShip.setSendRouteDialog(EPDShore.getInstance().getMainFrame().getSendRouteDialog());
        sendRouteToShip.setEnabled(EPDShore.getInstance().getRouteSuggestionHandler()
                .shipAvailableForRouteSuggestion(vesselTarget.getMmsi()));

        add(sendRouteToShip);

        hideAisTargetName.setVesselTargetGraphic(vesselTargetGraphic);
        hideAisTargetName.setIAisTargetListener(this.aisLayer);

        add(hideAisTargetName);

        // Toggle show intended route
        addIntendedRouteToggle(intendedRouteHandler.getIntendedRoute(vesselTarget.getMmsi()));

        // Toggle show past-track
        aisTogglePastTrack.setMobileTarget(vesselTarget);
        aisTogglePastTrack.setAisLayer(aisLayer);
        aisTogglePastTrack.setText((vesselTarget.getSettings().isShowPastTrack()) ? "Hide past-track" : "Show past-track");
        add(aisTogglePastTrack);

        // Clear past-track
        aisClearPastTrack.setMobileTarget(vesselTarget);
        aisClearPastTrack.setAisLayer(aisLayer);
        aisClearPastTrack.setText("Clear past-track");
        add(aisClearPastTrack);

        // Send chat message
        addSeparator();
        sendChatMessage.setVesselTarget(vesselTarget);
        sendChatMessage.checkEnabled();
        add(sendChatMessage);

        generalMenu(false);
        revalidate();
    }

    /**
     * SART menu option
     * 
     * @param aisLayer
     * @param sarTarget
     */
    public void sartMenu(AisLayer aisLayer, SarTarget sarTarget) {
        removeAll();

        sarTargetDetails.setSarTarget(sarTarget);
        sarTargetDetails.setMainFrame(EPDShore.getInstance().getMainFrame());
        sarTargetDetails.setPntHandler(null);

        add(sarTargetDetails);

        addSeparator();

        // Toggle show past-track
        aisTogglePastTrack.setMobileTarget(sarTarget);
        aisTogglePastTrack.setAisLayer(aisLayer);
        aisTogglePastTrack.setText((sarTarget.getSettings().isShowPastTrack()) ? "Hide past-track" : "Show past-track");
        add(aisTogglePastTrack);

        // Clear past-track
        aisClearPastTrack.setMobileTarget(sarTarget);
        aisClearPastTrack.setAisLayer(aisLayer);
        aisClearPastTrack.setText("Clear past-track");
        add(aisClearPastTrack);

        revalidate();
        generalMenu(false);
    }

    public void generalRouteMenu(int routeIndex) {

        routeManager = EPDShore.getInstance().getMainFrame().getRouteManagerDialog().getRouteManager();
        route = routeManager.getRoute(routeIndex);

        routeAppendWaypoint.setRouteManager(routeManager);
        routeAppendWaypoint.setRouteIndex(routeIndex);
        add(routeAppendWaypoint);

        addSeparator();

        setRouteExchangeRoute.setRoute(route);
        setRouteExchangeRoute.setSendRouteDialog(EPDShore.getInstance().getMainFrame().getSendRouteDialog());
        add(setRouteExchangeRoute);

        routeHide.setRouteManager(routeManager);
        routeHide.setRouteIndex(routeIndex);
        add(routeHide);

        routeDelete.setRouteManager(routeManager);
        routeDelete.setRouteIndex(routeIndex);
        add(routeDelete);

        routeCopy.setRouteManager(routeManager);
        routeCopy.setRouteIndex(routeIndex);
        add(routeCopy);

        routeReverse.setRouteManager(routeManager);
        routeReverse.setRouteIndex(routeIndex);
        add(routeReverse);

        routeRequestMetoc.setRouteManager(routeManager);
        routeRequestMetoc.setRouteIndex(routeIndex);
        add(routeRequestMetoc);

        if (routeManager.hasMetoc(route)) {
            routeShowMetocToggle.setEnabled(true);
        } else {
            routeShowMetocToggle.setEnabled(false);
        }

        if (route.getRouteMetocSettings().isShowRouteMetoc() && routeManager.hasMetoc(route)) {
            routeShowMetocToggle.setText("Hide METOC");
        } else {
            routeShowMetocToggle.setText("Show METOC");
        }

        routeShowMetocToggle.setRouteManager(routeManager);
        routeShowMetocToggle.setRouteIndex(routeIndex);
        add(routeShowMetocToggle);

        routeMetocProperties.setRouteManager(routeManager);
        routeMetocProperties.setRouteIndex(routeIndex);
        add(routeMetocProperties);

        routeProperties.setRouteManager(routeManager);
        routeProperties.setRouteIndex(routeIndex);
        routeProperties.setChartPanel(EPDShore.getInstance().getMainFrame().getActiveMapWindow().getChartPanel());
        add(routeProperties);

        revalidate();
        generalMenu(false);
    }

    /**
     * Creates the route leg menu
     * 
     * @param routeIndex
     *            the route index
     * @param routeLeg
     *            the route leg
     * @param point
     *            the mouse location
     */
    @Override
    public void routeLegMenu(int routeIndex, RouteLeg routeLeg, Point point) {
        routeManager = EPDShore.getInstance().getMainFrame().getRouteManagerDialog().getRouteManager();

        removeAll();

        routeLegInsertWaypoint.setEnabled(true);
        routeLegInsertWaypoint.setMapBean(mapBean);
        routeLegInsertWaypoint.setRouteManager(routeManager);
        routeLegInsertWaypoint.setRouteLeg(routeLeg);
        routeLegInsertWaypoint.setRouteIndex(routeIndex);
        routeLegInsertWaypoint.setPoint(point);

        add(routeLegInsertWaypoint);

        generalRouteMenu(routeIndex);
        // TODO: add leg specific items
        revalidate();
    }

    /**
     * Creates the route way point menu
     * 
     * @param routeIndex
     *            the route index
     * @param routeWaypointIndex
     *            the route way point index
     */
    @Override
    public void routeWaypointMenu(int routeIndex, int routeWaypointIndex) {
        routeManager = EPDShore.getInstance().getMainFrame().getRouteManagerDialog().getRouteManager();

        removeAll();

        routeWaypointDelete.setEnabled(true);

        routeWaypointDelete.setRouteWaypointIndex(routeWaypointIndex);
        routeWaypointDelete.setRouteIndex(routeIndex);
        routeWaypointDelete.setRouteManager(routeManager);
        add(routeWaypointDelete);

        generalRouteMenu(routeIndex);
        revalidate();
    }

    public void voyageGeneralMenu(long transactionID, long mmsi, Route route, MapBean mapBean) {
        removeAll();

        VesselTarget vesselTarget = aisHandler.getVesselTarget(mmsi);
        if (vesselTarget != null) {
            voyageZoomToShip.setEnabled(true);
            Position pos = vesselTarget.getPositionData().getPos();
            voyageZoomToShip.setMapBean(mapBean);
            voyageZoomToShip.setPosition(pos);
        } else {
            voyageZoomToShip.setEnabled(false);
        }

        if (strategicRouteHandler.getStrategicNegotiationData().containsKey(transactionID)) {
            voyageShowTransaction.setEnabled(true);
            voyageShowTransaction.setTransactionID(transactionID);
        } else {
            voyageShowTransaction.setEnabled(false);
        }

        voyageProperties.setEnabled(false);

        voyageRenegotiate.setTransactionid(transactionID);
        voyageRenegotiate.setAisHandler(aisHandler);
        voyageRenegotiate.setStrategicRouteHandler(strategicRouteHandler);

        voyageRenegotiate.setEnabled(EPDShore.getInstance().getStrategicRouteHandler()
                .shipAvailableForStrategicRouteTransaction(mmsi)
                && strategicRouteHandler.getStrategicNegotiationData().containsKey(transactionID));

        add(voyageZoomToShip);
        add(voyageShowTransaction);

        add(voyageProperties);
        add(voyageRenegotiate);

        // Set ID of voyage to be deleted when this menu item is invoked.
        this.voyageDelete.setVoyageId(transactionID);
        this.add(this.voyageDelete);
        // Zoom to Ship
        // Show transaction
        // Show voyage plan
        // Renegotiate Voyage

        revalidate();
    }

    public void voyageWaypointMenu(VoyageHandlingLayer voyageHandlingLayer, MapBean mapBean, Voyage voyage, boolean modified,
            JMapFrame parent, VoyagePlanInfoPanel voyagePlanInfoPanel, boolean waypoint, Route route, RouteLeg routeLeg,
            Point point, int routeWayPointIndex, boolean renegotiate) {

        removeAll();

        openVoyagePlan.setVoyagePlanInfoPanel(voyagePlanInfoPanel);

        sendVoyage.setRenegotiate(renegotiate);
        sendVoyage.setVoyage(voyage);
        sendVoyage.setModifiedRoute(modified);
        sendVoyage.setSendVoyageDialog(EPDShore.getInstance().getMainFrame().getSendVoyageDialog());
        sendVoyage.setParent(parent);

        add(openVoyagePlan);
        add(sendVoyage);

        addSeparator();

        if (waypoint) {

            // Delete waypoint
            voyageHandlingWaypointDelete.setEnabled(true);
            voyageHandlingWaypointDelete.setRouteWaypointIndex(routeWayPointIndex);
            voyageHandlingWaypointDelete.setRoute(route);
            voyageHandlingWaypointDelete.setVoyageHandlingLayer(voyageHandlingLayer);

            add(voyageHandlingWaypointDelete);

        } else {

            voyageHandlingLegInsertWaypoint.setMapBean(mapBean);
            // voyageHandlingLegInsertWaypoint
            // .setVoyageHandlingLayer(voyageHandlingLayer);
            voyageHandlingLegInsertWaypoint.setRoute(route);
            voyageHandlingLegInsertWaypoint.setRouteLeg(routeLeg);
            voyageHandlingLegInsertWaypoint.setPoint(point);

            add(voyageHandlingLegInsertWaypoint);

        }

        voyageHandlingAppendWaypoint.setVoyageHandlingLayer(voyageHandlingLayer);
        voyageHandlingAppendWaypoint.setRoute(route);
        add(voyageHandlingAppendWaypoint);
        // Right click, hide voyages and intended routes maybe?

        addSeparator();

        voyageHandlingOptimizeRoute.setVoyageHandlingLayer(voyageHandlingLayer);
        voyageHandlingOptimizeRoute.setAisHandler(aisHandler);
        voyageHandlingOptimizeRoute.setRoute(route);
        voyageHandlingOptimizeRoute.setMmsi(voyage.getMmsi());

        add(voyageHandlingOptimizeRoute);
        revalidate();
    }

    /**
     * Creates the route edit menu
     */
    @Override
    public void routeEditMenu() {
        removeAll();
        routeManager = EPDShore.getInstance().getMainFrame().getRouteManagerDialog().getRouteManager();

        routeEditEndRoute.setToolBar(EPDShore.getInstance().getMainFrame().getToolbar());

        add(routeEditEndRoute);

        generalMenu(false);
        revalidate();
    }

    // Allows MapMenu to be added to the MapHandler (eg. use the find and init)
    @Override
    public void findAndInit(Object obj) {
        super.findAndInit(obj);

        if (obj instanceof NewRouteContainerLayer) {
            // newRouteLayer = (NewRouteContainerLayer) obj;
        }
        if (obj instanceof AisLayer) {
            aisLayer = (AisLayer) obj;
        }
        if (obj instanceof StrategicRouteHandler) {
            strategicRouteHandler = (StrategicRouteHandler) obj;
        }
        if (obj instanceof JMapFrame) {
            jMapFrame = (JMapFrame) obj;
            layerTogglingWindow.setLayerToggling(jMapFrame.getLayerTogglingPanel());
        }
    }

    public ToggleAisTargetNames getAisNames() {
        return aisNames;
    }

    public void setAisNames(ToggleAisTargetNames aisNames) {
        this.aisNames = aisNames;
    }
}
