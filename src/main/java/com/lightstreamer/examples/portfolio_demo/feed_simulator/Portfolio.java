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


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.Logger;

/**
 * Manages the contents for a single portfolio.
 * The contents can be changed through "buy" and "sell" methods
 * and can be inquired through a listener; upon setting of a new listener,
 * the current contents are notified, followed by the notifications
 * of subsequent content changes.
 * To make it simple, a single listener is allowed at each time.
 * All methods are synchronized, but none can be blocking. The calls
 * to the listener are enqueued and send from a local thread; they may
 * occur just after "removeListener" has been issued.
 */
public class Portfolio {

    /**
     * Private logger; we lean on a creator supplied logger.
     */
    private Logger logger;

    /**
     * Single listener for the contents.
     */
    private final List<PortfolioListener> listeners;

    private final String id;

    /**
     * Used to enqueue the calls to the listener.
     */
    private final ExecutorService executor;

    /**
     * The portfolio contents; associates stock ids with quantities;
     * only stocks with positive quantities are included.
     */
    private final HashMap<String,Integer> quantities = new HashMap<String,Integer>();

    public Portfolio(String id, Logger logger) {
        this.id = id;
        this.logger = logger;
        // create the executor for this instance;
        // the SingleThreadExecutor ensures a FIFO behaviour
        listeners = new ArrayList<>();
        executor = Executors.newSingleThreadExecutor();
    }

    public synchronized void buy(String stock, int qty) throws Exception {
        if (qty <= 0) {
            //We can't buy 0 or less...
            logger.warn("Cannot buy " + qty + " " + stock + " for " + this.id + " use an integer greater than 0");
            throw new Exception("Cannot buy " + qty + " " + stock + " for " + this.id + " use an integer greater than 0");
        }

        if (!PortfolioFeedSimulator.checkStock(stock)) {
            //this stock does not exist
            logger.warn("Not valid stock to buy: " + stock);
            throw new Exception("Not valid stock to buy: " + stock);
        }

        logger.debug("Buying " + qty + " " + stock + " for " + this.id);
        //Pass the quantity to add to the changeQty method
        this.changeQty(stock,qty);
    }

    public synchronized void sell(String stock, int qty) throws Exception {
        if (qty <= 0) {
            //We can't sell 0 or less...
            logger.warn("Cannot sell " + qty + " " + stock + " for " + this.id + " use an integer greater than 0");
            throw new Exception("Cannot sell " + qty + " " + stock + " for " + this.id + " use an integer greater than 0");
        }

        if (!PortfolioFeedSimulator.checkStock(stock)) {
            //this stock does not exist
            logger.warn("Not valid stock to sell: " + stock);
            throw new Exception("Not valid stock to sell: " + stock);
        }

        logger.debug("Selling " + qty + " " + stock + " for " + this.id);
        //Change the quantity sing and pass it to the changeQty method
        this.changeQty(stock,-qty);
    }

    private synchronized void changeQty(String stock, int qty) {
        //Get the old quantity for the stock
        Integer oldQty = quantities.get(stock);
        int newQty;
        if (oldQty == null) {
            //If oldQty is null it means that we have not that stock on our portfolio
            if (qty <= 0) {
                //We can't sell something we don't have, warn and return.
                logger.warn(this.id+"|No stock to sell: " + stock);
                return;
            }
            //Set oldQty to 0 to let the listener know that we previously didn't have such stock
            oldQty = 0;
            //The new quantity is equal to the bought value
            newQty = qty;

        } else {
            assert(oldQty > 0);
            //The new quantity will be the value of the old quantity plus the qty value.
            //If qty is a negative number than we are selling, in the other case we're buying
            newQty = oldQty + qty;

            // overflow check; just in case
            if (qty > 0 && newQty <= qty) {
                newQty = oldQty;
                logger.warn(this.id+"|Quantity overflow; order ignored: " + stock);
                return;
            }
        }

        if (newQty < 0) {
            //We sold more than we had
            logger.warn(this.id+"|Not enough stock to sell: " + stock);
            //We interpret this as "sell everything"
            newQty = 0;
        }

        if (newQty == 0) {
            //If we sold everything we remove the stock from the internal structure
            quantities.remove(stock);
        } else {
            //Save the actual quantity in internal structure
            quantities.put(stock, newQty);
        }

        for (PortfolioListener listener : listeners) {
            //copy the actual listener to a constant that will be used inside the inner class
            final PortfolioListener localListener = listener;
            //copy the values to constant to be used inside the inner class
            final int newVal = newQty;
            final int oldVal = oldQty.intValue();
            final String stockId = stock;

            //Clone the actual status of the portfolio
            final HashMap<String,Integer> currentStatus =
                (HashMap<String,Integer>) quantities.clone();

            //If we have a listener create a new Runnable to be used as a task to pass the
            //new update to the listener
            Runnable updateTask = new Runnable() {
                public void run() {
                    // call the update on the listener;
                    // in case the listener has just been detached,
                    // the listener should detect the case
                    localListener.update(stockId, newVal, oldVal, currentStatus);
                }
            };

            //We add the task on the executor to pass to the listener the actual status
            executor.execute(updateTask);
        }
    }

    public synchronized void addListener(PortfolioListener newListener) {
        if (newListener == null) {
            //we don't accept a null parameter. to delete the actual listener
            //the removeListener method must be used
            return;
        }
        //Set the listener
        this.listeners.add(newListener);

        logger.debug("Listener set on " + this.id);

        //copy the actual listener to a final variable that will be used inside the inner class
        final PortfolioListener localListener = newListener;

        //Clone the actual status of the portfolio
        final HashMap<String,Integer> currentStatus =
            (HashMap<String,Integer>) quantities.clone();

        //Create a new Runnable to be used as a task to pass the actual status to the listener
        Runnable statusTask = new Runnable() {
            public void run() {
                // call the onActualStatus on the listener;
                // in case the listener has just been detached,
                // the listener should detect the case
                localListener.onActualStatus(currentStatus);
            }
        };

        //We add the task on the executor to pass to the listener the actual status
        executor.execute(statusTask);
    }

    public synchronized void removeListener(PortfolioListener listener) {
        //remove the listener
        this.listeners.remove(listener);
    }

    // never called in the demo, just showing the feature
    public synchronized void empty() {
        logger.debug("Cleaning status " + this.id);
        
        //remove all the quantities so that the portfolio will result empty
        quantities.clear();
        
        for (PortfolioListener listener : listeners) {
            //copy the actual listener to a constant that will be used inside the inner class
            final PortfolioListener localListener = listener;
        
            Runnable clearTask = new Runnable() {
                public void run() {
                    localListener.empty();
                }
            };

            //We add the task on the executor to pass to the listener the actual status
            executor.execute(clearTask);
        }
    }
}