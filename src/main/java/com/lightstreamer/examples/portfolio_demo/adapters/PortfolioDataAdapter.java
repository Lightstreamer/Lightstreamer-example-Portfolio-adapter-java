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
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lightstreamer.examples.portfolio_demo.feed_simulator.Portfolio;
import com.lightstreamer.examples.portfolio_demo.feed_simulator.PortfolioFeedSimulator;
import com.lightstreamer.examples.portfolio_demo.feed_simulator.PortfolioListener;

import com.lightstreamer.interfaces.data.DataProviderException;
import com.lightstreamer.interfaces.data.FailureException;
import com.lightstreamer.interfaces.data.ItemEventListener;
import com.lightstreamer.interfaces.data.SmartDataProvider;
import com.lightstreamer.interfaces.data.SubscriptionException;

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
public class PortfolioDataAdapter implements SmartDataProvider {

    /**
     * Private logger; a specific "LS_demos_Logger.Portfolio" category
     * should be supplied by log4j configuration.
     */
    private Logger logger;

    /**
     * The listener of updates set by Lightstreamer Kernel.
     */
    private ItemEventListener listener;

    /**
     * A map containing every active subscriptions;
     * It associates each item name with the item handle to be used
     * to identify the item towards Lightstreamer Kernel.
     */
    private final ConcurrentHashMap<String, Object> subscriptions =
        new ConcurrentHashMap<String, Object>();

    /**
     * The feed simulator.
     */
    private PortfolioFeedSimulator feed;

    /**
     * A static map, to be used by the Metadata Adapter to find the feed
     * instance; this allows the Metadata Adapter to forward client order
     * requests to the feed.
     * The map allows multiple instances of this Data Adapter to be included
     * in different Adapter Sets. Each instance is identified with the name
     * of the related Adapter Set; defining multiple instances in the same
     * Adapter Set is not allowed.
     */
    public static final ConcurrentHashMap<String, PortfolioFeedSimulator> feedMap =
        new ConcurrentHashMap<String, PortfolioFeedSimulator>();

    public PortfolioDataAdapter() {
    }

    public void init(Map params, File configDir) throws DataProviderException {
 
        // Logging configuration for the demo is carried out in the init
        // method of Metadata Adapter. In order to be sure that this method 
        // is executed after log configuration was completed, this parameter 
        // must be present in the Adapter Set configuration (adapters.xml):
        // <metadata_adapter_initialised_first>Y</metadata_adapter_initialised_first>
        logger = LogManager.getLogger("LS_demos_Logger.Portfolio");

        // Read the Adapter Set name, which is supplied by the Server as a parameter
        String adapterSetId = (String) params.get("adapters_conf.id");

        // "Bind" to the feed simulator
        feed = new PortfolioFeedSimulator(logger);

        // Put the feed instance on a static map to be read by the Metadata
        // Adapter
        feedMap.put(adapterSetId, feed);

        // Adapter ready
        logger.info("PortfolioDataAdapter ready");
    }

    public void setListener(ItemEventListener listener) {
        // Save the update listener
        this.listener = listener;
    }

    public boolean isSnapshotAvailable(String arg0)
            throws SubscriptionException {
        // We have always the snapshot available from our feed
        return true;
    }

    public void subscribe(String portfolioId, Object handle, boolean arg2)
            throws SubscriptionException, FailureException {

        assert(! subscriptions.containsKey(portfolioId));

        Portfolio portfolio = feed.getPortfolio(portfolioId);
        if (portfolio == null) {
            logger.error("No such portfolio: " + portfolioId);
            throw new SubscriptionException("No such portfolio: "
                    + portfolioId);
        }

        // Add the new item to the list of subscribed items
        subscriptions.put(portfolioId, handle);

        // Create a new listener for the portfolio
        MyPortfolioListener listener = new MyPortfolioListener(
                handle, portfolioId);
        // Set the listener on the feed
        portfolio.setListener(listener);

        logger.info(portfolioId + " subscribed");
    }

    public void unsubscribe(String portfolioId)
            throws SubscriptionException, FailureException {

        assert(subscriptions.containsKey(portfolioId));

        Portfolio portfolio = feed.getPortfolio(portfolioId);
        if (portfolio != null) {
            // Remove the listener from the feed to not receive new
            // updates
            portfolio.removeListener();
        }
        // Remove the handle from the list of subscribed items
        subscriptions.remove(portfolioId);

        logger.info(portfolioId + " unsubscribed");
    }

    private final boolean isSubscribed(Object handle) {
        // Just check if a given handle is in the map of subscribed items
        return subscriptions.contains(handle);
    }

    private void onUpdate(Object handle, String key, int qty) {
        // An update was received from the feed
        // Check for late calls
        if (isSubscribed(handle)) {
            // Create a new HashMap instance that will represent the update
            HashMap<String, String> update = new HashMap<String, String>();
            // We have to set the key
            update.put("key", key);
            // The UPDATE command
            update.put("command", "UPDATE");
            // And the new quantity value
            update.put("qty", String.valueOf(qty));

            // Pass everything to the kernel
            listener.smartUpdate(handle, update, false);
        }
    }

    private void onDelete(Object handle, String key) {
        // An update was received from the feed
        // Check for late calls
        if (isSubscribed(handle)) {
            // Create a new HashMap instance that will represent the update
            HashMap<String, String> update = new HashMap<String, String>();
            // We just need the key
            update.put("key", key);
            // And the DELETE command
            update.put("command", "DELETE");

            // Pass everything to the kernel
            listener.smartUpdate(handle, update, false);
        }
    }

    private void onAdd(Object handle, String key, int qty, boolean snapshot) {
        // An update for a new stock was received from the feed or the snapshot was read
        // Check for late calls
        if (isSubscribed(handle)) {
            // Create a new HashMap instance that will represent the update
            HashMap<String, String> update = new HashMap<String, String>();
            // We have to set the key
            update.put("key", key);
            // The ADD command
            update.put("command", "ADD");
            // And the initial quantity
            update.put("qty", String.valueOf(qty));

            // Pass everything to the kernel
            listener.smartUpdate(handle, update, snapshot);
        }

    }

    /**
     * Inner class that listens to a single Portfolio.
     */
    private class MyPortfolioListener implements PortfolioListener {

        // The handle representing the subscription
        private Object handle;
        // Id of the portfolio, used just for the log
        private String portfolioId;

        public MyPortfolioListener(Object handle, String portfolioId) {
            this.handle = handle;
            this.portfolioId = portfolioId;
        }

        public void update(String stock, int qty, int oldQty) {
            // An update was received from the feed
            if (qty <= 0) {
                // If qty is 0 or less we have to delete the "row"
                onDelete(this.handle, stock);
                logger.debug(this.portfolioId + ": deleted " + stock);

            } else if (oldQty == 0) {
                // If oldQty value is 0 then this is a new stock
                // in the portfolio so that we have to add a "row"
                onAdd(this.handle, stock, qty, false);
                logger.debug(this.portfolioId + ": added " + stock);

            } else {
                // A simple update
                onUpdate(this.handle, stock, qty);
                logger.debug(this.portfolioId + ": updated " + stock);
            }
        }

        public void onActualStatus(Map<String, Integer> currentStatus) {
            Set<String> keys = currentStatus.keySet();
            // Iterates through the Hash representing the actual status to send
            // the snapshot to
            // the kernel
            for (String key : keys) {
                onAdd(handle, key, currentStatus.get(key).intValue(), true);
            }
            
            // Notify the end of snapshot to the kernel
            listener.smartEndOfSnapshot(handle);

            logger.info(this.portfolioId + ": snapshot sent");
        }

        public void empty() {
            //tell the server to clean its status
            listener.smartClearSnapshot(handle);
            
            logger.info(this.portfolioId + ": snapshot cleared");
        }
    }

    public void subscribe(String portfolioId, boolean arg1)
            throws SubscriptionException, FailureException {
        // Never called on a SmartDataProvider
        assert(false);
    }

}