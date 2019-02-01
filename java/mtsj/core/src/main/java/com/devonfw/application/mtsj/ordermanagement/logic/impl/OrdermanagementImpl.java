package com.devonfw.application.mtsj.ordermanagement.logic.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import com.devonfw.application.mtsj.bookingmanagement.logic.api.Bookingmanagement;
import com.devonfw.application.mtsj.bookingmanagement.logic.api.to.BookingEto;
import com.devonfw.application.mtsj.bookingmanagement.logic.api.to.InvitedGuestEto;
import com.devonfw.application.mtsj.dishmanagement.common.api.Ingredient;
import com.devonfw.application.mtsj.dishmanagement.dataaccess.api.IngredientEntity;
import com.devonfw.application.mtsj.dishmanagement.logic.api.Dishmanagement;
import com.devonfw.application.mtsj.dishmanagement.logic.api.to.DishCto;
import com.devonfw.application.mtsj.dishmanagement.logic.api.to.DishEto;
import com.devonfw.application.mtsj.dishmanagement.logic.api.to.IngredientEto;
import com.devonfw.application.mtsj.general.common.api.constants.Roles;
import com.devonfw.application.mtsj.general.logic.base.AbstractComponentFacade;
import com.devonfw.application.mtsj.mailservice.Mail;
import com.devonfw.application.mtsj.ordermanagement.dataaccess.api.OrderEntity;
import com.devonfw.application.mtsj.ordermanagement.dataaccess.api.OrderLineEntity;
import com.devonfw.application.mtsj.ordermanagement.dataaccess.api.repo.OrderLineRepository;
import com.devonfw.application.mtsj.ordermanagement.dataaccess.api.repo.OrderRepository;
import com.devonfw.application.mtsj.ordermanagement.logic.api.Ordermanagement;
import com.devonfw.application.mtsj.ordermanagement.logic.api.to.OrderCto;
import com.devonfw.application.mtsj.ordermanagement.logic.api.to.OrderEto;
import com.devonfw.application.mtsj.ordermanagement.logic.api.to.OrderLineCto;
import com.devonfw.application.mtsj.ordermanagement.logic.api.to.OrderLineEto;
import com.devonfw.application.mtsj.ordermanagement.logic.api.to.OrderLineSearchCriteriaTo;
import com.devonfw.application.mtsj.ordermanagement.logic.api.to.OrderSearchCriteriaTo;

/**
 * Implementation of component interface of ordermanagement
 */
@Named
@Transactional
public class OrdermanagementImpl extends AbstractComponentFacade implements Ordermanagement {

  /**
   * Logger instance.
   */
  private static final Logger LOG = LoggerFactory.getLogger(OrdermanagementImpl.class);

  /**
   * @see #getOrderDao()
   */
  @Inject
  private OrderRepository orderDao;

  /**
   * @see #getOrderLineDao()
   */
  @Inject
  private OrderLineRepository orderLineDao;


  @Inject
  private Dishmanagement dishManagement;

  @Inject
  private Mail mailService;

  @Value("${client.port}")
  private int clientPort;

  @Value("${server.servlet.context-path}")
  private String serverContextPath;

  @Value("${mythaistar.hourslimitcancellation}")
  private int hoursLimit;

  /**
   * The constructor.
   */
  public OrdermanagementImpl() {

    super();
  }

  @Override
  public OrderCto findOrder(Long id) {

    LOG.debug("Get Order with id {} from database.", id);
    OrderEntity entity = getOrderDao().find(id);
    OrderCto cto = new OrderCto();
    cto.setBooking(getBeanMapper().map(entity.getBooking(), BookingEto.class));
    cto.setHost(getBeanMapper().map(entity.getHost(), BookingEto.class));
    cto.setOrderLines(getBeanMapper().mapList(entity.getOrderLines(), OrderLineCto.class));
    cto.setOrder(getBeanMapper().map(entity, OrderEto.class));
    cto.setInvitedGuest(getBeanMapper().map(entity.getInvitedGuest(), InvitedGuestEto.class));
    return cto;
  }

  @RolesAllowed(Roles.WAITER)
  public Page<OrderCto> findOrdersByPost(OrderSearchCriteriaTo criteria) {

    return findOrderCtos(criteria);
  }

  @Override
  public List<OrderCto> findOrdersByInvitedGuest(Long invitedGuestId) {

    List<OrderCto> ctos = new ArrayList<>();
    List<OrderEntity> orders = getOrderDao().findOrdersByInvitedGuest(invitedGuestId);
    for (OrderEntity order : orders) {
      processOrders(ctos, order);
    }

    return ctos;

  }

  @Override
  public List<OrderCto> findOrdersByBookingToken(String bookingToken) {

    List<OrderCto> ctos = new ArrayList<>();
    List<OrderEntity> orders = getOrderDao().findOrdersByBookingToken(bookingToken);
    for (OrderEntity order : orders) {
      processOrders(ctos, order);
    }
    return ctos;

  }

  @Override
  public Page<OrderCto> findOrderCtos(OrderSearchCriteriaTo criteria) {

    List<OrderCto> ctos = new ArrayList<>();
    Page<OrderCto> pagListTo = null;
    Page<OrderEntity> orders = getOrderDao().findOrders(criteria);
    for (OrderEntity order : orders.getContent()) {
      processOrders(ctos, order);
    }

    if (!ctos.isEmpty()) {
      Pageable pagResultTo = PageRequest.of(criteria.getPageable().getPageNumber(), ctos.size());
      pagListTo = new PageImpl<>(ctos, pagResultTo, orders.getTotalElements());
    }
    return pagListTo;

  }

  /**
   * @param ctos
   * @param order
   */
  private void processOrders(List<OrderCto> ctos, OrderEntity order) {

    OrderCto cto = new OrderCto();
    cto.setBooking(getBeanMapper().map(order.getBooking(), BookingEto.class));
    cto.setHost(getBeanMapper().map(order.getHost(), BookingEto.class));
    cto.setInvitedGuest(getBeanMapper().map(order.getInvitedGuest(), InvitedGuestEto.class));
    cto.setOrder(getBeanMapper().map(order, OrderEto.class));
    cto.setOrderLines(getBeanMapper().mapList(order.getOrderLines(), OrderLineCto.class));
    List<OrderLineCto> orderLinesCto = new ArrayList<>();
    for (OrderLineEntity orderLine : order.getOrderLines()) {
      OrderLineCto orderLineCto = new OrderLineCto();
      orderLineCto.setDish(getBeanMapper().map(orderLine.getDish(), DishEto.class));
      orderLineCto.setExtras(getBeanMapper().mapList(orderLine.getExtras(), IngredientEto.class));
      orderLineCto.setOrderLine(getBeanMapper().map(orderLine, OrderLineEto.class));
      orderLinesCto.add(orderLineCto);
    }
    cto.setOrderLines(orderLinesCto);
    ctos.add(cto);

  }

  @Override
  public List<OrderCto> findOrders(Long idBooking) {

    List<OrderCto> ctos = new ArrayList<>();
    List<OrderEntity> orders = getOrderDao().findOrders(idBooking);
    for (OrderEntity order : orders) {
      processOrders(ctos, order);
    }

    return ctos;
  }

  @Override
  public OrderEto findOrderEto(Long idOrder) {

    OrderEntity order = getOrderDao().find(idOrder);

    return getBeanMapper().map(order, OrderEto.class);
  }

  @Override
  public boolean deleteOrder(Long orderId) {
	  List<OrderLineEntity> orderLines = getOrderLineDao().findOrderLines(orderId);

	for (OrderLineEntity orderLine : orderLines) {
		getOrderLineDao().deleteById(orderLine.getId());
	}
    getOrderDao().deleteById(orderId);
    LOG.debug("The order with id '{}' has been deleted.", orderId);

    return true;

  }

  @Override
  public OrderEto saveOrder(OrderCto order) {

	    Objects.requireNonNull(order, "order");
	    List<OrderLineCto> linesCto = order.getOrderLines();
	    List<OrderLineEntity> orderLineEntities = new ArrayList<>();
	    for (OrderLineCto lineCto : linesCto) {
	      OrderLineEntity orderLineEntity = getBeanMapper().map(lineCto, OrderLineEntity.class);
	      orderLineEntity.setExtras(getBeanMapper().mapList(lineCto.getExtras(), IngredientEntity.class));
	      orderLineEntity.setDishId(lineCto.getOrderLine().getDishId());
	      orderLineEntity.setAmount(lineCto.getOrderLine().getAmount());
	      orderLineEntity.setComment(lineCto.getOrderLine().getComment());
	      orderLineEntities.add(orderLineEntity);
	    }

	    OrderEntity orderEntity = getBeanMapper().map(order, OrderEntity.class);


	    orderEntity.setOrderLines(orderLineEntities);
	    OrderEntity resultOrderEntity = getOrderDao().save(orderEntity);
	    LOG.debug("Order with id '{}' has been created.", resultOrderEntity.getId());

	    for (OrderLineEntity orderLineEntity : orderLineEntities) {
	      orderLineEntity.setOrderId(resultOrderEntity.getId());
	      OrderLineEntity resultOrderLine = getOrderLineDao().save(orderLineEntity);
	      LOG.info("OrderLine with id '{}' has been created.", resultOrderLine.getId());
	    }

	    return getBeanMapper().map(resultOrderEntity, OrderEto.class);
  }

  /**
   * Returns the field 'orderDao'.
   *
   * @return the {@link OrderDao} instance.
   */
  public OrderRepository getOrderDao() {

    return this.orderDao;
  }

  @Override
  public OrderLineEto findOrderLine(Long id) {

    LOG.debug("Get OrderLine with id {} from database.", id);
    return getBeanMapper().map(getOrderLineDao().find(id), OrderLineEto.class);
  }

  @Override
  public Page<OrderLineCto> findOrderLineCtos(OrderLineSearchCriteriaTo criteria) {

    Page<OrderLineEntity> orderlines = getOrderLineDao().findOrderLines(criteria);
    List<OrderLineCto> orderLinesCto = new ArrayList<>();
    for (OrderLineEntity orderline : orderlines.getContent()) {
      OrderLineCto orderLineCto = new OrderLineCto();
      orderLineCto.setOrderLine(getBeanMapper().map(this.orderLineDao.find(orderline.getId()), OrderLineEto.class));
      orderLineCto.setExtras(getBeanMapper().mapList(orderline.getExtras(), IngredientEto.class));
      orderLinesCto.add(orderLineCto);
    }

    Pageable pagResultTo = PageRequest.of(criteria.getPageable().getPageNumber(), orderLinesCto.size());
    return new PageImpl<>(orderLinesCto, pagResultTo, pagResultTo.getPageSize());
  }

  @Override
  public boolean deleteOrderLine(Long orderLineId) {

    OrderLineEntity orderLine = getOrderLineDao().find(orderLineId);
    getOrderLineDao().delete(orderLine);
    LOG.debug("The orderLine with id '{}' has been deleted.", orderLineId);
    return true;
  }

  @Override
  public OrderLineEto saveOrderLine(OrderLineEto orderLine) {

    Objects.requireNonNull(orderLine, "orderLine");
    OrderLineEntity orderLineEntity = getBeanMapper().map(orderLine, OrderLineEntity.class);

    // initialize, validate orderLineEntity here if necessary
    OrderLineEntity resultEntity = getOrderLineDao().save(orderLineEntity);
    LOG.debug("OrderLine with id '{}' has been created.", resultEntity.getId());

    return getBeanMapper().map(resultEntity, OrderLineEto.class);
  }

  /**
   * Returns the field 'orderLineDao'.
   *
   * @return the {@link OrderLineDao} instance.
   */
  public OrderLineRepository getOrderLineDao() {

    return this.orderLineDao;
  }



  public String getContentFormatedWithCost(OrderEto order) {

    List<OrderLineEntity> orderLines = this.orderLineDao.findOrderLines(order.getId());

    StringBuilder sb = new StringBuilder();
    sb.append("\n");
    BigDecimal finalPrice = BigDecimal.ZERO;
    for (OrderLineEntity orderLine : orderLines) {
      DishCto dishCto = this.dishManagement.findDish(orderLine.getDishId());
      List<IngredientEto> extras = dishCto.getExtras();
      Set<IngredientEto> set = new HashSet<>();
      set.addAll(extras);
      extras.clear();
      extras.addAll(set);
      // dish name
      sb.append(dishCto.getDish().getName()).append(", x").append(orderLine.getAmount());
      // dish cost
      BigDecimal dishCost = dishCto.getDish().getPrice().multiply(new BigDecimal(orderLine.getAmount()));
      BigDecimal linePrice = dishCost;
      // dish selected extras
      sb.append(". Extras: ");
      for (Ingredient extra : extras) {
        for (Ingredient selectedExtra : orderLine.getExtras()) {
          if (extra.getId().equals(selectedExtra.getId())) {
            sb.append(extra.getName()).append(",");
            linePrice = linePrice.add(extra.getPrice());
            break;
          }
        }
      }

      // dish cost
      sb.append(" ==>").append(". Dish cost: ").append(linePrice.toString());
      sb.append("\n");
      // increase the finalPrice of the order
      finalPrice = finalPrice.add(linePrice);
    }

    return sb.append("Total Order cost: ").append(finalPrice.toString()).toString();
  }


}
