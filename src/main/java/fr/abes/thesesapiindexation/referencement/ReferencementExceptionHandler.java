package fr.abes.thesesapiindexation.referencement;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class ReferencementExceptionHandler {

    @ExceptionHandler(ReferencementValidationException.class)
    ProblemDetail validationFailure(
            ReferencementValidationException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Requête de référencement invalide",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail bodyValidationFailure(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String detail = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> "Le champ " + error.getField()
                        + " " + error.getDefaultMessage())
                .orElse("Le corps de la requête est invalide");
        return problem(
                HttpStatus.BAD_REQUEST,
                "Requête de référencement invalide",
                detail,
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadableBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Requête de référencement invalide",
                "Le corps JSON est invalide",
                request
        );
    }

    @ExceptionHandler(ReferencementDocumentAccessException.class)
    ProblemDetail elasticsearchFailure(
            ReferencementDocumentAccessException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Elasticsearch indisponible",
                exception.getMessage(),
                request
        );
    }

    private ProblemDetail problem(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                detail
        );
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
