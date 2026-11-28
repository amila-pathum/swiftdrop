package com.swiftdrop.courier.photo;

import com.swiftdrop.courier.entity.DeliveryPhotoUpload;
import com.swiftdrop.courier.exception.CourierException;
import com.swiftdrop.courier.repository.DeliveryPhotoUploadRepository;
import org.junit.jupiter.api.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeliveryPhotoServiceTest {
    PhotoStorage storage;DeliveryPhotoUploadRepository uploads;DeliveryPhotoService photos;
    UUID parcelId=UUID.randomUUID();Map<String,DeliveryPhotoUpload> saved;

    @BeforeEach void setup() {
        storage=mock(PhotoStorage.class);uploads=mock(DeliveryPhotoUploadRepository.class);
        photos=new DeliveryPhotoService(storage,uploads);saved=new HashMap<>();
        when(uploads.save(any())).thenAnswer(i->{DeliveryPhotoUpload u=i.getArgument(0);saved.put(u.photoKey,u);return u;});
        when(uploads.findById(any())).thenAnswer(i->Optional.ofNullable(saved.get(i.<String>getArgument(0))));
        when(uploads.lockByKey(any())).thenAnswer(i->Optional.ofNullable(saved.get(i.<String>getArgument(0))));
        when(storage.presignUpload(any(),any(),anyLong())).thenReturn(new PhotoStorage.UploadTicket("https://signed",Map.of("Content-Type","image/png"),Instant.now()));
    }
    static byte[] png() throws Exception {
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();
    }

    @Test void issuesAServerChosenKeyScopedToTheParcel() {
        var ticket=photos.issue(10L,parcelId,"image/PNG",1234);
        assertThat(ticket.photoKey()).matches("proofs/"+parcelId+"/[a-f0-9-]{36}\\.png");
        assertThat(ticket.uploadUrl()).isEqualTo("https://signed");
        verify(storage).presignUpload(ticket.photoKey(),"image/png",1234);
        assertThat(saved.get(ticket.photoKey()).courierId).isEqualTo(10L);
    }
    @Test void refusesOtherTypesAndOversizeFilesBeforeSigning() {
        assertThatThrownBy(()->photos.issue(10L,parcelId,"application/pdf",10)).hasMessageContaining("JPG and PNG");
        assertThatThrownBy(()->photos.issue(10L,parcelId,"image/png",0)).hasMessageContaining("empty");
        assertThatThrownBy(()->photos.issue(10L,parcelId,"image/jpeg",DeliveryPhotoService.MAX_BYTES+1)).hasMessageContaining("5 MB");
        verifyNoInteractions(storage);
    }
    @Test void acceptsARealImageOnlyForItsOwnerParcelAndOnce() throws Exception {
        var key=photos.issue(10L,parcelId,"image/png",100).photoKey();
        when(storage.read(eq(key),anyInt())).thenReturn(png());
        assertThatThrownBy(()->photos.verify(11L,parcelId,key)).hasMessageContaining("another delivery");
        assertThatThrownBy(()->photos.verify(10L,UUID.randomUUID(),key)).hasMessageContaining("another delivery");
        assertThatThrownBy(()->photos.verify(10L,parcelId,"proofs/unknown.png")).hasMessageContaining("Unknown photo");
        photos.verify(10L,parcelId,key);
        photos.markUsed(key);
        assertThatThrownBy(()->photos.verify(10L,parcelId,key)).hasMessageContaining("already attached");
        assertThatThrownBy(()->photos.markUsed(key)).hasMessageContaining("already attached");
    }
    @Test void anUnfinishedUploadIsReportedAndAFakeImageIsDeleted() {
        var key=photos.issue(10L,parcelId,"image/jpeg",100).photoKey();
        when(storage.read(eq(key),anyInt())).thenReturn(null);
        assertThatThrownBy(()->photos.verify(10L,parcelId,key)).hasMessageContaining("did not finish");
        when(storage.read(eq(key),anyInt())).thenReturn("not an image".getBytes());
        assertThatThrownBy(()->photos.verify(10L,parcelId,key)).isInstanceOf(CourierException.class).hasMessageContaining("not a supported image");
        verify(storage).delete(key);verify(uploads).delete(saved.get(key));
    }
    @Test void pngContentUnderAJpegTypeIsRejected() throws Exception {
        var key=photos.issue(10L,parcelId,"image/jpeg",100).photoKey();
        when(storage.read(eq(key),anyInt())).thenReturn(png());
        assertThatThrownBy(()->photos.verify(10L,parcelId,key)).hasMessageContaining("does not match");
    }
    @Test void removesUploadsThatWereNeverAttached() {
        var stale=new DeliveryPhotoUpload();stale.photoKey="proofs/p/old.png";
        when(uploads.findTop100ByUsedAtIsNullAndCreatedAtBefore(any())).thenReturn(List.of(stale));
        photos.removeUnused();
        verify(storage).delete("proofs/p/old.png");verify(uploads).delete(stale);
    }
}
