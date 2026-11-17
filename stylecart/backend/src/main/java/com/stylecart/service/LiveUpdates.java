package com.stylecart.service;
import jakarta.servlet.http.HttpSession;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
@Component
public class LiveUpdates {
    public record Changed(Long customerId,UUID orderId){}
    private record Client(Long userId,boolean admin,HttpSession session,SseEmitter emitter){}
    private final CopyOnWriteArrayList<Client> clients=new CopyOnWriteArrayList<>();
    public SseEmitter connect(Long id,boolean admin,HttpSession session){
        var emitter=new SseEmitter(1800000L);var client=new Client(id,admin,session,emitter);clients.add(client);
        emitter.onCompletion(()->clients.remove(client));emitter.onTimeout(()->clients.remove(client));emitter.onError(e->clients.remove(client));
        send(client,"connected");return emitter;
    }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void changed(Changed event){for(var client:clients)if(client.admin||client.userId.equals(event.customerId))send(client,"changed");}
    private void send(Client client,String type){try{client.session.getCreationTime();client.emitter.send(SseEmitter.event().data(java.util.Map.of("type",type)));}
        catch(Exception e){clients.remove(client);client.emitter.complete();}}
    @Scheduled(fixedDelay=15000) public void heartbeat(){for(var client:clients)send(client,"heartbeat");}
}
