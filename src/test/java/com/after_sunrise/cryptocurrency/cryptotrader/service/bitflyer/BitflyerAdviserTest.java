package com.after_sunrise.cryptocurrency.cryptotrader.service.bitflyer;

import com.after_sunrise.cryptocurrency.cryptotrader.TestModule;
import com.after_sunrise.cryptocurrency.cryptotrader.framework.Context;
import com.after_sunrise.cryptocurrency.cryptotrader.framework.Context.Key;
import com.after_sunrise.cryptocurrency.cryptotrader.framework.Request;
import com.after_sunrise.cryptocurrency.cryptotrader.service.bitflyer.BitflyerService.ProductType;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;

import static com.after_sunrise.cryptocurrency.cryptotrader.service.bitflyer.BitflyerService.ProductType.*;
import static java.math.BigDecimal.ZERO;
import static java.math.BigDecimal.valueOf;
import static org.apache.commons.lang3.math.NumberUtils.INTEGER_ZERO;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * @author takanori.takase
 * @version 0.0.1
 */
public class BitflyerAdviserTest {

    private BitflyerAdviser target;

    private TestModule module;

    private Context context;

    @BeforeMethod
    public void setUp() throws Exception {

        module = new TestModule();

        context = module.getMock(Context.class);

        target = spy(new BitflyerAdviser());

        when(context.roundLotSize(any(), any(), any())).thenAnswer(i -> {

            BigDecimal value = i.getArgumentAt(1, BigDecimal.class);

            RoundingMode mode = i.getArgumentAt(2, RoundingMode.class);

            if (value == null || mode == null) {
                return null;
            }

            BigDecimal unit = new BigDecimal("0.5");

            BigDecimal units = value.divide(unit, INTEGER_ZERO, mode);

            return units.multiply(unit);

        });

    }

    @Test
    public void testGet() {
        assertEquals(target.get(), BitflyerService.ID);
    }

    @Test
    public void testCalculateSwapRate() {

        ZoneId zone = ZoneId.of("Asia/Tokyo");
        LocalDateTime zdt = LocalDateTime.of(2017, 8, 25, 8, 0);
        Instant now = ZonedDateTime.of(zdt, zone).toInstant();

        Request.RequestBuilder b = Request.builder().currentTime(now);

        // SD (No Swap)
        LocalDateTime exp = LocalDateTime.of(2017, 8, 25, 16, 0);
        when(context.getExpiry(Key.from(b.build()))).thenReturn(ZonedDateTime.of(exp, zone));
        assertEquals(target.calculateSwapRate(context, b.build()), ZERO);

        // Past (No Swap)
        exp = LocalDateTime.of(2017, 8, 24, 16, 0);
        when(context.getExpiry(Key.from(b.build()))).thenReturn(ZonedDateTime.of(exp, zone));
        assertEquals(target.calculateSwapRate(context, b.build()), ZERO);

        // S+1
        exp = LocalDateTime.of(2017, 8, 26, 16, 0);
        when(context.getExpiry(Key.from(b.build()))).thenReturn(ZonedDateTime.of(exp, zone));
        assertEquals(target.calculateSwapRate(context, b.build()), new BigDecimal("0.0004000000"));

        // S+7
        exp = LocalDateTime.of(2017, 9, 1, 16, 0);
        when(context.getExpiry(Key.from(b.build()))).thenReturn(ZonedDateTime.of(exp, zone));
        assertEquals(target.calculateSwapRate(context, b.build()), new BigDecimal("0.0028033623"));

        // S+14
        exp = LocalDateTime.of(2017, 9, 8, 16, 0);
        when(context.getExpiry(Key.from(b.build()))).thenReturn(ZonedDateTime.of(exp, zone));
        assertEquals(target.calculateSwapRate(context, b.build()), new BigDecimal("0.0056145834"));

        // Null current time
        assertEquals(target.calculateSwapRate(context, Request.builder().build()), ZERO);

        // Null Expiry
        when(context.getExpiry(Key.from(b.build()))).thenReturn(null);
        assertEquals(target.calculateSwapRate(context, b.build()), ZERO);

    }

    @Test
    public void testAdjustBasis() {

        Request request = Request.builder().build();

        doReturn(new BigDecimal("0.0005")).when(target).calculateSwapRate(context, request);

        BigDecimal result = target.adjustBasis(context, request, new BigDecimal("0.002"));

        assertEquals(result, new BigDecimal("0.0025"));

        assertNull(target.adjustBasis(context, request, null));

    }

    @Test
    public void testGetUnderlyingKey() {

        Request.RequestBuilder builder = Request.builder().site("bf").currentTime(Instant.now());

        // Null instrument
        Key key = target.getUnderlyingKey(builder.build());
        assertNull(key);

        // Cash Instrument
        key = target.getUnderlyingKey(builder.instrument("BTC_JPY").build());
        assertNull(key);

        // Derivatives Instrument
        key = target.getUnderlyingKey(builder.instrument("BTCJPY_MAT1WK").build());
        assertEquals(key.getSite(), builder.build().getSite());
        assertEquals(key.getInstrument(), "BTC_JPY");
        assertEquals(key.getTimestamp(), builder.build().getCurrentTime());

    }

    @Test
    public void testAdjustBuyBoundaryPrice() {

        Request request = Request.builder().site("s").instrument("i")
                .tradingSpread(new BigDecimal("0.0008")).build();
        Key key = Key.from(request);

        doReturn(key).when(target).getUnderlyingKey(request);
        doReturn(new BigDecimal("0.0004")).when(target).calculateSwapRate(context, request);
        when(context.getCommissionRate(key)).thenReturn(new BigDecimal("0.0012"));
        when(context.getBestBidPrice(key)).thenReturn(new BigDecimal("5000"));

        // Passive than market
        BigDecimal result = target.adjustBuyBoundaryPrice(context, request, new BigDecimal("6000"));
        assertEquals(result, new BigDecimal("4988.0000"));

        // Aggressive than Market
        result = target.adjustBuyBoundaryPrice(context, request, new BigDecimal("4000"));
        assertEquals(result, new BigDecimal("4000"));

        // Null Market
        result = target.adjustBuyBoundaryPrice(context, request, null);
        assertEquals(result, null);

    }

    @Test
    public void testAdjustSellBoundaryPrice() {

        Request request = Request.builder().site("s").instrument("i")
                .tradingSpread(new BigDecimal("0.0008")).build();
        Key key = Key.from(request);

        doReturn(key).when(target).getUnderlyingKey(request);
        doReturn(new BigDecimal("0.0004")).when(target).calculateSwapRate(context, request);
        when(context.getCommissionRate(key)).thenReturn(new BigDecimal("0.0012"));
        when(context.getBestAskPrice(key)).thenReturn(new BigDecimal("5000"));

        // Passive than market
        BigDecimal result = target.adjustSellBoundaryPrice(context, request, new BigDecimal("4000"));
        assertEquals(result, new BigDecimal("5012.0000"));

        // Aggressive than Market
        result = target.adjustSellBoundaryPrice(context, request, new BigDecimal("6000"));
        assertEquals(result, new BigDecimal("6000"));

        // Null Market
        result = target.adjustSellBoundaryPrice(context, request, null);
        assertEquals(result, null);

    }

    @Test
    public void testGetHedgeSize() {

        Request request = Request.builder().instrument(FX_BTC_JPY.name()).build();
        Key k0 = Key.from(request);
        Key k1 = Key.build(k0).instrument(BTCJPY_MAT1WK.name()).build();
        Key k2 = Key.build(k0).instrument(BTCJPY_MAT2WK.name()).build();
        Set<ProductType> products = EnumSet.of(BTCJPY_MAT1WK, BTCJPY_MAT2WK);

        //
        // Zero hedged
        //
        when(context.getInstrumentPosition(k0)).thenReturn(valueOf(0));

        when(context.getInstrumentPosition(k1)).thenReturn(valueOf(0));
        when(context.getInstrumentPosition(k2)).thenReturn(valueOf(0));
        assertEquals(target.getHedgeSize(context, request, products), valueOf(0));

        when(context.getInstrumentPosition(k1)).thenReturn(valueOf(-1));
        when(context.getInstrumentPosition(k2)).thenReturn(valueOf(+1));
        assertEquals(target.getHedgeSize(context, request, products), valueOf(0));

        when(context.getInstrumentPosition(k1)).thenReturn(valueOf(-2));
        when(context.getInstrumentPosition(k2)).thenReturn(valueOf(+1));
        assertEquals(target.getHedgeSize(context, request, products), valueOf(+1));

        when(context.getInstrumentPosition(k1)).thenReturn(valueOf(-1));
        when(context.getInstrumentPosition(k2)).thenReturn(valueOf(+2));
        assertEquals(target.getHedgeSize(context, request, products), valueOf(-1));

        //
        // Long hedged
        //
        when(context.getInstrumentPosition(k0)).thenReturn(valueOf(+1));

        when(context.getInstrumentPosition(k1)).thenReturn(valueOf(0));
        when(context.getInstrumentPosition(k2)).thenReturn(valueOf(0));
        assertEquals(target.getHedgeSize(context, request, products), valueOf(-1));

        when(context.getInstrumentPosition(k1)).thenReturn(valueOf(-1));
        when(context.getInstrumentPosition(k2)).thenReturn(valueOf(+1));
        assertEquals(target.getHedgeSize(context, request, products), valueOf(-1));

        when(context.getInstrumentPosition(k1)).thenReturn(valueOf(-2));
        when(context.getInstrumentPosition(k2)).thenReturn(valueOf(+1));
        assertEquals(target.getHedgeSize(context, request, products), valueOf(0));

        when(context.getInstrumentPosition(k1)).thenReturn(valueOf(-1));
        when(context.getInstrumentPosition(k2)).thenReturn(valueOf(+2));
        assertEquals(target.getHedgeSize(context, request, products), valueOf(-2));

        //
        // Short hedged
        //
        when(context.getInstrumentPosition(k0)).thenReturn(valueOf(-1));

        when(context.getInstrumentPosition(k1)).thenReturn(valueOf(0));
        when(context.getInstrumentPosition(k2)).thenReturn(valueOf(0));
        assertEquals(target.getHedgeSize(context, request, products), valueOf(+1));

        when(context.getInstrumentPosition(k1)).thenReturn(valueOf(-1));
        when(context.getInstrumentPosition(k2)).thenReturn(valueOf(+1));
        assertEquals(target.getHedgeSize(context, request, products), valueOf(+1));

        when(context.getInstrumentPosition(k1)).thenReturn(valueOf(-2));
        when(context.getInstrumentPosition(k2)).thenReturn(valueOf(+1));
        assertEquals(target.getHedgeSize(context, request, products), valueOf(2));

        when(context.getInstrumentPosition(k1)).thenReturn(valueOf(-1));
        when(context.getInstrumentPosition(k2)).thenReturn(valueOf(+2));
        assertEquals(target.getHedgeSize(context, request, products), valueOf(0));

        //
        // Null hedged
        //
        when(context.getInstrumentPosition(k0)).thenReturn(null);
        when(context.getInstrumentPosition(k1)).thenReturn(valueOf(0));
        when(context.getInstrumentPosition(k2)).thenReturn(valueOf(0));
        assertEquals(target.getHedgeSize(context, request, products), null);

        when(context.getInstrumentPosition(k0)).thenReturn(valueOf(0));
        when(context.getInstrumentPosition(k1)).thenReturn(null);
        when(context.getInstrumentPosition(k2)).thenReturn(valueOf(0));
        assertEquals(target.getHedgeSize(context, request, products), null);

        when(context.getInstrumentPosition(k0)).thenReturn(valueOf(0));
        when(context.getInstrumentPosition(k1)).thenReturn(valueOf(0));
        when(context.getInstrumentPosition(k2)).thenReturn(null);
        assertEquals(target.getHedgeSize(context, request, products), null);

    }

    @Test
    public void testAdjustBuyLimitSize() {

        Request request1 = Request.builder().instrument(FX_BTC_JPY.name()).build();
        Request request2 = Request.builder().instrument(BTC_JPY.name()).build();
        BigDecimal size = new BigDecimal("123.45");

        doReturn(valueOf(0)).when(target).getHedgeSize(any(), any(), any());
        assertEquals(target.adjustBuyLimitSize(context, request1, size), new BigDecimal("0.0"));
        assertEquals(target.adjustBuyLimitSize(context, request2, size), size);

        doReturn(valueOf(+1)).when(target).getHedgeSize(any(), any(), any());
        assertEquals(target.adjustBuyLimitSize(context, request1, size), new BigDecimal("1.0"));
        assertEquals(target.adjustBuyLimitSize(context, request2, size), size);

        doReturn(valueOf(-1)).when(target).getHedgeSize(any(), any(), any());
        assertEquals(target.adjustBuyLimitSize(context, request1, size), new BigDecimal("0.0"));
        assertEquals(target.adjustBuyLimitSize(context, request2, size), size);

        doReturn(null).when(target).getHedgeSize(any(), any(), any());
        assertEquals(target.adjustBuyLimitSize(context, request1, size), ZERO);
        assertEquals(target.adjustBuyLimitSize(context, request2, size), size);

    }

    @Test
    public void testAdjustSellLimitSize() {

        Request request1 = Request.builder().instrument(FX_BTC_JPY.name()).build();
        Request request2 = Request.builder().instrument(BTC_JPY.name()).build();
        BigDecimal size = new BigDecimal("123.45");

        doReturn(valueOf(0)).when(target).getHedgeSize(any(), any(), any());
        assertEquals(target.adjustSellLimitSize(context, request1, size), new BigDecimal("0.0"));
        assertEquals(target.adjustSellLimitSize(context, request2, size), size);

        doReturn(valueOf(-1)).when(target).getHedgeSize(any(), any(), any());
        assertEquals(target.adjustSellLimitSize(context, request1, size), new BigDecimal("1.0"));
        assertEquals(target.adjustSellLimitSize(context, request2, size), size);

        doReturn(valueOf(+1)).when(target).getHedgeSize(any(), any(), any());
        assertEquals(target.adjustSellLimitSize(context, request1, size), new BigDecimal("0.0"));
        assertEquals(target.adjustSellLimitSize(context, request2, size), size);

        doReturn(null).when(target).getHedgeSize(any(), any(), any());
        assertEquals(target.adjustSellLimitSize(context, request1, size), ZERO);
        assertEquals(target.adjustSellLimitSize(context, request2, size), size);

    }

}
