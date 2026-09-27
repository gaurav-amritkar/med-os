package com.medos.controller;

import com.medos.dto.DispenseRequest;
import com.medos.entity.MedicineBatch;
import com.medos.entity.MedicineCatalog;
import com.medos.entity.StockTransaction;
import com.medos.modules.pharmacy.service.DispenseService;
import com.medos.modules.pharmacy.service.InventoryService;
import com.medos.modules.pharmacy.service.MedicineCatalogService;
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

/**
 * Pharmacy API. Delegates to the pharmacy bounded-context services:
 * {@link MedicineCatalogService} (catalog queries),
 * {@link InventoryService} (batches & stock), and
 * {@link DispenseService} (FEFO dispensing + charge posting).
 */
@RestController
@RequestMapping("/api/v1/pharmacy")
@RequiredArgsConstructor
public class PharmacyController {

    private final MedicineCatalogService medicineCatalogService;
    private final InventoryService inventoryService;
    private final DispenseService dispenseService;

    @GetMapping("/medicines")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MedicineCatalog>> listMedicines() {
        return ResponseEntity.ok(medicineCatalogService.listAllMedicines());
    }

    @GetMapping("/medicines/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MedicineCatalog> getMedicine(@PathVariable UUID id) {
        return ResponseEntity.ok(inventoryService.getMedicine(id));
    }

    @PostMapping("/medicines")
    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    public ResponseEntity<MedicineCatalog> createMedicine(@RequestBody MedicineCatalog med) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.createMedicine(med));
    }

    @GetMapping("/medicines/{id}/batches")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MedicineBatch>> getBatches(@PathVariable UUID id) {
        return ResponseEntity.ok(inventoryService.getBatches(id));
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
                .body(inventoryService.addStock(id, batchNo, expiryDate, quantity, purchasePrice, supplier));
    }

    @PostMapping("/dispense")
    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    public ResponseEntity<Void> dispense(@Valid @RequestBody DispenseRequest request) {
        dispenseService.dispense(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    public ResponseEntity<List<StockTransaction>> getTransactions(
            @RequestParam(required = false) UUID medicineId) {
        if (medicineId != null) {
            return ResponseEntity.ok(inventoryService.getStockLedger(medicineId));
        }
        return ResponseEntity.ok(inventoryService.getAllTransactions());
    }
}
