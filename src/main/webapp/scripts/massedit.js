if(typeof HBT === "undefined") HBT = {};

HBT.MassEdit = HBT.MassEdit || (function(){
	
	var boxes = []; // collection of the mass edit boxes
	
	// called, when a mass edit checkbox has benn clicked
	var onChangeHandler = function() {
		var isAnyChecked = false;
		boxes.forEach(function(box) {
			if(box.checked) {
				isAnyChecked = true;
			}
		});
		
		if(isAnyChecked) {
			// a checkbox has been checked
			$('td.massedit').removeClass('invisible');
		} else {
			// no checkbox is checked
			$('td.massedit').addClass('invisible');
		}
	}
	
	// ask the user if he really wants to mass-delete selected bookings
	var confirmDelete = function(form, confirmMsg) {
		var confirmation = confirm(confirmMsg);
		if (confirmation) {
			var ids = [];
			boxes.forEach(function(box) {
				if(box.checked) {
					var id = box.id.replace("massedit_", "");
					ids.push(id);
				}
			});
			form.action = "/do/ShowDailyReport?task=MassDelete&ids=" + ids.join(",");
			form.submit();
		} else {
			return false;
		}
	}
	
	var findForm = function(item) {
		while(item) {
			if(item.tagName.toLowerCase() == "form") {
				return item;
			}
			item = item.parentElement;
		}
		return null;
	}
	
	var confirmShiftDays = function(element, confirmMsg, days) {
		var form = findForm(element);
		if(form) {
			var confirmation = confirm(confirmMsg);
			if (confirmation) {
				var ids = [];
				boxes.forEach(function(box) {
					if(box.checked) {
						var id = box.id.replace("massedit_", "");
						ids.push(id);
					}
				});
				form.action = "/do/ShowDailyReport?task=MassShiftDays&ids=" + ids.join(",") + "&byDays=" + days;
				form.submit();
			} else {
				return false;
			}
		} else {
			return false;
		}
	}
	
	$(document).ready(function() {
		initializeMasseditCheckboxes();
		$('input.massedit').each(function() {
			boxes.push(this);
		});
		onChangeHandler();
	});
	
	var getParameter = function() {
		var sPageURL = decodeURIComponent(window.location.search.substring(1));
		var sURLVariables = sPageURL.split('&');

		for (var i = 0; i < sURLVariables.length; i++) {
			var sParameterName = sURLVariables[i].split('=');

			if (sParameterName[0] === "ids") {
				return sParameterName[1].split(",");
	        }
	    }
	}
	
	var ids = getParameter();
	var initializeMasseditCheckboxes = function() {};
	
	if(typeof failedMassEditIds != "undefined" && failedMassEditIds != null && failedMassEditIds.length != 0) {
		initializeMasseditCheckboxes = function() {
			if(ids) {
				for(var i = 0; i < ids.length; i++) {
					var id = ids[i];
					var query = $('input#massedit_'+id);
					query.each(function() {
						this.checked = true;
					});
				}
			}
			
			for(var i = 0; i < failedMassEditIds.length; i++) {
				var failedId = failedMassEditIds[i];
				var query = $('span#span-massedit-'+failedId);
				query.addClass('massedit-error');

				var query = $('input#massedit_'+failedId);
				query.each(function() {
					this.title = cannotShiftReportsMsg;
				});
			}
		};
	} else {
		var href = window.location.href;
		
		if(href.indexOf("task=MassShiftDays") > -1 || href.indexOf("task=MassDelete") > -1) {
			var newUrl = href.replace(/\?.*$/g, "");
			window.history.replaceState({}, newUrl, newUrl);
		}
	}
	
	return {
		onChangeHandler: onChangeHandler,
		confirmDelete: confirmDelete,
		confirmShiftDays: confirmShiftDays,
		boxes: boxes
	};
})();
