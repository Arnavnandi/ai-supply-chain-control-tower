package com.supplychain.controltower.dto.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvImportHistoryDto {
    private Long id;
    private Long companyId;
    private String companyCode;
    private Long userId;
    private String username;
    private String filename;
    private String entityType;
    private String status;
    private Integer totalRows;
    private Integer recordsImported;
    private Integer recordsFailed;
    private String errorSummary;
    private List<String> errorList;
    private Long fileSizeBytes;
    private String fileHash;
    private boolean duplicateFile;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public String getOriginalFilename() {
        return filename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.filename = originalFilename;
    }
}
