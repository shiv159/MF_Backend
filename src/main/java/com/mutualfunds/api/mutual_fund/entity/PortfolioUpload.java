package com.mutualfunds.api.mutual_fund.entity;

import com.mutualfunds.api.mutual_fund.enums.UploadStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "portfolio_uploads")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "upload_id")
    private UUID uploadId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "upload_date")
    private LocalDateTime uploadDate;

    @Enumerated(EnumType.STRING)
    private UploadStatus status;

    @Column(name = "parsed_holdings_count")
    private Integer parsedHoldingsCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}