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
package dk.dma.epd.common.prototype.layers.ais;

import java.awt.BasicStroke;
import java.awt.Color;

import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoly;

import dk.dma.enav.model.geometry.CoordinateSystem;
import dk.dma.enav.model.geometry.Position;
import dk.dma.epd.common.prototype.ais.VesselPositionData;
import dk.dma.epd.common.prototype.ais.VesselStaticData;
import dk.dma.epd.common.prototype.ais.VesselTarget;

/**
 * Graphic for displaying a {@link VesselTarget} on the map. This graphic class displays the vessel outline based on vessel size data and map scale.
 * @author Janus Varmarken
 */
@SuppressWarnings("serial")
public class VesselOutline extends VesselGraphic {
    
    /**
     * Graphical polygon that depicts the vessel outline.
     */
    private OMPoly shipOutline;
    
    /**
     * Color to use for outline when this graphic <b>is</b> selected.
     */
    private Color selectionColor = Color.GREEN;
    
    /**
     * Color to use for outline when this graphic <b>is not</b> selected.
     */
    private Color lineColor;
    
    /**
     * Creates a new {@code VesselOutline} graphic.
     * @param lineColor Color of the stroke used for the vessel outline.
     * @param lineThickness Thickness of the stroke used for the vessel outline.
     */
    public VesselOutline(Color lineColor, float lineThickness) {
        super();
        this.setLinePaint(lineColor);
        this.lineColor = lineColor;
        this.setStroke(new BasicStroke(lineThickness));
        // Fill the polygon with an invisible color - helps the AisLayer to keep showing the infopanel,
        // when mouse is hovering the outline of the vessel.
        this.setFillPaint(new Color(0, 0, 0, 1));
    }
    
    /**
     * <p>Updates the display of this {@code VesselOutline} with new position and static data.</p>
     * <p>This method calls the super implementation as part of the update process:<br/>
     * {@inheritDoc}
     * </p>
     */
    @Override
    public void updateGraphic(VesselTarget vesselTarget, float mapScale) {
        VesselPositionData positionData = vesselTarget != null ? vesselTarget.getPositionData() : null;
        VesselStaticData staticData = vesselTarget != null ? vesselTarget.getStaticData() : null;
        if(positionData == null || staticData == null) {
            // We cannot update the graphic if either position or static data is not available.
            return;
        }
        // Let super store reference to the updated VesselTarget.
        super.updateGraphic(vesselTarget, mapScale);
        this.producePolygon(positionData, staticData);
    }
    
    /**
     * Updates selection status of this graphic. Selection is visualized by
     * changing the line color of this graphic.
     * 
     * @param selected
     *            True if the graphic is now selected, false if the graphic is
     *            no longer selected.
     */
    @Override
    public void setSelectionStatus(boolean selected) {
        super.setSelectionStatus(selected);
        // Simply change the color of the outline when selected.
        if (selected) {
            this.setLinePaint(this.selectionColor);
        } else {
            this.setLinePaint(this.lineColor);
        }
    }
    
    /**
     * Produces the vessel outline polygon based on the given vessel position and static data.
     * 
     * @param positionData
     *            the vessel position data
     * @param staticData
     *            the vessel static data
     */
    private void producePolygon(VesselPositionData positionData, VesselStaticData staticData) {
        // Get angle from PNT to lower left corner of ship
        double anglLowerLeft = this.calcAngleFromCenter(staticData.getDimStern(), staticData.getDimPort());
        // calculate distance to lower left corner of vessel (Pythagoras)
        double distLowerLeftCorner = Math.sqrt(Math.pow(staticData.getDimStern(), 2.0) + Math.pow(staticData.getDimPort(), 2.0));

        float heading = positionData.getTrueHeading();

        anglLowerLeft += heading + 180;

        if (360 <= anglLowerLeft) {
            anglLowerLeft -= 360.0;
        }
        Position vessPos = positionData.getPos();

        // find latlon of lower left corner of ship
        Position leftSideBottomLL = CoordinateSystem.CARTESIAN.pointOnBearing(vessPos, distLowerLeftCorner, anglLowerLeft);

        double shipFullLength = staticData.getDimBow() + staticData.getDimStern();
        double shipSideLength = shipFullLength * 0.85;
        double shipSternWidth = staticData.getDimPort() + staticData.getDimStarboard();

        // Not a point in the final polygon, simply used for finding polygon points in the bow.
        Position outerRectTopLeftLL = CoordinateSystem.CARTESIAN.pointOnBearing(leftSideBottomLL, shipFullLength, 0.0 + heading);

        // Point on port side of ship where the bow begins.
        Position leftSideTopLL = CoordinateSystem.CARTESIAN.pointOnBearing(leftSideBottomLL, shipSideLength, 0.0 + heading);

        // Left point in ship's tip
        Position bowLeftLL = CoordinateSystem.CARTESIAN.pointOnBearing(outerRectTopLeftLL, shipSternWidth / 4.0, 90.0 + heading);
        // right point in ship's tip
        Position bowRightLL = CoordinateSystem.CARTESIAN.pointOnBearing(bowLeftLL, shipSternWidth / 2.0, 90.0 + heading);
        // find lat lon of lower right corner of ship
        Position rightSideBottomLL = CoordinateSystem.CARTESIAN.pointOnBearing(leftSideBottomLL, shipSternWidth, 90.0 + heading);

        // Point on starboard side of ship where the bow begins.
        Position rightSideTopLL = CoordinateSystem.CARTESIAN.pointOnBearing(leftSideTopLL, shipSternWidth, 90.0 + heading);

        double[] shipCorners = new double[14];
        shipCorners[0] = leftSideBottomLL.getLatitude();
        shipCorners[1] = leftSideBottomLL.getLongitude();
        shipCorners[2] = leftSideTopLL.getLatitude();
        shipCorners[3] = leftSideTopLL.getLongitude();
        shipCorners[4] = bowLeftLL.getLatitude();
        shipCorners[5] = bowLeftLL.getLongitude();
        shipCorners[6] = bowRightLL.getLatitude();
        shipCorners[7] = bowRightLL.getLongitude();
        shipCorners[8] = rightSideTopLL.getLatitude();
        shipCorners[9] = rightSideTopLL.getLongitude();
        shipCorners[10] = rightSideBottomLL.getLatitude();
        shipCorners[11] = rightSideBottomLL.getLongitude();
        // end poly where it begun (to create a closed shape)
        shipCorners[12] = shipCorners[0];
        shipCorners[13] = shipCorners[1];
        // clear old shape
        this.remove(this.shipOutline);
        // create and add new shape
        this.shipOutline = new OMPoly(shipCorners, OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_RHUMB);
        this.shipOutline.setLinePaint(this.getLinePaint());
        this.add(this.shipOutline);
    }
    
    /**
     * Assumes a triangle:         B 
     *                           c/|a 
     *                           /_| 
     *                          A b C 
     * 
     * Calculates the value of angle B.
     * 
     * @param a
     *            length of side a
     * @param b
     *            length of side b
     * @return the angle B in degrees
     */
    private double calcAngleFromCenter(float a, float b) {
        // Use Pythagoras to find the distance to the corner
        double dist = Math.sqrt(a * a + b * b);
        // find angle A
        double angleA = Math.toDegrees(Math.asin(a / dist));

        // find angle B
        double angleB = 180.0 - 90.0 - angleA;
        return angleB;
    }
}
