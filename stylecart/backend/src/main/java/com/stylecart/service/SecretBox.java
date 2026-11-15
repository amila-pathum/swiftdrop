package com.stylecart.service;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.SecureRandom;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class SecretBox {
    private final byte[] key;
    public SecretBox(@Value("${stylecart.settings-key}") String value){
        try{key=Base64.getDecoder().decode(value);if(key.length!=32)throw new IllegalArgumentException();}
        catch(RuntimeException e){throw new IllegalStateException("Set STYLECART_SETTINGS_KEY to a base64 32-byte StyleCart-only key.");}
    }
    public String encrypt(String value){
        if(value==null||value.isBlank())return null;
        try{byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);var cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
            byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),all=Arrays.copyOf(iv,iv.length+encrypted.length);
            System.arraycopy(encrypted,0,all,iv.length,encrypted.length);return Base64.getEncoder().encodeToString(all);
        }catch(Exception e){throw new IllegalStateException("Settings encryption failed.");}
    }
    public String decrypt(String value){
        if(value==null)return null;
        try{byte[] all=Base64.getDecoder().decode(value);var cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOf(all,12)));
            return new String(cipher.doFinal(Arrays.copyOfRange(all,12,all.length)),StandardCharsets.UTF_8);
        }catch(Exception e){throw new IllegalStateException("Stored credentials cannot be decrypted with this StyleCart key.");}
    }
}
