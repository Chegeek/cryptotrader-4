package com.after_sunrise.cryptocurrency.cryptotrader.framework.impl;

import com.after_sunrise.cryptocurrency.cryptotrader.core.Composite;
import com.after_sunrise.cryptocurrency.cryptotrader.framework.Service;
import com.after_sunrise.cryptocurrency.cryptotrader.framework.Trade;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.configuration2.ImmutableConfiguration;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

import static java.lang.Long.MAX_VALUE;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Collections.*;

/**
 * @author takanori.takase
 * @version 0.0.1
 */
public abstract class AbstractService implements Service {

    protected static final String WILDCARD = "*";

    protected static final int SCALE = 10;

    protected static final BigDecimal EPSILON = ONE.movePointLeft(SCALE);

    protected static final BigDecimal HALF = new BigDecimal("0.5");

    protected static final NavigableMap<Long, BigDecimal> DEGREES;

    static {
        NavigableMap<Long, BigDecimal> degrees = new TreeMap<>();
        degrees.put(1L, new BigDecimal("12.7062"));
        degrees.put(2L, new BigDecimal("4.3027"));
        degrees.put(3L, new BigDecimal("3.1824"));
        degrees.put(4L, new BigDecimal("2.7764"));
        degrees.put(5L, new BigDecimal("2.5706"));
        degrees.put(6L, new BigDecimal("2.4469"));
        degrees.put(7L, new BigDecimal("2.3646"));
        degrees.put(8L, new BigDecimal("2.3060"));
        degrees.put(9L, new BigDecimal("2.2622"));
        degrees.put(10L, new BigDecimal("2.2281"));
        degrees.put(15L, new BigDecimal("2.1314"));
        degrees.put(20L, new BigDecimal("2.0860"));
        degrees.put(30L, new BigDecimal("2.0423"));
        degrees.put(45L, new BigDecimal("2.0141"));
        degrees.put(60L, new BigDecimal("2.0003"));
        degrees.put(90L, new BigDecimal("1.9867"));
        degrees.put(120L, new BigDecimal("1.9799"));
        degrees.put(180L, new BigDecimal("1.9732"));
        degrees.put(360L, new BigDecimal("1.9666"));
        degrees.put(720L, new BigDecimal("1.9633"));
        degrees.put(1440L, new BigDecimal("1.9616"));
        degrees.put(2880L, new BigDecimal("1.9608"));
        degrees.put(MAX_VALUE, new BigDecimal("1.9600"));
        DEGREES = Collections.unmodifiableNavigableMap(degrees);
    }

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final String prefix = getClass().getName() + ".";

    private ImmutableConfiguration configuration;

    @Inject
    @VisibleForTesting
    public void setConfiguration(ImmutableConfiguration configuration) {
        this.configuration = configuration;
    }

    protected String getStringProperty(String key, String defaultValue) {

        String value;

        try {
            value = configuration.getString(prefix + key, defaultValue);
        } catch (RuntimeException e) {
            value = defaultValue;
        }

        return value;

    }

    protected int getIntProperty(String key, int defaultValue) {

        int value;

        try {
            value = configuration.getInt(prefix + key, defaultValue);
        } catch (RuntimeException e) {
            value = defaultValue;
        }

        return value;

    }

    protected long getLongProperty(String key, long defaultValue) {

        long value;

        try {
            value = configuration.getLong(prefix + key, defaultValue);
        } catch (RuntimeException e) {
            value = defaultValue;
        }

        return value;

    }

    protected BigDecimal getDecimalProperty(String key, BigDecimal defaultValue) {

        BigDecimal value;

        try {
            value = configuration.getBigDecimal(prefix + key, defaultValue);
        } catch (RuntimeException e) {
            value = defaultValue;
        }

        return value;

    }

    protected <V> V trim(V first, V second) {
        return first != null ? first : second;
    }

    protected BigDecimal trimToZero(BigDecimal value) {
        return trim(value, ZERO);
    }

    protected <T> List<T> trimToEmpty(List<T> values) {
        return values != null ? values : emptyList();
    }

    protected <T> Set<T> trimToEmpty(Set<T> values) {
        return values != null ? values : emptySet();
    }

    protected <K, V> Map<K, V> trimToEmpty(Map<K, V> values) {
        return values != null ? values : emptyMap();
    }

    @VisibleForTesting
    public BigDecimal calculateComposite(List<Composite> products, BiFunction<String, String, BigDecimal> f) {

        if (CollectionUtils.isEmpty(products)) {
            return null;
        }

        BigDecimal[] results = {null};

        for (Composite composite : products) {

            if (composite == null || composite.getSite() == null || composite.getSite().length() < 2) {
                return null;
            }

            char operation = composite.getSite().charAt(0);

            BinaryOperator<BigDecimal> operator = null;

            if (operation == '+') {
                operator = BigDecimal::add;
            }

            if (operation == '-') {
                operator = BigDecimal::subtract;
            }

            if (operation == '*') {
                operator = BigDecimal::multiply;
            }

            if (operation == '/') {
                operator = (o1, o2) -> o1.divide(o2, SCALE, HALF_UP);
            }

            if (operation != '@' && operator == null) {
                return null; // Average
            }

            String site = composite.getSite().substring(1);

            String product = composite.getInstrument();

            BigDecimal value = f.apply(site, product);

            if (value == null || value.signum() == 0) {
                return null;
            }

            if (operator == null) {

                results = ArrayUtils.add(results, value);

                continue;

            }

            BigDecimal current = trim(results[0], ONE);

            results[0] = operator.apply(current, value);

        }

        long count = Stream.of(results).filter(Objects::nonNull).count();

        BigDecimal total = Stream.of(results).filter(Objects::nonNull)
                .reduce(BigDecimal::add).orElse(ZERO);

        return total.divide(BigDecimal.valueOf(count), SCALE, HALF_UP);

    }

    @VisibleForTesting
    public NavigableMap<Instant, BigDecimal> collapsePrices(List<Trade> values,
                                                            Duration interval, Instant from, Instant to, boolean sum) {

        NavigableMap<Instant, BigDecimal[]> collapsed = new TreeMap<>();

        for (long i = from.toEpochMilli(); i < to.toEpochMilli(); i += interval.toMillis()) {

            Instant instant = Instant.ofEpochMilli(i);

            collapsed.put(instant, new BigDecimal[2]); // [size, notional]

        }

        trimToEmpty(values).stream()
                .filter(Objects::nonNull)
                .filter(t -> t.getTimestamp() != null)
                .filter(t -> t.getTimestamp().isAfter(from.minus(interval)))
                .filter(t -> t.getTimestamp().isBefore(to))
                .filter(t -> t.getPrice() != null)
                .filter(t -> t.getSize() != null)
                .sorted(Comparator.comparing(Trade::getTimestamp))
                .forEach(t -> {

                    Instant timestamp = t.getTimestamp();

                    Map.Entry<Instant, BigDecimal[]> entry = collapsed.ceilingEntry(timestamp);

                    if (entry == null) {
                        return;
                    }

                    BigDecimal quantity = t.getSize();
                    BigDecimal notional = t.getSize().multiply(t.getPrice());
                    BigDecimal[] elements = entry.getValue();

                    if (sum) {
                        elements[0] = elements[0] == null ? quantity : quantity.add(elements[0]);
                        elements[1] = elements[1] == null ? notional : notional.add(elements[1]);
                    } else {
                        elements[0] = quantity;
                        elements[1] = notional;
                    }

                });

        NavigableMap<Instant, BigDecimal> prices = new TreeMap<>();

        BigDecimal previous = null;

        for (Map.Entry<Instant, BigDecimal[]> entry : collapsed.entrySet()) {

            BigDecimal[] elements = entry.getValue();

            BigDecimal current = previous;

            if (elements[0] != null && elements[0].signum() != 0) {
                current = elements[1].divide(elements[0], SCALE, HALF_UP);
            }

            prices.put(entry.getKey(), current);

            previous = current;

        }

        return prices;

    }

    @VisibleForTesting
    public NavigableMap<Instant, BigDecimal> calculateReturns(SortedMap<Instant, BigDecimal> prices) {

        if (prices == null) {
            return Collections.emptyNavigableMap();
        }

        NavigableMap<Instant, BigDecimal> returns = new TreeMap<>();

        List<Map.Entry<Instant, BigDecimal>> entries = new ArrayList<>(prices.entrySet());

        for (int i = 1; i < entries.size(); i++) {

            BigDecimal p0 = entries.get(i - 1).getValue();

            BigDecimal p1 = entries.get(i).getValue();

            BigDecimal value = null;

            if (p0 != null && p1 != null) {

                double diff = Math.log(p1.doubleValue() / p0.doubleValue());

                if (Double.isFinite(diff)) {

                    value = BigDecimal.valueOf(diff);

                    value = value.setScale(SCALE, HALF_UP);

                }

            }

            returns.put(entries.get(i).getKey(), value);

        }

        return returns;

    }

}
