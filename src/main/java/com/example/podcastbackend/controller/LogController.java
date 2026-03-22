package com.example.podcastbackend.controller;

import com.example.podcastbackend.log.ClickLogService;
import com.example.podcastbackend.request.ClickLogRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/log")
@Tag(name = "Log", description = "Client-side event logging APIs")
public class LogController {

    private final ClickLogService clickLogService;

    public LogController(ClickLogService clickLogService) {
        this.clickLogService = clickLogService;
    }

    @PostMapping("/click")
    @Operation(summary = "Log a click event", description = "Records a user click on an episode search result. Fire-and-forget: always returns 200 on valid input.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Click logged successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or missing required fields")
    })
    public ResponseEntity<Void> logClick(@Valid @RequestBody ClickLogRequest request) {
        clickLogService.logClick(request);
        return ResponseEntity.ok().build();
    }
}
