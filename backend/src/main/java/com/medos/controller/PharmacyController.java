package com.medos.controller;

import com.medos.dto.DispenseRequest;
import com.medos.entity.MedicineBatch;
import com.medos.entity.MedicineCatalog;
import com.medos.entity.StockTransaction;
import com.medos.service.PharmacyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pharmacy")
@RequiredArgsConstructor
public class PharmacyController {

    private final PharmacyService pharmacyService;

    @GetMapping("/medicines")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MedicineCatalog>> listMedicines() {
        return ResponseEntity.ok(pharmacyService.listAllMedicines());
    }

    @GetMapping("/medicines/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MedicineCatalog> getMedicine(@PathVariable UUID id) {
        return ResponseEntity.ok(pharmacyService.getMedicine(id));
    }

    @PostMapping("/medicines")
    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    public ResponseEntity<MedicineCatalog> createMedicine(@RequestBody MedicineCatalog med) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pharmacyService.createMedicine(med));
    }

    @GetMapping("/medicines/{id}/batches")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MedicineBatch>> getBatches(@PathVariable UUID id) {
        return ResponseEntity.ok(pharmacyService.getBatches(id));
    }

    @PostMapping("/medicines/{id}/stock-in")
    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    public ResponseEntity<MedicineBatch> addStock(
            @PathVariable UUID id,
            @RequestParam String batchNo,
            @RequestParam LocalDate expiryDate,
            @RequestParam int quantity,
            @RequestParam(required = false) BigDecimal purchasePrice,
            @RequestParam(required = false) String supplier) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pharmacyService.addStock(id, batchNo, expiryDate, quantity, purchasePrice, supplier));
    }

    @PostMapping("/dispense")
    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    public ResponseEntity<Void> dispense(@Valid @RequestBody DispenseRequest request) {
        pharmacyService.dispense(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    public ResponseEntity<List<StockTransaction>> getTransactions(
            @RequestParam(required = false) UUID medicineId) {
        if (medicineId != null) {
            return ResponseEntity.ok(pharmacyService.getStockLedger(medicineId));
        }
        return ResponseEntity.ok(pharmacyService.getAllTransactions());
    }
}
