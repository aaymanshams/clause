package com.clauseiq.contract;

import com.clauseiq.contract.ContractDtos.ContractDetail;
import com.clauseiq.contract.ContractDtos.ContractSummary;
import com.clauseiq.contract.ContractDtos.RiskReport;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ContractSummary upload(@RequestPart("file") MultipartFile file) {
        return contractService.upload(file);
    }

    @GetMapping
    public List<ContractSummary> list() {
        return contractService.list();
    }

    @GetMapping("/{id}")
    public ContractDetail get(@PathVariable Long id) {
        return contractService.get(id);
    }

    @GetMapping("/{id}/risks")
    public RiskReport risks(@PathVariable Long id) {
        return contractService.risks(id);
    }

    @PostMapping("/{id}/reprocess")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ContractSummary reprocess(@PathVariable Long id) {
        return contractService.reprocess(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        contractService.delete(id);
    }
}
