/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Chris Whitney
 *  
 */
package org.geowebcache.layer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class BBOX {
    private static Log log = LogFactory
            .getLog(org.geowebcache.layer.BBOX.class);

    private static String DELIMITER = ",";

    private static double equalityThreshold = 0.03;

    // minx, miny, maxx, maxy
    protected double[] coords = new double[4];

    public BBOX(String BBOX) {
        setFromBBOXString(BBOX, 0);
        if (log.isTraceEnabled()) {
            log.trace("Created BBOX: " + getReadableString());
        }
    }

    public BBOX(String[] BBOX) {
        setFromStringArray(BBOX);
        if (log.isTraceEnabled()) {
            log.trace("Created BBOX: " + getReadableString());
        }
    }

    public BBOX(double minx, double miny, double maxx, double maxy) {
        coords[0] = minx;
        coords[1] = miny;
        coords[2] = maxx;
        coords[3] = maxy;

        if (log.isTraceEnabled()) {
            log.trace("Created BBOX: " + getReadableString());
        }
    }

    /**
     * Sets from an array of strings
     * 
     * @param BBOX
     */
    public void setFromStringArray(String[] BBOX) {
        setFromStringArray(BBOX, 0);
    }

    public void setFromStringArray(String[] BBOX, int recWatch) {
        if (BBOX.length == 4) {
            coords[0] = Double.valueOf(BBOX[0]).doubleValue();
            coords[1] = Double.valueOf(BBOX[1]).doubleValue();
            coords[2] = Double.valueOf(BBOX[2]).doubleValue();
            coords[3] = Double.valueOf(BBOX[3]).doubleValue();

        } else if (recWatch < 4) {
            setFromBBOXString(BBOX[0], recWatch);
        } else {
            String tmp = "";
            for (int i = 0; i < BBOX.length; i++) {
                tmp += "[" + BBOX[i] + "] ";
            }
            log.error("Doesnt understand " + BBOX);
        }
    }

    /**
     * Parses the BBOX parameters from a comma separted value list
     * 
     * @param BBOX
     */
    public void setFromBBOXString(String BBOX, int recWatch) {
        String[] tokens = BBOX.split(DELIMITER);
        setFromStringArray(tokens, recWatch + 1);
    }

    /**
     * Outputs a string suitable for logging and other human-readable tasks
     * 
     * @return a readable string
     */
    public String getReadableString() {
        return "Min X: " + coords[0] + " Min Y: " + coords[1] + " Max X: "
                + coords[2] + " Max Y: " + coords[3];
    }

    /**
     * Returns a comma separated value String suitable for URL output
     */
    @Override
    public String toString() {
        StringBuffer buff = new StringBuffer(40);
        buff.append(Double.toString(coords[0]));
        buff.append(',');
        buff.append(Double.toString(coords[1]));
        buff.append(',');
        buff.append(Double.toString(coords[2]));
        buff.append(',');
        buff.append(Double.toString(coords[3]));
        return buff.toString();
    }

    /**
     * Comparing whether the differences between the bounding boxes can be
     * ignored.
     * 
     * @param other
     * @return whether the boxes are equal
     */
    public boolean equals(BBOX other) {
        return (coords[0] - other.coords[0]) / (coords[0] + other.coords[0]) < equalityThreshold
                && (coords[1] - other.coords[1])
                        / (coords[1] + other.coords[1]) < equalityThreshold
                && (coords[2] - other.coords[2])
                        / (coords[2] + other.coords[2]) < equalityThreshold
                && (coords[3] - other.coords[3])
                        / (coords[3] + other.coords[3]) < equalityThreshold;
    }

    /**
     * Check whether this bbox contains the bbox
     * 
     * @param other
     * @return whether other is contained by this
     */
    public boolean contains(BBOX other) {
        return (coords[0] - equalityThreshold <= other.coords[0]
                && coords[1] - equalityThreshold <= other.coords[1]
                && coords[2] + equalityThreshold >= other.coords[2] && coords[3]
                + equalityThreshold >= other.coords[3]);
    }

    /**
     * Minimal sanity check
     * 
     * @return whether min x < max x, min y < max y
     */
    public boolean isSane() {
        return (coords[0] < coords[2] && coords[1] < coords[3]);
    }

    @Override
    public int hashCode() {
        return Float.floatToIntBits((float) coords[0])
                ^ Float.floatToIntBits((float) coords[2]);
    }

    /**
     * GeoServer fails the entire tile if it crosses the max extents, dateline boundary.
     * 
     * This reduces the bounding box slightly, to accomodate for this.
     * 
     * This is a bad hack, but a necessary one.
     * 
     * @param srs
     */
    public void adjustForGeoServer(String srs) {
        double minX = 0;
        double minY = 0;
        double maxX = 0;
        double maxY = 0;
        
        if(srs.equalsIgnoreCase("epsg:900913")) {
            minX = -20037508.34 * 0.995;
            minY = -20037508.34 * 0.995;
            maxX = 20037508.34 * 0.995;
            maxY = 20037508.34 * 0.995;
                        
        } else if(srs.equalsIgnoreCase("epsg:4326")) {
            minX = -180.0 * 0.9999;
            minY = -90.0 * 0.99995;
            maxX = 180.0 * 0.9999;
            maxY =  90.0* 0.99995;
        } else {
            return;
        }
        
        if(this.coords[0] < minX ) {
            this.coords[0] = minX;
            log.warn("Limited minX bounds "+Double.toString(minX)+" to avoid GeoServer problems");
        }
        if(this.coords[1] < minY ) {
            this.coords[1] = minY;
            log.warn("Limited minY bounds "+Double.toString(minY)+" to avoid GeoServer problems");
        }
        if(this.coords[2] > maxX ) {
            this.coords[2] = maxX;
            log.warn("Limited maxX bounds "+Double.toString(maxX)+" to avoid GeoServer problems");
        }
        if(this.coords[3] > maxY ) {
            this.coords[3] = maxY;
            log.warn("Limited maxY bounds "+Double.toString(maxY)+" to avoid GeoServer problems");
        }
    }
}
