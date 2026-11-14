package com.swiftdrop.courier.service;
import com.swiftdrop.courier.exception.CourierException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;

class ProofStorageServiceTest {
    @TempDir Path directory;
    @Test void savesRealImageUsingGeneratedNameAndDiscardsIt() throws Exception {
        var storage=new ProofStorageService(directory.toString());var bytes=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);
        var image=storage.validate(new MockMultipartFile("photo","../../evidence.png","image/png",bytes.toByteArray()));
        var key=storage.store(image);
        assertThat(key).matches("[a-f0-9-]{36}\\.png");assertThat(storage.load(key).exists()).isTrue();
        storage.discard(key);assertThatThrownBy(()->storage.load(key)).isInstanceOf(CourierException.class);
    }
    @Test void rejectsFakeImagesBadExtensionsOversizeAndPaths() {
        var storage=new ProofStorageService(directory.toString());
        assertThatThrownBy(()->storage.validate(new MockMultipartFile("photo","x.jpg","image/jpeg","not an image".getBytes()))).isInstanceOf(CourierException.class);
        assertThatThrownBy(()->storage.validate(new MockMultipartFile("photo","x.exe","image/png",new byte[]{1}))).isInstanceOf(CourierException.class);
        assertThatThrownBy(()->storage.validate(new MockMultipartFile("photo","x.png","image/png",new byte[ProofStorageService.MAX_BYTES+1]))).isInstanceOf(CourierException.class);
        assertThatThrownBy(()->storage.load("../../secret.jpg")).isInstanceOf(CourierException.class);
    }
}
