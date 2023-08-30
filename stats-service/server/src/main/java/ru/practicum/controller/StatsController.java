package ru.practicum.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.practicum.RequestDto;
import ru.practicum.RequestOutputDto;
import ru.practicum.service.StatsService;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class StatsController {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StatsService statsService;

    @PostMapping("/hit")
    @ResponseStatus(code = HttpStatus.CREATED)
    public void addRequest(@Valid @RequestBody RequestDto requestDto) {
        log.info("Добавление запроса в статистику: " + requestDto.toString());
        statsService.addRequest(requestDto);
    }

    @GetMapping("/stats")
    public ResponseEntity<List<RequestOutputDto>> getStats(@RequestParam String start,
                                                           @RequestParam String end,
                                                           @RequestParam(required = false) List<String> uris,
                                                           @RequestParam(defaultValue = "false") Boolean unique) {

        LocalDateTime startDT;
        LocalDateTime endDT;
        try {
            startDT = LocalDateTime.parse(start, DTF);
            endDT = LocalDateTime.parse(end, DTF);
        } catch (DateTimeParseException e) {
            log.error("Ошибка формата даты. Ожидается yyyy-MM-dd HH:mm:ss");
            return ResponseEntity.badRequest().build();
        }

        List<RequestOutputDto> results = statsService.getRequestsWithViews(startDT, endDT, uris, unique);
        return ResponseEntity.ok().body(results);
    }
}
