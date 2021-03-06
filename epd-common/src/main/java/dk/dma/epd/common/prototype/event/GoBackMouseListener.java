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
package dk.dma.epd.common.prototype.event;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import dk.dma.epd.common.prototype.gui.GoBackButton;
import dk.dma.epd.common.prototype.gui.GoForwardButton;
import dk.dma.epd.common.prototype.gui.views.ChartPanelCommon;

public class GoBackMouseListener extends MouseAdapter {
    
    /****************\
    * private fields *
    \****************/
    
    private GoBackButton goBackButton;
    private GoForwardButton goForwardButton;
    private HistoryListener historyListener;
    private ChartPanelCommon chartPanel;
    
    public GoBackMouseListener(GoBackButton goBackButton, 
            GoForwardButton goForwardButton, 
            HistoryListener historyListener,
            ChartPanelCommon chartPanel) {
        this.goBackButton = goBackButton;
        this.goForwardButton = goForwardButton;
        this.historyListener = historyListener;
        this.chartPanel = chartPanel;
    }

    /*****************\
    * private methods *
    \*****************/
    
    /**
     * Toggles the button enabled or disabled.
     */
    private void toogleButton() {
        if (this.historyListener.containsElements()) {
            this.goForwardButton.setEnabled(true);
        }
        
        if (this.historyListener.isAtLowestElement()) {
            this.goBackButton.setEnabled(false);
        }
    }

    /****************\
    * public methods *
    \****************/
    
    @Override
    public void mouseClicked(MouseEvent e) {
        // Get the HistoryPosition one element back in history.
        HistoryPosition hpos = historyListener.goOneElementBack();
        
        // If the HistoryPosition is not null, go to the position and zoom scale.
        if (hpos != null) {
            chartPanel.goToPosition(hpos.getPosition());
            chartPanel.getMap().setScale(hpos.getZoomScale());
            this.toogleButton(); // Toggle the button.
        }
    }
    
}
