package br.com.fintrackapi.web;

import br.com.fintrackapi.dto.DashboardResponse;
import br.com.fintrackapi.security.CurrentUser;
import br.com.fintrackapi.service.DashboardService;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUser currentUser;

    /**
     * @param month {@code yyyy-MM}; defaults to the current month. Any month is valid, including
     *     future ones — balances are cumulative, so a later month reads as a forecast over the
     *     installments and recurring occurrences already scheduled.
     */
    @GetMapping
    public DashboardResponse forMonth(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return dashboardService.forMonth(currentUser.ownerId(), month != null ? month : YearMonth.now());
    }
}
