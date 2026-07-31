/*
 *  Copyright (c) Lightstreamer Srl
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.lightstreamer.examples.portfolio_demo.adapters;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lightstreamer.examples.portfolio_demo.feed_simulator.Portfolio;
import com.lightstreamer.examples.portfolio_demo.feed_simulator.PortfolioFeedSimulator;
import com.lightstreamer.examples.portfolio_demo.feed_simulator.PortfolioListener;

import com.lightstreamer.adapter.common.AvailabilityException;
import com.lightstreamer.adapter.common.InitException;
import com.lightstreamer.adapter.common.Mode;
import com.lightstreamer.adapter.data.DataAdapter;
import com.lightstreamer.adapter.data.ItemListener;
import com.lightstreamer.adapter.data.ItemManager;
import com.lightstreamer.adapter.data.SubscriptionException;

/**
 * This Data Adapter accepts subscriptions to items representing stock
 * portfolios and inquiries a (simulated) portfolio feed, getting the current
 * portfolio contents and waiting for update events. The events are then
 * forwarded to Lightstreamer according to the COMMAND mode protocol.
 *
 * This example demonstrates how a Data Adapter could interoperate with
 * an external feed; in this example, the feed provides a bean object
 * for each single portfolio instance.
 */
public class PortfolioDataAdapter implements DataAdapter {

    /**
     * Private logger; a specific "LS_demos_Logger.Portfolio" category
     * should be supplied by log4j configuration.
     */
    private Logger logger;

    /**
     * The feed simulator, received from the Metadata Adapter
     * via {@link PortfolioMetadataAdapter#onDataAdapterAvailable}.
     */
    private volatile PortfolioFeedSimulator feed;

    public PortfolioDataAdapter() {
    }

    /**
     * Called by the Metadata Adapter to supply the shared feed simulator.
     */
    public void setFeed(PortfolioFeedSimulator feed) {
        this.feed = feed;
    }

    public void init(Map<String, String> params, File configDir) throws InitException {
 
        // Logging configuration for the demo is carried out in the init
        // method of Metadata Adapter. In order to be sure that this method 
        // is executed after log configuration was completed, this parameter 
        // must be present in the Adapter Set configuration (adapters.xml):
        // <metadata_adapter_initialised_first>Y</metadata_adapter_initialised_first>
        logger = LogManager.getLogger("LS_demos_Logger.Portfolio");

        // Adapter ready
        logger.info("PortfolioDataAdapter ready");
    }

    @Override
    public CompletionStage<ItemManager> getItemManager(String portfolioId)
            throws SubscriptionException, AvailabilityException {

        if (feed == null) {
            throw new AvailabilityException("Portfolio feed not available");
        }

        Portfolio portfolio = feed.getPortfolio(portfolioId);
        if (portfolio == null) {
            logger.error("No such portfolio: " + portfolioId);
            throw new SubscriptionException("No such portfolio: "
                    + portfolioId);
        }

        return CompletableFuture.completedFuture(new MyItemManager(portfolioId, portfolio));
    }

    @Override
    public void shutdown() {
    }

    /**
     * Inner class that implements ItemManager for a single portfolio item.
     */
    private class MyItemManager implements ItemManager {

        private final String portfolioId;
        private final Portfolio portfolio;
        private MyPortfolioListener portfolioListener;

        MyItemManager(String portfolioId, Portfolio portfolio) {
            this.portfolioId = portfolioId;
            this.portfolio = portfolio;
        }

        @Override
        public Mode getSupportedMode() {
            return Mode.COMMAND;
        }

        @Override
        public boolean isSnapshotAvailable() {
            // We have always the snapshot available from our feed
            return true;
        }

        @Override
        public CompletionStage<Void> subscribe(ItemListener listener) {
            // Create a new listener for the portfolio
            portfolioListener = new MyPortfolioListener(
                    listener, portfolioId);

            // Set the listener on the feed
            portfolio.addListener(portfolioListener);

            logger.info(portfolioId + " subscribed");

            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unsubscribe() {
            assert(portfolio != null);

            // Mark as inactive before removing, to handle late calls
            portfolioListener.deactivate();

            // Remove the listener from the feed to not receive new
            // updates
            portfolio.removeListener(portfolioListener);

            logger.info(portfolioId + " unsubscribed");

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Inner class that listens to a single Portfolio.
     */
    private class MyPortfolioListener implements PortfolioListener {

        // The ItemListener for sending updates
        private final ItemListener itemListener;
        // Id of the portfolio, used just for the log
        private final String portfolioId;
        // Flag to detect late calls after unsubscription
        private volatile boolean active = true;

        public MyPortfolioListener(ItemListener itemListener, String portfolioId) {
            this.itemListener = itemListener;
            this.portfolioId = portfolioId;
        }

        void deactivate() {
            active = false;
        }

        public void update(String stock, int qty, int oldQty, Map<String, Integer> currentStatus) {
            // An update was received from the feed
            if (qty <= 0) {
                // If qty is 0 or less we have to delete the "row"
                // Check for late calls
                if (active) {
                    // Create a new HashMap instance that will represent the update
                    HashMap<String, String> update = new HashMap<String, String>();
                    // We just need the key
                    update.put(ItemListener.KEY_FIELD, stock);
                    // And the DELETE command
                    update.put(ItemListener.COMMAND_FIELD, ItemListener.DELETE_COMMAND);

                    // Pass everything to the kernel
                    itemListener.update(update);
                }
                logger.debug(this.portfolioId + ": deleted " + stock);

            } else if (oldQty == 0) {
                // If oldQty value is 0 then this is a new stock
                // in the portfolio so that we have to add a "row"
                // Check for late calls
                if (active) {
                    // Create a new HashMap instance that will represent the update
                    HashMap<String, String> update = new HashMap<String, String>();
                    // We have to set the key
                    update.put(ItemListener.KEY_FIELD, stock);
                    // The ADD command
                    update.put(ItemListener.COMMAND_FIELD, ItemListener.ADD_COMMAND);
                    // And the initial quantity
                    update.put("qty", String.valueOf(qty));

                    // Pass everything to the kernel
                    itemListener.update(update);
                }
                logger.debug(this.portfolioId + ": added " + stock);

            } else {
                // A simple update
                // Check for late calls
                if (active) {
                    // Create a new HashMap instance that will represent the update
                    HashMap<String, String> update = new HashMap<String, String>();
                    // We have to set the key
                    update.put(ItemListener.KEY_FIELD, stock);
                    // The UPDATE command
                    update.put(ItemListener.COMMAND_FIELD, ItemListener.UPDATE_COMMAND);
                    // And the new quantity value
                    update.put("qty", String.valueOf(qty));

                    // Pass everything to the kernel
                    itemListener.update(update);
                }
                logger.debug(this.portfolioId + ": updated " + stock);
            }
        }

        public void onActualStatus(Map<String, Integer> currentStatus) {
            // The snapshot was received from the feed
            Set<String> keys = currentStatus.keySet();
            // Iterates through the Hash representing the actual status to send
            // the snapshot to the kernel
            for (String key : keys) {
                // Check for late calls
                if (active) {
                    // Create a new HashMap instance that will represent the update
                    HashMap<String, String> update = new HashMap<String, String>();
                    // We have to set the key
                    update.put(ItemListener.KEY_FIELD, key);
                    // The ADD command
                    update.put(ItemListener.COMMAND_FIELD, ItemListener.ADD_COMMAND);
                    // And the initial quantity
                    update.put("qty", String.valueOf(currentStatus.get(key).intValue()));

                    // Pass everything to the kernel
                    itemListener.snapshot(update, null);
                }
            }
            
            // Notify the end of snapshot to the kernel
            // Check for late calls
            if (active) {
                itemListener.endOfSnapshot();
            }

            logger.info(this.portfolioId + ": snapshot sent");
        }

        public void empty() {
            //tell the server to clean its status
            itemListener.clearSnapshot();
            
            logger.info(this.portfolioId + ": snapshot cleared");
        }
    }

}