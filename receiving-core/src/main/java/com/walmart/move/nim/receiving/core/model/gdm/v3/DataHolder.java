package com.walmart.move.nim.receiving.core.model.gdm.v3;

import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpHeaders;

import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataHolder {
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    private DeliveryDocument deliveryDocument;
    private DeliveryDocumentLine deliveryDocumentLine;
    private DocumentLine documentLine;

    private SsccScanResponse.Container container;
    private List<String> hints;
    @Transient
    private String receivingFlow;

    @Transient
    private Instruction instruction;
    private  HttpHeaders httpHeaders;

    @Transient
    SsccScanResponse.Container gdmResponseForScannedData;

    private Integer quantityToBeReceivedInEaches;
    private Integer quantityToBeReceivedInVNPK;
    private List<Receipt> receipts;


    //TODO: Can this be enum ?
}
