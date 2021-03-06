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
package dk.dma.epd.shore.gui.views.menuitems;

import javax.swing.JMenuItem;

import dk.dma.epd.common.prototype.ais.AisHandlerCommon;
import dk.dma.epd.common.prototype.ais.VesselTarget;
import dk.dma.epd.common.prototype.gui.menuitems.event.IMapMenuAction;
import dk.dma.epd.common.prototype.model.route.Route;
import dk.dma.epd.shore.EPDShore;
import dk.dma.epd.shore.service.StrategicRouteHandler;
import dk.dma.epd.shore.service.StrategicRouteNegotiationData;
import dk.dma.epd.shore.voyage.Voyage;

public class VoyageRenegotiate extends JMenuItem implements IMapMenuAction {

    private long transactionid;
    private AisHandlerCommon aisHandler;
    private static final long serialVersionUID = 1L;
    private StrategicRouteHandler strategicRouteHandler;

    /**
     * @param transactionid
     *            the transactionid to set
     */
    public void setTransactionid(long transactionid) {
        this.transactionid = transactionid;
    }

    public VoyageRenegotiate(String text) {
        super();
        setText(text);
    }

    
    
    /**
     * @param aisHandler the aisHandler to set
     */
    public void setAisHandler(AisHandlerCommon aisHandler) {
        this.aisHandler = aisHandler;
    }

    /**
     * @param strategicRouteHandler the strategicRouteHandler to set
     */
    public void setStrategicRouteHandler(StrategicRouteHandler strategicRouteHandler) {
        this.strategicRouteHandler = strategicRouteHandler;
    }

    @Override
    public void doAction() {
        handleNegotiation();
        
        
    }
    
    private void handleNegotiation(){
        
        if (strategicRouteHandler.getStrategicNegotiationData().containsKey(transactionid)){
            
        System.out.println("Handling it!");
        
        StrategicRouteNegotiationData message = strategicRouteHandler.getStrategicNegotiationData().get(transactionid);
        

        String shipName = "" + message.getMmsi();
        
        VesselTarget vesselTarget = aisHandler.getVesselTarget(message.getMmsi());
        if (vesselTarget.getStaticData() != null) {
            shipName = vesselTarget.getStaticData().getName();
        }

        // Get latest route
        Route route = null;
        
        //The one we sent out was accepted
        if (message.getRouteMessage().size() > message.getRouteReply().size()){
          route = new Route(message.getRouteMessage()
          .get(message.getRouteMessage().size() - 1)
          .getRoute());
        }else{
            route = new Route(message.getRouteReply()
                    .get(message.getRouteReply().size() - 1)
                    .getRoute());  
        }

        Voyage voyage = new Voyage(message.getMmsi(), route,
                message.getId());

        Route originalRoute = new Route(message.getRouteMessage().get(0).getRoute());
        
        EPDShore.getInstance().getMainFrame().addStrategicRouteExchangeHandlingWindow(originalRoute,
                shipName, voyage, true);
    }
    }
}
