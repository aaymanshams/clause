package com.clauseiq.ai.rag;

import com.clauseiq.ai.rag.ChatDtos.ChatRequest;
import com.clauseiq.ai.rag.ChatDtos.ChatResponse;
import com.clauseiq.security.TenantContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final RagService ragService;

    public ChatController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/api/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return ragService.ask(TenantContext.currentTenantId(), request.question(), request.contractId());
    }
}
