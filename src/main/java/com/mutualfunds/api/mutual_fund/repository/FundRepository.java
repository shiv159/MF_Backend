package com.mutualfunds.api.mutual_fund.repository;

import com.mutualfunds.api.mutual_fund.entity.Fund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FundRepository extends JpaRepository<Fund, UUID> {

    Optional<Fund> findByIsin(String isin);

    List<Fund> findByFundNameContainingIgnoreCase(String name);

    List<Fund> findByFundCategoryAndDirectPlanTrueAndExpenseRatioLessThan(String category, Double maxER);

    @Query("SELECT f FROM Fund f WHERE f.fundCategory = :category AND f.directPlan = true AND f.expenseRatio < :maxER ORDER BY f.expenseRatio ASC")
    List<Fund> findTopFundsByCategoryAndExpenseRatio(@Param("category") String category, @Param("maxER") Double maxER);

    List<Fund> findByDirectPlanTrueAndExpenseRatioLessThanOrderByExpenseRatioAsc(double expenseRatio);
}