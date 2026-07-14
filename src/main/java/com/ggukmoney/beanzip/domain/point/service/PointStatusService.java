package com.ggukmoney.beanzip.domain.point.service;

import com.ggukmoney.beanzip.domain.point.dto.response.PointLedgerItemResponse;
import com.ggukmoney.beanzip.domain.point.dto.response.PointLedgerPageResponse;
import com.ggukmoney.beanzip.domain.point.dto.response.PointMeResponse;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.entity.PointLedger;
import com.ggukmoney.beanzip.domain.point.repository.PointLedgerRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PointStatusService {

    private static final int MINIMUM_CASHOUT_POINT = 10;
    private static final double CASHOUT_KRW_RATE = 0.7;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final PointAccountService pointAccountService;
    private final PointLedgerRepository pointLedgerRepository;

    public PointMeResponse getMyPoints(UUID userId) {
        PointAccount account = pointAccountService.getForUser(userId);
        long balance = account.getBalance();
        boolean cashoutEligible = balance >= MINIMUM_CASHOUT_POINT;
        long estimatedKrw = (long) Math.floor(balance * CASHOUT_KRW_RATE);

        return new PointMeResponse(
                balance,
                account.getLifetimeEarned(),
                account.getLifetimeSpent(),
                cashoutEligible,
                MINIMUM_CASHOUT_POINT,
                estimatedKrw
        );
    }

    public PointLedgerPageResponse getLedger(
            UUID userId,
            String cursor,
            Integer size,
            PointLedger.EntryType entryType,
            String reason,
            Instant from,
            Instant to
    ) {
        int pageSize = clampPageSize(size);
        Long cursorId = decodeCursor(cursor);

        Specification<PointLedger> spec = buildSpecification(userId, cursorId, entryType, reason, from, to);
        List<PointLedger> fetched = pointLedgerRepository.findAll(
                spec, PageRequest.of(0, pageSize + 1, Sort.by(Sort.Direction.DESC, "id"))
        ).getContent();

        boolean hasMore = fetched.size() > pageSize;
        List<PointLedger> page = hasMore ? fetched.subList(0, pageSize) : fetched;

        List<PointLedgerItemResponse> items = page.stream()
                .map(ledger -> new PointLedgerItemResponse(
                        ledger.getPublicId(),
                        ledger.getEntryType().name(),
                        ledger.getAmount(),
                        ledger.getReason(),
                        ledger.getCreatedAt()
                ))
                .collect(Collectors.toList());

        String nextCursor = hasMore ? encodeCursor(page.get(page.size() - 1).getId()) : null;

        return new PointLedgerPageResponse(items, nextCursor, hasMore);
    }

    private Specification<PointLedger> buildSpecification(
            UUID userId, Long cursorId, PointLedger.EntryType entryType, String reason, Instant from, Instant to
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));
            if (cursorId != null) {
                predicates.add(cb.lessThan(root.get("id"), cursorId));
            }
            if (entryType != null) {
                predicates.add(cb.equal(root.get("entryType"), entryType));
            }
            if (reason != null) {
                predicates.add(cb.equal(root.get("reason"), reason));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private int clampPageSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private String encodeCursor(Long id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(id).getBytes(StandardCharsets.UTF_8));
    }

    private Long decodeCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return Long.parseLong(decoded);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMMON_VALIDATION_ERROR", exception);
        }
    }
}
