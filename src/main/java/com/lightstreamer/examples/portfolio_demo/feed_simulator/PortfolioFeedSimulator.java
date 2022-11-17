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

package com.lightstreamer.examples.portfolio_demo.feed_simulator;


import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Simulates an external data feed that provides the contents of multiple
 * stock portfolios. Only 5 portfolios of names "portfolio1" to "portfolio5"
 * are currently accepted.
 * The managed portfolios are initialized with random content. Each portfolio
 * is initialized only when needed, then it is kept permanently.
 * The feed provides the clients with a single bean for each managed portfolio,
 * which can be used either to listen to the contents
 * or to notify buy/sell orders.
 */
public class PortfolioFeedSimulator {

    /**
     * Private logger; we lean on a creator supplied logger.
     */
    private final Logger logger;

    /**
     * A static map, to be used by the Metadata Adapter to find the feed
     * instance; this allows the Metadata Adapter to forward client order
     * requests to the feed.
     * Metadata and Data Adapter need to share the classloader.
     * The map allows multiple instances of this Data Adapter to be included
     * in different Adapter Sets and still share the classloader.
     * Each instance is identified with the name of the related Adapter Set.
     */
    public static final ConcurrentHashMap<String, PortfolioFeedSimulator> feedMap =
        new ConcurrentHashMap<String, PortfolioFeedSimulator>();

    public static synchronized PortfolioFeedSimulator start(String adapterSetId) {
        PortfolioFeedSimulator feed = PortfolioFeedSimulator.feedMap.get(adapterSetId);

        if (feed == null) {
            feed = new PortfolioFeedSimulator();

            // Put the feed instance on a static map to be read by the Metadata Adapter
            feedMap.put(adapterSetId, feed);
        }
        
        return feed;
    }

    /**
     * Map of portfolios.
     */
    private final ConcurrentHashMap<String,Portfolio> portfolios =
        new ConcurrentHashMap<String,Portfolio>();

    public PortfolioFeedSimulator() {
        logger = LogManager.getLogger("LS_demos_Logger.Portfolio");
    }

    public Portfolio getPortfolio(String portfolioId) {
        //Check the portfolioId to see if it's a valid one
        if (!checkPortfolio(portfolioId)) {
            logger.warn("Wrong portfolio ID: " + portfolioId);
            return null;
        }

        //Get the portfolio by id from the portfolios map
        Portfolio portfolio = portfolios.get(portfolioId);
        if (portfolio != null) {
            //If the portfolio is already available return it
            return portfolio;
        } else {
            //If the portfolio is not yet available we will create it
            //We have to synchronize to avoid conflict with other thread that
            //need to create the same portfolio
            synchronized (this) {
                //Check again if the portfolio is available in case another thread created it
                //while we were waiting for the lock
                portfolio = portfolios.get(portfolioId);
                if (portfolio == null) {
                    //If no such portfolio exists we create a new portfolio
                    portfolio = new Portfolio(portfolioId,logger);

                    //We need to generate an actual status of the portfolio to avoid starting with
                    //an empty one. Some random quantity will do the trick.
                    addRandomQuantities(portfolio);

                    //Add the new portfolio to the list of available portfolios
                    portfolios.put(portfolioId, portfolio);

                    logger.info(portfolioId + " created");
                }
                //Return the portfolio
                return portfolio;
            }
        }
    }

    /**
     * Creates a random initial status for the portfolio.
     */
    private static void addRandomQuantities(Portfolio portfolio) {
        Random generator = new Random();

        boolean[] used = new boolean[30];
        for (int i = 0; i < 30; i++) {
            used[i] = false;
        }

        //we start with 6-8 stocks
        int stocks = 6+generator.nextInt(3);

        for (int i = 1; i <= stocks; i++) {

            int stockN;
            do {
                //We need a stock number between 0 and 29
                stockN = generator.nextInt(30);
            } while (used[stockN]); //We need a stockId that's not been already used for this portfolio
            //Sign that we've used this stock number
            used[stockN] = true;

            //The stock id will be itemN where N is a number between 1 and 30
            String item = "item"+(stockN+1);

            //The initial quantity will be between 100 and 2500
            int qty = generator.nextInt(25)+1;
            qty *= 100;

            //Use the buy method to initialize the status
            try {
                portfolio.buy(item, qty);
            } catch (Exception e) {
                // should never happen here
            }
        }
    }

    /**
     * Performs a simple hard-coded portfolio id validation;
     * we accept portfolioN where N is a number between 1 and 10.
     */
    public static boolean checkPortfolio(String portfolio) {
        if (portfolio.indexOf("portfolio") != 0) {
            return false;
        }
        int stNum;
        try {
            stNum = Integer.parseInt(portfolio.substring(9));
        } catch (NumberFormatException e) {
            return false;
        }
        if (stNum <= 0 || stNum > 10) {
            return false;
        }

        return true;
    }

    /**
     * Performs a simple hard-coded stock id validation;
     * We accept itemN where N is a number between 1 and 30.
     * NOTE that also the Portfolio class is aware about
     * the way the stock id are composed.
     */
    public static boolean checkStock(String stock) {
        if (stock.indexOf("item") != 0) {
            return false;
        }
        int stNum;
        try {
            stNum = Integer.parseInt(stock.substring(4));
        } catch (NumberFormatException e) {
            return false;
        }
        if (stNum <= 0 || stNum > 30) {
            return false;
        }

        return true;
    }
}