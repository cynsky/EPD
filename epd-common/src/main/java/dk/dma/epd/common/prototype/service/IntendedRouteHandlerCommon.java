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
package dk.dma.epd.common.prototype.service;

import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import net.maritimecloud.net.MaritimeCloudClient;
import net.maritimecloud.net.broadcast.BroadcastListener;
import net.maritimecloud.net.broadcast.BroadcastMessageHeader;

import org.joda.time.DateTime;

import com.bbn.openmap.MapBean;
import com.bbn.openmap.geo.Geo;
import com.bbn.openmap.geo.Intersection;
import com.bbn.openmap.proj.GreatCircle;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.proj.coords.LatLonPoint;

import dk.dma.enav.model.geometry.CoordinateSystem;
import dk.dma.enav.model.geometry.Position;
import dk.dma.epd.common.Heading;
import dk.dma.epd.common.prototype.EPD;
import dk.dma.epd.common.prototype.ais.AisHandlerCommon;
import dk.dma.epd.common.prototype.ais.VesselTarget;
import dk.dma.epd.common.prototype.enavcloud.intendedroute.IntendedRouteBroadcast;
import dk.dma.epd.common.prototype.model.intendedroute.FilteredIntendedRoute;
import dk.dma.epd.common.prototype.model.intendedroute.IntendedRouteFilterMessage;
import dk.dma.epd.common.prototype.model.route.ActiveRoute;
import dk.dma.epd.common.prototype.model.route.IntendedRoute;
import dk.dma.epd.common.prototype.model.route.Route;
import dk.dma.epd.common.prototype.model.route.RouteWaypoint;
import dk.dma.epd.common.prototype.notification.GeneralNotification;
import dk.dma.epd.common.prototype.notification.Notification.NotificationSeverity;
import dk.dma.epd.common.prototype.notification.NotificationAlert.AlertType;
import dk.dma.epd.common.prototype.notification.NotificationAlert;
import dk.dma.epd.common.prototype.sensor.pnt.PntTime;
import dk.dma.epd.common.util.Calculator;
import dk.dma.epd.common.util.Converter;

/**
 * Intended route service implementation.
 * <p>
 * Listens for intended route broadcasts, and updates the vessel target when one is received.
 */
public abstract class IntendedRouteHandlerCommon extends EnavServiceHandlerCommon {

    /**
     * Time an intended route is considered valid without update
     */
    public static final long ROUTE_TTL = 10 * 60 * 1000; // 10 min

    /**
     * In nautical miles - distance between two lines for it to be put in filter
     */
    public static final double FILTER_DISTANCE_EPSILON = 0.5;

    /**
     * In minutes - how close should the warning point be in time
     */
    public static final int FILTER_TIME_EPSILON = 120;

    public static final double NOTIFICATION_DISTANCE_EPSILON = 1; // Nautical miles
    public static final int NOTIFICATION_TIME_EPSILON = 30; // Minutes
    public static final double ALERT_DISTANCE_EPSILON = 0.5; // Nautical miles
    public static final int ALERT_TIME_EPSILON = 10; // Minutes

    protected ConcurrentHashMap<Long, IntendedRoute> intendedRoutes = new ConcurrentHashMap<>();
    protected ConcurrentHashMap<Long, FilteredIntendedRoute> filteredIntendedRoutes = new ConcurrentHashMap<>();

    protected List<IIntendedRouteListener> listeners = new CopyOnWriteArrayList<>();

    private List<Position> intersectPositions = new ArrayList<Position>();

    private AisHandlerCommon aisHandler;
    private MapBean mapBean;

    /**
     * Constructor
     */
    public IntendedRouteHandlerCommon() {
        super();

        // Checks and remove stale intended routes every minute
        getScheduler().scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                checkForInactiveRoutes();
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * Returns the intended route associated with the given MMSI
     * 
     * @param mmsi
     *            the MMSI of the intended route
     * @return the intended route or null if not present
     */
    public IntendedRoute getIntendedRoute(long mmsi) {
        return intendedRoutes.get(mmsi);
    }

    /**
     * Returns a copy of the current list of intended routes
     * 
     * @return a copy of the current list of intended routes
     */
    public List<IntendedRoute> fetchIntendedRoutes() {
        List<IntendedRoute> list = new ArrayList<>();
        list.addAll(intendedRoutes.values());
        return list;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cloudConnected(MaritimeCloudClient connection) {

        // Hook up as a broadcast listener
        connection.broadcastListen(IntendedRouteBroadcast.class, new BroadcastListener<IntendedRouteBroadcast>() {
            public void onMessage(BroadcastMessageHeader l, IntendedRouteBroadcast r) {

                getStatus().markCloudReception();
                int id = MaritimeCloudUtils.toMmsi(l.getId());
                updateIntendedRoute(id, r);
            }
        });
    }

    /**
     * Update intended route of vessel target
     * 
     * @param mmsi
     * @param r
     */
    private synchronized void updateIntendedRoute(long mmsi, IntendedRouteBroadcast r) {

        IntendedRoute intendedRoute = new IntendedRoute(r.getRoute());
        intendedRoute.setMmsi(mmsi);

        IntendedRoute oldIntendedRoute = intendedRoutes.get(mmsi);
        if (oldIntendedRoute != null) {
            intendedRoute.setVisible(oldIntendedRoute.isVisible());
        }

        // Check if this is a real intended route or one that signals a removal
        if (!intendedRoute.hasRoute()) {
            if (intendedRoutes.containsKey(mmsi)) {
                intendedRoutes.remove(mmsi);
                // fireIntendedRouteRemoved(intendedRoute);
            }
            if (filteredIntendedRoutes.containsKey(mmsi)) {
                filteredIntendedRoutes.remove(mmsi);
            }
            // return;
        } else {

            // The intended route is valid
            intendedRoutes.put(mmsi, intendedRoute);

            // Apply the filter to the route
            applyFilter(intendedRoute);

            // Sanity check
            if (aisHandler != null) {
                // Try to find exiting target
                VesselTarget vesselTarget = aisHandler.getVesselTarget(mmsi);
                if (vesselTarget != null) {
                    intendedRoute.update(vesselTarget.getPositionData());
                }
            }

        }

        // Fire event
        fireIntendedEvent(intendedRoute);

        System.out.println("Did the route get put into the filter? " + filteredIntendedRoutes.size());
    }

    /**
     * Remove stale intended routes.
     */
    private synchronized void checkForInactiveRoutes() {
        Date now = PntTime.getInstance().getDate();
        for (Iterator<Map.Entry<Long, IntendedRoute>> it = intendedRoutes.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Long, IntendedRoute> entry = it.next();
            if (now.getTime() - entry.getValue().getReceived().getTime() > ROUTE_TTL) {
                // Remove the intended route
                it.remove();
                fireIntendedEvent(entry.getValue());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void findAndInit(Object obj) {
        super.findAndInit(obj);

        if (obj instanceof AisHandlerCommon) {
            aisHandler = (AisHandlerCommon) obj;
        }
        if (obj instanceof MapBean) {
            mapBean = (MapBean) obj;
        }
    }

    /**
     * Hide all intended routes
     */
    public void hideAllIntendedRoutes() {
        for (IntendedRoute intendedRoute : intendedRoutes.values()) {
            intendedRoute.setVisible(false);
            fireIntendedEvent(intendedRoute);
        }
    }

    /**
     * Show all intended routes
     */
    public void showAllIntendedRoutes() {
        for (IntendedRoute intendedRoute : intendedRoutes.values()) {
            intendedRoute.setVisible(true);
            fireIntendedEvent(intendedRoute);
        }
    }

    /****************************************/
    /** Listener functions **/
    /****************************************/

    /**
     * Adds an intended route listener
     * 
     * @param listener
     *            the listener to add
     */
    public void addListener(IIntendedRouteListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes an intended route listener
     * 
     * @param listener
     *            the listener to remove
     */
    public void removeListener(IIntendedRouteListener listener) {
        listeners.remove(listener);
    }

    /**
     * Called when an intended route event has been recieved
     * 
     * @param intendedRoute
     *            the intended route
     */
    public void fireIntendedEvent(IntendedRoute intendedRoute) {
        for (IIntendedRouteListener listener : listeners) {
            listener.intendedRouteEvent(intendedRoute);
        }
    }

    /****************************************/
    /** Intended route filtering **/
    /****************************************/

    /**
     * Update all filters
     */
    protected abstract void updateFilter();

    /**
     * Update filter with new intended route
     * 
     * @param route
     */
    protected abstract void applyFilter(IntendedRoute route);

    /**
     * Check if notifications should be generated based on a re-computed set of filtered intended routes
     * 
     * @param oldFilteredRoutes
     *            the old set of filtered routes
     * @param newFilteredRoutes
     *            the new set of filtered routes
     */
    protected void checkGenerateNotifications(Map<Long, FilteredIntendedRoute> oldFilteredRoutes,
            Map<Long, FilteredIntendedRoute> newFilteredRoutes) {
        for (FilteredIntendedRoute filteredIntendedRoute : newFilteredRoutes.values()) {
            checkGenerateNotifications(oldFilteredRoutes, filteredIntendedRoute);
        }
    }

    /**
     * Check if a notification should be generated based on a new filtered intended route
     * 
     * @param oldFilteredRoutes
     *            the old set of filtered routes
     * @param newFilteredRoute
     *            the new filtered route
     */
    protected void checkGenerateNotifications(Map<Long, FilteredIntendedRoute> oldFilteredRoutes,
            FilteredIntendedRoute newFilteredRoute) {
        Long mmsi = newFilteredRoute.getIntendedRoute().getMmsi();
        FilteredIntendedRoute oldFilteredRoute = oldFilteredRoutes.get(mmsi);

        // NB: For now, we add a notification when a new filtered intended route surfaces
        // and it is within a certain amount of time and distance.
        // In the future add a more fine-grained comparison
        boolean sendNotification;
        if (oldFilteredRoute == null) {
            sendNotification = true;
        } else {
            newFilteredRoute.setGeneratedNotification(oldFilteredRoute.hasGeneratedNotification());
            sendNotification = !newFilteredRoute.hasGeneratedNotification()
                    && newFilteredRoute.isWithinRange(NOTIFICATION_DISTANCE_EPSILON, NOTIFICATION_TIME_EPSILON);
        }

        if (sendNotification) {
            newFilteredRoute.setGeneratedNotification(true);
            GeneralNotification notification = new GeneralNotification(newFilteredRoute, "IntendedRouteNotificaiton_" + mmsi + "_"
                    + System.currentTimeMillis());
            notification.setTitle("Potential collision detected");
            StringBuilder desc = new StringBuilder();
            for (IntendedRouteFilterMessage msg : newFilteredRoute.getFilterMessages()) {
                if (msg.isWithinRange(NOTIFICATION_DISTANCE_EPSILON, NOTIFICATION_TIME_EPSILON)) {
                    desc.append(msg.getMessage()).append("\n");
                }
            }
            notification.setDescription(desc.toString());
            if (newFilteredRoute.isWithinRange(ALERT_DISTANCE_EPSILON, ALERT_TIME_EPSILON)) {
                notification.setSeverity(NotificationSeverity.ALERT);
                notification.addAlerts(new NotificationAlert(AlertType.POPUP, AlertType.BEEP));
            } else {
                notification.setSeverity(NotificationSeverity.WARNING);
                notification.addAlerts(new NotificationAlert(AlertType.POPUP));
            }
            notification.setLocation(newFilteredRoute.getFilterMessages().get(0).getPosition1());
            EPD.getInstance().getNotificationCenter().addNotification(notification);
        }
    }

    protected FilteredIntendedRoute findTCPA(Route route1, Route route2) {

        intersectPositions.clear();
        // filteredIntendedRoutes.clear();

        // Focus on time
        FilteredIntendedRoute filteredIntendedRoute = new FilteredIntendedRoute();

        // We need to check if there's a previous waypoint, ie. we are either starting navigating or are between two waypoints
        // int route1StartWp = route1.getActiveWpIndex();
        // int route2StartWp = route2.getActiveWpIndex();

        int route1StartWp = 0;
        int route2StartWp = 0;

        int route1ActiveWp = 0;

        if (route1 instanceof IntendedRoute) {
            route1StartWp = ((IntendedRoute) route1).getActiveWpIndex();
            route1ActiveWp = ((IntendedRoute) route1).getActiveWpIndex();
        }

        if (route2 instanceof IntendedRoute) {
            route1StartWp = ((IntendedRoute) route2).getActiveWpIndex();

        }
        
        
        
        if (route1 instanceof ActiveRoute) {
            route1StartWp = ((ActiveRoute) route1).getActiveWaypointIndex();
            route1ActiveWp = ((ActiveRoute) route1).getActiveWaypointIndex();
        }

        if (route2 instanceof ActiveRoute) {
            route1StartWp = ((ActiveRoute) route2).getActiveWaypointIndex();

        }
        
        

        if (route1StartWp > 0) {
            route1StartWp = route1StartWp - 1;
        }

        if (route2StartWp > 0) {
            route2StartWp = route2StartWp - 1;
        }

        // Should the comparison even be made

        DateTime route1Start = new DateTime(route1.getEtas().get(route1StartWp));
        DateTime route1End = new DateTime(route1.getEtas().get(route1.getEtas().size() - 1));

        DateTime route2Start = new DateTime(route2.getEtas().get(route2StartWp));
        DateTime route2End = new DateTime(route2.getEtas().get(route2.getEtas().size() - 1));

        // The route dates does not overlap, return immediately
        if (route2Start.isAfter(route1End) || route1Start.isAfter(route2End)) {
            System.out.println("The route dates does not overlap, return immediately");

            System.out.println("Route 1 Start: " + route1Start + " and end: " + route1End);
            System.out.println("Route 2 Start: " + route2Start + " and end: " + route2End);

            return filteredIntendedRoute;
        }

        // Find first point in time that they have in common
        if (route1Start.isBefore(route2Start)) {
            // Route1 starts first
            // Thus route2 start must be first common timeslot

            // Find location for both at route2start time
            // route2Start

            // Location for route2 is given from
            Position route2StartPos = route2.getWaypoints().get(route2StartWp).getPos();

            DateTime route1WpStart = null;
            DateTime route1WpEnd;

            boolean foundSegment = false;

            int i;
            for (i = route1ActiveWp; i < route1.getWaypoints().size(); i++) {
                if (i > 0) {
                    route1WpStart = new DateTime(route1.getEtas().get(i - 1));
                    route1WpEnd = new DateTime(route1.getEtas().get(i));

                    if (route1WpStart.isBefore(route2Start) && route1WpEnd.isAfter(route2Start)) {
                        // We have the found the segment we need to start from

                        System.out.println("Found segment");
                        foundSegment = true;
                        break;
                    }
                }
            }

            if (foundSegment) {

                // Now find position at time of route2Start

                System.out.println("Route 1 WP Start is at " + route1.getEtas().get(i - 1));
                System.out.println("Route 2 Start is at " + route2.getEtas().get(route2StartWp));

                // How long will we have travelled along our route (route 1)
                long timeTravelledSeconds = (route2Start.getMillis() - route1WpStart.getMillis()) / 1000;

                double speedInLeg = route1.getWaypoints().get(i - 1).getOutLeg().getSpeed();

                System.out.println("We have travelled for how many minutes " + timeTravelledSeconds / 60 + " at speed "
                        + speedInLeg);

                double distanceTravelled = Calculator.distanceAfterTimeMph(speedInLeg, timeTravelledSeconds);

                System.out.println("We have travelled " + distanceTravelled + " nautical miles in direction: "
                        + route1.getWaypoints().get(i - 1).calcBrg());

                Position position = Calculator.findPosition(route1.getWaypoints().get(i - 1).getPos(),
                        route1.getWaypoints().get(i - 1).calcBrg(), Converter.nmToMeters(distanceTravelled));

                System.out.println("Difference start pos" + route1.getWaypoints().get(i - 1).getPos() + " vs " + position);

                System.out.println("The distance between points is "
                        + Converter.metersToNm(position.distanceTo(route2StartPos, CoordinateSystem.CARTESIAN)));

                // intersectPositions.add(position);
                //
                // intersectPositions.add(route2StartPos);

                // In nautical miles
                // double route1SegmentTraversed = distanceTravelled;
                // double route2SegmentTraversed = 0;

                // Okay so we are in position and in route2StartPos
                // We must start traversing the route now, assume straight lines, for each traversing check the distance between
                // points

                // Adaptive traversing, start with time slots of 10 minutes, if distance between points is smaller than x
                // Or if distance is simply decreasing, reduce time traversing.
                // If the distance becomes below the threshold locate point where it starts

                // Ensure that we do not traverse beyond the length of the route

                // We need to switch segments at some point - wait with that

                Position route1CurrentPosition = position;
                Position route2CurrentPosition = route2StartPos;

                int route1CurrentWaypoint = i - 1;
                int route2CurrentWaypoint = route2StartWp;

                double route1SegmentSpeed = route1.getWaypoints().get(route1CurrentWaypoint).getOutLeg().getSpeed();
                double route2SegmentSpeed = route2.getWaypoints().get(route2CurrentWaypoint).getOutLeg().getSpeed();

                double route1Bearing = route1.getWaypoints().get(route1CurrentWaypoint).calcBrg();
                double route2Bearing = route2.getWaypoints().get(route2CurrentWaypoint).calcBrg();

                DateTime traverseTime = route2Start;

                DateTime route1SegmentEnd = new DateTime(route1.getEtas().get(route1CurrentWaypoint + 1));
                DateTime route2SegmentEnd = new DateTime(route2.getEtas().get(route2CurrentWaypoint + 1));

                while (true) {

                    double currentDistance = Converter.metersToNm(route1CurrentPosition.distanceTo(route2CurrentPosition,
                            CoordinateSystem.CARTESIAN));

                    if (currentDistance < FILTER_DISTANCE_EPSILON) {
                        IntendedRouteFilterMessage filterMessage = new IntendedRouteFilterMessage(route1CurrentPosition,
                                route2CurrentPosition, "Warning stuff", 0, 0);

                        filterMessage.setTime1(traverseTime);
                        filterMessage.setTime2(traverseTime);

                        filteredIntendedRoute.getFilterMessages().add(filterMessage);
                        System.out.println("Adding warning");
                    } else {
                        System.out.println("Found distance of " + currentDistance + " at " + traverseTime);
                    }

                    // We start in this position

                    // route1CurrentPosition
                    // route2CurrentPosition
                    // At this time
                    // route2Start

                    // System.out.println("We start at time " + traverseTime);
                    traverseTime = traverseTime.plusMinutes(1);

                    // System.out.println("And traverse until " + traverseTime);

                    double route1DistanceToTravel = Calculator.distanceAfterTimeMph(route1SegmentSpeed, 60);

                    // System.out.println("We travel " + route1DistanceToTravel + " miles for route 1");

                    double route2DistanceToTravel = Calculator.distanceAfterTimeMph(route2SegmentSpeed, 60);

                    // Position route1NextPosition = traverseLine(route1CurrentPosition, route1Bearing, distanceTravelledRoute1);
                    // Position route2NextPosition = traverseLine(route2CurrentPosition, route2Bearing, distanceTravelledRoute2);

                    if (route1.getWaypoints().get(route1CurrentWaypoint).getHeading() == Heading.RL) {
                        route1CurrentPosition = traverseLine(route1CurrentPosition, route1Bearing, route1DistanceToTravel);
                    }

                    if (route2.getWaypoints().get(route2CurrentWaypoint).getHeading() == Heading.RL) {
                        route2CurrentPosition = traverseLine(route2CurrentPosition, route2Bearing, route2DistanceToTravel);
                    }

                    if (route1.getWaypoints().get(route1CurrentWaypoint).getHeading() == Heading.GC) {
                        route1CurrentPosition = traverseLine(route1CurrentPosition,
                                route1.getWaypoints().get(route1CurrentWaypoint + 1).getPos(), route1DistanceToTravel);
                    }

                    if (route2.getWaypoints().get(route2CurrentWaypoint).getHeading() == Heading.GC) {
                        route2CurrentPosition = traverseLine(route2CurrentPosition,
                                route2.getWaypoints().get(route2CurrentWaypoint + 1).getPos(), route2DistanceToTravel);
                    }

                    // route1SegmentTraversed = route1SegmentTraversed + route1DistanceToTravel;
                    // route2SegmentTraversed = route2SegmentTraversed + route2DistanceToTravel;

                    // if (route1SegmentTraversed > Converter.milesToNM(route1.getWaypoints().get(route1CurrentWaypoint).calcRng()))
                    // {
                    if (traverseTime.isAfter(route1SegmentEnd)) {

                        // System.out.println("We have traversed " + route1SegmentTraversed + " nautical miles");
                        System.out.println("We are at waypoint id  " + route1CurrentWaypoint + " and the route has a total of "
                                + route1.getWaypoints().size() + " waypoints");
                        // We are done with current leg, is there a next one?

                        // No more waypoints - terminate zero indexing and last waypoint does not have an out leg thus -2
                        if (route1CurrentWaypoint == route1.getWaypoints().size() - 2) {
                            System.out.println("We are breaking - route 1 is done");
                            break;
                        } else {
                            System.out.println("SWITCHING LEG FOR ROUTE 1, current bearing is ");
                            // Switch to next leg
                            route1CurrentWaypoint++;

                            System.out.println("We are now at waypoint " + route1CurrentWaypoint);

                            route1CurrentPosition = route1.getWaypoints().get(route1CurrentWaypoint).getPos();
                            route1SegmentSpeed = route1.getWaypoints().get(route1CurrentWaypoint).getOutLeg().getSpeed();
                            route1Bearing = route1.getWaypoints().get(route1CurrentWaypoint).calcBrg();
                            route1SegmentEnd = new DateTime(route1.getEtas().get(route1CurrentWaypoint + 1));

                            // Skip to next WP start traverse
                            // traverseTime = new DateTime(route1.getEtas().get(route1CurrentWaypoint));
                        }
                    }

                    // if (route2SegmentTraversed > Converter.milesToNM(route2.getWaypoints().get(route2CurrentWaypoint).calcRng()))
                    // {
                    if (traverseTime.isAfter(route2SegmentEnd)) {

                        // System.out.println("ROUTE 2: We have traversed " + route2SegmentTraversed + " nautical miles out of "
                        // + Converter.milesToNM(route2.getWaypoints().get(route2CurrentWaypoint).calcRng()));
                        System.out.println("We are at waypoint id  " + route2CurrentWaypoint + " and the route has a total of "
                                + route2.getWaypoints().size() + " waypoints");

                        // No more waypoints - terminate
                        if (route2CurrentWaypoint == route2.getWaypoints().size() - 2) {
                            System.out.println("We are breaking - route 2 is done");
                            break;
                        } else {
                            System.out.println("SWITCHING LEG FOR ROUTE 1");

                            // Switch to next leg
                            route2CurrentWaypoint++;

                            route2CurrentPosition = route2.getWaypoints().get(route2CurrentWaypoint).getPos();
                            route2SegmentSpeed = route2.getWaypoints().get(route2CurrentWaypoint).getOutLeg().getSpeed();
                            route2Bearing = route2.getWaypoints().get(route2CurrentWaypoint).calcBrg();
                            route2SegmentEnd = new DateTime(route2.getEtas().get(route2CurrentWaypoint + 1));

                            // Skip to next WP start traverse
                            // traverseTime = new DateTime(route2.getEtas().get(route2CurrentWaypoint));
                        }

                    }

                }
            } else {
                System.out.println("No segment was found - not sure how we reached this point...");
            }

        } else {
            // Route2 starts first
            // Thus route1 start must be first common timeslot

        }

        // findPosition(Position startingLocation, Position endLocation, double distanceTravelled){

        return filteredIntendedRoute;
    }

    /**
     * Rhumb line traversing
     * 
     * @param startPosition
     * @param bearing
     * @param distanceTravelled
     * @return
     */
    private Position traverseLine(Position startPosition, double bearing, double distanceTravelled) {

        // How long will we have travelled along our route (route 1)
        // long timeTravelledSeconds = minutes * 60;

        // double distanceTravelled = Calculator.distanceAfterTimeMph(speed, timeTravelledSeconds);

        Position position = Calculator.findPosition(startPosition, bearing, Converter.nmToMeters(distanceTravelled));

        return position;
    }

    /**
     * Great Circle Traversing
     * 
     * @param startPosition
     * @param bearing
     * @param distanceTravelled
     * @return
     */
    private Position traverseLine(Position startPosition, Position endPosition, double distanceTravelled) {

        // How long will we have travelled along our route (route 1)
        // long timeTravelledSeconds = minutes * 60;

        // double distanceTravelled = Calculator.distanceAfterTimeMph(speed, timeTravelledSeconds);

        Position position = Calculator.findPosition(startPosition, endPosition, Converter.nmToMeters(distanceTravelled));

        return position;
    }

    /**
     * Apply Filter on the two routes
     * 
     * @param route1
     * @param route2
     */
    protected FilteredIntendedRoute compareRoutes(Route route1, IntendedRoute route2) {

        intersectPositions.clear();

        System.out.println("Comparing routes");

        // The returned FilteredIntendedRoute is connected to route2
        FilteredIntendedRoute filteredIntendedRoute = new FilteredIntendedRoute();

        // First route (active route in ship)
        LinkedList<RouteWaypoint> route1Waypoints = route1.getWaypoints();

        // Second route list
        LinkedList<RouteWaypoint> route2Waypoints = route2.getWaypoints();

        Position previousPositionActiveRoute = route1Waypoints.get(0).getPos();
        for (int i = 1; i < route1Waypoints.size(); i++) {

            Position route1Waypoint1 = previousPositionActiveRoute;
            Position route1Waypoint2 = route1Waypoints.get(i).getPos();

            Position previousPositionIntendedRoute = route2Waypoints.get(0).getPos();
            for (int j = 1; j < route2Waypoints.size(); j++) {

                // Line segment
                Position route2Waypoint1 = previousPositionIntendedRoute;
                Position route2Waypoint2 = route2Waypoints.get(j).getPos();

                // This is where we apply the filters
                IntendedRouteFilterMessage intersectionResultMessage = intersectionFilter(route1, route2, i - 1, j - 1,
                        route1Waypoint1, route1Waypoint2, route2Waypoint1, route2Waypoint2);

                if (intersectionResultMessage != null) {
                    filteredIntendedRoute.getFilterMessages().add(intersectionResultMessage);
                } else {

                    // Region filter - do not apply if we have an intersection of line segments/
                    IntendedRouteFilterMessage regionResultMessage = proxmityFilter(route1, route2, i - 1, j - 1, route1Waypoint1,
                            route1Waypoint2, route2Waypoint1, route2Waypoint2, FILTER_DISTANCE_EPSILON);

                    if (regionResultMessage != null) {
                        filteredIntendedRoute.getFilterMessages().add(regionResultMessage);
                    }
                }

                // Add more filters

                previousPositionIntendedRoute = route2Waypoint2;
            }

            previousPositionActiveRoute = route1Waypoint2;

            // it.remove(); // avoids a ConcurrentModificationException
        }

        return filteredIntendedRoute;

    }

    private Position intersection(Position A1, Position A2, Position B1, Position B2) {
        Geo intersectionPoint = null;

        Geo a1 = new Geo(A1.getLatitude(), A1.getLongitude());
        Geo a2 = new Geo(A2.getLatitude(), A2.getLongitude());

        Geo b1 = new Geo(B1.getLatitude(), B1.getLongitude());
        Geo b2 = new Geo(B2.getLatitude(), B2.getLongitude());

        intersectionPoint = Intersection.segmentsIntersect(a1, a2, b1, b2);

        if (intersectionPoint != null) {
            // System.out.println("We have interesection at " + intersectionPoint);
            return Position.create(intersectionPoint.getLatitude(), intersectionPoint.getLongitude());
        } else {
            return null;

        }

    }

    public ConcurrentHashMap<Long, IntendedRoute> getIntendedRoutes() {
        return intendedRoutes;
    }

    public ConcurrentHashMap<Long, FilteredIntendedRoute> getFilteredIntendedRoutes() {
        return filteredIntendedRoutes;
    }

    private Point2D nearestPointOnLine(double ax, double ay, double bx, double by, double px, double py, boolean clampToSegment) {
        Double dest = new Point2D.Double();

        double apx = px - ax;
        double apy = py - ay;
        double abx = bx - ax;
        double aby = by - ay;

        double ab2 = abx * abx + aby * aby;
        double ap_ab = apx * abx + apy * aby;
        double t = ap_ab / ab2;
        if (clampToSegment) {
            if (t < 0) {
                t = 0;
            } else if (t > 1) {
                t = 1;
            }
        }
        dest.setLocation(ax + abx * t, ay + aby * t);
        return dest;
    }

    private IntendedRouteFilterMessage proximityFilterRhumbLine(Route route1, Route route2, int i, int j, Position A, Position B,
            Position C, Position D, double epsilon) {

        // List<IntendedRouteFilterMessage> messageList = new ArrayList<>();

        Projection projection = mapBean.getProjection();

        // projection = new LambertConformal((LatLonPoint) projection.getCenter(), projection.getScale(), projection.getWidth(),
        // projection.getHeight(), 1, 1, 1, 1, 1, 1, Ellipsoid.WGS_84);

        // projection = new LambertConformalLoader().create(new Properties());

        Point2D pointA = projection.forward(A.getLatitude(), A.getLongitude());
        Point2D pointB = projection.forward(B.getLatitude(), B.getLongitude());
        Point2D pointC = projection.forward(C.getLatitude(), C.getLongitude());
        Point2D pointD = projection.forward(D.getLatitude(), D.getLongitude());

        // Calculations when looking at route going from A to B with external point C
        Point2D first = nearestPointOnLine(pointA.getX(), pointA.getY(), pointB.getX(), pointB.getY(), pointC.getX(),
                pointC.getY(), true);
        LatLonPoint firstProjected = projection.inverse(first);
        Position posWP1Segment1 = Position.create(firstProjected.getLatitude(), firstProjected.getLongitude());
        double distanceWP1Segment1 = C.distanceTo(posWP1Segment1, CoordinateSystem.CARTESIAN);

        // Calculations when looking at route going from A to B with external point D
        Point2D second = nearestPointOnLine(pointA.getX(), pointA.getY(), pointB.getX(), pointB.getY(), pointD.getX(),
                pointD.getY(), true);
        LatLonPoint secondProjected = projection.inverse(second);
        Position posWP2Segment1 = Position.create(secondProjected.getLatitude(), secondProjected.getLongitude());
        double distanceWP2Segment1 = D.distanceTo(posWP2Segment1, CoordinateSystem.CARTESIAN);

        // Calculations when looking at route going from C to D with external point A
        Point2D third = nearestPointOnLine(pointC.getX(), pointC.getY(), pointD.getX(), pointD.getY(), pointA.getX(),
                pointA.getY(), true);
        LatLonPoint thirdProjected = projection.inverse(third);
        Position posWP1Segment2 = Position.create(thirdProjected.getLatitude(), thirdProjected.getLongitude());
        double distanceWP1Segment2 = A.distanceTo(posWP1Segment2, CoordinateSystem.CARTESIAN);

        // Calculations when looking at route going from C to D with external point B
        Point2D fourth = nearestPointOnLine(pointC.getX(), pointC.getY(), pointD.getX(), pointD.getY(), pointB.getX(),
                pointB.getY(), true);
        LatLonPoint fourthProjected = projection.inverse(fourth);
        Position posWP2Segment2 = Position.create(fourthProjected.getLatitude(), fourthProjected.getLongitude());
        double distanceWP2Segment2 = B.distanceTo(posWP2Segment2, CoordinateSystem.CARTESIAN);

        //
        // intersectPositions.add(Position.create(firstProjected.getLatitude(), firstProjected.getLongitude()));
        // intersectPositions.add(Position.create(secondProjected.getLatitude(), secondProjected.getLongitude()));
        // intersectPositions.add(Position.create(thirdProjected.getLatitude(), thirdProjected.getLongitude()));
        // intersectPositions.add(Position.create(fouthProjected.getLatitude(), fouthProjected.getLongitude()));

        // System.out.println("distanceWP1Segment1 is " + distanceWP1Segment1);
        // System.out.println("distanceWP2Segment1 is " + distanceWP2Segment1);
        // System.out.println("distanceWP1Segment2 is " + distanceWP1Segment2);
        // System.out.println("distanceWP2Segment2 is " + distanceWP2Segment2);

        double shorestDistanceSegment1 = distanceWP1Segment1;
        double shorestDistanceSegment2 = distanceWP1Segment2;

        Position shortestDistanceSegment1Position = posWP1Segment1;
        Position shortestDistanceSegment2Position = posWP1Segment2;

        Position point1 = C;

        Position point2 = A;

        if (distanceWP1Segment1 > distanceWP2Segment1) {
            shorestDistanceSegment1 = distanceWP2Segment1;
            shortestDistanceSegment1Position = posWP2Segment1;
            point1 = D;
            System.out.println("First");
        }

        if (distanceWP1Segment2 > distanceWP2Segment2) {
            shorestDistanceSegment2 = distanceWP2Segment2;
            shortestDistanceSegment2Position = posWP2Segment2;
            point2 = B;
            System.out.println("Second");
        }

        double shortestDistance = shorestDistanceSegment1;
        Position shortestDistancePosition = shortestDistanceSegment1Position;
        Position finalPoint = point1;

        if (shorestDistanceSegment1 > shorestDistanceSegment2) {
            shortestDistance = shorestDistanceSegment2;
            System.out.println("Third");
            shortestDistancePosition = shortestDistanceSegment2Position;
            finalPoint = point2;
        }

        System.out.println("The shorest distance is " + Converter.metersToNm(shortestDistance));

        // intersectPositions.add(shortestDistancePosition);

        if (Converter.metersToNm(shortestDistance) <= epsilon) {

            System.out.println("Checking time");

            IntendedRouteFilterMessage message = new IntendedRouteFilterMessage(shortestDistancePosition, finalPoint,
                    "Route Segments proximity warning", j - 1, j);

            if (checkDateInterval(shortestDistancePosition, finalPoint, route1, route2, i, j, message)) {

                System.out.println("Adding shortest distance is " + Converter.metersToNm(shortestDistance) + " at "
                        + shortestDistanceSegment2Position);

                // intersectPositions.add(shortestDistanceSegment2Position);

                return message;
            }

        }
        return null;

    }

    private double determineSegmentSize(double distance) {

        double lineSegmentSize;
        int iterations = 512;

        // If the segment is larger than 300 nautical miles
        if (distance / 1000 > 555.6) {
            lineSegmentSize = distance / iterations;
            System.out.println("Split into iterations " + distance);
        } else {
            // Line segment of 1 nautical mile
            lineSegmentSize = 1852;
            System.out.println("Set size");
        }

        return lineSegmentSize;
    }

    private IntendedRouteFilterMessage proximityFilterGreatCircle(Route route1, Route route2, int i, int j, Position A, Position B,
            Position C, Position D, double epsilon) {

        List<IntendedRouteFilterMessage> proximityFilterMessages = new ArrayList<IntendedRouteFilterMessage>();

        // int iterations = 512;

        // Get x points from GC between A and B
        double distance = A.distanceTo(B, CoordinateSystem.GEODETIC);

        double lineSegmentSize = determineSegmentSize(distance);
        double segmentAccumulated = 0;

        List<Position> segment1Positions = new ArrayList<Position>();

        long currentTime = System.currentTimeMillis();
        while (segmentAccumulated <= distance) {

            LatLonPoint result = GreatCircle.pointAtDistanceBetweenPoints(Math.toRadians(A.getLatitude()),
                    Math.toRadians(A.getLongitude()), Math.toRadians(B.getLatitude()), Math.toRadians(B.getLongitude()),
                    segmentAccumulated / 6371000, 256);

            if (result != null) {
                segment1Positions.add(Position.create(result.getLatitude(), result.getLongitude()));

                // intersectPositions.add(Position.create(result.getLatitude(), result.getLongitude()));

            }

            segmentAccumulated = segmentAccumulated + lineSegmentSize;

        }
        // Add the last waypoint
        segment1Positions.add(B);
        // intersectPositions.add(B);

        System.out.println("Done with segment1, Elapsed miliseconds: " + (System.currentTimeMillis() - currentTime));

        // Get x points from GC between C and D

        distance = C.distanceTo(D, CoordinateSystem.GEODETIC);
        lineSegmentSize = determineSegmentSize(distance);
        segmentAccumulated = 0;

        List<Position> segment2Positions = new ArrayList<Position>();

        currentTime = System.currentTimeMillis();
        while (segmentAccumulated <= distance) {

            LatLonPoint result = GreatCircle.pointAtDistanceBetweenPoints(Math.toRadians(C.getLatitude()),
                    Math.toRadians(C.getLongitude()), Math.toRadians(D.getLatitude()), Math.toRadians(D.getLongitude()),
                    segmentAccumulated / 6371000, 256);

            if (result != null) {
                segment2Positions.add(Position.create(result.getLatitude(), result.getLongitude()));

                // intersectPositions.add(Position.create(result.getLatitude(), result.getLongitude()));

            }

            segmentAccumulated = segmentAccumulated + lineSegmentSize;

        }
        // Add the last waypoint
        segment2Positions.add(D);
        // intersectPositions.add(D);

        System.out.println("Done with segment2, Elapsed miliseconds: " + (System.currentTimeMillis() - currentTime));

        System.out.println("Beginning point comparisons");
        currentTime = System.currentTimeMillis();
        for (int k = 0; k < segment1Positions.size(); k++) {

            for (int k2 = 0; k2 < segment2Positions.size(); k2++) {

                if (Converter
                        .metersToNm(segment1Positions.get(k).distanceTo(segment2Positions.get(k2), CoordinateSystem.CARTESIAN)) <= epsilon) {

                    System.out.println("Checking time");

                    IntendedRouteFilterMessage message = new IntendedRouteFilterMessage(segment1Positions.get(k),
                            segment2Positions.get(k2), "Route Segments proximity warning", j - 1, j);

                    if (checkDateInterval(segment1Positions.get(k), segment2Positions.get(k2), route1, route2, i, j, message)) {

                        proximityFilterMessages.add(message);

                    }

                    System.out.println("Problem area - check date time");
                }
            }
        }

        // Compare distances for all
        System.out.println("Done with comparisons, Elapsed miliseconds: " + (System.currentTimeMillis() - currentTime));

        double smallestDistance = 999999999;
        int bestMatch = 0;

        // Find the best match
        for (int k = 0; k < proximityFilterMessages.size(); k++) {

            double currentDistance = proximityFilterMessages.get(i).getPosition1()
                    .distanceTo(proximityFilterMessages.get(i).getPosition2(), CoordinateSystem.CARTESIAN);

            if (smallestDistance > currentDistance) {
                smallestDistance = currentDistance;
                bestMatch = i;
            }

        }

        return proximityFilterMessages.get(bestMatch);
    }

    private IntendedRouteFilterMessage proximityFilterMix(Route route1, Route route2, int i, int j, Position A, Position B,
            Position C, Position D, double epsilon) {

        // List<IntendedRouteFilterMessage> messageList = new ArrayList<IntendedRouteFilterMessage>();

        // We need to determine which is RL and which is GC

        // The returned area needs to be for route2

        // route1 is the GC
        if (route1.getWaypoints().get(i).getOutLeg().getHeading() == Heading.GC) {

            // Sample route1
            // Get x points from GC between A and B
            double distance = A.distanceTo(B, CoordinateSystem.GEODETIC);

            double lineSegmentSize = determineSegmentSize(distance);
            double segmentAccumulated = 0;

            List<Position> segment1Positions = new ArrayList<Position>();

            while (segmentAccumulated <= distance) {

                LatLonPoint result = GreatCircle.pointAtDistanceBetweenPoints(Math.toRadians(A.getLatitude()),
                        Math.toRadians(A.getLongitude()), Math.toRadians(B.getLatitude()), Math.toRadians(B.getLongitude()),
                        segmentAccumulated / 6371000, 256);

                if (result != null) {
                    segment1Positions.add(Position.create(result.getLatitude(), result.getLongitude()));

                    // intersectPositions.add(Position.create(result.getLatitude(), result.getLongitude()));

                }

                segmentAccumulated = segmentAccumulated + lineSegmentSize;

            }
            // Add the last waypoint
            segment1Positions.add(B);
            // intersectPositions.add(B);

            // Project all points into 2d space and compare against the RL line
            // The returned point will be on route2 line

            Projection projection = mapBean.getProjection();

            Point2D pointC = projection.forward(C.getLatitude(), C.getLongitude());
            Point2D pointD = projection.forward(D.getLatitude(), D.getLongitude());

            // External points are all those created from segment1Positions list

            for (int k = 0; k < segment1Positions.size(); k++) {
                Point2D GCPoint = projection.forward(segment1Positions.get(k).getLatitude(), segment1Positions.get(k)
                        .getLongitude());
                // Calculations when looking at route going from C to D with external segment1Positions
                Point2D third = nearestPointOnLine(pointC.getX(), pointC.getY(), pointD.getX(), pointD.getY(), GCPoint.getX(),
                        GCPoint.getY(), true);
                LatLonPoint thirdProjected = projection.inverse(third);
                Position route1SegmentExternalPoint = Position.create(thirdProjected.getLatitude(), thirdProjected.getLongitude());

                double distanceRoute1SegmentExternal = segment1Positions.get(k).distanceTo(route1SegmentExternalPoint,
                        CoordinateSystem.CARTESIAN);

                if (Converter.metersToNm(distanceRoute1SegmentExternal) <= epsilon) {

                    System.out.println("Checking time");

                    IntendedRouteFilterMessage message = new IntendedRouteFilterMessage(segment1Positions.get(k),
                            route1SegmentExternalPoint, "Route Segments proximity warning", j - 1, j);

                    if (checkDateInterval(route1SegmentExternalPoint, segment1Positions.get(k), route1, route2, i, j, message)) {
                        // messageList.add(message);
                        return message;
                    }

                }
            }

        } else {
            // route2 is the GC

            double distance = C.distanceTo(D, CoordinateSystem.GEODETIC);

            double lineSegmentSize = determineSegmentSize(distance);
            double segmentAccumulated = 0;

            List<Position> segment2Positions = new ArrayList<Position>();

            while (segmentAccumulated <= distance) {

                LatLonPoint result = GreatCircle.pointAtDistanceBetweenPoints(Math.toRadians(C.getLatitude()),
                        Math.toRadians(C.getLongitude()), Math.toRadians(D.getLatitude()), Math.toRadians(D.getLongitude()),
                        segmentAccumulated / 6371000, 256);

                if (result != null) {
                    segment2Positions.add(Position.create(result.getLatitude(), result.getLongitude()));

                    // intersectPositions.add(Position.create(result.getLatitude(), result.getLongitude()));

                }

                segmentAccumulated = segmentAccumulated + lineSegmentSize;

            }
            // Add the last waypoint
            segment2Positions.add(D);

            // Project all points into 2d space and compare against the RL line
            // The returned point will be on route1 line

            Projection projection = mapBean.getProjection();

            Point2D pointA = projection.forward(A.getLatitude(), A.getLongitude());
            Point2D pointB = projection.forward(B.getLatitude(), B.getLongitude());

            // External points are all those created from segment1Positions list

            for (int k = 0; k < segment2Positions.size(); k++) {
                Point2D GCPoint = projection.forward(segment2Positions.get(k).getLatitude(), segment2Positions.get(k)
                        .getLongitude());
                // Calculations when looking at route going from A to B with external point from segment2Positions
                Point2D third = nearestPointOnLine(pointA.getX(), pointA.getY(), pointB.getX(), pointB.getY(), GCPoint.getX(),
                        GCPoint.getY(), true);
                LatLonPoint thirdProjected = projection.inverse(third);
                Position route2SegmentExternalPoint = Position.create(thirdProjected.getLatitude(), thirdProjected.getLongitude());

                double distanceRoute2SegmentExternal = segment2Positions.get(k).distanceTo(route2SegmentExternalPoint,
                        CoordinateSystem.CARTESIAN);

                if (Converter.metersToNm(distanceRoute2SegmentExternal) <= epsilon) {

                    System.out.println("Checking time");

                    IntendedRouteFilterMessage message = new IntendedRouteFilterMessage(route2SegmentExternalPoint,
                            segment2Positions.get(k), "Route Segments proximity warning", j - 1, j);

                    if (checkDateInterval(route2SegmentExternalPoint, segment2Positions.get(k), route1, route2, i, j, message)) {
                        return message;
                    }

                }
            }

        }

        return null;
    }

    private IntendedRouteFilterMessage proxmityFilter(Route route1, Route route2, int i, int j, Position route1Waypoint1,
            Position route1Waypoint2, Position route2Waypoint1, Position route2Waypoint2, double epsilon) {

        Position A = route1Waypoint1;
        Position B = route1Waypoint2;

        Position C = route2Waypoint1;
        Position D = route2Waypoint2;

        // If all headins are RL use first method
        // Otherwise us GC method
        // If they are different - use hybrid

        // Should we even compare?

        if (route1.getWaypoints().get(i).getOutLeg() != null && route2.getWaypoints().get(j).getOutLeg() != null) {

            if (route1.getWaypoints().get(i).getOutLeg().getHeading() == Heading.RL
                    && route2.getWaypoints().get(j).getOutLeg().getHeading() == Heading.RL) {

                System.out.println("Doing Rhumbline comparisons");

                // Determine using projection into 2d space
                return proximityFilterRhumbLine(route1, route2, i, j, A, B, C, D, epsilon);

            }

            if (route1.getWaypoints().get(i).getOutLeg().getHeading() == Heading.GC
                    && route2.getWaypoints().get(j).getOutLeg().getHeading() == Heading.GC) {

                System.out.println("Doing GreatCircle comparison");

                // Determine using sampling along the GC
                return proximityFilterGreatCircle(route1, route2, i, j, A, B, C, D, epsilon);
            }

            System.out.println("Doing mix sampling RL and GC");

            // Determine using sampling along GC and RL
            return proximityFilterMix(route1, route2, i, j, A, B, C, D, epsilon);
        }

        System.out.println("Not doing anything - why?");

        return null;
    }

    /**
     * Checks that the CPA between the two route segments is within {@code FILTER_TIME_EPSILON} minutes
     * 
     * @param positionRoute1
     *            the CPA on route 1
     * @param positionRoute2
     *            the CPA on route 2
     * @param route1
     *            route 1
     * @param route2
     *            route 2
     * @param i
     *            way point index of route 1
     * @param j
     *            way point index of route 2
     * @param message
     *            the intended route filter message to update with the times
     * @return if the CPA between the two route segments is within {@code FILTER_TIME_EPSILON} minutes
     */
    private boolean checkDateInterval(Position positionRoute1, Position positionRoute2, Route route1, Route route2, int i, int j,
            IntendedRouteFilterMessage message) {

        DateTime routeSegment1StartDate = new DateTime(route1.getEtas().get(i));
        DateTime routeSegment1EndDate = new DateTime(route1.getEtas().get(i + 1));

        DateTime routeSegment2StartDate = new DateTime(route2.getEtas().get(j));
        DateTime routeSegment2EndDate = new DateTime(route2.getEtas().get(j + 1));

        // Segment 1
        double routeSegment1Length = Converter.metersToNm(route1.getWaypoints().get(i).getPos()
                .distanceTo(route1.getWaypoints().get(i + 1).getPos(), CoordinateSystem.CARTESIAN));

        long routeSegment1MilisecondsLength = routeSegment1EndDate.getMillis() - routeSegment1StartDate.getMillis();

        double routeSegment1SpeedMiliPrNm = routeSegment1Length / routeSegment1MilisecondsLength;

        // Distance travelled in nautical miles
        double distanceTravelledSegment1 = Converter.metersToNm(route1.getWaypoints().get(i + 1).getPos()
                .distanceTo(positionRoute1, CoordinateSystem.CARTESIAN));

        long timeTravelledSegment1 = (long) (distanceTravelledSegment1 / routeSegment1SpeedMiliPrNm);
        DateTime segment1IntersectionTime = routeSegment1StartDate.plus(timeTravelledSegment1);

        // Segment 2
        double routeSegment2Length = Converter.metersToNm(route2.getWaypoints().get(j).getPos()
                .distanceTo(route2.getWaypoints().get(j + 1).getPos(), CoordinateSystem.CARTESIAN));
        long routeSegment2MilisecondsLength = routeSegment2EndDate.getMillis() - routeSegment2StartDate.getMillis();

        double routeSegment2SpeedMiliPrNm = routeSegment2Length / routeSegment2MilisecondsLength;

        // Distance travelled in nautical miles
        double distanceTravelledSegment2 = Converter.metersToNm(route2.getWaypoints().get(j + 1).getPos()
                .distanceTo(positionRoute2, CoordinateSystem.CARTESIAN));

        long timeTravelledSegment2 = (long) (distanceTravelledSegment2 / routeSegment2SpeedMiliPrNm);
        DateTime segment2IntersectionTime = routeSegment2StartDate.plus(timeTravelledSegment2);

        // Update the filter message
        message.setTime1(segment1IntersectionTime);
        message.setTime2(segment2IntersectionTime);

        if (segment2IntersectionTime.isAfter(segment1IntersectionTime.minusMinutes(FILTER_TIME_EPSILON))
                && segment2IntersectionTime.isBefore(segment1IntersectionTime.plusMinutes(FILTER_TIME_EPSILON))) {
            return true;
        }

        System.out.println("Time check failed Segment1: " + segment1IntersectionTime + " vs Segment2: " + segment2IntersectionTime);
        return false;
    }

    private IntendedRouteFilterMessage intersectionFilter(Route route1, Route route2, int i, int j, Position route1Waypoint1,
            Position route1Waypoint2, Position route2Waypoint1, Position route2Waypoint2) {

        Position intersection = intersection(route1Waypoint1, route1Waypoint2, route2Waypoint1, route2Waypoint2);

        if (intersection != null) {

            System.out.println("We have an intersection");

            IntendedRouteFilterMessage message = new IntendedRouteFilterMessage(intersection, intersection,
                    "Intersection occurs within 2 hour of eachother", j, j + 1);

            if (checkDateInterval(intersection, intersection, route1, route2, i, j, message)) {
                // intersectPositions.add(intersection);
                return message;
            }
        }

        return null;
    }

    public List<Position> getIntersectPositions() {
        return intersectPositions;
    }

}
