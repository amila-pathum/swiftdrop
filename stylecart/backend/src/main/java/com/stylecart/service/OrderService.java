package com.stylecart.service;
import com.stylecart.model.*;
import com.stylecart.repo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class OrderService {
    public static final Set<String> STATUSES=Set.of("CREATED","PICKED_UP","IN_TRANSIT","OUT_FOR_DELIVERY","DELIVERED","FAILED","CANCELLED");
    public static final List<String> DISTRICTS=List.of("Ampara","Anuradhapura","Badulla","Batticaloa","Colombo","Galle","Gampaha","Hambantota","Jaffna","Kalutara","Kandy","Kegalle","Kilinochchi","Kurunegala","Mannar","Matale","Matara","Monaragala","Mullaitivu","Nuwara Eliya","Polonnaruwa","Puttalam","Ratnapura","Trincomalee","Vavuniya");
    private final OrderRepository orders;private final ProductRepository products;private final EventRepository received;private final ApplicationEventPublisher events;
    public OrderService(OrderRepository orders,ProductRepository products,EventRepository received,ApplicationEventPublisher events){this.orders=orders;this.products=products;this.received=received;this.events=events;}
    @Transactional public OrderView create(Long customer,Checkout request){
        if(!DISTRICTS.contains(request.city()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a supported delivery district.");
        var order=new StoreOrder();order.reference="SC-"+UUID.randomUUID();order.customerId=customer;order.receiverName=request.name().trim();order.phone=request.phone().trim();
        order.address=request.address().trim();order.city=request.city();order.postalCode=request.postalCode()==null?"":request.postalCode().trim();order.subtotal=BigDecimal.ZERO;
        for(var line:request.items()){
            var product=products.findById(line.productId()).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"A product is no longer available."));
            if(!Arrays.asList(product.sizes.split(",")).contains(line.size())||!Arrays.asList(product.colors.split(",")).contains(line.color()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose an available size and colour.");
            var item=new OrderItem();item.order=order;item.productId=product.id;item.name=product.name;item.image=product.image;item.size=line.size();item.color=line.color();item.quantity=line.quantity();item.unitPrice=product.price;item.weight=product.weight;
            order.items.add(item);order.subtotal=order.subtotal.add(item.unitPrice.multiply(BigDecimal.valueOf(item.quantity)));
        }
        order=orders.saveAndFlush(order);events.publishEvent(new LiveUpdates.Changed(customer,order.id));return view(order);
    }
    @Transactional(readOnly=true) public List<OrderView> list(Long customer){return (customer==null?orders.findTop100ByOrderByCreatedAtDesc():orders.findTop100ByCustomerIdOrderByCreatedAtDesc(customer)).stream().map(o->view(o,customer==null)).toList();}
    @Transactional(readOnly=true) public OrderView get(UUID id,Long customer){var order=orders.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(customer!=null&&!customer.equals(order.customerId))throw new ResponseStatusException(HttpStatus.NOT_FOUND);return view(order,customer==null);}
    public OrderView view(StoreOrder order){return view(order,true);}
    private OrderView view(StoreOrder order,boolean admin){return new OrderView(order.id,order.reference,order.customerId,order.receiverName,order.phone,order.address,order.city,order.postalCode,order.subtotal,order.shippingCharge,order.shipmentState,
        order.trackingNumber,order.deliveryStatus,admin?order.lastError:null,order.createdAt,order.requestPayload!=null,
        order.items.stream().map(i->new ItemView(i.productId,i.name,i.image,i.size,i.color,i.quantity,i.unitPrice)).toList(),
        received.findByOrderIdOrderByOccurredAtAsc(order.id).stream().map(e->new Timeline(e.eventId,e.newStatus,e.occurredAt)).toList());}
    public record Line(@NotBlank String productId,@NotBlank String size,@NotBlank String color,@Min(1) @Max(10) int quantity){}
    public record Checkout(@NotEmpty @Size(max=20) List<@Valid Line> items,@NotBlank @Size(max=150) String name,
        @NotBlank @Pattern(regexp="^(?=(?:[^0-9]*[0-9]){7})[+0-9 ()-]{7,30}$") String phone,@NotBlank @Size(max=255) String address,@NotBlank String city,@Size(max=20) String postalCode){@Override public String toString(){return "Checkout[REDACTED]";}}
    public record ItemView(String productId,String name,String image,String size,String color,int quantity,BigDecimal unitPrice){}
    public record Timeline(UUID eventId,String status,Instant occurredAt){}
    public record OrderView(UUID id,String reference,Long customerId,String receiverName,String phone,String address,String city,String postalCode,BigDecimal subtotal,BigDecimal shippingCharge,String shipmentState,
        String trackingNumber,String deliveryStatus,String error,Instant createdAt,boolean requestFrozen,List<ItemView> items,List<Timeline> timeline){}
}
