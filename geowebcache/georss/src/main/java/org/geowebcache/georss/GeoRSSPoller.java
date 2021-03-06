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
 * @author Gabriel Roldan (OpenGeo) 2010
 *  
 */
package org.geowebcache.georss;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.updatesource.GeoRSSFeedDefinition;
import org.geowebcache.layer.updatesource.UpdateSourceDefinition;
import org.geowebcache.seed.TileBreeder;

/**
 * 
 * @author groldan
 * @version $Id$
 */
public class GeoRSSPoller {

    private static final Log logger = LogFactory.getLog(GeoRSSPoller.class);

    private final TileBreeder seeder;

    private final ScheduledExecutorService schedulingPollExecutorService;

    private final List<PollDef> scheduledPolls;

    private final List<GeoRSSPollTask> scheduledTasks;

    /**
     * Upon instantiation, spawns out a thread after #{@code startUpDelaySecs} seconds that
     * periodically (at least every each layer's {@link GeoRSSFeedDefinition#getPollInterval() poll
     * interval} polls the layers feed for change sets and if changes are found spawns a reseed
     * process on the tiles affected by the change set.
     * 
     * @param seeder
     * @param startUpDelaySecs
     *            seconds to wait before start polling the layers
     */
    public GeoRSSPoller(final TileBreeder seeder, final int startUpDelaySecs) {

        this.seeder = seeder;
        this.scheduledPolls = new ArrayList<PollDef>();
        this.scheduledTasks = new ArrayList<GeoRSSPollTask>();

        findEnabledPolls();

        if (pollCount() > 0) {
            final int corePoolSize = 1;
            schedulingPollExecutorService = Executors.newScheduledThreadPool(corePoolSize);

            final TimeUnit seconds = TimeUnit.SECONDS;
            for (PollDef poll : this.scheduledPolls) {
                GeoRSSPollTask command = new GeoRSSPollTask(poll, this.seeder);
                GeoRSSFeedDefinition pollDef = poll.getPollDef();
                long period = pollDef.getPollInterval();

                logger.info("Scheduling layer " + poll.getLayer().getName()
                        + " to poll the GeoRSS feed " + pollDef.getFeedUrl() + " every "
                        + pollDef.getPollIntervalStr());

                schedulingPollExecutorService.scheduleAtFixedRate(command, startUpDelaySecs,
                        period, seconds);

                scheduledTasks.add(command);
            }
            logger.info("Will wait " + startUpDelaySecs + " seconds before launching the "
                    + pollCount() + " GeoRSS polls found");
        } else {
            schedulingPollExecutorService = null;
            logger.info("No enabled GeoRSS feeds found, poller will not run.");
        }
    }

    private void findEnabledPolls() {
        logger.info("Initializing GeoRSS poller...");

        final List<TileLayer> layers = seeder.getLayers();
        if (layers.size() == 0) {
            logger.info("Found no layers configured, GeoRSS poller won't run");
            return;
        }
        for (TileLayer layer : layers) {
            if (layer.getUpdateSources().size() == 0) {
                continue;
            }
            if (!layer.isEnabled()) {
                logger.info("Ignoring polling GeoRSS update sources for layer '" + layer.getName()
                        + "' as the layer is disabled");
            }
            for (UpdateSourceDefinition usd : layer.getUpdateSources()) {
                if (usd instanceof GeoRSSFeedDefinition) {
                    final GeoRSSFeedDefinition georssDef = (GeoRSSFeedDefinition) usd;

                    final String gridSetId = georssDef.getGridSetId();
                    final GridSubset gridSubset = layer.getGridSubset(gridSetId);
                    if (gridSubset == null) {
                        throw new IllegalStateException("Layer " + layer.getName()
                                + " has no grid subset " + gridSetId
                                + " as configured by its GeoRSS seeding feed " + georssDef);
                    }

                    if (georssDef.getPollInterval() > 0) {
                        logger.info("Scheduling GeoRSS feed for layer " + layer.getName() + ":"
                                + georssDef);
                        scheduledPolls.add(new PollDef(layer, georssDef));
                    } else {
                        logger.info("Feed disabled for layer " + layer.getName() + ", ignoring: "
                                + georssDef);
                    }
                }
            }
        }
    }

    /**
     * @return number of scheduled polls
     */
    public int pollCount() {
        return scheduledPolls.size();
    }

    /**
     * Destroy method for Spring
     */
    public void destroy() {
        logger.info("destroy() invoked");
        if (schedulingPollExecutorService != null) {
            schedulingPollExecutorService.shutdown();
        }

        for (GeoRSSPollTask task : scheduledTasks) {
            task.stopSeeding(false);
        }
        // And that's all we can do...
    }

}
