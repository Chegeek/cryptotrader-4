package com.after_sunrise.cryptocurrency.cryptotrader.service.poloniex;

import com.after_sunrise.cryptocurrency.cryptotrader.framework.Context;
import com.after_sunrise.cryptocurrency.cryptotrader.framework.Request;
import com.after_sunrise.cryptocurrency.cryptotrader.service.estimator.Estimators;
import com.after_sunrise.cryptocurrency.cryptotrader.service.estimator.LastEstimator;
import com.after_sunrise.cryptocurrency.cryptotrader.service.estimator.MidEstimator;
import com.after_sunrise.cryptocurrency.cryptotrader.service.estimator.VwapEstimator;

/**
 * @author takanori.takase
 * @version 0.0.1
 */
public interface PoloniexService {

    String ID = "poloniex";

    class PoloniexLastEstimator extends LastEstimator {
        @Override
        public Context.Key getKey(Request request) {
            return Estimators.getKey(request, ID);
        }
    }

    class PoloniexMidEstimator extends MidEstimator {
        @Override
        public Context.Key getKey(Request request) {
            return Estimators.getKey(request, ID);
        }
    }

    class PoloniexVwapEstimator extends VwapEstimator {
        @Override
        public Context.Key getKey(Request request) {
            return Estimators.getKey(request, ID);
        }
    }

}