package de.hbt.salat.order.domain;

import de.hbt.salat.tag.domain.TagColor;

/**
 * A tag as the order and suborder forms and lists offer it (#847): its name and colour, and how often it is used
 * already — the count makes an existing tag the obvious pick over typing a new one.
 */
public record TagChoice(long id, String name, TagColor color, long customerorders, long suborders) {}
