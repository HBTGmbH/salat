package org.tb.dailyreport.controller;

import java.time.Duration;

record FavoriteView(Long id, String label, String comment, Duration duration) {}
