
//$(document).ready(function() {
	
	$(document).on('pageinit', '#bookingPage', function(event){
		
		console.log('init page: ' + event.target.id);
		
	var booking = (function() {
		var $orderSelect           = $('#orderSelect');
		var $bookingForm           = $('#bookingForm');
		var $inputHours            = $('input[name*=hours]');
		var $inputMinutes          = $('input[name*=minutes]');
		var $bookingMissingPage    = $('#bookingMissingPage');
		var $bookingSuccessPage    = $('#bookingSuccessPage');
		var $bookingOverviewPage   = $('#bookingOverviewPage');
		var $bookingOverviewDialog = $('#bookingOverviewDialog');
		var $bookingPage           = $('#bookingPage');
		var $bookingFooter         = $('#bookingFooter');
		var $bookingOverviewFooter = $('#bookingOverviewFooter');
		var $bookingSubmit         = $('#bookingSubmit');
		var $commentInput          = $('#commentInput');
		var $commentInputDiv        = $commentInput.parent();
		var $commentMissingPage    = $('#commentMissingPage');
		var $thisBookingHours      = $('#thisBookingHours');
		var $thisBookingMinutes    = $('#thisBookingMinutes');
		var $thisBookingSuborder   = $('#thisBookingSuborder');
		var $hiddenTimereportId    = $('#hiddenTimereportId');
		//Constants
		var NOT_SELECTED           = 'choose'; 
		var URLBASE                = '../../do/mobile/';
		//Call urls
		var getTimereportUrl       = 'timereport/getTimereport';
		var getTimereportsUrl      = 'timereport/getTimereports';
		var getSubordersUrl        = 'suborder/getSuborders';
		var bookingSubmitUrl       = 'timereport/store';
		//Context and states 
		var minutesRadioState      = {};
		var hoursRadioState        = {};

		// Reseting the state of radio buttons,submit button, select menu and comment input
		var resetBookingForm = function() {
			$bookingSubmit.prop('value','Speichern').prev('span').find('span.ui-btn-text').text("Buchen");
			$('input:checked').prop('checked',false).checkboxradio('refresh');
			$orderSelect.val(NOT_SELECTED).selectmenu('refresh');
			$commentInputDiv.removeClass('required');
			$hiddenTimereportId.val('');
			$commentInput.val('');
		},
		//General ajax call function
		ajaxCall = function(callUrl, callType, dataToSend, successFunction){
			$.ajax({
				url : buildUrl(callUrl),
				type : callType,
				datatype : 'json',
				data: dataToSend,
				success : function(data) {
					successFunction.apply(data);            		
        		}
			});  
		},
		buildUrl = function(url){
			return URLBASE + url;
		},
		//Filling the option of the selector with entries from the response data
		fillSubordersOptions = function(){
			var tempData = this;
			output = [];            	
			$.each(tempData, function(key, value) {
				output.push('<option value="' + key + '" data-comreq="'+value[1]+'" >'
					+ value[0] + '</option>');
			});
			$orderSelect.append(output.join('')).selectmenu('refresh');
		},
		// Getting the list of the suborders
		getSuborders = function(){
			if($orderSelect.children().length == 1){
				ajaxCall(getSubordersUrl,'GET', '',fillSubordersOptions);
        	}    	   	
		},
		setBookingSuccessPage = function(){
			var tempData = this;
			if (tempData.isValid) {
				//Setting the values for the booking success page
				$thisBookingHours.html(tempData.hours);
				$thisBookingMinutes.html(tempData.minutes);
				$thisBookingSuborder.html(tempData.suborder);
				$.mobile.changePage($bookingSuccessPage, {role:'dialog'});
				resetBookingForm();
				} else {
				alert('Ein Fehler ist aufgetretten. Kontaktieren Sie bitte den Administator!');
				}
		},
		//Check if required fields are filled out
		checkBookingformFields = function(){
	    	if ($('input:checked').length == 0) {
	    		return 'radioOrSuborderMissing';
	    		}
	    	if ($orderSelect.val() == NOT_SELECTED) {
	    		return 'radioOrSuborderMissing';
	    		}
	    	if($('.required').children('input').val() == ''){
	    		return 'commentMissing';
	    	}
	    	return 'Ok';
		},
		getTimereportsSuccessFunction = function(){
			var tempData = this;
        	//Delete the content of the booking overview first
        	$bookingOverviewDialog.empty();
        	//if no booking were done this day before
        	if(tempData.length==0){
        		$bookingOverviewDialog.append("<h2>Heute haben Sie noch keine Buchungen vorgenommen!</h2>");
            }
        	//Otherwise run through the json data and fill a list with all the bookings
        	appendTimereportsList(tempData);
        	handleTimereportEdit();

		},
		//Gets all timereports for this day and creates a list 
		getTimereports = function(){
			ajaxCall(getTimereportsUrl,'GET', '', getTimereportsSuccessFunction);  
		},
		handleTimereportEdit = function(){
    		$(document).on('click', '#BookingsOverviewList a', function(){        		
        		var timereportId = $(this).data('id');
        		$hiddenTimereportId.val(timereportId);
        		$bookingSubmit.prop('value','Speichern').prev('span').find('span.ui-btn-text').text("Speichern");          		
        		$.mobile.changePage($bookingPage);
        		getTimereport(timereportId);
        	});	
		},
		//Generate a html string of a  timereports list from response data
		generateTimereportsListHtml = function(tempData){
			var output = [];
    		$.each(tempData, function(key, value) {
    			output.push('<li><a data-id="' + key +'">Auftrag: ' + value[0]+'-- ' + value[1] + ':' + value[2] + '</a></li>');
    		});
    		return output.join('');
		},
		//Create a list view with the timereport data and append it to the overview page
		appendTimereportsList = function(tempData){
			$(document.createElement('ul')).data('role','listview').data('inset', 'true')
			.prop('id', 'BookingsOverviewList').html(generateTimereportsListHtml(tempData)).appendTo($bookingOverviewDialog);
			$('#BookingsOverviewList').listview();
		},
	    //Set the values in the booking form to the values of the selected timereport
	    showSelectedTimereport = function(){
	    	var tempData = this;
			$orderSelect.val(tempData.suborderId).change().selectmenu('refresh');
			$commentInput.val(tempData.timereportComment);
			$.each($inputHours, function(){
				if($(this).val()==tempData.timereportHours){
					$(this).prop('checked', 'checked').checkboxradio('refresh');
				}
			});
			$.each($inputMinutes, function(){
				if($(this).val()==tempData.timereportMinutes){
					$(this).prop('checked', 'checked').checkboxradio('refresh');
				}
			});
	    },
	    //Gets the clicked timereport
	    getTimereport = function(timereportId){
			ajaxCall(getTimereportUrl,'GET', {'timereportId' : timereportId} , showSelectedTimereport);			   	
	    }
	    ;
	    $bookingPage.on('pagebeforeshow', function(){
        	$bookingFooter.find('a').first().removeClass('ui-btn-active').addClass('ui-btn-active');        	       	
        });
		//Setting the Booking Overview Page (defining event handlers)	    
		$bookingOverviewPage.on('pagebeforeshow', function(){
			//Highlight the proper button in the footer
	    	$(this).find('a').last().removeClass('ui-btn-active').addClass('ui-btn-active');
	    	resetBookingForm();
	    	//Delete all set clickhandlers for the list of timereports 
	    	if($('#BookingsOverviewList').length > 0) {
	    		$(document).off('click', '#BookingsOverviewList a');
	    	}    	
	    	getTimereports();    	
		});	
	    //Making time buttons unklickable
	    $inputHours.on('click', function (){
	    	if (hoursRadioState && hoursRadioState.value == this.value) {
	    		$(this).prop('checked', !hoursRadioState.status).checkboxradio('refresh');
	    		}
	    	hoursRadioState = {
	    			value : this.value,
	    			status : this.checked
	    			};
	    });
	    $inputMinutes.on('click', function (){
	    	if (minutesRadioState && minutesRadioState.value == this.value) {
	    		$(this).prop('checked', !minutesRadioState.status).checkboxradio('refresh');
	    		}
	    	minutesRadioState = {
	    			value : this.value,
	    			status : this.checked
	    			};
	    });
	    //Handle the submission of the booking form
	    $bookingForm.submit(function() {
	    	var requiredFieldsFilled = checkBookingformFields();	    	
	    	if (requiredFieldsFilled == 'radioOrSuborderMissing') {
	    		$.mobile.changePage($bookingMissingPage, {role:'dialog'});
	    		return false;
	    		}
	    	if (requiredFieldsFilled == 'commentMissing') {
	    		$.mobile.changePage($commentMissingPage, {role:'dialog'});
	    		return false;
	    		}
	    	//When all needed information present fire the ajax request to save the timereport
	    	ajaxCall(bookingSubmitUrl,'POST',$bookingForm.serialize(), setBookingSuccessPage );	    	
	    	return false;
	    });
	    
	    // Highlighting the comment input if comments are required.
	    $orderSelect.change(function(){
	    	var state = $orderSelect.find('option:selected').data('comreq');
	          if(state == true){
	        	  $commentInputDiv.addClass('required');
	          }
	          else if(state == false){
	        	  $commentInputDiv.removeClass('required');
	          }   
	    });	    
		return {
			getSuborders: getSuborders,
			};
	})();	       
	booking.getSuborders();
});