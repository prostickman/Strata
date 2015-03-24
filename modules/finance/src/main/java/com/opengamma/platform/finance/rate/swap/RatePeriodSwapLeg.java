/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.finance.rate.swap;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.opengamma.collect.Guavate.toImmutableList;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.joda.beans.Bean;
import org.joda.beans.BeanDefinition;
import org.joda.beans.ImmutableBean;
import org.joda.beans.ImmutableConstructor;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.opengamma.basics.currency.Currency;
import com.opengamma.basics.date.BusinessDayAdjustment;
import com.opengamma.platform.finance.rate.FixedRateObservation;
import com.opengamma.platform.finance.rate.IborRateObservation;
import com.opengamma.platform.finance.rate.OvernightCompoundedRateObservation;

/**
 * A rate swap leg defined using payment and accrual periods.
 * <p>
 * This defines a single swap leg paying a rate, such as an interest rate.
 * The rate may be fixed or floating, for examples see {@link FixedRateObservation},
 * {@link IborRateObservation} and {@link OvernightCompoundedRateObservation}.
 * <p>
 * The swap is built up of one or more <i>payment periods</i>, each of which produces a single payment.
 * Each payment period is made up of one or more <i>accrual periods</i>.
 * If there is more than one accrual period in a payment period then compounding may apply.
 * See {@link RatePaymentPeriod} and {@link RateAccrualPeriod} for more details.
 * <p>
 * This class allows the entire structure of the payment and accrual periods to be defined.
 * This permits weird and wonderful swaps to be created, however it is important to note
 * that there is no ability to adjust the accrual period dates if the holiday calendar changes.
 * Provision is provided to adjust the payment dates if the holiday calendar changes.
 * Note however that it is intended that the dates on {@code RatePaymentPeriod} and
 * {@code RateAccrualPeriod} are already adjusted to be valid business days.
 * <p>
 * In general, it is recommended to use the parameterized {@link RateCalculationSwapLeg}
 * instead of this class.
 */
@BeanDefinition
public final class RatePeriodSwapLeg
    implements SwapLeg, ImmutableBean, Serializable {

  /**
   * The payment periods that combine to form the swap leg.
   * <p>
   * Each payment period represents part of the life-time of the leg.
   * In most cases, the periods do not overlap. However, since each payment period
   * is essentially independent the data model allows overlapping periods.
   */
  @PropertyDefinition(validate = "notEmpty")
  private final ImmutableList<RatePaymentPeriod> paymentPeriods;
  /**
   * The flag indicating whether to exchange the initial notional.
   * <p>
   * Setting this to true indicates that the notional is transferred at the start of the trade.
   * This should typically be set to true in the case of an FX reset swap, or one with a varying notional.
   * <p>
   * This flag controls whether a notional exchange object is created when the leg is expanded.
   * It covers an exchange on the initial payment date of the swap leg, treated as the start date.
   * If there is an FX reset, then this flag is ignored, see {@code intermediateExchange}.
   * If there is no FX reset and the flag is true, then a {@link NotionalExchange} object will be created.
   */
  @PropertyDefinition
  private final boolean initialExchange;
  /**
   * The flag indicating whether to exchange the differences in the notional during the lifetime of the swap.
   * <p>
   * Setting this to true indicates that the notional is transferred when it changes during the trade.
   * This should typically be set to true in the case of an FX reset swap, or one with a varying notional.
   * <p>
   * This flag controls whether a notional exchange object is created when the leg is expanded.
   * It covers an exchange on each intermediate payment date of the swap leg.
   * If set to true, the behavior depends on whether an FX reset payment period is defined.
   * If there is an FX reset, then an {@link FxResetNotionalExchange} object will be created.
   * If there is no FX reset, then a {@link NotionalExchange} object will be created.
   */
  @PropertyDefinition
  private final boolean intermediateExchange;
  /**
   * The flag indicating whether to exchange the final notional.
   * <p>
   * Setting this to true indicates that the notional is transferred at the end of the trade.
   * This should typically be set to true in the case of an FX reset swap, or one with a varying notional.
   * <p>
   * This flag controls whether a notional exchange object is created when the leg is expanded.
   * It covers an exchange on the final payment date of the swap leg.
   * If there is an FX reset, then this flag is ignored, see {@code intermediateExchange}.
   * If there is no FX reset and the flag is true, then a {@link NotionalExchange} object will be created.
   */
  @PropertyDefinition
  private final boolean finalExchange;
  /**
   * The additional payment events that are associated with the swap leg.
   * <p>
   * Payment events include fees.
   * Notional exchange may also be specified here instead of via the dedicated fields.
   */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableList<PaymentEvent> paymentEvents;
  /**
   * The business day date adjustment to be applied to each payment date, default is to apply no adjustment.
   * <p>
   * The business day adjustment is applied to period, exchange and event payment dates.
   */
  @PropertyDefinition(validate = "notNull")
  private final BusinessDayAdjustment paymentBusinessDayAdjustment;
  /**
   * The currency of the leg.
   */
  private final Currency currency;  // not a property, derived and cached from input data

  //-------------------------------------------------------------------------
  @ImmutableConstructor
  private RatePeriodSwapLeg(
      List<RatePaymentPeriod> paymentPeriods,
      boolean initialExchange,
      boolean intermediateExchange,
      boolean finalExchange,
      List<PaymentEvent> paymentEvents,
      BusinessDayAdjustment paymentBusinessDayAdjustment) {

    JodaBeanUtils.notEmpty(paymentPeriods, "paymentPeriods");
    JodaBeanUtils.notNull(paymentEvents, "paymentEvents");
    this.paymentPeriods = ImmutableList.copyOf(paymentPeriods);
    this.initialExchange = initialExchange;
    this.intermediateExchange = intermediateExchange;
    this.finalExchange = finalExchange;
    this.paymentBusinessDayAdjustment = firstNonNull(paymentBusinessDayAdjustment, BusinessDayAdjustment.NONE);
    this.paymentEvents = ImmutableList.copyOf(paymentEvents);
    // determine and validate currency, with explicit error message
    Stream<Currency> periodCurrencies = paymentPeriods.stream().map(PaymentPeriod::getCurrency);
    Stream<Currency> eventCurrencies = paymentEvents.stream().map(PaymentEvent::getCurrency);
    Set<Currency> currencies = Stream.concat(periodCurrencies, eventCurrencies).collect(Collectors.toSet());
    if (currencies.size() > 1) {
      throw new IllegalArgumentException("Swap leg must have a single currency, found: " + currencies);
    }
    this.currency = Iterables.getOnlyElement(currencies);
  }

  //-------------------------------------------------------------------------
  /**
   * Gets the start date of the leg.
   * <p>
   * This is the first accrual date in the leg, often known as the effective date.
   * This date has been adjusted to be a valid business day.
   * 
   * @return the start date of the leg
   */
  @Override
  public LocalDate getStartDate() {
    return paymentPeriods.get(0).getStartDate();
  }

  /**
   * Gets the end date of the leg.
   * <p>
   * This is the last accrual date in the leg, often known as the maturity date.
   * This date has been adjusted to be a valid business day.
   * 
   * @return the end date of the leg
   */
  @Override
  public LocalDate getEndDate() {
    return paymentPeriods.get(paymentPeriods.size() - 1).getEndDate();
  }

  /**
   * Gets the currency of the swap leg.
   * <p>
   * All periods in the leg will have this currency.
   * 
   * @return the currency
   */
  @Override
  public Currency getCurrency() {
    return currency;
  }

  /**
   * Converts this swap leg to the equivalent {@code ExpandedSwapLeg}.
   * <p>
   * An {@link ExpandedSwapLeg} represents the same data as this leg, but with
   * the schedules expanded to be {@link PaymentPeriod} instances.
   * 
   * @return the equivalent expanded swap leg
   * @throws RuntimeException if unable to expand due to an invalid definition
   */
  @Override
  public ExpandedSwapLeg expand() {
    ImmutableList<RatePaymentPeriod> adjusted = paymentPeriods.stream()
        .map(pp -> pp.adjustPaymentDate(paymentBusinessDayAdjustment))
        .collect(toImmutableList());
    return ExpandedSwapLeg.builder()
        .paymentPeriods(ImmutableList.copyOf(adjusted))  // copyOf needed for type conversion
        .paymentEvents(createEvents(adjusted))
        .build();
  }

  // notional exchange events
  private ImmutableList<PaymentEvent> createEvents(List<RatePaymentPeriod> adjPaymentPeriods) {

    ImmutableList.Builder<PaymentEvent> events = ImmutableList.builder();
    LocalDate initialExchangeDate = getStartDate().with(paymentBusinessDayAdjustment);
    events.addAll(NotionalSchedule.createEvents(
        adjPaymentPeriods, initialExchangeDate, initialExchange, intermediateExchange, finalExchange));
    paymentEvents.forEach(pe -> events.add(pe.adjustPaymentDate(paymentBusinessDayAdjustment)));
    return events.build();
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code RatePeriodSwapLeg}.
   * @return the meta-bean, not null
   */
  public static RatePeriodSwapLeg.Meta meta() {
    return RatePeriodSwapLeg.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(RatePeriodSwapLeg.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static RatePeriodSwapLeg.Builder builder() {
    return new RatePeriodSwapLeg.Builder();
  }

  @Override
  public RatePeriodSwapLeg.Meta metaBean() {
    return RatePeriodSwapLeg.Meta.INSTANCE;
  }

  @Override
  public <R> Property<R> property(String propertyName) {
    return metaBean().<R>metaProperty(propertyName).createProperty(this);
  }

  @Override
  public Set<String> propertyNames() {
    return metaBean().metaPropertyMap().keySet();
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the payment periods that combine to form the swap leg.
   * <p>
   * Each payment period represents part of the life-time of the leg.
   * In most cases, the periods do not overlap. However, since each payment period
   * is essentially independent the data model allows overlapping periods.
   * @return the value of the property, not empty
   */
  public ImmutableList<RatePaymentPeriod> getPaymentPeriods() {
    return paymentPeriods;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the flag indicating whether to exchange the initial notional.
   * <p>
   * Setting this to true indicates that the notional is transferred at the start of the trade.
   * This should typically be set to true in the case of an FX reset swap, or one with a varying notional.
   * <p>
   * This flag controls whether a notional exchange object is created when the leg is expanded.
   * It covers an exchange on the initial payment date of the swap leg, treated as the start date.
   * If there is an FX reset, then this flag is ignored, see {@code intermediateExchange}.
   * If there is no FX reset and the flag is true, then a {@link NotionalExchange} object will be created.
   * @return the value of the property
   */
  public boolean isInitialExchange() {
    return initialExchange;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the flag indicating whether to exchange the differences in the notional during the lifetime of the swap.
   * <p>
   * Setting this to true indicates that the notional is transferred when it changes during the trade.
   * This should typically be set to true in the case of an FX reset swap, or one with a varying notional.
   * <p>
   * This flag controls whether a notional exchange object is created when the leg is expanded.
   * It covers an exchange on each intermediate payment date of the swap leg.
   * If set to true, the behavior depends on whether an FX reset payment period is defined.
   * If there is an FX reset, then an {@link FxResetNotionalExchange} object will be created.
   * If there is no FX reset, then a {@link NotionalExchange} object will be created.
   * @return the value of the property
   */
  public boolean isIntermediateExchange() {
    return intermediateExchange;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the flag indicating whether to exchange the final notional.
   * <p>
   * Setting this to true indicates that the notional is transferred at the end of the trade.
   * This should typically be set to true in the case of an FX reset swap, or one with a varying notional.
   * <p>
   * This flag controls whether a notional exchange object is created when the leg is expanded.
   * It covers an exchange on the final payment date of the swap leg.
   * If there is an FX reset, then this flag is ignored, see {@code intermediateExchange}.
   * If there is no FX reset and the flag is true, then a {@link NotionalExchange} object will be created.
   * @return the value of the property
   */
  public boolean isFinalExchange() {
    return finalExchange;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the additional payment events that are associated with the swap leg.
   * <p>
   * Payment events include fees.
   * Notional exchange may also be specified here instead of via the dedicated fields.
   * @return the value of the property, not null
   */
  public ImmutableList<PaymentEvent> getPaymentEvents() {
    return paymentEvents;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the business day date adjustment to be applied to each payment date, default is to apply no adjustment.
   * <p>
   * The business day adjustment is applied to period, exchange and event payment dates.
   * @return the value of the property, not null
   */
  public BusinessDayAdjustment getPaymentBusinessDayAdjustment() {
    return paymentBusinessDayAdjustment;
  }

  //-----------------------------------------------------------------------
  /**
   * Returns a builder that allows this bean to be mutated.
   * @return the mutable builder, not null
   */
  public Builder toBuilder() {
    return new Builder(this);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      RatePeriodSwapLeg other = (RatePeriodSwapLeg) obj;
      return JodaBeanUtils.equal(getPaymentPeriods(), other.getPaymentPeriods()) &&
          (isInitialExchange() == other.isInitialExchange()) &&
          (isIntermediateExchange() == other.isIntermediateExchange()) &&
          (isFinalExchange() == other.isFinalExchange()) &&
          JodaBeanUtils.equal(getPaymentEvents(), other.getPaymentEvents()) &&
          JodaBeanUtils.equal(getPaymentBusinessDayAdjustment(), other.getPaymentBusinessDayAdjustment());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(getPaymentPeriods());
    hash = hash * 31 + JodaBeanUtils.hashCode(isInitialExchange());
    hash = hash * 31 + JodaBeanUtils.hashCode(isIntermediateExchange());
    hash = hash * 31 + JodaBeanUtils.hashCode(isFinalExchange());
    hash = hash * 31 + JodaBeanUtils.hashCode(getPaymentEvents());
    hash = hash * 31 + JodaBeanUtils.hashCode(getPaymentBusinessDayAdjustment());
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(224);
    buf.append("RatePeriodSwapLeg{");
    buf.append("paymentPeriods").append('=').append(getPaymentPeriods()).append(',').append(' ');
    buf.append("initialExchange").append('=').append(isInitialExchange()).append(',').append(' ');
    buf.append("intermediateExchange").append('=').append(isIntermediateExchange()).append(',').append(' ');
    buf.append("finalExchange").append('=').append(isFinalExchange()).append(',').append(' ');
    buf.append("paymentEvents").append('=').append(getPaymentEvents()).append(',').append(' ');
    buf.append("paymentBusinessDayAdjustment").append('=').append(JodaBeanUtils.toString(getPaymentBusinessDayAdjustment()));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code RatePeriodSwapLeg}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code paymentPeriods} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableList<RatePaymentPeriod>> paymentPeriods = DirectMetaProperty.ofImmutable(
        this, "paymentPeriods", RatePeriodSwapLeg.class, (Class) ImmutableList.class);
    /**
     * The meta-property for the {@code initialExchange} property.
     */
    private final MetaProperty<Boolean> initialExchange = DirectMetaProperty.ofImmutable(
        this, "initialExchange", RatePeriodSwapLeg.class, Boolean.TYPE);
    /**
     * The meta-property for the {@code intermediateExchange} property.
     */
    private final MetaProperty<Boolean> intermediateExchange = DirectMetaProperty.ofImmutable(
        this, "intermediateExchange", RatePeriodSwapLeg.class, Boolean.TYPE);
    /**
     * The meta-property for the {@code finalExchange} property.
     */
    private final MetaProperty<Boolean> finalExchange = DirectMetaProperty.ofImmutable(
        this, "finalExchange", RatePeriodSwapLeg.class, Boolean.TYPE);
    /**
     * The meta-property for the {@code paymentEvents} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableList<PaymentEvent>> paymentEvents = DirectMetaProperty.ofImmutable(
        this, "paymentEvents", RatePeriodSwapLeg.class, (Class) ImmutableList.class);
    /**
     * The meta-property for the {@code paymentBusinessDayAdjustment} property.
     */
    private final MetaProperty<BusinessDayAdjustment> paymentBusinessDayAdjustment = DirectMetaProperty.ofImmutable(
        this, "paymentBusinessDayAdjustment", RatePeriodSwapLeg.class, BusinessDayAdjustment.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "paymentPeriods",
        "initialExchange",
        "intermediateExchange",
        "finalExchange",
        "paymentEvents",
        "paymentBusinessDayAdjustment");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case -1674414612:  // paymentPeriods
          return paymentPeriods;
        case -511982201:  // initialExchange
          return initialExchange;
        case -2147112388:  // intermediateExchange
          return intermediateExchange;
        case -1048781383:  // finalExchange
          return finalExchange;
        case 1031856831:  // paymentEvents
          return paymentEvents;
        case -1420083229:  // paymentBusinessDayAdjustment
          return paymentBusinessDayAdjustment;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public RatePeriodSwapLeg.Builder builder() {
      return new RatePeriodSwapLeg.Builder();
    }

    @Override
    public Class<? extends RatePeriodSwapLeg> beanType() {
      return RatePeriodSwapLeg.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code paymentPeriods} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ImmutableList<RatePaymentPeriod>> paymentPeriods() {
      return paymentPeriods;
    }

    /**
     * The meta-property for the {@code initialExchange} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Boolean> initialExchange() {
      return initialExchange;
    }

    /**
     * The meta-property for the {@code intermediateExchange} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Boolean> intermediateExchange() {
      return intermediateExchange;
    }

    /**
     * The meta-property for the {@code finalExchange} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Boolean> finalExchange() {
      return finalExchange;
    }

    /**
     * The meta-property for the {@code paymentEvents} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ImmutableList<PaymentEvent>> paymentEvents() {
      return paymentEvents;
    }

    /**
     * The meta-property for the {@code paymentBusinessDayAdjustment} property.
     * @return the meta-property, not null
     */
    public MetaProperty<BusinessDayAdjustment> paymentBusinessDayAdjustment() {
      return paymentBusinessDayAdjustment;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case -1674414612:  // paymentPeriods
          return ((RatePeriodSwapLeg) bean).getPaymentPeriods();
        case -511982201:  // initialExchange
          return ((RatePeriodSwapLeg) bean).isInitialExchange();
        case -2147112388:  // intermediateExchange
          return ((RatePeriodSwapLeg) bean).isIntermediateExchange();
        case -1048781383:  // finalExchange
          return ((RatePeriodSwapLeg) bean).isFinalExchange();
        case 1031856831:  // paymentEvents
          return ((RatePeriodSwapLeg) bean).getPaymentEvents();
        case -1420083229:  // paymentBusinessDayAdjustment
          return ((RatePeriodSwapLeg) bean).getPaymentBusinessDayAdjustment();
      }
      return super.propertyGet(bean, propertyName, quiet);
    }

    @Override
    protected void propertySet(Bean bean, String propertyName, Object newValue, boolean quiet) {
      metaProperty(propertyName);
      if (quiet) {
        return;
      }
      throw new UnsupportedOperationException("Property cannot be written: " + propertyName);
    }

  }

  //-----------------------------------------------------------------------
  /**
   * The bean-builder for {@code RatePeriodSwapLeg}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<RatePeriodSwapLeg> {

    private List<RatePaymentPeriod> paymentPeriods = ImmutableList.of();
    private boolean initialExchange;
    private boolean intermediateExchange;
    private boolean finalExchange;
    private List<PaymentEvent> paymentEvents = ImmutableList.of();
    private BusinessDayAdjustment paymentBusinessDayAdjustment;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(RatePeriodSwapLeg beanToCopy) {
      this.paymentPeriods = beanToCopy.getPaymentPeriods();
      this.initialExchange = beanToCopy.isInitialExchange();
      this.intermediateExchange = beanToCopy.isIntermediateExchange();
      this.finalExchange = beanToCopy.isFinalExchange();
      this.paymentEvents = beanToCopy.getPaymentEvents();
      this.paymentBusinessDayAdjustment = beanToCopy.getPaymentBusinessDayAdjustment();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case -1674414612:  // paymentPeriods
          return paymentPeriods;
        case -511982201:  // initialExchange
          return initialExchange;
        case -2147112388:  // intermediateExchange
          return intermediateExchange;
        case -1048781383:  // finalExchange
          return finalExchange;
        case 1031856831:  // paymentEvents
          return paymentEvents;
        case -1420083229:  // paymentBusinessDayAdjustment
          return paymentBusinessDayAdjustment;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case -1674414612:  // paymentPeriods
          this.paymentPeriods = (List<RatePaymentPeriod>) newValue;
          break;
        case -511982201:  // initialExchange
          this.initialExchange = (Boolean) newValue;
          break;
        case -2147112388:  // intermediateExchange
          this.intermediateExchange = (Boolean) newValue;
          break;
        case -1048781383:  // finalExchange
          this.finalExchange = (Boolean) newValue;
          break;
        case 1031856831:  // paymentEvents
          this.paymentEvents = (List<PaymentEvent>) newValue;
          break;
        case -1420083229:  // paymentBusinessDayAdjustment
          this.paymentBusinessDayAdjustment = (BusinessDayAdjustment) newValue;
          break;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
      return this;
    }

    @Override
    public Builder set(MetaProperty<?> property, Object value) {
      super.set(property, value);
      return this;
    }

    @Override
    public Builder setString(String propertyName, String value) {
      setString(meta().metaProperty(propertyName), value);
      return this;
    }

    @Override
    public Builder setString(MetaProperty<?> property, String value) {
      super.setString(property, value);
      return this;
    }

    @Override
    public Builder setAll(Map<String, ? extends Object> propertyValueMap) {
      super.setAll(propertyValueMap);
      return this;
    }

    @Override
    public RatePeriodSwapLeg build() {
      return new RatePeriodSwapLeg(
          paymentPeriods,
          initialExchange,
          intermediateExchange,
          finalExchange,
          paymentEvents,
          paymentBusinessDayAdjustment);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the {@code paymentPeriods} property in the builder.
     * @param paymentPeriods  the new value, not empty
     * @return this, for chaining, not null
     */
    public Builder paymentPeriods(List<RatePaymentPeriod> paymentPeriods) {
      JodaBeanUtils.notEmpty(paymentPeriods, "paymentPeriods");
      this.paymentPeriods = paymentPeriods;
      return this;
    }

    /**
     * Sets the {@code paymentPeriods} property in the builder
     * from an array of objects.
     * @param paymentPeriods  the new value, not empty
     * @return this, for chaining, not null
     */
    public Builder paymentPeriods(RatePaymentPeriod... paymentPeriods) {
      return paymentPeriods(ImmutableList.copyOf(paymentPeriods));
    }

    /**
     * Sets the {@code initialExchange} property in the builder.
     * @param initialExchange  the new value
     * @return this, for chaining, not null
     */
    public Builder initialExchange(boolean initialExchange) {
      this.initialExchange = initialExchange;
      return this;
    }

    /**
     * Sets the {@code intermediateExchange} property in the builder.
     * @param intermediateExchange  the new value
     * @return this, for chaining, not null
     */
    public Builder intermediateExchange(boolean intermediateExchange) {
      this.intermediateExchange = intermediateExchange;
      return this;
    }

    /**
     * Sets the {@code finalExchange} property in the builder.
     * @param finalExchange  the new value
     * @return this, for chaining, not null
     */
    public Builder finalExchange(boolean finalExchange) {
      this.finalExchange = finalExchange;
      return this;
    }

    /**
     * Sets the {@code paymentEvents} property in the builder.
     * @param paymentEvents  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder paymentEvents(List<PaymentEvent> paymentEvents) {
      JodaBeanUtils.notNull(paymentEvents, "paymentEvents");
      this.paymentEvents = paymentEvents;
      return this;
    }

    /**
     * Sets the {@code paymentEvents} property in the builder
     * from an array of objects.
     * @param paymentEvents  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder paymentEvents(PaymentEvent... paymentEvents) {
      return paymentEvents(ImmutableList.copyOf(paymentEvents));
    }

    /**
     * Sets the {@code paymentBusinessDayAdjustment} property in the builder.
     * @param paymentBusinessDayAdjustment  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder paymentBusinessDayAdjustment(BusinessDayAdjustment paymentBusinessDayAdjustment) {
      JodaBeanUtils.notNull(paymentBusinessDayAdjustment, "paymentBusinessDayAdjustment");
      this.paymentBusinessDayAdjustment = paymentBusinessDayAdjustment;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(224);
      buf.append("RatePeriodSwapLeg.Builder{");
      buf.append("paymentPeriods").append('=').append(JodaBeanUtils.toString(paymentPeriods)).append(',').append(' ');
      buf.append("initialExchange").append('=').append(JodaBeanUtils.toString(initialExchange)).append(',').append(' ');
      buf.append("intermediateExchange").append('=').append(JodaBeanUtils.toString(intermediateExchange)).append(',').append(' ');
      buf.append("finalExchange").append('=').append(JodaBeanUtils.toString(finalExchange)).append(',').append(' ');
      buf.append("paymentEvents").append('=').append(JodaBeanUtils.toString(paymentEvents)).append(',').append(' ');
      buf.append("paymentBusinessDayAdjustment").append('=').append(JodaBeanUtils.toString(paymentBusinessDayAdjustment));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}