package com.swiftdrop.courier.service;

import com.swiftdrop.courier.exception.CourierException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

@Service
public class ProofStorageService {
    public static final int MAX_BYTES=5*1024*1024;
    private final Path root;
    public ProofStorageService(@Value("${swiftdrop.proofs.directory:.data/courier-proofs}") String directory) {root=Path.of(directory).toAbsolutePath().normalize();}
    public record Photo(byte[] bytes,String extension,String digest) {}
    public Photo validate(MultipartFile upload) {
        if(upload==null) return null;
        if(upload.isEmpty())throw CourierException.invalid("The uploaded photo is empty.");
        if(upload.getSize()>MAX_BYTES)throw new CourierException(HttpStatus.PAYLOAD_TOO_LARGE,"Photos must be at most 5 MB.");
        String filename=Optional.ofNullable(upload.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        String extension=filename.endsWith(".jpg")?"jpg":filename.endsWith(".png")?"png":null;
        if(extension==null)throw CourierException.invalid("Only .jpg and .png photos are accepted.");
        try(InputStream input=upload.getInputStream()) {
            byte[] bytes=input.readNBytes(MAX_BYTES+1);
            if(bytes.length>MAX_BYTES)throw new CourierException(HttpStatus.PAYLOAD_TOO_LARGE,"Photos must be at most 5 MB.");
            try(var image=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(image);
                if(!readers.hasNext())throw CourierException.invalid("The file is not a supported image.");
                var reader=readers.next();
                try {
                    reader.setInput(image);String format=reader.getFormatName();
                    if(!format.equalsIgnoreCase(extension) && !(extension.equals("jpg")&&format.equalsIgnoreCase("JPEG")))
                        throw CourierException.invalid("Photo content does not match its extension.");
                    if((long)reader.getWidth(0)*reader.getHeight(0)>20_000_000L)throw CourierException.invalid("Photo dimensions are too large.");
                    if(reader.read(0)==null)throw CourierException.invalid("The image cannot be decoded.");
                } finally {reader.dispose();}
            }
            return new Photo(bytes,extension,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch(IOException e) {throw CourierException.invalid("The image cannot be decoded.");}
          catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    public String store(Photo photo) {
        if(photo==null)return null;
        String key=UUID.randomUUID()+"."+photo.extension();
        try {Files.createDirectories(root);Files.write(root.resolve(key),photo.bytes(),StandardOpenOption.CREATE_NEW);return key;}
        catch(IOException e){throw new CourierException(HttpStatus.SERVICE_UNAVAILABLE,"Proof storage is unavailable.");}
    }
    public Resource load(String key) {
        if(key==null||!key.matches("[a-f0-9-]{36}\\.(jpg|png)"))throw CourierException.missing("Photo not found.");
        Path file=root.resolve(key).normalize();
        if(!file.startsWith(root)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw CourierException.missing("Photo not found.");
        return new FileSystemResource(file);
    }
    public void discard(String key) {
        if(key==null||!key.matches("[a-f0-9-]{36}\\.(jpg|png)"))return;
        try{Files.deleteIfExists(root.resolve(key));}catch(IOException ignored){/* A retained orphan can be cleaned up offline. */}
    }
}
