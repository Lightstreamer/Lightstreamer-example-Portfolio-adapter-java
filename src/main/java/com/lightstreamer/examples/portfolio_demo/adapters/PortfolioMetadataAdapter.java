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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lightstreamer.examples.portfolio_demo.feed_simulator.Portfolio;
import com.lightstreamer.examples.portfolio_demo.feed_simulator.PortfolioFeedSimulator;

import com.lightstreamer.adapter.common.InitException;
import com.lightstreamer.adapter.metadata.CreditsException;
import com.lightstreamer.adapter.metadata.LoginInfo;
import com.lightstreamer.adapter.metadata.MessageException;
import com.lightstreamer.adapter.metadata.MetadataAdapter;
import com.lightstreamer.adapter.metadata.SessionRequestHandler;
import com.lightstreamer.adapter.metadata.SubscriptionHandler;
import com.lightstreamer.adapter.metadata.SubscriptionInfo;

/**
 * Implements a simple Metadata Adapter suitable for managing client
 * requests to both the sample Quote Data Adapter and the sample Portfolio
 * Data Adapter.
 * It implements MetadataAdapter directly, which is enough for all
 * demo clients. In addition, it implements the userMessageAsync method,
 * in order to handle "sendMessage" requests from the Portfolio Demo
 * client. This allows the Portfolio Demo client to use "sendMessage"
 * in order to submit buy/sell orders to the Portfolio Feed Simulator.
 * The communication to the Portfolio Feed Simulator, through the
 * Portfolio Data Adapter, is handled here.
 */
public class PortfolioMetadataAdapter implements MetadataAdapter {

    /**
     * The associated feed to which buy and sell operations will be forwarded.
     * Created by this Metadata Adapter and shared with the Data Adapters.
     */
    private PortfolioFeedSimulator portfolioFeed;

    /**
     * Private logger; a specific "LS_demos_Logger.Portfolio" category
     * should be supplied by log4j configuration.
     */
    private Logger logger;

    private final ExecutorService messageProcessingPool = Executors.newCachedThreadPool();

    public PortfolioMetadataAdapter() {
    }

    @Override
    public void init(Map<String, String> params, File configDir) throws InitException {
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

        // Create the feed simulator instance, owned by this Metadata Adapter
        portfolioFeed = PortfolioFeedSimulator.create();

        logger.info("PortfolioMetadataAdapter ready");
    }

    @Override
    public void onDataAdapterAvailable(String dataAdapterName, Object dataAdapter) {
        if (dataAdapter instanceof PortfolioDataAdapter) {
            ((PortfolioDataAdapter) dataAdapter).setFeed(portfolioFeed);
            logger.info("Feed shared with PortfolioDataAdapter '" + dataAdapterName + "'");
        } else if (dataAdapter instanceof PortfolioDataAdapterJsonVersion) {
            ((PortfolioDataAdapterJsonVersion) dataAdapter).setFeed(portfolioFeed);
            logger.info("Feed shared with PortfolioDataAdapterJsonVersion '" + dataAdapterName + "'");
        }
        // Other Data Adapters (e.g. StockList) are ignored
    }

    @Override
    public CompletionStage<SessionRequestHandler> loginAsync(LoginInfo loginInfo) {
        //////////////////////////////////////////////////////////////////////////
        // WARNING: No authentication logic is implemented; any login is accepted.
        // This is suitable for demos only and MUST NOT be used in production.
        //////////////////////////////////////////////////////////////////////////
        return CompletableFuture.completedFuture(new MySessionRequestHandler());
    }

    @Override
    public void shutdown() {
        messageProcessingPool.shutdownNow();
    }

    /**
     * Inner class that handles session requests.
     */
    private class MySessionRequestHandler implements SessionRequestHandler {

        @Override
        public Double getAllowedMaxBandwidth() {
            return null;
        }

        @Override
        public CompletionStage<SubscriptionHandler> newSubscription(SubscriptionInfo info) {
            return CompletableFuture.completedFuture(new SubscriptionHandler(info));
        }

        @Override
        public CompletionStage<String> userMessageAsync(String message)
                throws CreditsException, MessageException {
            return notifyUserMessage(message);
        }

        /**
         * Triggered by a client "sendMessage" call.
         * The message encodes an order entry request by the client.
         * In this basic implementation, we accept messages from any user
         * to modify any portfolio.
         */
        private CompletableFuture<String> notifyUserMessage(String message)
                throws MessageException, CreditsException {

            //NOTE: since the order processing is potentially blocking (in a real scenario), we have 
            //configured a dedicated ExecutorService. Moreover, to provide backpressure to the Server
            //when the number of pending operations is too high, we have properly configured the
            //messages thread pool in the adapters.xml configuration file for this adapter.

            if (message == null) {
                logger.warn("Null message received");
                throw new MessageException("Null message received");
            }

            CompletableFuture<String> future = new CompletableFuture<>();
            messageProcessingPool.execute(() -> {
                try {
                    String[] pieces = message.split("\\|");
                    handlePortfolioMessage(pieces, message);
                    future.complete("SUBMITTED");
                } catch (CreditsException e) {
                    future.completeExceptionally(e);
                } catch (MessageException e) {
                    future.completeExceptionally(e);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
                assert (future.isDone());
            });

            return future;
        }



        private void handlePortfolioMessage(String[] operation, String message)
            throws MessageException, CreditsException {
            if (operation.length != 4) {
                logger.warn("Wrong message received: " + message);
                throw new MessageException("Wrong message received");
            }

            int qty;
            try {
                // Parse the received quantity to be an integer
                qty = Integer.parseInt(operation[3]);
            } catch (NumberFormatException e) {
                logger.warn("Wrong message received (quantity must be an integer number): "
                                + message);
                throw new MessageException("Wrong message received");
            }
            if (qty <= 0) {
                // Quantity can't be a negative number or 0; just ignore
                logger.warn("Wrong message received (quantity must be greater than 0): "
                                + message);
                return;
            }

            // get the needed portfolio
            Portfolio portfolio = portfolioFeed.getPortfolio(operation[1]);
            if (portfolio == null) {
                // since the feed creates a new portfolio if no one is available for
                // an id, this will never occur
                logger.error("No such portfolio: " + operation[1]);
                throw new CreditsException("Portfolio not available",
                        0, "Portfolio not available");
            }
            if (operation[0].equals("BUY")) {
                try {
                    // Call the buy operation on the selected portfolio
                    portfolio.buy(operation[2], qty);
                } catch (Exception e) {
                    throw new CreditsException(1, e.getMessage());
                }
            } else if (operation[0].equals("SELL")) {
                try {
                    // Call the sell operation on the selected portfolio
                    portfolio.sell(operation[2], qty);
                } catch (Exception e) {
                    throw new CreditsException(1, e.getMessage());
                }
            } else {
                throw new MessageException("Wrong operation specified");
            }
        }
    }

}