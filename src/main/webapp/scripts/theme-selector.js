(function (factory) {
	typeof define === 'function' && define.amd ? define(factory) :
	factory();
})((function () { 'use strict';

	var themeStorageKey = "salatTheme";
	var defaultTheme = "light";
	var storedTheme = localStorage.getItem(themeStorageKey);
	var selectedTheme = storedTheme ? storedTheme : defaultTheme;
	if (selectedTheme === 'dark') {
	  document.body.setAttribute("data-bs-theme", selectedTheme);
	} else {
	  document.body.removeAttribute("data-bs-theme");
	}

}));

function selectTheme(theme) {
	var themeStorageKey = "salatTheme";
	var defaultTheme = "light";
	var selectedTheme = defaultTheme;
	if (theme === 'dark') {
		selectedTheme = theme;
	}
	document.body.setAttribute("data-bs-theme", selectedTheme);
	localStorage.setItem(themeStorageKey, selectedTheme);
}