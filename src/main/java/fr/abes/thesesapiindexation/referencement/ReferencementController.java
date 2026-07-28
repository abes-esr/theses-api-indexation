package fr.abes.thesesapiindexation.referencement;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!init-index")
@RequestMapping("/api/v1/referencements")
public class ReferencementController {

    private final ReferencementWriteService service;

    public ReferencementController(ReferencementWriteService service) {
        this.service = service;
    }

    @PutMapping("/{identifiant}")
    ResponseEntity<ReferencementWriteResponse> write(
            @PathVariable String identifiant,
            @Valid @RequestBody ReferencementWriteRequest request
    ) {
        ReferencementWriteResult result =
                service.write(identifiant, request.toCommand());
        return ResponseEntity.ok(
                ReferencementWriteResponse.from(result)
        );
    }
}
