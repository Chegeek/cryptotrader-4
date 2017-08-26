package com.after_sunrise.cryptocurrency.cryptotrader.framework.impl;

import com.after_sunrise.cryptocurrency.cryptotrader.core.ServiceFactory;
import com.after_sunrise.cryptocurrency.cryptotrader.framework.Context;
import com.after_sunrise.cryptocurrency.cryptotrader.framework.Instruction.CancelInstruction;
import com.after_sunrise.cryptocurrency.cryptotrader.framework.Instruction.CreateInstruction;
import com.after_sunrise.cryptocurrency.cryptotrader.framework.Order;
import com.after_sunrise.cryptocurrency.cryptotrader.framework.Service;
import com.after_sunrise.cryptocurrency.cryptotrader.framework.Trade;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Injector;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author takanori.takase
 * @version 0.0.1
 */
@Slf4j
public class ContextImpl implements Context {

    private final Map<String, Context> contexts;

    @Inject
    public ContextImpl(Injector injector) {

        contexts = injector.getInstance(ServiceFactory.class).loadMap(Context.class);

    }

    @Override
    public void close() throws Exception {

        Exception exception = null;

        for (Context context : contexts.values()) {

            try {

                context.close();

            } catch (Exception e) {

                if (exception == null) {
                    exception = new Exception("Failed to close context(s).");
                }

                exception.addSuppressed(e);

            }

        }

        if (exception != null) {
            throw exception;
        }

    }

    @Override
    public String get() {
        return Service.WILDCARD;
    }

    @VisibleForTesting
    <R> R forContext(Key key, Function<Context, R> function) {

        if (key == null) {
            return null;
        }

        Context context = contexts.get(key.getSite());

        if (context == null) {
            return null;
        }

        return function.apply(context);

    }

    @Override
    public BigDecimal getBestAskPrice(Key key) {
        return forContext(key, c -> c.getBestAskPrice(key));
    }

    @Override
    public BigDecimal getBestBidPrice(Key key) {
        return forContext(key, c -> c.getBestBidPrice(key));
    }

    @Override
    public BigDecimal getMidPrice(Key key) {
        return forContext(key, c -> c.getMidPrice(key));
    }

    @Override
    public BigDecimal getLastPrice(Key key) {
        return forContext(key, c -> c.getLastPrice(key));
    }

    @Override
    public List<Trade> listTrades(Key key, Instant fromTime) {
        return forContext(key, c -> c.listTrades(key, fromTime));
    }

    @Override
    public BigDecimal getInstrumentPosition(Key key) {
        return forContext(key, c -> c.getInstrumentPosition(key));
    }

    @Override
    public BigDecimal getFundingPosition(Key key) {
        return forContext(key, c -> c.getFundingPosition(key));
    }

    @Override
    public BigDecimal roundLotSize(Key key, BigDecimal value, RoundingMode mode) {
        return forContext(key, c -> c.roundLotSize(key, value, mode));
    }

    @Override
    public BigDecimal roundTickSize(Key key, BigDecimal value, RoundingMode mode) {
        return forContext(key, c -> c.roundTickSize(key, value, mode));
    }

    @Override
    public BigDecimal getCommissionRate(Key key) {
        return forContext(key, c -> c.getCommissionRate(key));
    }

    @Override
    public Boolean isMarginable(Key key) {
        return forContext(key, c -> c.isMarginable(key));
    }

    @Override
    public Order findOrder(Key key, String id) {
        return forContext(key, c -> c.findOrder(key, id));
    }

    @Override
    public List<Order> listActiveOrders(Key key) {
        return forContext(key, c -> c.listActiveOrders(key));
    }

    @Override
    public String createOrder(Key key, CreateInstruction instruction) {
        return forContext(key, c -> c.createOrder(key, instruction));
    }

    @Override
    public String cancelOrder(Key key, CancelInstruction instruction) {
        return forContext(key, c -> c.cancelOrder(key, instruction));
    }

}