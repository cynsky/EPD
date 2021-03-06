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
package dk.dma.epd.ship.gui.component_panels;

import java.awt.BorderLayout;

import javax.swing.border.EtchedBorder;

import com.bbn.openmap.event.ProjectionEvent;
import com.bbn.openmap.event.ProjectionListener;
import com.bbn.openmap.gui.OMComponentPanel;
import com.bbn.openmap.proj.coords.LatLonPoint;

import dk.dma.epd.common.prototype.event.mouse.IMapCoordListener;
import dk.dma.epd.common.prototype.voct.VOCTUpdateEvent;
import dk.dma.epd.common.prototype.voct.VOCTUpdateListener;
import dk.dma.epd.ship.gui.panels.SARPanel;
import dk.dma.epd.ship.service.voct.VOCTManager;

public class SARComponentPanel extends OMComponentPanel implements
 Runnable, ProjectionListener, IMapCoordListener,
        VOCTUpdateListener {

    private static final long serialVersionUID = 1L;
    private final SARPanel sarPanel;
    private VOCTManager voctManager;

    public SARComponentPanel() {
        super();

        // this.setMinimumSize(new Dimension(10, 165));

        sarPanel = new SARPanel();
        // activeWaypointPanel.setVisible(false);
        sarPanel.setBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null));
        setBorder(null);

        setLayout(new BorderLayout(0, 0));
        add(sarPanel, BorderLayout.NORTH);
        setVisible(false);
    }

    @Override
    public void projectionChanged(ProjectionEvent e) {
        // TODO Auto-generated method stub

    }

    @Override
    public void run() {
        // TODO Auto-generated method stub

    }



    @Override
    public void findAndInit(Object obj) {

   

        if (obj instanceof VOCTManager) {
            voctManager = (VOCTManager) obj;
            voctManager.addListener(this);
            sarPanel.setVoctManager(voctManager);
        }
    }

    @Override
    public void voctUpdated(VOCTUpdateEvent e) {

        if (e == VOCTUpdateEvent.SAR_CANCEL) {
            sarPanel.sarCancel();
        }

        if (e == VOCTUpdateEvent.SAR_DISPLAY) {
            sarPanel.sarComplete(voctManager.getSarData());
            sarPanel.getBtnEffortAllocation().setEnabled(true);
//            sarPanel.getBtnGenerateSearchPattern().setEnabled(true);
        }
        if (e == VOCTUpdateEvent.EFFORT_ALLOCATION_DISPLAY) {
            sarPanel.effortAllocationComplete(voctManager.getSarData());
        }
        if (e == VOCTUpdateEvent.SEARCH_PATTERN_GENERATED) {
            sarPanel.searchPatternGenerated(voctManager.getSarData());
        }

        if (e == VOCTUpdateEvent.SAR_RECEIVED_CLOUD) {
            
            sarPanel.sarComplete(voctManager.getSarData());
            sarPanel.getBtnReopenCalculations().setEnabled(false);

            
            if (voctManager.getSarData().getEffortAllocationData().size() > 0) {
                sarPanel.effortAllocationComplete(voctManager.getSarData());
                sarPanel.getBtnEffortAllocation().setEnabled(false);

                if (voctManager.getSarData().getEffortAllocationData().get(0)
                        .getSearchPatternRoute() != null) {

                    sarPanel.getChckbxShowDynamicPattern().setEnabled(true);
                    sarPanel.getBtnGenerateSearchPattern().setEnabled(false);
                }else{
                    sarPanel.getChckbxShowDynamicPattern().setEnabled(true);
                    sarPanel.getBtnGenerateSearchPattern().setEnabled(true);
                }

            }else{
                sarPanel.getBtnEffortAllocation().setEnabled(true);
                sarPanel.resetEffortAllocation();
                
            }

        }
    }

    @Override
    public void receiveCoord(LatLonPoint llp) {
        // TODO Auto-generated method stub
        
    }

}
