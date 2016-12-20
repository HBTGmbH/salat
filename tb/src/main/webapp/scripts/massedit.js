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
			form.action = "/tb/do/ShowDailyReport?task=MassDelete&ids=" + ids.join(",");
			form.submit();
		} else {
			return false;
		}
	}
	
	$(document).ready(function() {
		$('input.massedit').each(function() {
			boxes.push(this);
		});
	});
	
	if(window.location.href.indexOf("task=MassDelete") > -1) {
		var newUrl = window.location.href.replace(/\?.*$/g, "");
		window.history.replaceState({}, newUrl, newUrl);
	}
	
	return {
		onChangeHandler: onChangeHandler,
		confirmDelete: confirmDelete,
		boxes: boxes
	};
})();