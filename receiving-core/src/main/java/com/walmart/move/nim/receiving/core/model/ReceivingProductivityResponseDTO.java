package com.walmart.move.nim.receiving.core.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@Data
@ToString
@Getter
@Setter
public class ReceivingProductivityResponseDTO {

        private String receiverUserId;

        private String receivedDate;

        private Long casesReceived;

        private Long palletsReceived;

        public ReceivingProductivityResponseDTO() {}

        public ReceivingProductivityResponseDTO(String receiverUserId, String receivedDate, Long casesReceived, Long palletsReceived) {
            this.receiverUserId = receiverUserId;
            this.receivedDate = receivedDate;
            this.casesReceived = casesReceived;
            this.palletsReceived = palletsReceived;
        }
    }

