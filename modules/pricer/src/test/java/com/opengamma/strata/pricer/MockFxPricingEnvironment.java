/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer;

import java.time.LocalDate;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.FxIndex;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.pricer.sensitivity.CurveParameterSensitivity;
import com.opengamma.strata.pricer.sensitivity.PointSensitivities;
import com.opengamma.strata.pricer.sensitivity.PointSensitivityBuilder;

/**
 * Mock class for {@link PricingEnvironment}.
 * Mockito does not support default methods.
 */
public class MockFxPricingEnvironment implements PricingEnvironment {

  public static final double RATE = 1.6d;

  @Override
  public LocalDate getValuationDate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T rawData(Class<T> cls) {
    throw new UnsupportedOperationException();
  }

  @Override
  public LocalDateDoubleTimeSeries timeSeries(Index index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public double fxRate(Currency baseCurrency, Currency counterCurrency) {
    return (baseCurrency.equals(counterCurrency) ? 1 : RATE);
  }

  @Override
  public double discountFactor(Currency currency, LocalDate date) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PointSensitivityBuilder discountFactorZeroRateSensitivity(Currency currency, LocalDate date) {
    throw new UnsupportedOperationException();
  }

  @Override
  public double fxIndexRate(FxIndex index, Currency baseCurrency, LocalDate fixingDate) {
    throw new UnsupportedOperationException();
  }

  @Override
  public double iborIndexRate(IborIndex index, LocalDate fixingDate) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PointSensitivityBuilder iborIndexRateSensitivity(IborIndex index, LocalDate fixingDate) {
    throw new UnsupportedOperationException();
  }

  @Override
  public double overnightIndexRate(OvernightIndex index, LocalDate fixingDate) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PointSensitivityBuilder overnightIndexRateSensitivity(OvernightIndex index, LocalDate fixingDate) {
    throw new UnsupportedOperationException();
  }

  @Override
  public double overnightIndexRatePeriod(OvernightIndex index, LocalDate startDate, LocalDate endDate) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PointSensitivityBuilder overnightIndexRatePeriodSensitivity(OvernightIndex index, LocalDate startDate, LocalDate endDate) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CurveParameterSensitivity parameterSensitivity(PointSensitivities pointSensitivities) {
    throw new UnsupportedOperationException();
  }

  @Override
  public double relativeTime(LocalDate date) {
    throw new UnsupportedOperationException();
  }

}