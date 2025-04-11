package com.walmart.move.nim.receiving.core.model;

import java.util.List;

public class Productivity {

    private List<ReceivingProductivityResponseDTO> productivity;

    private Integer totalPages;
    private Long totalElements;
    private Integer page;
    private Integer recordsPerPage;

    public List<ReceivingProductivityResponseDTO> getProductivity() {
        return productivity;
    }

    public void setProductivity(List<ReceivingProductivityResponseDTO> productivity) {
        this.productivity = productivity;
    }

    public Integer getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }

    public Long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(Long totalElements) {
        this.totalElements = totalElements;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getRecordsPerPage() {
        return recordsPerPage;
    }

    public void setRecordsPerPage(Integer recordsPerPage) {
        this.recordsPerPage = recordsPerPage;
    }


}
