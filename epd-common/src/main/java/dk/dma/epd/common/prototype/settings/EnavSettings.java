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
package dk.dma.epd.common.prototype.settings;

import java.io.Serializable;
import java.util.Properties;

import com.bbn.openmap.util.PropUtils;

/**
 * Specific e-Navigation settings
 */
public class EnavSettings implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String PREFIX = "enav.";
    
    private double defaultWindWarnLimit = 10.0; // m/s
    private double defaultCurrentWarnLimit = 4.0; // m/s
    private double defaultWaveWarnLimit = 3.0; // m
    
    // default metoc-symbol levels. In knots
    private double defaultCurrentLow = 1.0;
    private double defaultCurrentMedium = 2.0;
    
    // In meters
    private double defaultWaveLow = 1.0;
    private double defaultWaveMedium = 2.0;
    
    /**
     * How long should METOC for route be considered valid
     */
    private int metocTtl = 60; // min
    /**
     * The minimum interval between metoc polls for active route 0 - never
     */
    private int activeRouteMetocPollInterval = 5; // min
    /**
     * The tolerance of how long we may drift from plan before METOC is considered invalid 
     */
    private int metocTimeDiffTolerance = 15; // 15 min
    
    private String serverName = "service.e-navigation.net";
    private int httpPort = 80;
    private int connectTimeout = 30000;
    private int msiPollInterval = 600; // sek
    private int readTimeout = 60000;
    private int msiTextboxesVisibleAtScale = 80000;
    private double msiRelevanceGpsUpdateRange = 0.5d;
    private double msiRelevanceFromOwnShipRange = 40.0d;
    private double msiVisibilityFromNewWaypoint = 30.0d;
    private boolean msiFilter = true;
    private String monaLisaServer = "www.optiroute.se/RouteRequest";
    private int monaLisaPort = 80;
    
    
    public EnavSettings() {
        
    }

    public static String getPrefix() {
        return PREFIX;
    }
    
    public void readProperties(Properties props) {
        defaultWindWarnLimit = PropUtils.doubleFromProperties(props, PREFIX + "defaultWindWarnLimit", defaultWindWarnLimit);
        defaultCurrentWarnLimit = PropUtils.doubleFromProperties(props, PREFIX + "defaultCurrentWarnLimit", defaultCurrentWarnLimit);
        defaultWaveWarnLimit = PropUtils.doubleFromProperties(props, PREFIX + "defaultWaveWarnLimit", defaultWaveWarnLimit);
        defaultCurrentLow = PropUtils.doubleFromProperties(props, PREFIX + "defaultCurrentLow", defaultCurrentLow);
        defaultCurrentMedium = PropUtils.doubleFromProperties(props, PREFIX + "defaultCurrentMedium", defaultCurrentMedium);
        defaultWaveLow = PropUtils.doubleFromProperties(props, PREFIX + "defaultWaveLow", defaultWaveLow);
        defaultWaveMedium = PropUtils.doubleFromProperties(props, PREFIX + "defaultWaveMedium", defaultWaveMedium);
        serverName = props.getProperty(PREFIX + "serverName", serverName);
        httpPort = PropUtils.intFromProperties(props, PREFIX + "httpPort", httpPort);
        connectTimeout = PropUtils.intFromProperties(props, PREFIX + "connectTimeout", connectTimeout);
        readTimeout = PropUtils.intFromProperties(props, PREFIX + "readTimeout", readTimeout);
        metocTtl = PropUtils.intFromProperties(props, PREFIX + "metocTtl", metocTtl);
        activeRouteMetocPollInterval = PropUtils.intFromProperties(props, PREFIX + "activeRouteMetocPollInterval", activeRouteMetocPollInterval);
        metocTimeDiffTolerance = PropUtils.intFromProperties(props, PREFIX + "metocTimeDiffTolerance", metocTimeDiffTolerance);
        msiPollInterval = PropUtils.intFromProperties(props, PREFIX + "msiPollInterval", msiPollInterval);
        msiTextboxesVisibleAtScale = PropUtils.intFromProperties(props, PREFIX + "msiTextboxesVisibleAtScale", msiTextboxesVisibleAtScale);
        msiRelevanceGpsUpdateRange = PropUtils.doubleFromProperties(props, PREFIX + "msiRelevanceGpsUpdateRange", msiRelevanceGpsUpdateRange);
        msiRelevanceFromOwnShipRange = PropUtils.doubleFromProperties(props, PREFIX + "msiRelevanceFromOwnShipRange", msiRelevanceFromOwnShipRange);
        msiVisibilityFromNewWaypoint = PropUtils.doubleFromProperties(props, PREFIX + "msiVisibilityFromNewWaypoint", msiVisibilityFromNewWaypoint);
        msiFilter = PropUtils.booleanFromProperties(props, PREFIX + "msiFilter", msiFilter);
        
        // Temporary hack to move away from enav.frv.dk to service.e-navigation.net
        if (serverName.contains("enav.frv.dk")) {
            serverName = "service.e-navigation.net";
        }
        
        monaLisaServer = props.getProperty(PREFIX + "monaLisaServer", monaLisaServer);
        monaLisaPort = PropUtils.intFromProperties(props, PREFIX + "monaLisaPort", monaLisaPort);    
    }
    
    public void setProperties(Properties props) {
        props.put(PREFIX + "defaultWindWarnLimit", Double.toString(defaultWindWarnLimit));
        props.put(PREFIX + "defaultCurrentWarnLimit", Double.toString(defaultCurrentWarnLimit));
        props.put(PREFIX + "defaultWaveWarnLimit", Double.toString(defaultWaveWarnLimit));
        props.put(PREFIX + "defaultCurrentLow", Double.toString(defaultCurrentLow));
        props.put(PREFIX + "defaultCurrentMedium", Double.toString(defaultCurrentMedium));
        props.put(PREFIX + "defaultWaveLow", Double.toString(defaultWaveLow));
        props.put(PREFIX + "defaultWaveMedium", Double.toString(defaultWaveMedium));
        props.put(PREFIX + "serverName", serverName);
        props.put(PREFIX + "httpPort", Integer.toString(httpPort));
        props.put(PREFIX + "connectTimeout", Integer.toString(connectTimeout));
        props.put(PREFIX + "readTimeout", Integer.toString(readTimeout));
        props.put(PREFIX + "metocTtl", Integer.toString(metocTtl));
        props.put(PREFIX + "activeRouteMetocPollInterval", Integer.toString(activeRouteMetocPollInterval));
        props.put(PREFIX + "metocTimeDiffTolerance", Integer.toString(metocTimeDiffTolerance));
        props.put(PREFIX + "msiPollInterval", Integer.toString(msiPollInterval));
        props.put(PREFIX + "msiTextboxesVisibleAtScale", Integer.toString(msiTextboxesVisibleAtScale));
        props.put(PREFIX + "msiRelevanceGpsUpdateRange", Double.toString(msiRelevanceGpsUpdateRange));
        props.put(PREFIX + "msiRelevanceFromOwnShipRange", Double.toString(msiRelevanceFromOwnShipRange));
        props.put(PREFIX + "msiVisibilityFromNewWaypoint", Double.toString(msiVisibilityFromNewWaypoint));
        props.put(PREFIX + "msiFilter", Boolean.toString(msiFilter));
        props.put(PREFIX + "monaLisaServer", monaLisaServer);
        props.put(PREFIX + "monaLisaPort", Integer.toString(monaLisaPort));
    }

    public double getDefaultWindWarnLimit() {
        return defaultWindWarnLimit;
    }

    public void setDefaultWindWarnLimit(double defaultWindWarnLimit) {
        this.defaultWindWarnLimit = defaultWindWarnLimit;
    }

    public double getDefaultCurrentWarnLimit() {
        return defaultCurrentWarnLimit;
    }

    public void setDefaultCurrentWarnLimit(double defaultCurrentWarnLimit) {
        this.defaultCurrentWarnLimit = defaultCurrentWarnLimit;
    }

    public double getDefaultWaveWarnLimit() {
        return defaultWaveWarnLimit;
    }

    public void setDefaultWaveWarnLimit(double defaultWaveWarnLimit) {
        this.defaultWaveWarnLimit = defaultWaveWarnLimit;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }
    
    public int getMetocTtl() {
        return metocTtl;
    }
    
    public void setMetocTtl(int metocTtl) {
        this.metocTtl = metocTtl;
    }
    
    public int getActiveRouteMetocPollInterval() {
        return activeRouteMetocPollInterval;
    }
    
    public void setActiveRouteMetocPollInterval(int activeRouteMetocPollInterval) {
        this.activeRouteMetocPollInterval = activeRouteMetocPollInterval;
    }
    
    public int getMsiPollInterval() {
        return msiPollInterval;
    }
    
    public void setMsiPollInterval(int msiPollInterval) {
        this.msiPollInterval = msiPollInterval;
    }

    public int getMsiTextboxesVisibleAtScale() {
        return msiTextboxesVisibleAtScale;
    }

    public void setMsiTextboxesVisibleAtScale(int msiTextboxesVisibleAtScale) {
        this.msiTextboxesVisibleAtScale = msiTextboxesVisibleAtScale;
    }
    
    public double getDefaultCurrentLow() {
        return defaultCurrentLow;
    }

    public double getDefaultCurrentMedium() {
        return defaultCurrentMedium;
    }

    public double getDefaultWaveLow() {
        return defaultWaveLow;
    }

    public double getDefaultWaveMedium() {
        return defaultWaveMedium;
    }
    
    public int getMetocTimeDiffTolerance() {
        return metocTimeDiffTolerance;
    }
    
    public void setMetocTimeDiffTolerance(int metocTimeDiffTolerance) {
        this.metocTimeDiffTolerance = metocTimeDiffTolerance;
    }

    public double getMsiRelevanceGpsUpdateRange() {
        return msiRelevanceGpsUpdateRange;
    }

    public void setMsiRelevanceGpsUpdateRange(double msiRelevanceGpsUpdateRange) {
        this.msiRelevanceGpsUpdateRange = msiRelevanceGpsUpdateRange;
    }

    public double getMsiRelevanceFromOwnShipRange() {
        return msiRelevanceFromOwnShipRange;
    }

    public void setMsiRelevanceFromOwnShipRange(double msiRelevanceFromOwnShipRange) {
        this.msiRelevanceFromOwnShipRange = msiRelevanceFromOwnShipRange;
    }

    public boolean isMsiFilter() {
        return msiFilter;
    }

    public void setMsiFilter(boolean msiFilter) {
        this.msiFilter = msiFilter;
    }

    public double getMsiVisibilityFromNewWaypoint() {
        return msiVisibilityFromNewWaypoint;
    }

    public void setMsiVisibilityFromNewWaypoint(double msiVisibilityFromNewWaypoint) {
        this.msiVisibilityFromNewWaypoint = msiVisibilityFromNewWaypoint;
    }

    public void setDefaultCurrentLow(double defaultCurrentLow) {
        this.defaultCurrentLow = defaultCurrentLow;
    }

    public void setDefaultCurrentMedium(double defaultCurrentMedium) {
        this.defaultCurrentMedium = defaultCurrentMedium;
    }

    public void setDefaultWaveLow(double defaultWaveLow) {
        this.defaultWaveLow = defaultWaveLow;
    }

    public void setDefaultWaveMedium(double defaultWaveMedium) {
        this.defaultWaveMedium = defaultWaveMedium;
    }

    public String getMonaLisaServer() {
        return monaLisaServer;
    }

    public void setMonaLisaServer(String monaLisaServer) {
        this.monaLisaServer = monaLisaServer;
    }

    public int getMonaLisaPort() {
        return monaLisaPort;
    }

    public void setMonaLisaPort(int monaLisaPort) {
        this.monaLisaPort = monaLisaPort;
    }

    
}
