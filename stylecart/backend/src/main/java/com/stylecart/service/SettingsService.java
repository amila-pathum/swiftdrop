package com.stylecart.service;
import com.stylecart.model.IntegrationSettings;
import com.stylecart.repo.SettingsRepository;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class SettingsService {
    private final SettingsRepository settings;private final SecretBox secrets;private final Set<String> allowed;private final String publicUrl;
    private final ApplicationEventPublisher events;
    public SettingsService(SettingsRepository settings,SecretBox secrets,@Value("${stylecart.allowed-gateways}") String origins,@Value("${stylecart.public-url}") String publicUrl,ApplicationEventPublisher events){
        this.settings=settings;this.secrets=secrets;this.publicUrl=publicUrl;this.events=events;
        allowed=new HashSet<>();for(String value:origins.split(","))allowed.add(origin(value.trim()));
    }
    public static String origin(String value){try{var u=URI.create(value);boolean local=Set.of("localhost","127.0.0.1","[::1]").contains(u.getHost());
        if(u.getUserInfo()!=null||u.getFragment()!=null||u.getQuery()!=null||u.getHost()==null||!(u.getPath().isEmpty()||u.getPath().equals("/"))
            ||!(u.getScheme().equals("https")||u.getScheme().equals("http")&&local))throw new IllegalArgumentException();
        return u.getScheme()+"://"+u.getAuthority();}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use an approved HTTPS gateway or local HTTP gateway origin.");}}
    @Transactional(readOnly=true) public View view(){return toView(settings.findById(1L).orElseThrow());}
    private View toView(IntegrationSettings s){return new View(s.gatewayUrl,s.apiKeyCipher!=null,s.secretCipher!=null,publicUrl.replaceAll("/$","")+"/webhooks/swiftdrop",
        s.pickupName,s.pickupPhone,s.pickupAddress,s.pickupCity,s.pickupPostal,s.failureMode,s.failuresRemaining);}
    @Transactional public View update(Update value){var s=settings.lock().orElseThrow();String gateway=origin(value.gatewayUrl());
        if(!allowed.contains(gateway))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Gateway is not in this shop server’s approved gateway list.");
        if(value.apiKey()!=null&&!value.apiKey().isBlank())s.apiKeyCipher=secrets.encrypt(limit(value.apiKey(),512));
        if(value.signingSecret()!=null&&!value.signingSecret().isBlank())s.secretCipher=secrets.encrypt(limit(value.signingSecret(),512));
        s.gatewayUrl=gateway;s.pickupName=limit(value.pickupName(),150);s.pickupPhone=limit(value.pickupPhone(),30);
        s.pickupAddress=limit(value.pickupAddress(),255);s.pickupCity=limit(value.pickupCity(),100);s.pickupPostal=limit(value.pickupPostal(),20);
        events.publishEvent(new LiveUpdates.Changed(null,null));return toView(s);
    }
    @Transactional public void failures(String mode){if(!Set.of("NORMAL","NEXT_TWO","ALWAYS").contains(mode))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var s=settings.lock().orElseThrow();s.failureMode=mode;s.failuresRemaining=mode.equals("NEXT_TWO")?2:0;events.publishEvent(new LiveUpdates.Changed(null,null));}
    @Transactional(readOnly=true) public Credentials credentials(){var s=settings.findById(1L).orElseThrow();
        if(!allowed.contains(origin(s.gatewayUrl)))throw new ResponseStatusException(HttpStatus.CONFLICT,"Configure an approved gateway first.");
        return new Credentials(s.gatewayUrl,secrets.decrypt(s.apiKeyCipher));}
    public static String limit(String value,int max){if(value==null)return "";if(value.trim().length()>max)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A setting is too long.");return value.trim();}
    public record View(String gatewayUrl,boolean apiKeyConfigured,boolean signingSecretConfigured,String webhookUrl,String pickupName,String pickupPhone,String pickupAddress,String pickupCity,String pickupPostal,String failureMode,int failuresRemaining){}
    public record Update(String gatewayUrl,String apiKey,String signingSecret,String pickupName,String pickupPhone,String pickupAddress,String pickupCity,String pickupPostal){@Override public String toString(){return "SettingsUpdate[REDACTED]";}}
    public record Credentials(String gateway,String apiKey){@Override public String toString(){return "Credentials[REDACTED]";}}
}
