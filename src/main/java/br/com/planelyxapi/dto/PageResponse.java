package br.com.planelyxapi.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * A stable page envelope.
 *
 * Spring's own {@code Page}/{@code PageImpl} is deliberately not returned from controllers —
 * its JSON shape is documented as unstable and serializing it emits a warning. This record
 * pins the contract the UI codes against.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
