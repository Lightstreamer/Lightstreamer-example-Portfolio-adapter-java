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

import java.util.Map;

/**
 * Used to receive data from the simulated portfolio feed in an
 * asynchronous way.
 * Upon listener submission, a single call to onActualStatus is issued
 * in short time, then multiple calls to "update" can be issued.
 */
public interface PortfolioListener {

    /**
     * Called at first to send the actual portfolio contents.
     * The map associates stock ids with quantities.
     * Only stocks with positive quantities are included.
     */
    public void onActualStatus(Map<String, Integer> currentStatus);

    /**
     * Called on each new update on the state of the portfolio.
     * If oldQty is 0 means that the stock wasn't on the portfolio before;
     * if qty is 0 means that the stock was completely sold from the portfolio.
     * The overall status of the portfolio is also supplied to the caller.
     */
    public void update(String stock, int qty, int oldQty, Map<String, Integer> currentStatus);
    
    /**
     * May be called if the whole portfolio has to be cleaned at once.
     */
    public void empty();

}