package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.apache.commons.lang3.builder.ToStringBuilder;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReceivingProductivityRequestDTO {

    private String userId;
    private String receivedFromDate;
    private String receivedToDate;
    private Integer page;
    private Integer limit;

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setReceivedFromDate(String receivedFromDate) {
        this.receivedFromDate = receivedFromDate;
    }

    public void setReceivedToDate(String receivedToDate) {
        this.receivedToDate = receivedToDate;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public ReceivingProductivityRequestDTO(
            String userId, String receivedFromDate, String receivedToDate, Integer page, Integer limit) {
        this.userId = userId;
        this.receivedFromDate = receivedFromDate;
        this.receivedToDate = receivedToDate;
        this.page = page;
        this.limit = limit;
    }

    public ReceivingProductivityRequestDTO() {}

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("userId", userId)
                .append("receivedFromDate", receivedFromDate)
                .append("receivedToDate", receivedToDate)
                .append("page", page)
                .append("limit", limit)
                .toString();
    }
}

