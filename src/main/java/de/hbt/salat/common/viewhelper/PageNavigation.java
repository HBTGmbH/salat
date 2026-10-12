package de.hbt.salat.common.viewhelper;

import org.springframework.data.domain.Page;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Paging through a list that comes from the database a page at a time (#1324), for the fragment
 * {@code fragments/pagination}.
 *
 * <p>The address counts pages from 1, as the page shows them; Spring Data counts from 0. The links
 * keep the filter they were built with: {@code target} carries its parameters, and only
 * {@value #PAGE_PARAM} is replaced.
 *
 * <p>Paging is the last resort (ui-style-guide §4.1): render in full, filter, limit — and only then
 * page. Only for a list that grows without bound, in which every entry matters and which no filter
 * alone makes small enough, such as the executions of one ETL run. A new use is justified in the
 * pull request.
 */
public final class PageNavigation {

    public static final String PAGE_PARAM = "page";

    private final Page<?> page;
    private final UriComponentsBuilder target;

    private PageNavigation(Page<?> page, UriComponentsBuilder target) {
        this.page = page;
        this.target = target.cloneBuilder();
    }

    /**
     * @param target the address of the list with its filter parameters, without the page
     */
    public static PageNavigation of(Page<?> page, UriComponentsBuilder target) {
        return new PageNavigation(page, target);
    }

    /** The index Spring Data expects for the page number of the address, which counts from 1. */
    public static int pageIndexOf(Integer pageNumber) {
        return pageNumber == null || pageNumber < 1 ? 0 : pageNumber - 1;
    }

    /** Whether there is anything to page through; a single page needs no navigation. */
    public boolean isNeeded() {
        return page.getTotalPages() > 1;
    }

    /** The page shown, counted from 1. */
    public int getNumber() {
        return page.getNumber() + 1;
    }

    public int getTotalPages() {
        return page.getTotalPages();
    }

    public long getTotalElements() {
        return page.getTotalElements();
    }

    /** The position of the first entry shown among all entries, counted from 1. */
    public long getFirstShown() {
        return page.isEmpty() ? 0 : page.getPageable().getOffset() + 1;
    }

    /** The position of the last entry shown among all entries. */
    public long getLastShown() {
        return page.isEmpty() ? 0 : page.getPageable().getOffset() + page.getNumberOfElements();
    }

    public boolean isFirst() {
        return page.isFirst();
    }

    public boolean isLast() {
        return page.isLast();
    }

    public String getFirstHref() {
        return hrefOf(1);
    }

    public String getPreviousHref() {
        return hrefOf(Math.max(getNumber() - 1, 1));
    }

    public String getCurrentHref() {
        return hrefOf(getNumber());
    }

    public String getNextHref() {
        return hrefOf(Math.min(getNumber() + 1, Math.max(getTotalPages(), 1)));
    }

    public String getLastHref() {
        return hrefOf(Math.max(getTotalPages(), 1));
    }

    /** The first page goes without the parameter: its address is the one of the list itself. */
    String hrefOf(int pageNumber) {
        var builder = target.cloneBuilder();
        builder.replaceQueryParam(PAGE_PARAM, pageNumber == 1 ? new Object[0] : new Object[]{pageNumber});
        return builder.build().encode().toUriString();
    }

}
