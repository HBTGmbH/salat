if(typeof HBT === "undefined") HBT = {};
var stored;
HBT.Salat = HBT.Salat || {};
HBT.Salat.FavouriteOrders = HBT.Salat.FavouriteOrders || function() {
	var stored = null;
	var user = null;
	var lang = null;
	
	var getOptionById = function(options, id) {
		for(var i = 0; i < options.length; i++) {
			if(options[i].value == id) {
				return options[i].innerHTML;
			}
		}
		return null;
	}
	
	var setSuborder = function(order, suborder, isSetting) {
		if(!checkLocalstorageConsent()) return false;
		if(isSetting) {
			user.defaultSuborders[order] = suborder;
		} else {
			delete user.defaultSuborders[order];
		}
		localStorage.setItem("SALAT", JSON.stringify(stored));
		return true;
	}
	
	var setOrder = function(order, isSetting) {
		if(!checkLocalstorageConsent()) return false;
		if(isSetting) {
			user.defaultOrder = order;
		} else {
			delete user.defaultOrder;
		}
		localStorage.setItem("SALAT", JSON.stringify(stored));
		return true;
	}
	
	var checkLocalstorageConsent = function() {
		if(localStorage.getItem("SALAT") == null) {
			return confirm(lang.localStorageMsg);
		}
		return true;
	}
	
	var getOrder = function(combo) {
		var order = user.defaultOrder;
		if(!order) return null;
		
		for(var i = 0; i < combo.options.length; i++) {
			var option = combo.options[i];
			if(option.value == order) {
				return order;
			}
		}
		return null;
	}
	
	var getSuborder = function(combo, order) {
		var suborder = user.defaultSuborders[order];
		if(!suborder) return null;
		
		for(var i = 0; i < combo.options.length; i++) {
			var option = combo.options[i];
			if(option.value == suborder) {
				return suborder;
			}
		}
		return null;
	}
	
	var evaluatePlaceholderStr = function() {
		var work = arguments[0];
		for(var i = 1; i < arguments.length; i++) {
			work = work.replace("{" + (i-1) + "}", arguments[i]);
		}
		return work;
	}
	
	return {
		initialize: function(langRes) {
			lang = langRes;
			stored = JSON.parse(localStorage.getItem("SALAT")) || {
				users: {}
			};
			stored.users[currentUser] = stored.users[currentUser] || {
				defaultSuborders: {}	
			};
			user = stored.users[currentUser];
		},
		getDefaultSuborder: function(orderIndex) {
			return user.defaultSuborders[orderIndex];
		},
		actionOrderSet: function(img) {
			var elem = $(".orderCls")[0];
			if(elem) {
				var selected = elem.options[elem.selectedIndex].value;
				if(img.getAttribute("data-isFav") == "true") {
					if(!setOrder(selected, false)) return;
					img.src = "/tb/images/Button/whiteStar.svg"
					img.title = lang.noDefaultOrder;
					img.removeAttribute("data-isFav");
				} else {
					if(!setOrder(selected, true)) return;
					img.src = "/tb/images/Button/goldStar.svg"
					img.title = lang.thisIsTheDefaultOrder;
					img.setAttribute("data-isFav", "true");
				}
			}
		},
		actionSuborderSet: function(img) {
			var elemOrder = $(".orderCls")[0];
			var elemSuborder = $(".suborderCls")[0];
			if(elemOrder && elemSuborder) {
				var selectedOrder = elemOrder.options[elemOrder.selectedIndex].value;
				var selectedSuborder = elemSuborder.options[elemSuborder.selectedIndex].value;
				if(img.getAttribute("data-isFav") == "true") {
					if(!setSuborder(selectedOrder, selectedSuborder, false)) return;
					img.src = "/tb/images/Button/whiteStar.svg"
					img.title = lang.noDefaultSuborder;
					img.removeAttribute("data-isFav");
				} else {
					if(!setSuborder(selectedOrder, selectedSuborder, true)) return;
					img.src = "/tb/images/Button/goldStar.svg"
					img.title = lang.thisIsTheDefaultSuborder;
					img.setAttribute("data-isFav", "true");
				}
			}
		},
		initializeOrderSelection: function() {
			var combo = $(".orderCls");
			var elem = combo.select2({
				dropdownAutoWidth: true,
				width: 'element',
			});
			var defaultOrder = getOrder(combo[0]);
			if(defaultOrder == null) return;
			
			if(document.URL.endsWith("CreateDailyReport")) {
				var isSamePage = false;
			} else {
				var isSamePage = document.referrer.endsWith("addDailyReport.jsp") || document.referrer.indexOf("continue") > -1;
			}
			
			if(combo[0]) {
				var selected = combo[0].options[combo[0].selectedIndex].value;
				if(!isSamePage && selected != defaultOrder) {
					elem.val(defaultOrder).trigger("change.select2");
				} else if(selected == defaultOrder) {
					$("#favOrderBtn").attr("src", "/tb/images/Button/goldStar.svg");
					$("#favOrderBtn").attr("title", lang.thisIsTheDefaultOrder);
				} else {
					$("#favOrderBtn").attr("src", "/tb/images/Button/bleachedStar.svg");
					$("#favOrderBtn").attr("title", evaluatePlaceholderStr(lang.otherIsTheDefaultOrder, getOptionById(combo[0].options, defaultOrder)));
				}
			}
		},
		initializeSuborderSelection: function() {
			var comboOrder = $(".orderCls");
			var comboSuborder = $(".suborderCls");
			var elem = comboSuborder.select2({
				dropdownAutoWidth: true,
				width: 'element',
			});
			var selectedOrder = comboOrder[0].options[comboOrder[0].selectedIndex].value;
			var defaultSuborder = getSuborder(comboSuborder[0], selectedOrder);
			
			if(defaultSuborder == null) return;
			
			if(document.URL.endsWith("CreateDailyReport")) {
				var isSamePage = false;
			} else {
				var isSamePage = document.referrer.endsWith("addDailyReport.jsp") || document.referrer.indexOf("continue") > -1;
			}
			
			if(comboSuborder[0]) {
				var selectedSuborder = comboSuborder[0].options[comboSuborder[0].selectedIndex].value;
				if(!isSamePage && selectedSuborder != defaultSuborder) {
					elem.val(defaultSuborder).trigger("change.select2");
				} else if(selectedSuborder == defaultSuborder) {
					$("#favSuborderBtn").attr("src", "/tb/images/Button/goldStar.svg");
					$("#favSuborderBtn").attr("title", lang.thisIsTheDefaultSuborder);
				} else {
					$("#favSuborderBtn").attr("src", "/tb/images/Button/bleachedStar.svg");
					$("#favSuborderBtn").attr("title", evaluatePlaceholderStr(lang.otherIsTheDefaultSuborder, getOptionById(comboSuborder[0].options, defaultSuborder)));
				}
			}

		}
	};
}();
