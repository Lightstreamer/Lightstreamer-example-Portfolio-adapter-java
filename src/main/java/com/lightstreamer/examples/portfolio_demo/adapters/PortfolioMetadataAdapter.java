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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lightstreamer.examples.portfolio_demo.feed_simulator.Portfolio;
import com.lightstreamer.examples.portfolio_demo.feed_simulator.PortfolioFeedSimulator;

import com.lightstreamer.adapters.metadata.LiteralBasedProvider;
import com.lightstreamer.interfaces.metadata.CreditsException;
import com.lightstreamer.interfaces.metadata.ItemsException;
import com.lightstreamer.interfaces.metadata.MetadataProviderException;
import com.lightstreamer.interfaces.metadata.NotificationException;

/**
 * Implements a simple Metadata Adapter suitable for managing client
 * requests to both the sample Quote Data Adapter and the sample Portfolio
 * Data Adapter.
 * It inherits from the LiteralBasedProvider, which is enough for all
 * demo clients. In addition, it implements the NotifyUserMessage method,
 * in order to handle "sendMessage" requests from the Portfolio Demo
 * client. This allows the Portfolio Demo client to use "sendMessage"
 * in order to submit buy/sell orders to the Portfolio Feed Simulator.
 * The communication to the Portfolio Feed Simulator, through the
 * Portfolio Data Adapter, is handled here.
 */
public class PortfolioMetadataAdapter extends LiteralBasedProvider {

    /**
     * The associated feed to which buy and sell operations will be forwarded.
     */
    private volatile PortfolioFeedSimulator portfolioFeed;

    /**
     * Unique identification of the related Portfolio Data Adapter instance;
     * see feedMap on the PortfolioFeedSimulator.
     */
    private String adapterSetId;

    /**
     * Private logger; a specific "LS_demos_Logger.Portfolio" category
     * should be supplied by log4j configuration.
     */
    private Logger logger;

    public PortfolioMetadataAdapter() {
    }

    public void init(Map params, File configDir) throws MetadataProviderException {
        //Call super's init method to handle basic Metadata Adapter features
        super.init(params,configDir);

        /*
        String logConfig = (String) params.get("log_config");
        if (logConfig != null) {
            File logConfigFile = new File(configDir, logConfig);
            String logRefresh = (String) params.get("log_config_refresh_seconds");
            if (logRefresh != null) {
                DOMConfigurator.configureAndWatch(logConfigFile.getAbsolutePath(), Integer.parseInt(logRefresh) * 1000);
            } else {
                DOMConfigurator.configure(logConfigFile.getAbsolutePath());
            }
        }
        */
        logger = LogManager.getLogger("LS_demos_Logger.Portfolio");

        // Read the Adapter Set name, which is supplied by the Server as a parameter
        this.adapterSetId = (String) params.get("adapters_conf.id");

        /*
         * Note: the PortfolioFeedSimulator instance cannot be looked for here
         * to initialize the "portfolioFeed" variable, because the Portfolio
         * Data Adapter may not be loaded and initialized at this moment.
         * We need to wait until the first "sendMessage" occurrence;
         * then we can store the reference for later use.
         */

        logger.info("PortfolioMetadataAdapter ready");
    }

    private static ExecutorService messageProcessingPool = Executors.newCachedThreadPool();

    /**
     * Triggered by a client "sendMessage" call.
     * The message encodes an order entry request by the client.
     * In this basic implementation, the user is ignored,
     * we accept messages from any user to modify any portfolio;
     * session information is ignored too.
     */
    public CompletableFuture<String> notifyUserMessage(String user, String session, String message)
            throws NotificationException, CreditsException {

        //NOTE: since the order processing is potentially blocking (in a real scenario), we have 
        //configured a dedicated ExecutorService. Moreover, to provide backpressure to the Server
        //when the number of pending operations is too high, we have properly configured the
        //messages thread pool in the adapters.xml configuration file for this adapter.

        if (message == null) {
            logger.warn("Null message received");
            throw new NotificationException("Null message received");
        }

        CompletableFuture<String> future = new CompletableFuture<>();
        messageProcessingPool.execute(() -> {
            try {
                String[] pieces = message.split("\\|");
                this.loadPortolioFeed();
                this.handlePortfolioMessage(pieces,message);
                future.complete(null);
            } catch (CreditsException e) {
                future.completeExceptionally(e);
            } catch (NotificationException e) {
                future.completeExceptionally(e);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
            assert (future.isDone());
        });

        return future;
    }

    private void loadPortolioFeed() throws CreditsException {
        if (this.portfolioFeed == null) {
            try {
                // Get the PortfolioFeedSimulator instance to bind it with this
                // Metadata Adapter and call buy/sell operations on it
                this.portfolioFeed = PortfolioFeedSimulator.feedMap
                        .get(this.adapterSetId);
            } catch (Throwable t) {
                // It can happen if the Portfolio Data Adapter jar was not even
                // included in the Adapter Set lib directory (the Portfolio
                // Data Adapter could not be included in the Adapter Set as
                // well)
                logger.error("PortfolioDataAdapter class was not loaded: " + t);
                throw new CreditsException(0, "No portfolio feed available",
                        "No portfolio feed available");
            }

            if (this.portfolioFeed == null) {
                // The feed is not yet available on the static map, maybe the
                // Portfolio Data Adapter was not included in the Adapter Set
                logger.error("PortfolioFeedSimulator not found");
                throw new CreditsException(0, "No portfolio feed available",
                        "No portfolio feed available");
            }
        }
    }

    private void handlePortfolioMessage(String[] operation, String message)
        throws NotificationException, CreditsException {
        if (operation.length != 4) {
            logger.warn("Wrong message received: " + message);
            throw new NotificationException("Wrong message received");
        }

        int qty;
        try {
            // Parse the received quantity to be an integer
            qty = Integer.parseInt(operation[3]);
        } catch (NumberFormatException e) {
            logger.warn("Wrong message received (quantity must be an integer number): "
                            + message);
            throw new NotificationException("Wrong message received");
        }
        if (qty <= 0) {
            // Quantity can't be a negative number or 0; just ignore
            logger.warn("Wrong message received (quantity must be greater than 0): "
                            + message);
            return;
        }

        // get the needed portfolio
        Portfolio portfolio = this.portfolioFeed.getPortfolio(operation[1]);
        if (portfolio == null) {
            // since the feed creates a new portfolio if no one is available for
            // an id, this will never occur
            logger.error("No such portfolio: " + operation[1]);
            throw new CreditsException(0, "Portfolio not available",
                    "Portfolio not available");
        }
        try {
            if (operation[0].equals("BUY")) {
                // Call the buy operation on the selected portfolio
                portfolio.buy(operation[2], qty);
            } else if (operation[0].equals("SELL")) {
                // Call the sell operation on the selected portfolio
                portfolio.sell(operation[2], qty);
            }
        } catch (Exception e) {
            throw new CreditsException(1, e.getMessage());
        }
    }

}