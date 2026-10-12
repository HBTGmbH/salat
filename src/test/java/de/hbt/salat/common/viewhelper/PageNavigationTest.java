package de.hbt.salat.common.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.util.UriComponentsBuilder;

/** Paging through a list a page at a time (#1324): numbers from 1, links that keep the filter. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class PageNavigationTest {

    private static final UriComponentsBuilder TARGET = UriComponentsBuilder.fromPath("/etl/runs/7")
        .queryParam("onlyFailed", true);

    @Test
    void the_address_counts_from_one_and_spring_data_from_zero() {
        assertThat(PageNavigation.pageIndexOf(null)).isZero();
        assertThat(PageNavigation.pageIndexOf(1)).isZero();
        assertThat(PageNavigation.pageIndexOf(3)).isEqualTo(2);
        // from a foreign hand: the address
        assertThat(PageNavigation.pageIndexOf(0)).isZero();
        assertThat(PageNavigation.pageIndexOf(-4)).isZero();
    }

    @Test
    void a_page_in_the_middle_links_to_its_neighbours_and_keeps_the_filter() {
        var pages = PageNavigation.of(page(1, 10, 25), TARGET);

        assertThat(pages.isNeeded()).isTrue();
        assertThat(pages.getNumber()).isEqualTo(2);
        assertThat(pages.getTotalPages()).isEqualTo(3);
        assertThat(pages.getFirstShown()).isEqualTo(11);
        assertThat(pages.getLastShown()).isEqualTo(20);
        assertThat(pages.getFirstHref()).isEqualTo("/etl/runs/7?onlyFailed=true");
        assertThat(pages.getPreviousHref()).isEqualTo("/etl/runs/7?onlyFailed=true");
        assertThat(pages.getCurrentHref()).isEqualTo("/etl/runs/7?onlyFailed=true&page=2");
        assertThat(pages.getNextHref()).isEqualTo("/etl/runs/7?onlyFailed=true&page=3");
        assertThat(pages.getLastHref()).isEqualTo("/etl/runs/7?onlyFailed=true&page=3");
    }

    @Test
    void the_last_page_shows_what_is_left() {
        var pages = PageNavigation.of(page(2, 5, 25), TARGET);

        assertThat(pages.isLast()).isTrue();
        assertThat(pages.getFirstShown()).isEqualTo(21);
        assertThat(pages.getLastShown()).isEqualTo(25);
    }

    @Test
    void a_single_page_needs_no_navigation() {
        assertThat(PageNavigation.of(page(0, 4, 4), TARGET).isNeeded()).isFalse();
        var empty = PageNavigation.of(page(0, 0, 0), TARGET);
        assertThat(empty.isNeeded()).isFalse();
        assertThat(empty.getFirstShown()).isZero();
        assertThat(empty.getLastHref()).isEqualTo("/etl/runs/7?onlyFailed=true");
    }

    private static PageImpl<String> page(int index, int size, long total) {
        List<String> content = Collections.nCopies(size, "x");
        return new PageImpl<>(content, PageRequest.of(index, 10), total);
    }

}
