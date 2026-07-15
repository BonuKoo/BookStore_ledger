package com.bookService.ledger.repository;

import com.bookService.ledger.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
}
