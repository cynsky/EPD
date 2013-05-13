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
package dk.dma.epd.shore.layers.voyage;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JPanel;
import javax.swing.JLabel;

import dk.dma.epd.shore.EPDShore;
import dk.dma.epd.shore.gui.settingtabs.GuiStyler;

public class ShipIndicatorPanel extends JPanel implements MouseListener {

    private static final long serialVersionUID = 1L;
    long id;
    JLabel lblRouteRequestPending = new JLabel("Route Request Pending");

    /**
     * Create the panel.
     */
    public ShipIndicatorPanel(long id) {
        add(lblRouteRequestPending);
        lblRouteRequestPending.setVisible(true);
        this.id = id;

        // setBackground(new Color(1f, 0, 0, 0.5f));
        setBackground(new Color(83, 83, 83));

        setBounds(50, 50, 150, 30);
        setOpaque(false);

        
        GuiStyler.styleTitle(lblRouteRequestPending);
//        GuiStyler.styleButton(lblRouteRequestPending);

        lblRouteRequestPending.addMouseListener(this);
        this.addMouseListener(this);

    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Dimension arcs = new Dimension(15, 15);
        int width = getWidth();
        int height = getHeight();
        Graphics2D graphics = (Graphics2D) g;

        graphics.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, 0.8f));

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // Draws the rounded opaque panel with borders.
        graphics.setColor(getBackground());
        graphics.fillRoundRect(0, 0, width - 1, height - 1, arcs.width,
                arcs.height);// paint background
        graphics.setColor(getForeground());
        graphics.drawRoundRect(0, 0, width - 1, height - 1, arcs.width,
                arcs.height);// paint border
    }

    @Override
    public void mouseClicked(MouseEvent arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void mouseEntered(MouseEvent arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void mouseExited(MouseEvent arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void mousePressed(MouseEvent arg0) {
        setBackground(new Color(45, 45, 45));
    }

    @Override
    public void mouseReleased(MouseEvent arg0) {
//        if (arg0.getSource() == lblRouteRequestPending && lblRouteRequestPending.isEnabled()) {
        setBackground(new Color(83, 83, 83));
            EPDShore.getMainFrame().getNotificationCenter().showMonaLisaMsg(2, id);
            
//        }
        

    }
}
